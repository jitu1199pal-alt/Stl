package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AdMobBanner
import com.example.ui.viewmodel.ActiveModel
import com.example.ui.viewmodel.MainViewModel
import com.example.util.CadFileType
import com.example.util.FileTypeResolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToProgramViewer: () -> Unit,
    onNavigateToStlViewer: () -> Unit,
    onNavigateToDxfViewer: () -> Unit,
    onNavigateToCadFolder: () -> Unit,
    onNavigateToExcelViewer: () -> Unit,
    onNavigateToPdfViewer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToPrivacy: () -> Unit
) {
    val context = LocalContext.current
    val recentFiles by viewModel.recentFiles.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()

    // Helper to extract real display filename from Android Content Uri
    fun queryDisplayName(uri: Uri): String? {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // Universal SAF Document Picker (opens full System File Manager with side drawer for all folders)
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val resolved = FileTypeResolver.resolve(context, it)
            viewModel.openResolvedFile(resolved, autoNavigate = false)
            when {
                resolved.fileType == CadFileType.EXCEL -> onNavigateToExcelViewer()
                resolved.fileType == CadFileType.PDF -> onNavigateToPdfViewer()
                resolved.fileType.is3DModel -> onNavigateToStlViewer()
                resolved.fileType == CadFileType.DXF || resolved.fileType == CadFileType.DWG -> onNavigateToDxfViewer()
                else -> onNavigateToProgramViewer()
            }
        }
    }

    // Dedicated 3D Model Picker
    val stlFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val resolved = FileTypeResolver.resolve(context, it)
            viewModel.openResolvedFile(resolved, autoNavigate = false)
            onNavigateToStlViewer()
        }
    }

    // Dedicated AutoCAD File Picker: directly opens AutoCAD Drawing Viewer upon selection
    val autocadFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val resolved = FileTypeResolver.resolve(context, it)
            viewModel.openResolvedFile(resolved, autoNavigate = false)
            onNavigateToDxfViewer()
        }
    }

    // Dedicated Excel / CSV File Picker: directly opens Excel Viewer
    val excelFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val resolved = FileTypeResolver.resolve(context, it)
            viewModel.openResolvedFile(resolved, autoNavigate = false)
            onNavigateToExcelViewer()
        }
    }

    // Dedicated PDF File Picker: directly opens PDF Document Viewer
    val pdfFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val resolved = FileTypeResolver.resolve(context, it)
            viewModel.openResolvedFile(resolved, autoNavigate = false)
            onNavigateToPdfViewer()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Toolpath Simulation",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = "3D CNC Toolpath, STL Mesh & DXF Viewer",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                    IconButton(onClick = onNavigateToHelp) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Help", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        bottomBar = {
            AdMobBanner()
        },
        containerColor = Color(0xFF020617)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick Resume Banner if model is loaded
            if (activeModel !is ActiveModel.None) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (activeModel) {
                                    is ActiveModel.GCode -> onNavigateToProgramViewer()
                                    is ActiveModel.STL -> onNavigateToStlViewer()
                                    is ActiveModel.DXF -> onNavigateToDxfViewer()
                                    else -> {}
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (activeModel) {
                                    is ActiveModel.GCode -> Icons.Default.Code
                                    is ActiveModel.STL -> Icons.Default.ViewInAr
                                    else -> Icons.Default.Layers
                                },
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Currently Active Model",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = when (activeModel) {
                                        is ActiveModel.GCode -> (activeModel as ActiveModel.GCode).model.fileName
                                        is ActiveModel.STL -> (activeModel as ActiveModel.STL).model.fileName
                                        is ActiveModel.DXF -> (activeModel as ActiveModel.DXF).model.fileName
                                        else -> ""
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Button(
                                onClick = {
                                    when (activeModel) {
                                        is ActiveModel.GCode -> onNavigateToProgramViewer()
                                        is ActiveModel.STL -> onNavigateToStlViewer()
                                        is ActiveModel.DXF -> onNavigateToDxfViewer()
                                        is ActiveModel.Excel -> onNavigateToExcelViewer()
                                        is ActiveModel.Pdf -> onNavigateToPdfViewer()
                                        else -> {}
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                Text("View File", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Main Actions Grid - 5 Distinct File Viewers & Folders
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FILE VIEWERS & FOLDERS (5 CATEGORIES)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF25D366).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "WhatsApp Auto-Detect Active",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF25D366)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Folder 1 & Folder 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionTile(
                        title = "1. Toolpath G-Code",
                        subtitle = "CNC Code (.tap .nc .bin)",
                        icon = Icons.Default.Code,
                        accentColor = Color(0xFF00E5FF),
                        modifier = Modifier.weight(1f),
                        onClick = { filePicker.launch(arrayOf("*/*")) }
                    )
                    ActionTile(
                        title = "2. 3D Model & Relief",
                        subtitle = "STL, OBJ, RLF, ART, 3DXML, Aspire",
                        icon = Icons.Default.ViewInAr,
                        accentColor = Color(0xFFFFD700),
                        modifier = Modifier.weight(1f),
                        onClick = { stlFilePicker.launch(arrayOf("*/*")) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Folder 3 & Folder 4
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionTile(
                        title = "3. AutoCAD Drawing",
                        subtitle = "CAD Drawing (.dxf .dwg)",
                        icon = Icons.Default.Layers,
                        accentColor = Color(0xFF10B981),
                        modifier = Modifier.weight(1f),
                        onClick = { autocadFilePicker.launch(arrayOf("*/*")) }
                    )
                    ActionTile(
                        title = "4. Excel Spreadsheet",
                        subtitle = "Excel (.xlsx .xls .csv)",
                        icon = Icons.Default.TableChart,
                        accentColor = Color(0xFF34D399),
                        modifier = Modifier.weight(1f),
                        onClick = { excelFilePicker.launch(arrayOf("*/*")) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Folder 5
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionTile(
                        title = "5. PDF Document Viewer",
                        subtitle = "Blueprints & Job Orders (.pdf)",
                        icon = Icons.Default.PictureAsPdf,
                        accentColor = Color(0xFFEF4444),
                        modifier = Modifier.weight(1f),
                        onClick = { pdfFilePicker.launch(arrayOf("*/*")) }
                    )
                    ActionTile(
                        title = "CAD Folder Browser",
                        subtitle = "Browse Device Storage",
                        icon = Icons.Default.FolderOpen,
                        accentColor = Color(0xFF059669),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToCadFolder
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Dedicated Folder 4 Card: Excel File Viewer
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { excelFilePicker.launch(arrayOf("*/*")) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF059669)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.TableChart,
                                contentDescription = "Excel Viewer",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Folder 4: Excel File Viewer",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF10B981), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "XLSX / CSV",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "एक्सेल स्प्रेडशीट देखें (.xlsx, .xls, .csv) • WhatsApp से तुरंत खुलेगी",
                                fontSize = 11.sp,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                        Button(
                            onClick = { excelFilePicker.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Select Excel", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Dedicated Folder 5 Card: PDF Document Viewer
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pdfFilePicker.launch(arrayOf("*/*")) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFDC2626)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = "PDF Viewer",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Folder 5: PDF Document Viewer",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFEF4444), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = ".PDF",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "पीडीएफ ड्राइंग व जॉब आर्डर देखें • ज़ूम व पैन सपोर्ट के साथ",
                                fontSize = 11.sp,
                                color = Color(0xFFFECACA)
                            )
                        }
                        Button(
                            onClick = { pdfFilePicker.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Select PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Storage Tip Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "💡 WhatsApp & File Tip: WhatsApp se aayi koi bhi STL, OBJ, RLF, ART, 3DXML, Aspire, DXF, DWG, Excel (.xlsx/.csv) ya PDF file ko tap karne par app use turant identify karke proper viewer me open karegi.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Built-in Demo Samples Card (All 5 Formats)
            item {
                Text(
                    text = "PRE-LOADED DEMO FILES (ALL 5 FORMATS)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.loadSampleGCode()
                            onNavigateToProgramViewer()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("3D Relief (.tap)", fontSize = 10.sp, color = Color(0xFF00E5FF))
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.loadSampleStl()
                            onNavigateToStlViewer()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Bracket (.stl)", fontSize = 10.sp, color = Color(0xFFFFD700))
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.loadSampleDxf()
                            onNavigateToDxfViewer()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Flange (.dxf)", fontSize = 10.sp, color = Color(0xFF10B981))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.loadSampleExcel()
                            onNavigateToExcelViewer()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cutting List (.xlsx)", fontSize = 11.sp, color = Color(0xFF34D399))
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.loadSamplePdf()
                            onNavigateToPdfViewer()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("CNC Blueprint (.pdf)", fontSize = 11.sp, color = Color(0xFFEF4444))
                    }
                }
            }

            // Recent Files Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT FILES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    if (recentFiles.isNotEmpty()) {
                        Text(
                            text = "${recentFiles.size} Items",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            if (recentFiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recent files opened yet.\nTap one of the folder buttons above to load a .tap, .stl, .dxf, .xlsx, or .pdf file!",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                items(recentFiles) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.openUri(android.net.Uri.parse(item.uriString), item.name)
                                when (item.fileType) {
                                    "EXCEL" -> onNavigateToExcelViewer()
                                    "PDF" -> onNavigateToPdfViewer()
                                    "STL", "OBJ", "RLF", "ART", "3DXML", "ASPIRE" -> onNavigateToStlViewer()
                                    "DXF", "DWG" -> onNavigateToDxfViewer()
                                    else -> onNavigateToProgramViewer()
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (item.fileType) {
                                    "EXCEL" -> Icons.Default.TableChart
                                    "PDF" -> Icons.Default.PictureAsPdf
                                    "STL", "OBJ", "RLF", "ART", "3DXML", "ASPIRE" -> Icons.Default.ViewInAr
                                    "DXF", "DWG" -> Icons.Default.Layers
                                    else -> Icons.Default.Code
                                },
                                contentDescription = null,
                                tint = when (item.fileType) {
                                    "EXCEL" -> Color(0xFF10B981)
                                    "PDF" -> Color(0xFFEF4444)
                                    "STL", "OBJ", "RLF", "ART", "3DXML", "ASPIRE" -> Color(0xFFFFD700)
                                    "DXF", "DWG" -> Color(0xFF10B981)
                                    else -> Color(0xFF00E5FF)
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${item.fileType} • ${item.lineOrFaceCount} elements",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            IconButton(onClick = { viewModel.deleteRecentFile(item.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Links Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToPrivacy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Privacy Policy", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                    OutlinedButton(
                        onClick = onNavigateToHelp,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("G-Code Guide", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}

@Composable
fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}
