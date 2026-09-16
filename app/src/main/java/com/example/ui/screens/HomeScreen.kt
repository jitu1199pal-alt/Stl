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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToProgramViewer: () -> Unit,
    onNavigateToStlViewer: () -> Unit,
    onNavigateToDxfViewer: () -> Unit,
    onNavigateToCadFolder: () -> Unit,
    onNavigateToRlfViewer: () -> Unit,
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

    // SAF Document Picker (opens full System File Manager with side drawer for all folders)
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = queryDisplayName(it) ?: it.lastPathSegment?.substringAfterLast('/') ?: "imported_file"
            viewModel.openUri(it, fileName)
            val lower = fileName.lowercase()
            when {
                lower.endsWith(".stl") -> onNavigateToStlViewer()
                lower.endsWith(".dxf") || lower.endsWith(".dwg") -> onNavigateToDxfViewer()
                lower.endsWith(".rlf") -> onNavigateToRlfViewer()
                else -> onNavigateToProgramViewer()
            }
        }
    }

    // Dedicated ArtCAM Relief (.rlf) File Picker: directly opens RLF 3D Relief Viewer upon selection
    val rlfFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val realName = queryDisplayName(it) ?: it.lastPathSegment?.substringAfterLast('/') ?: "relief.rlf"
            val safeName = if (realName.lowercase().endsWith(".rlf")) realName else "$realName.rlf"
            viewModel.openUri(it, safeName)
            onNavigateToRlfViewer()
        }
    }

    // Dedicated AutoCAD File Picker: directly opens AutoCAD Drawing Viewer upon selection
    val autocadFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = queryDisplayName(it) ?: it.lastPathSegment?.substringAfterLast('/') ?: "drawing.dxf"
            viewModel.openUri(it, fileName)
            onNavigateToDxfViewer()
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
                                        is ActiveModel.RLF -> (activeModel as ActiveModel.RLF).model.fileName
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
                                        is ActiveModel.RLF -> onNavigateToRlfViewer()
                                        else -> {}
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                Text("View 3D", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Main Actions Grid
            item {
                Text(
                    text = "OPEN FILE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionTile(
                        title = "Toolpath Simulation",
                        subtitle = "G-Code (.tap .nc .bin)",
                        icon = Icons.Default.Code,
                        accentColor = Color(0xFF00E5FF),
                        modifier = Modifier.weight(1f),
                        onClick = { filePicker.launch(arrayOf("*/*")) }
                    )
                    ActionTile(
                        title = "STL Viewer",
                        subtitle = "3D Mesh (.stl)",
                        icon = Icons.Default.ViewInAr,
                        accentColor = Color(0xFFFFD700),
                        modifier = Modifier.weight(1f),
                        onClick = { filePicker.launch(arrayOf("*/*")) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionTile(
                        title = "RLF File Viewer",
                        subtitle = "ArtCAM Relief (.rlf)",
                        icon = Icons.Default.ViewInAr,
                        accentColor = Color(0xFFDA984B),
                        modifier = Modifier.weight(1f),
                        onClick = { rlfFilePicker.launch(arrayOf("*/*")) }
                    )
                    ActionTile(
                        title = "AutoCAD Viewer",
                        subtitle = "CAD Drawing (.dxf .dwg)",
                        icon = Icons.Default.Layers,
                        accentColor = Color(0xFF10B981),
                        modifier = Modifier.weight(1f),
                        onClick = { autocadFilePicker.launch(arrayOf("*/*")) }
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                // Prominent Dedicated ArtCAM 3D Relief Card (Direct File Selection for Vishnu / Lakshmi / Temple Carvings)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rlfFilePicker.launch(arrayOf("*/*")) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFB45309)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ViewInAr,
                                contentDescription = "ArtCAM Relief 3D Viewer",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ArtCAM 3D Relief",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFF59E0B), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = ".RLF 3D",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "भगवान विष्णु, लक्ष्मी मां, मंदिर नक्काशी व 3D रिलीफ (.rlf) खोलें",
                                fontSize = 11.sp,
                                color = Color(0xFFFDE68A)
                            )
                        }
                        Button(
                            onClick = { rlfFilePicker.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open RLF", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Prominent Dedicated AutoCAD Viewer Option (Direct File Selection)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { autocadFilePicker.launch(arrayOf("*/*")) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF059669)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = "AutoCAD Viewer",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "AutoCAD Viewer",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF10B981), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = ".DXF / .DWG",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "ऑटोकेड फ़ाइल चुनें (.dxf, .dwg) • तुरंत फ़िट स्क्रीन में खुलेगी",
                                fontSize = 11.sp,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                        Button(
                            onClick = { autocadFilePicker.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select File", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Dedicated AutoCAD Drawings Folder Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCadFolder() },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(14.dp)
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
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "AutoCAD Drawings Folder",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF334155), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "BROWSE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                            Text(
                                text = "ऑटोकेड ड्राइंग्स फ़ोल्डर • Browse device folders for drawings",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Button(
                            onClick = onNavigateToCadFolder,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Browse", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                            text = "💡 Tip to see ALL device folders: In the file manager, tap the top-left ☰ menu and select 'Show Internal Storage' or 'Phone Storage'.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Built-in Demo Samples Card
            item {
                Text(
                    text = "PRE-LOADED DEMO FILES",
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
                        Text("3D Relief (.tap)", fontSize = 11.sp, color = Color(0xFF00E5FF))
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.loadSampleStl()
                            onNavigateToStlViewer()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Bracket (.stl)", fontSize = 11.sp, color = Color(0xFFFFD700))
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
                    OutlinedButton(
                        onClick = {
                            viewModel.loadSampleRlf()
                            onNavigateToRlfViewer()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Relief (.rlf)", fontSize = 10.sp, color = Color(0xFFDA984B))
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
                            text = "No recent files opened yet.\nTap one of the buttons above to load a .tap, .stl, or .dxf file!",
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
                                    "STL" -> onNavigateToStlViewer()
                                    "DXF" -> onNavigateToDxfViewer()
                                    "RLF" -> onNavigateToRlfViewer()
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
                                    "STL", "RLF" -> Icons.Default.ViewInAr
                                    "DXF" -> Icons.Default.Layers
                                    else -> Icons.Default.Code
                                },
                                contentDescription = null,
                                tint = when (item.fileType) {
                                    "STL" -> Color(0xFFFFD700)
                                    "DXF" -> Color(0xFF10B981)
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
