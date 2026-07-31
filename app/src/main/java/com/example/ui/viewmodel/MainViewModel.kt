package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.RecentFileEntity
import com.example.data.parser.DxfModel
import com.example.data.parser.RlfModel
import com.example.data.parser.StlModel
import com.example.data.parser.ToolpathModel
import com.example.data.repository.FileRepository
import com.example.ui.render3d.StlRenderMode
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
    data class RLF(val model: RlfModel) : ActiveModel()
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

    fun openUri(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val lower = fileName.lowercase()
                when {
                    lower.endsWith(".stl") -> {
                        val stl = repository.parseStlFromUri(uri, fileName)
                        _activeModel.value = ActiveModel.STL(stl)
                    }
                    lower.endsWith(".dxf") -> {
                        val dxf = repository.parseDxfFromUri(uri, fileName)
                        _activeModel.value = ActiveModel.DXF(dxf)
                        _dxfVisibleLayers.value = dxf.layers.toSet()
                    }
                    lower.endsWith(".rlf") -> {
                        val rlf = repository.parseRlfFromUri(uri, fileName)
                        _activeModel.value = ActiveModel.RLF(rlf)
                    }
                    else -> { // .bin, .tap, .nc, .txt, .gcode, .cnc, .din
                        val gcode = repository.parseGCodeFromUri(uri, fileName)
                        _activeModel.value = ActiveModel.GCode(gcode)
                        resetSimulation()
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to parse file: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
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

    fun loadSampleRlf() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val rlf = repository.loadSampleRlf()
                _activeModel.value = ActiveModel.RLF(rlf)
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
