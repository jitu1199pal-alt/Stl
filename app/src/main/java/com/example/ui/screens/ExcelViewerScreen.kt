package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.parser.ExcelRow
import com.example.data.parser.ExcelSheet
import com.example.ui.viewmodel.ActiveModel
import com.example.ui.viewmodel.MainViewModel
import com.example.util.FileTypeResolver

/**
 * Converts 0-indexed column integer to Excel letters:
 * 0 -> A, 1 -> B, 25 -> Z, 26 -> AA, 27 -> AB...
 */
fun getExcelColumnName(index: Int): String {
    var col = index
    val sb = StringBuilder()
    while (col >= 0) {
        val rem = col % 26
        sb.insert(0, ('A'.code + rem).toChar())
        col = (col / 26) - 1
    }
    return sb.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelViewerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val activeModel by viewModel.activeModel.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // If no excel model is currently open, initialize with a standard blank workbook
    LaunchedEffect(activeModel) {
        if (activeModel !is ActiveModel.Excel && !isLoading) {
            viewModel.createBlankExcelWorkbook()
        }
    }

    val excelModel = (activeModel as? ActiveModel.Excel)?.model
    val sheets = excelModel?.sheets ?: emptyList()

    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    // Ensure selected sheet index is always within bounds
    LaunchedEffect(sheets.size) {
        if (selectedSheetIndex >= sheets.size) {
            selectedSheetIndex = (sheets.size - 1).coerceAtLeast(0)
        }
    }

    val currentSheet = sheets.getOrNull(selectedSheetIndex)

    // Active Selected Cell (1-based rowIndex, 0-based colIndex) - Defaults to A1 as in Excel
    var selectedRowIndex by remember { mutableIntStateOf(1) }
    var selectedColIndex by remember { mutableIntStateOf(0) }

    // Formula bar text state
    var formulaText by remember { mutableStateOf("") }
    val formulaFocusRequester = remember { FocusRequester() }

    // Sync formula bar whenever selected cell changes
    LaunchedEffect(selectedSheetIndex, selectedRowIndex, selectedColIndex, currentSheet) {
        val row = currentSheet?.rows?.find { it.rowIndex == selectedRowIndex }
        val cellVal = row?.cells?.getOrNull(selectedColIndex) ?: ""
        formulaText = cellVal
    }

    // Interactive sizing & Zoom controls (matching user's request)
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var columnWidthDp by remember { mutableFloatStateOf(88f) }
    var rowHeightDp by remember { mutableFloatStateOf(26f) }
    var isWrapTextEnabled by remember { mutableStateOf(false) }
    var showResizeToolbar by remember { mutableStateOf(false) }

    // Dialogs for sheet actions
    var showAddSheetDialog by remember { mutableStateOf(false) }
    var newSheetNameInput by remember { mutableStateOf("") }
    var sheetContextMenuIndex by remember { mutableStateOf<Int?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameSheetInput by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val resolved = FileTypeResolver.resolve(context, it)
            viewModel.openResolvedFile(resolved, autoNavigate = false)
        }
    }

    val activeCellName = "${getExcelColumnName(selectedColIndex)}$selectedRowIndex"

    // Excel Colors Palette (Authentic Microsoft Excel style)
    val excelBrandGreen = Color(0xFF107C41)
    val excelDarkGreen = Color(0xFF0B5A2F)
    val formulaBarBg = Color(0xFFF3F2F1)
    val borderLightGray = Color(0xFFD4D4D4)
    val headerGray = Color(0xFFEFEFEF)
    val headerSelectedBg = Color(0xFFD6EADF) // subtle green tint on active row/col header
    val cellTextDark = Color(0xFF201F1E)
    val statusBarBg = Color(0xFFF3F2F1)
    val tabBg = Color(0xFFE8E8E8)
    val activeTabBg = Color(0xFFFFFFFF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
    ) {
        // 1. TOP APP BAR (Professional Excel Theme)
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "XLSX",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = excelBrandGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = excelModel?.fileName ?: "Book1.xlsx",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val totalSheets = sheets.size
                        Text(
                            text = "$totalSheets Sheet(s) • Zoom ${(zoomScale * 100).toInt()}% • Cell $activeCellName",
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("excel_back_btn")) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                // Toggle Row & Column stretch size toolbar
                IconButton(
                    onClick = { showResizeToolbar = !showResizeToolbar },
                    modifier = Modifier.testTag("excel_toggle_resize_btn")
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Resize Columns & Rows",
                        tint = if (showResizeToolbar) Color(0xFFFFD700) else Color.White
                    )
                }

                // Open File button
                IconButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.testTag("excel_open_file_btn")
                ) {
                    Icon(
                        Icons.Default.FileOpen,
                        contentDescription = "Open Excel",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = excelBrandGreen)
        )

        // 2. EXCEL FORMULA BAR (Exact match to sheet1.png)
        Surface(
            color = formulaBarBg,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Name Box (e.g. "A1", "B4")
                Row(
                    modifier = Modifier
                        .width(62.dp)
                        .fillMaxHeight()
                        .background(Color.White, RoundedCornerShape(2.dp))
                        .border(1.dp, borderLightGray, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = activeCellName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = cellTextDark
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color(0xFF605E5C),
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color(0xFFC8C6C4))
                )

                // Cancel Button (X)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            // Reset formula text to original cell value
                            val originalVal = currentSheet?.rows?.find { it.rowIndex == selectedRowIndex }
                                ?.cells?.getOrNull(selectedColIndex) ?: ""
                            formulaText = originalVal
                            keyboardController?.hide()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Cancel edit",
                        tint = Color(0xFFD83B01),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Enter / Commit Button (checkmark)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            viewModel.updateExcelCell(
                                sheetIndex = selectedSheetIndex,
                                rowIndex = selectedRowIndex,
                                colIndex = selectedColIndex,
                                newValue = formulaText
                            )
                            keyboardController?.hide()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Commit edit",
                        tint = excelBrandGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // fx Icon
                Text(
                    text = "fx",
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF605E5C),
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                // Formula / Cell Content Input Field (Full width)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.White, RoundedCornerShape(2.dp))
                        .border(1.dp, borderLightGray, RoundedCornerShape(2.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = formulaText,
                        onValueChange = { formulaText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(formulaFocusRequester)
                            .testTag("excel_formula_input"),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 12.5.sp,
                            color = cellTextDark,
                            fontFamily = FontFamily.Default
                        ),
                        cursorBrush = SolidColor(excelBrandGreen),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                viewModel.updateExcelCell(
                                    sheetIndex = selectedSheetIndex,
                                    rowIndex = selectedRowIndex,
                                    colIndex = selectedColIndex,
                                    newValue = formulaText
                                )
                                keyboardController?.hide()
                            }
                        )
                    )
                }
            }
        }

        // 3. COLUMN & ROW RESIZE STRIP (coloum or rows ko kam jayada size karne ka vesa hi rakhna)
        AnimatedVisibility(visible = showResizeToolbar) {
            Surface(
                color = Color(0xFFF9FAFB),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // COLUMN WIDTH ADJUSTMENT: [-] width [+]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(6.dp))
                                .border(1.dp, borderLightGray, RoundedCornerShape(6.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.ViewColumn,
                                contentDescription = null,
                                tint = excelBrandGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Col Width:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = cellTextDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { columnWidthDp = (columnWidthDp - 15f).coerceIn(45f, 350f) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Narrow Column", tint = excelBrandGreen, modifier = Modifier.size(14.dp))
                            }
                            Text(
                                text = "${columnWidthDp.toInt()}dp",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = excelBrandGreen,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { columnWidthDp = (columnWidthDp + 15f).coerceIn(45f, 350f) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Widen Column", tint = excelBrandGreen, modifier = Modifier.size(14.dp))
                            }
                        }

                        // ROW HEIGHT ADJUSTMENT: [-] height [+]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(6.dp))
                                .border(1.dp, borderLightGray, RoundedCornerShape(6.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.TableRows,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Row Height:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = cellTextDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { rowHeightDp = (rowHeightDp - 4f).coerceIn(18f, 120f) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Shorten Row", tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                            }
                            Text(
                                text = "${rowHeightDp.toInt()}dp",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { rowHeightDp = (rowHeightDp + 4f).coerceIn(18f, 120f) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Tall Row", tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Secondary row: Wrap text toggle, Sample data, Reset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { isWrapTextEnabled = !isWrapTextEnabled },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isWrapTextEnabled) excelBrandGreen else Color(0xFFE5E7EB)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                Icons.Default.WrapText,
                                contentDescription = null,
                                tint = if (isWrapTextEnabled) Color.White else cellTextDark,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isWrapTextEnabled) "Wrap Text ON" else "Wrap Text OFF",
                                fontSize = 11.sp,
                                color = if (isWrapTextEnabled) Color.White else cellTextDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                columnWidthDp = 88f
                                rowHeightDp = 26f
                                zoomScale = 1.0f
                                isWrapTextEnabled = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = cellTextDark, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Standard Size", fontSize = 11.sp, color = cellTextDark)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        TextButton(
                            onClick = { viewModel.loadSampleExcel() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Load Cutting List", fontSize = 11.sp, color = excelBrandGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. MAIN EXCEL SPREADSHEET GRID (Exact visual fidelity to sheet1.png)
        val sheetRows = currentSheet?.rows ?: emptyList()
        // Ensure at least 35 rows and 15 columns are displayed just like a real Excel sheet
        val totalDisplayRows = maxOf(sheetRows.size, 35)
        val totalDisplayCols = maxOf(currentSheet?.columnCount ?: 10, 15)

        // Dynamic scaled dimensions based on user stretch & zoom scale
        val scaledColWidth = (columnWidthDp * zoomScale).coerceAtLeast(35f).dp
        val scaledRowHeight = (rowHeightDp * zoomScale).coerceAtLeast(18f).dp
        val scaledRowNumWidth = (38f * zoomScale).coerceIn(30f, 65f).dp
        val cellFontSize = (11f * zoomScale).coerceIn(7f, 22f).sp
        val headerFontSize = (11f * zoomScale).coerceIn(8f, 22f).sp

        val horizontalScrollState = rememberScrollState()
        val verticalScrollState = rememberLazyListState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                // Pinch-to-zoom gesture on the spreadsheet page (page zoom in / zoom out)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.5f, 2.5f)
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // A. FIXED COLUMN HEADERS ROW (A, B, C, D, E, F, G, H, I...)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaledRowHeight)
                        .background(headerGray)
                        .border(width = 0.8.dp, color = borderLightGray)
                ) {
                    // Top-Left Corner Box (Triangle select-all box)
                    Box(
                        modifier = Modifier
                            .width(scaledRowNumWidth)
                            .fillMaxHeight()
                            .background(headerGray)
                            .border(width = 0.8.dp, color = borderLightGray)
                            .clickable {
                                selectedRowIndex = 1
                                selectedColIndex = 0
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Diagonal triangle mark
                        Text("◢", fontSize = 9.sp, color = Color(0xFF9E9E9E))
                    }

                    // Horizontally scrolled column headers: A, B, C, D...
                    Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                        for (colIdx in 0 until totalDisplayCols) {
                            val colName = getExcelColumnName(colIdx)
                            val isColActive = selectedColIndex == colIdx
                            Box(
                                modifier = Modifier
                                    .width(scaledColWidth)
                                    .fillMaxHeight()
                                    .background(if (isColActive) headerSelectedBg else headerGray)
                                    .border(width = 0.8.dp, color = borderLightGray)
                                    .clickable {
                                        selectedColIndex = colIdx
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = colName,
                                    fontSize = headerFontSize,
                                    fontWeight = if (isColActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isColActive) excelBrandGreen else cellTextDark,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // B. SPREADSHEET CELLS (Row numbers + Grid cells)
                LazyColumn(
                    state = verticalScrollState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("excel_grid_lazy_column")
                ) {
                    items(totalDisplayRows) { rowIdxZero ->
                        val rowIndex = rowIdxZero + 1
                        val isRowActive = selectedRowIndex == rowIndex
                        val existingRow = sheetRows.find { it.rowIndex == rowIndex }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isWrapTextEnabled) Modifier.defaultMinSize(minHeight = scaledRowHeight)
                                    else Modifier.height(scaledRowHeight)
                                )
                        ) {
                            // Row Header (1, 2, 3, 4, 5, 6...)
                            Box(
                                modifier = Modifier
                                    .width(scaledRowNumWidth)
                                    .then(
                                        if (isWrapTextEnabled) Modifier.defaultMinSize(minHeight = scaledRowHeight)
                                        else Modifier.height(scaledRowHeight)
                                    )
                                    .background(if (isRowActive) headerSelectedBg else headerGray)
                                    .border(width = 0.8.dp, color = borderLightGray)
                                    .clickable {
                                        selectedRowIndex = rowIndex
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = rowIndex.toString(),
                                    fontSize = headerFontSize,
                                    fontWeight = if (isRowActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isRowActive) excelBrandGreen else cellTextDark,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Horizontally scrolled cells in this row
                            Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                                for (colIdx in 0 until totalDisplayCols) {
                                    val cellValue = existingRow?.cells?.getOrNull(colIdx) ?: ""
                                    val isCellSelected = selectedRowIndex == rowIndex && selectedColIndex == colIdx

                                    Box(
                                        modifier = Modifier
                                            .width(scaledColWidth)
                                            .then(
                                                if (isWrapTextEnabled) Modifier.defaultMinSize(minHeight = scaledRowHeight)
                                                else Modifier.height(scaledRowHeight)
                                            )
                                            .background(Color.White)
                                            .border(
                                                width = if (isCellSelected) 2.dp else 0.8.dp,
                                                color = if (isCellSelected) excelBrandGreen else borderLightGray
                                            )
                                            .clickable {
                                                selectedRowIndex = rowIndex
                                                selectedColIndex = colIdx
                                                formulaText = cellValue
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = cellValue,
                                            fontSize = cellFontSize,
                                            color = cellTextDark,
                                            maxLines = if (isWrapTextEnabled) 8 else 1,
                                            overflow = if (isWrapTextEnabled) TextOverflow.Clip else TextOverflow.Ellipsis
                                        )

                                        // Excel iconic small green fill handle square at the bottom-right of active cell
                                        if (isCellSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(excelBrandGreen)
                                                    .align(Alignment.BottomEnd)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. EXCEL SHEET TABS & (+) ADD SHEET BUTTON (Matching sheet1.png bottom bar)
        Surface(
            color = tabBg,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Navigation arrows: [◀] [▶]
                IconButton(
                    onClick = {
                        if (selectedSheetIndex > 0) {
                            selectedSheetIndex--
                            selectedRowIndex = 1
                            selectedColIndex = 0
                        }
                    },
                    enabled = selectedSheetIndex > 0,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Previous Sheet",
                        tint = if (selectedSheetIndex > 0) Color(0xFF605E5C) else Color(0xFFBDBDBD),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (selectedSheetIndex < sheets.size - 1) {
                            selectedSheetIndex++
                            selectedRowIndex = 1
                            selectedColIndex = 0
                        }
                    },
                    enabled = selectedSheetIndex < sheets.size - 1,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Next Sheet",
                        tint = if (selectedSheetIndex < sheets.size - 1) Color(0xFF605E5C) else Color(0xFFBDBDBD),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Scrollable Sheet Tabs
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sheets.forEachIndexed { idx, sheet ->
                        val isSelected = selectedSheetIndex == idx

                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (isSelected) activeTabBg else tabBg)
                                .clickable {
                                    selectedSheetIndex = idx
                                    selectedRowIndex = 1
                                    selectedColIndex = 0
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = sheet.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) excelBrandGreen else Color(0xFF605E5C)
                                    )
                                    if (isSelected && sheets.size > 1) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { sheetContextMenuIndex = idx },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "Sheet options",
                                                tint = Color(0xFF605E5C),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }

                                // Green underline for active tab
                                if (isSelected) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(28.dp)
                                            .height(2.5.dp)
                                            .background(excelBrandGreen, RoundedCornerShape(1.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                // (+) ADD SHEET BUTTON (Exact circle plus matching sheet1.png)
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, borderLightGray, CircleShape)
                        .clickable {
                            // Automatically add new sheet, e.g. Sheet2, Sheet3
                            val newIndex = viewModel.addNewExcelSheet()
                            selectedSheetIndex = newIndex
                            selectedRowIndex = 1
                            selectedColIndex = 0
                            Toast.makeText(context, "Added new Sheet", Toast.LENGTH_SHORT).show()
                        }
                        .testTag("excel_add_sheet_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add New Sheet",
                        tint = excelBrandGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Copy cell content shortcut
                IconButton(
                    onClick = {
                        val currentVal = formulaText
                        if (currentVal.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Excel Cell", currentVal))
                            Toast.makeText(context, "Copied: $currentVal", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy cell",
                        tint = Color(0xFF605E5C),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 6. BOTTOM STATUS BAR & ZOOM CONTROLS (Exact match to sheet1.png bottom right)
        Surface(
            color = statusBarBg,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .border(width = 0.5.dp, color = borderLightGray)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: "Ready" status and cell info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ready",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF605E5C)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = null,
                        tint = Color(0xFF8A8886),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = activeCellName,
                        fontSize = 11.sp,
                        color = Color(0xFF605E5C),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Right: Zoom In, Zoom Out, Zoom Slider, Percentage (Matching sheet1.png)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Zoom Out Button [-]
                    IconButton(
                        onClick = { zoomScale = (zoomScale - 0.10f).coerceIn(0.5f, 2.5f) },
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("excel_zoom_out_btn")
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Zoom Out",
                            tint = Color(0xFF605E5C),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Zoom Slider
                    Slider(
                        value = zoomScale,
                        onValueChange = { zoomScale = it },
                        valueRange = 0.5f..2.5f,
                        modifier = Modifier
                            .width(90.dp)
                            .height(20.dp)
                            .testTag("excel_zoom_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = excelBrandGreen,
                            activeTrackColor = excelBrandGreen,
                            inactiveTrackColor = Color(0xFFC8C6C4)
                        )
                    )

                    // Zoom In Button [+]
                    IconButton(
                        onClick = { zoomScale = (zoomScale + 0.10f).coerceIn(0.5f, 2.5f) },
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("excel_zoom_in_btn")
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Zoom In",
                            tint = Color(0xFF605E5C),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Zoom Percentage Badge (Click to reset to 100%)
                    Text(
                        text = "${(zoomScale * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = cellTextDark,
                        modifier = Modifier
                            .clickable { zoomScale = 1.0f }
                            .padding(horizontal = 4.dp)
                            .testTag("excel_zoom_percentage_text")
                    )
                }
            }
        }
    }

    // Sheet context menu (Delete / Rename)
    sheetContextMenuIndex?.let { sheetIdx ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { sheetContextMenuIndex = null }
        ) {
            DropdownMenuItem(
                text = { Text("Delete Sheet", color = Color(0xFFD83B01)) },
                onClick = {
                    val nextIdx = viewModel.deleteExcelSheet(sheetIdx)
                    selectedSheetIndex = nextIdx
                    sheetContextMenuIndex = null
                    Toast.makeText(context, "Deleted Sheet", Toast.LENGTH_SHORT).show()
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFD83B01))
                },
                enabled = sheets.size > 1
            )
        }
    }
}
