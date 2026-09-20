package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    var selectedCellInfo by remember { mutableStateOf<Pair<Int, String>?>(null) } // row to cellValue

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
                        text = "$sheetCount Sheet(s) • $rowCount Total Rows",
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

            // Search Bar & Filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search spreadsheet cells...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
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

            // Spreadsheet Tabular Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
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
                                .border(0.5.dp, Color(0xFF1E293B))
                        ) {
                            // Row Number column
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(38.dp)
                                    .background(if (isHeaderRow) Color(0xFF047857) else Color(0xFF1E293B))
                                    .border(0.5.dp, Color(0xFF334155)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isHeaderRow) "#" else row.rowIndex.toString(),
                                    color = if (isHeaderRow) Color.White else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Data cells
                            for (colIdx in 0 until maxCols) {
                                val cellValue = row.cells.getOrNull(colIdx) ?: ""
                                val isCellSelected = selectedCellInfo?.second == cellValue && selectedCellInfo?.first == row.rowIndex

                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(38.dp)
                                        .border(
                                            width = if (isCellSelected) 1.5.dp else 0.5.dp,
                                            color = if (isCellSelected) Color(0xFF10B981) else Color(0xFF334155)
                                        )
                                        .background(if (isCellSelected) Color(0xFF064E3B) else Color.Transparent)
                                        .clickable {
                                            selectedCellInfo = Pair(row.rowIndex, cellValue)
                                        }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = cellValue,
                                        color = when {
                                            isHeaderRow -> Color(0xFFA7F3D0)
                                            isCellSelected -> Color.White
                                            else -> Color(0xFFE2E8F0)
                                        },
                                        fontSize = if (isHeaderRow) 12.sp else 11.sp,
                                        fontWeight = if (isHeaderRow) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cell details footer bar when a cell is clicked
            selectedCellInfo?.let { (rowNum, cellContent) ->
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
                            Text(
                                text = "Row $rowNum Selected:",
                                fontSize = 10.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = cellContent.ifEmpty { "(Empty Cell)" },
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
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
