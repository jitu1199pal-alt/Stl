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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ActiveModel
import com.example.ui.viewmodel.MainViewModel
import com.example.util.FileTypeResolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelViewerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activeModel by viewModel.activeModel.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val excelModel = (activeModel as? ActiveModel.Excel)?.model

    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCellInfo by remember { mutableStateOf<Triple<Int, Int, String>?>(null) } // rowIndex, colIndex, cellValue

    // Zoom and Stretch controls
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var columnWidthDp by remember { mutableFloatStateOf(150f) }
    var rowHeightDp by remember { mutableFloatStateOf(38f) }
    var isWrapTextEnabled by remember { mutableStateOf(false) }
    var showStretchPanel by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val resolved = FileTypeResolver.resolve(context, it)
            viewModel.openResolvedFile(resolved, autoNavigate = false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Column {
                    val fileName = excelModel?.fileName ?: "Excel Spreadsheet"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF10B981), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "EXCEL / CSV",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                    val sheetCount = excelModel?.sheets?.size ?: 0
                    val rowCount = excelModel?.totalRows ?: 0
                    Text(
                        text = "$sheetCount Sheet(s) • $rowCount Rows • Zoom ${(zoomScale * 100).toInt()}% • Col ${columnWidthDp.toInt()}dp",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                // Toggle Stretch & Zoom controls
                IconButton(onClick = { showStretchPanel = !showStretchPanel }) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Zoom & Stretch Settings",
                        tint = if (showStretchPanel) Color(0xFF10B981) else Color.White
                    )
                }
                IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Icon(
                        Icons.Default.FileOpen,
                        contentDescription = "Open Excel",
                        tint = Color(0xFF10B981)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF131D2F))
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF10B981))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading Spreadsheet...", color = Color.White, fontSize = 13.sp)
                }
            }
        } else if (excelModel == null || excelModel.sheets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.TableChart,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Excel / CSV File Loaded",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Open any .xlsx, .xls or .csv spreadsheet from WhatsApp or storage",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.loadSampleExcel() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
                        ) {
                            Text("Load Sample Cutting List", color = Color.White)
                        }
                        Button(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("Open File", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            val sheets = excelModel.sheets
            val currentSheet = sheets.getOrNull(selectedSheetIndex) ?: sheets.first()

            // Sheet selector tabs if multiple sheets exist
            if (sheets.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = selectedSheetIndex.coerceIn(0, sheets.size - 1),
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF10B981),
                    edgePadding = 12.dp,
                    indicator = { tabPositions ->
                        if (selectedSheetIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedSheetIndex]),
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                ) {
                    sheets.forEachIndexed { idx, sheet ->
                        Tab(
                            selected = selectedSheetIndex == idx,
                            onClick = {
                                selectedSheetIndex = idx
                                selectedCellInfo = null
                            },
                            text = {
                                Text(
                                    text = "${sheet.name} (${sheet.rows.size})",
                                    fontWeight = if (selectedSheetIndex == idx) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedSheetIndex == idx) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
            }

            // Quick Control Bar (Zoom + Column Width + Row Height + Wrap Text)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF162032)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Row 1: Search & Quick Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search cells...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Wrap Text Toggle Button
                        Button(
                            onClick = { isWrapTextEnabled = !isWrapTextEnabled },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isWrapTextEnabled) Color(0xFF10B981) else Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(
                                Icons.Default.WrapText,
                                contentDescription = "Wrap Text",
                                tint = if (isWrapTextEnabled) Color.Black else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isWrapTextEnabled) "Wrap ON" else "Wrap OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isWrapTextEnabled) Color.Black else Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2: Zoom & Stretch Interactive Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. ZOOM Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = { zoomScale = (zoomScale - 0.15f).coerceIn(0.5f, 3.0f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                            }
                            Text(
                                text = "${(zoomScale * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .clickable { zoomScale = 1.0f }
                                    .padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.15f).coerceIn(0.5f, 3.0f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                            }
                        }

                        // 2. COLUMN WIDTH STRETCH Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ViewColumn, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            IconButton(
                                onClick = { columnWidthDp = (columnWidthDp - 25f).coerceIn(60f, 450f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Narrow Column", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                            Text(
                                text = "${columnWidthDp.toInt()}w",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                            IconButton(
                                onClick = { columnWidthDp = (columnWidthDp + 25f).coerceIn(60f, 450f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Widen Column", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }

                        // 3. ROW HEIGHT STRETCH Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.TableRows, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            IconButton(
                                onClick = { rowHeightDp = (rowHeightDp - 8f).coerceIn(26f, 150f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Shorten Row", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                            Text(
                                text = "${rowHeightDp.toInt()}h",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                            IconButton(
                                onClick = { rowHeightDp = (rowHeightDp + 8f).coerceIn(26f, 150f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Stretch Row", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }

                        // Reset View Button
                        IconButton(
                            onClick = {
                                zoomScale = 1.0f
                                columnWidthDp = 150f
                                rowHeightDp = 38f
                                isWrapTextEnabled = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Detailed Stretch & Zoom Sliders (Collapsible)
                    AnimatedVisibility(visible = showStretchPanel) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .background(Color(0xFF0B111E), RoundedCornerShape(8.dp))
                                .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            // Zoom Slider
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Zoom: ${(zoomScale * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                                Slider(
                                    value = zoomScale,
                                    onValueChange = { zoomScale = it },
                                    valueRange = 0.5f..2.5f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981))
                                )
                            }

                            // Column Width Slider
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Col Width: ${columnWidthDp.toInt()}dp", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                                Slider(
                                    value = columnWidthDp,
                                    onValueChange = { columnWidthDp = it },
                                    valueRange = 70f..400f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF38BDF8))
                                )
                            }

                            // Row Height Slider
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Row H: ${rowHeightDp.toInt()}dp", fontSize = 11.sp, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                                Slider(
                                    value = rowHeightDp,
                                    onValueChange = { rowHeightDp = it },
                                    valueRange = 26f..120f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFBBF24), activeTrackColor = Color(0xFFFBBF24))
                                )
                            }

                            // Presets Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = columnWidthDp == 100f,
                                    onClick = { columnWidthDp = 100f; rowHeightDp = 30f },
                                    label = { Text("Compact", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.White)
                                )
                                FilterChip(
                                    selected = columnWidthDp == 160f,
                                    onClick = { columnWidthDp = 160f; rowHeightDp = 40f },
                                    label = { Text("Standard", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.White)
                                )
                                FilterChip(
                                    selected = columnWidthDp == 260f,
                                    onClick = { columnWidthDp = 260f; rowHeightDp = 56f },
                                    label = { Text("Extra Wide", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.White)
                                )
                            }
                        }
                    }
                }
            }

            // Filter rows based on search
            val filteredRows = remember(currentSheet, searchQuery) {
                if (searchQuery.isBlank()) {
                    currentSheet.rows
                } else {
                    currentSheet.rows.filterIndexed { index, row ->
                        index == 0 || row.cells.any { it.contains(searchQuery, ignoreCase = true) }
                    }
                }
            }

            val maxCols = currentSheet.columnCount.coerceAtLeast(1)
            val horizontalScrollState = rememberScrollState()

            // Scaled dynamic dimensions
            val cellWidth = (columnWidthDp * zoomScale).dp
            val cellHeight = (rowHeightDp * zoomScale).dp
            val rowNumWidth = (44f * zoomScale).coerceIn(34f, 80f).dp
            val fontSize = (((if (isWrapTextEnabled) 11f else 11.5f) * zoomScale).coerceIn(8f, 26f)).sp
            val headerFontSize = ((12f * zoomScale).coerceIn(9f, 28f)).sp
            val cellPaddingHorizontal = (8f * zoomScale).coerceIn(4f, 16f).dp

            // Spreadsheet Tabular Grid with Pinch-to-Zoom Gesture Support
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(0.5f, 3.0f)
                        }
                    }
                    .horizontalScroll(horizontalScrollState)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    itemsIndexed(filteredRows) { rowIndex, row ->
                        val isHeaderRow = (rowIndex == 0 && searchQuery.isBlank())
                        Row(
                            modifier = Modifier
                                .background(
                                    when {
                                        isHeaderRow -> Color(0xFF064E3B)
                                        rowIndex % 2 == 1 -> Color(0xFF131D2F)
                                        else -> Color(0xFF0F172A)
                                    }
                                )
                                .border(0.5.dp, Color(0xFF1E293B)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Row Number column
                            Box(
                                modifier = Modifier
                                    .width(rowNumWidth)
                                    .then(
                                        if (isWrapTextEnabled) Modifier.defaultMinSize(minHeight = cellHeight)
                                        else Modifier.height(cellHeight)
                                    )
                                    .background(if (isHeaderRow) Color(0xFF047857) else Color(0xFF1E293B))
                                    .border(0.5.dp, Color(0xFF334155)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isHeaderRow) "#" else row.rowIndex.toString(),
                                    color = if (isHeaderRow) Color.White else Color(0xFF94A3B8),
                                    fontSize = fontSize * 0.95f,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Data cells
                            for (colIdx in 0 until maxCols) {
                                val cellValue = row.cells.getOrNull(colIdx) ?: ""
                                val isCellSelected = selectedCellInfo?.first == row.rowIndex && selectedCellInfo?.second == colIdx

                                Box(
                                    modifier = Modifier
                                        .width(cellWidth)
                                        .then(
                                            if (isWrapTextEnabled) Modifier.defaultMinSize(minHeight = cellHeight)
                                            else Modifier.height(cellHeight)
                                        )
                                        .border(
                                            width = if (isCellSelected) 1.5.dp else 0.5.dp,
                                            color = if (isCellSelected) Color(0xFF10B981) else Color(0xFF334155)
                                        )
                                        .background(if (isCellSelected) Color(0xFF065F46) else Color.Transparent)
                                        .clickable {
                                            selectedCellInfo = Triple(row.rowIndex, colIdx, cellValue)
                                        }
                                        .padding(horizontal = cellPaddingHorizontal, vertical = 2.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = cellValue,
                                        color = when {
                                            isHeaderRow -> Color(0xFFA7F3D0)
                                            isCellSelected -> Color.White
                                            else -> Color(0xFFE2E8F0)
                                        },
                                        fontSize = if (isHeaderRow) headerFontSize else fontSize,
                                        fontWeight = if (isHeaderRow) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = if (isWrapTextEnabled) 10 else 1,
                                        overflow = if (isWrapTextEnabled) TextOverflow.Clip else TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cell details footer bar when a cell is clicked
            selectedCellInfo?.let { (rowNum, colNum, cellContent) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Row $rowNum • Col ${colNum + 1}:",
                                    fontSize = 11.sp,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF064E3B), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text("Selected Cell", fontSize = 9.sp, color = Color(0xFFA7F3D0))
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = cellContent.ifEmpty { "(Empty Cell)" },
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Copy Cell Value Button
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Cell Content", cellContent)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied cell text", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF10B981))
                        }

                        IconButton(onClick = { selectedCellInfo = null }) {
                            Icon(Icons.Default.Clear, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }
    }
}
