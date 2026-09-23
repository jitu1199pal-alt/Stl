package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.RecentFileEntity
import com.example.data.parser.DxfModel
import com.example.data.parser.ExcelModel
import com.example.data.parser.ExcelRow
import com.example.data.parser.ExcelSheet
import com.example.data.parser.PdfDocumentInfo
import com.example.data.parser.StlModel
import com.example.data.parser.ToolpathModel
import com.example.data.repository.FileRepository
import com.example.ui.render3d.StlRenderMode
import com.example.util.CadFileType
import com.example.util.FileTypeResolver
import com.example.util.ResolvedFileInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class ActiveModel {
    object None : ActiveModel()
    data class GCode(val model: ToolpathModel) : ActiveModel()
    data class STL(val model: StlModel) : ActiveModel()
    data class DXF(val model: DxfModel) : ActiveModel()
    data class Excel(val model: ExcelModel) : ActiveModel()
    data class Pdf(val info: PdfDocumentInfo) : ActiveModel()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FileRepository(application)

    val recentFiles: StateFlow<List<RecentFileEntity>> = repository.recentFiles.let { flow ->
        val state = MutableStateFlow<List<RecentFileEntity>>(emptyList())
        viewModelScope.launch {
            flow.collect { list -> state.value = list }
        }
        state.asStateFlow()
    }

    private val _activeModel = MutableStateFlow<ActiveModel>(ActiveModel.None)
    val activeModel: StateFlow<ActiveModel> = _activeModel.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Simulation playback controls
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSegmentIndex = MutableStateFlow(0)
    val currentSegmentIndex: StateFlow<Int> = _currentSegmentIndex.asStateFlow()

    private val _speedMultiplier = MutableStateFlow(1f)
    val speedMultiplier: StateFlow<Float> = _speedMultiplier.asStateFlow()

    private val _elapsedTimeSeconds = MutableStateFlow(0f)
    val elapsedTimeSeconds: StateFlow<Float> = _elapsedTimeSeconds.asStateFlow()

    // STL view state
    private val _stlRenderMode = MutableStateFlow(StlRenderMode.SOLID)
    val stlRenderMode: StateFlow<StlRenderMode> = _stlRenderMode.asStateFlow()

    // DXF view state
    private val _dxfVisibleLayers = MutableStateFlow<Set<String>>(emptySet())
    val dxfVisibleLayers: StateFlow<Set<String>> = _dxfVisibleLayers.asStateFlow()

    private var simulationJob: Job? = null

    init {
        // Load default sample on launch so 3D viewer is instantly alive
        loadSampleGCode()
    }

    // Target destination when file is opened via WhatsApp or other apps
    private val _pendingDestination = MutableStateFlow<String?>(null)
    val pendingDestination: StateFlow<String?> = _pendingDestination.asStateFlow()

    fun consumePendingDestination() {
        _pendingDestination.value = null
    }

    fun openResolvedFile(info: ResolvedFileInfo, autoNavigate: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                when {
                    info.fileType == CadFileType.EXCEL -> {
                        val excel = repository.parseExcelFromUri(info.uri, info.fileName)
                        _activeModel.value = ActiveModel.Excel(excel)
                        if (autoNavigate) {
                            _pendingDestination.value = "excel_viewer"
                        }
                    }
                    info.fileType == CadFileType.PDF -> {
                        val pdf = repository.preparePdfFromUri(info.uri, info.fileName)
                        _activeModel.value = ActiveModel.Pdf(pdf)
                        if (autoNavigate) {
                            _pendingDestination.value = "pdf_viewer"
                        }
                    }
                    info.fileType.is3DModel -> {
                        val stl = repository.parse3DModelFromUri(info.uri, info.fileName, info.fileType)
                        _activeModel.value = ActiveModel.STL(stl)
                        if (autoNavigate) {
                            _pendingDestination.value = "stl_viewer"
                        }
                    }
                    info.fileType == CadFileType.DXF || info.fileType == CadFileType.DWG -> {
                        val dxf = repository.parseDxfFromUri(info.uri, info.fileName)
                        _activeModel.value = ActiveModel.DXF(dxf)
                        _dxfVisibleLayers.value = dxf.layers.toSet()
                        if (autoNavigate) {
                            _pendingDestination.value = "dxf_viewer"
                        }
                    }
                    else -> {
                        val gcode = repository.parseGCodeFromUri(info.uri, info.fileName)
                        _activeModel.value = ActiveModel.GCode(gcode)
                        resetSimulation()
                        if (autoNavigate) {
                            _pendingDestination.value = "program_viewer"
                        }
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to parse file: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun openUri(uri: Uri, fileName: String, autoNavigate: Boolean = false) {
        val detected = FileTypeResolver.detectType(getApplication(), uri, fileName)
        val info = ResolvedFileInfo(uri, fileName, detected)
        openResolvedFile(info, autoNavigate = autoNavigate)
    }

    fun loadSampleGCode() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val gcode = repository.loadSampleGCode()
                _activeModel.value = ActiveModel.GCode(gcode)
                resetSimulation()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSampleStl() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val stl = repository.loadSampleStl()
                _activeModel.value = ActiveModel.STL(stl)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSampleDxf() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dxf = repository.loadSampleDxf()
                _activeModel.value = ActiveModel.DXF(dxf)
                _dxfVisibleLayers.value = dxf.layers.toSet()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadArchitecturalDxf() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dxf = repository.loadArchitecturalDxf()
                _activeModel.value = ActiveModel.DXF(dxf)
                _dxfVisibleLayers.value = dxf.layers.toSet()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSampleExcel() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val excel = repository.loadSampleExcel()
                _activeModel.value = ActiveModel.Excel(excel)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createBlankExcelWorkbook(workbookName: String = "Book1.xlsx") {
        val defaultCols = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O")
        val blankRows = (1..35).map { r ->
            ExcelRow(r, List(defaultCols.size) { "" })
        }
        val sheet1 = ExcelSheet(
            name = "Sheet1",
            rows = blankRows,
            columnCount = defaultCols.size
        )
        _activeModel.value = ActiveModel.Excel(ExcelModel(workbookName, listOf(sheet1)))
    }

    fun addNewExcelSheet(sheetName: String? = null): Int {
        val currentExcel = (_activeModel.value as? ActiveModel.Excel)?.model
        val defaultCols = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O")
        val blankRows = (1..35).map { r ->
            ExcelRow(r, List(defaultCols.size) { "" })
        }
        val nextIdx = (currentExcel?.sheets?.size ?: 0) + 1
        val finalName = sheetName?.takeIf { it.isNotBlank() } ?: "Sheet$nextIdx"
        val newSheet = ExcelSheet(
            name = finalName,
            rows = blankRows,
            columnCount = defaultCols.size
        )
        if (currentExcel == null) {
            _activeModel.value = ActiveModel.Excel(ExcelModel("Book1.xlsx", listOf(newSheet)))
            return 0
        } else {
            val updated = currentExcel.copy(sheets = currentExcel.sheets + newSheet)
            _activeModel.value = ActiveModel.Excel(updated)
            return updated.sheets.lastIndex
        }
    }

    fun updateExcelCell(sheetIndex: Int, rowIndex: Int, colIndex: Int, newValue: String) {
        val currentExcel = (_activeModel.value as? ActiveModel.Excel)?.model ?: return
        if (sheetIndex !in currentExcel.sheets.indices) return
        val targetSheet = currentExcel.sheets[sheetIndex]
        val maxCols = maxOf(targetSheet.columnCount, colIndex + 1)
        
        // Ensure row exists
        val existingRow = targetSheet.rows.find { it.rowIndex == rowIndex }
        val updatedRows = if (existingRow != null) {
            targetSheet.rows.map { row ->
                if (row.rowIndex == rowIndex) {
                    val mutableCells = row.cells.toMutableList()
                    while (mutableCells.size <= colIndex) {
                        mutableCells.add("")
                    }
                    mutableCells[colIndex] = newValue
                    row.copy(cells = mutableCells)
                } else {
                    row
                }
            }
        } else {
            val mutableRows = targetSheet.rows.toMutableList()
            val newCells = MutableList(colIndex + 1) { "" }
            newCells[colIndex] = newValue
            mutableRows.add(ExcelRow(rowIndex, newCells))
            mutableRows.sortedBy { it.rowIndex }
        }

        val updatedSheet = targetSheet.copy(rows = updatedRows, columnCount = maxCols)
        val updatedSheets = currentExcel.sheets.toMutableList()
        updatedSheets[sheetIndex] = updatedSheet
        _activeModel.value = ActiveModel.Excel(currentExcel.copy(sheets = updatedSheets))
    }

    fun deleteExcelSheet(sheetIndex: Int): Int {
        val currentExcel = (_activeModel.value as? ActiveModel.Excel)?.model ?: return 0
        if (currentExcel.sheets.size <= 1) return 0 // Keep at least one sheet
        if (sheetIndex !in currentExcel.sheets.indices) return 0
        val updatedSheets = currentExcel.sheets.toMutableList()
        updatedSheets.removeAt(sheetIndex)
        _activeModel.value = ActiveModel.Excel(currentExcel.copy(sheets = updatedSheets))
        return (sheetIndex - 1).coerceAtLeast(0)
    }

    fun loadSamplePdf() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val pdf = repository.loadSamplePdf()
                _activeModel.value = ActiveModel.Pdf(pdf)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCncBracketDxf() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dxf = repository.loadCncBracketDxf()
                _activeModel.value = ActiveModel.DXF(dxf)
                _dxfVisibleLayers.value = dxf.layers.toSet()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playSimulation() {
        if (_isPlaying.value) return
        val currentModel = (_activeModel.value as? ActiveModel.GCode)?.model ?: return
        if (_currentSegmentIndex.value >= currentModel.segments.size - 1) {
            _currentSegmentIndex.value = 0
            _elapsedTimeSeconds.value = 0f
        }

        _isPlaying.value = true
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (isActive && _isPlaying.value) {
                val segments = currentModel.segments
                val idx = _currentSegmentIndex.value
                val totalSegs = segments.size
                if (idx < totalSegs - 1) {
                    val mult = _speedMultiplier.value
                    val stepSize = ((totalSegs / 400f) * mult).toInt().coerceAtLeast(1)
                    val nextIdx = (idx + stepSize).coerceAtMost(totalSegs - 1)

                    val delayTimeMs = if (mult < 1f) (30f / mult).toLong().coerceIn(2L, 200L) else 25L
                    delay(delayTimeMs)

                    _currentSegmentIndex.value = nextIdx
                    val seg = segments[idx]
                    _elapsedTimeSeconds.value += (seg.lengthMm / (seg.feedRate.coerceAtLeast(100f) / 60f)) * stepSize / mult
                } else {
                    _isPlaying.value = false
                    break
                }
            }
        }
    }

    fun pauseSimulation() {
        _isPlaying.value = false
        simulationJob?.cancel()
    }

    fun resetSimulation() {
        pauseSimulation()
        _currentSegmentIndex.value = 0
        _elapsedTimeSeconds.value = 0f
    }

    fun seekToSegment(index: Int) {
        val model = (_activeModel.value as? ActiveModel.GCode)?.model ?: return
        val safeIndex = index.coerceIn(0, (model.segments.size - 1).coerceAtLeast(0))
        _currentSegmentIndex.value = safeIndex
    }

    fun setSpeedMultiplier(speed: Float) {
        _speedMultiplier.value = speed
        if (_isPlaying.value) {
            // Live update simulation coroutine
            pauseSimulation()
            playSimulation()
        }
    }

    fun setStlRenderMode(mode: StlRenderMode) {
        _stlRenderMode.value = mode
    }

    fun toggleDxfLayer(layer: String) {
        val current = _dxfVisibleLayers.value.toMutableSet()
        if (current.contains(layer)) current.remove(layer) else current.add(layer)
        _dxfVisibleLayers.value = current
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun deleteRecentFile(id: Long) {
        viewModelScope.launch { repository.deleteRecentFile(id) }
    }
}
