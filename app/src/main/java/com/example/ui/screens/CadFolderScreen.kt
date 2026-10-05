package com.example.ui.screens

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
import android.net.Uri
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AdMobBanner
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadFolderScreen(
    viewModel: MainViewModel,
    initialFolderType: String = "DXF",
    onBack: () -> Unit,
    onNavigateToDxfViewer: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val recentFiles by viewModel.recentFiles.collectAsState()

    var activeTab by remember {
        mutableStateOf(if (initialFolderType.equals("DWG", ignoreCase = true)) 1 else 0)
    }

    val dxfRecent = recentFiles.filter {
        it.fileType.equals("DXF", ignoreCase = true) || it.name.endsWith(".dxf", ignoreCase = true)
    }
    val dwgRecent = recentFiles.filter {
        it.fileType.equals("DWG", ignoreCase = true) || it.name.endsWith(".dwg", ignoreCase = true)
    }

    fun queryDisplayName(uri: android.net.Uri): String? {
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

    // Dedicated DXF File Picker
    val dxfFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = queryDisplayName(it) ?: it.lastPathSegment?.substringAfterLast('/') ?: "drawing.dxf"
            viewModel.openUri(it, fileName)
            onNavigateToDxfViewer()
        }
    }

    // Dedicated DWG File Picker
    val dwgFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = queryDisplayName(it) ?: it.lastPathSegment?.substringAfterLast('/') ?: "drawing.dwg"
            viewModel.openUri(it, fileName)
            onNavigateToDxfViewer()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (activeTab == 0) "DXF Drawings Folder" else "DWG Drawings Folder",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (activeTab == 0) "Dedicated .DXF Folder • 2D Vector CAD Engine" else "Dedicated .DWG Folder • AutoCAD Binary Drawing Engine",
                                fontSize = 11.sp,
                                color = if (activeTab == 0) Color(0xFF10B981) else Color(0xFF00E5FF)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (activeTab == 0) dxfFilePicker.launch(arrayOf("*/*")) else dwgFilePicker.launch(arrayOf("*/*"))
                        }) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = "Open CAD",
                                tint = if (activeTab == 0) Color(0xFF10B981) else Color(0xFF00E5FF)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
                )

                // Separate Folders Tabs
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = if (activeTab == 0) Color(0xFF10B981) else Color(0xFF00E5FF),
                            height = 3.dp
                        )
                    }
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (activeTab == 0) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "DXF Files (.dxf)",
                                    fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (activeTab == 0) Color(0xFF10B981) else Color(0xFF94A3B8)
                                )
                            }
                        }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (activeTab == 1) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "DWG Files (.dwg)",
                                    fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (activeTab == 1) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                                )
                            }
                        }
                    )
                }
            }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (activeTab == 0) {
                // ==================== DXF FOLDER TAB ====================
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF059669)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "DXF Drawing Folder",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "फ़ोन मेमोरी व WhatsApp से कोई भी .dxf CAD ड्राइंग खोलें",
                                    fontSize = 11.sp,
                                    color = Color(0xFFA7F3D0)
                                )
                            }
                            Button(
                                onClick = { dxfFilePicker.launch(arrayOf("*/*")) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("Browse DXF", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DXF TEMPLATES & SAMPLE BLUEPRINTS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                item {
                    CadDrawingCard(
                        title = "CNC Flange & Bolt Holes",
                        subtitle = "sample_flange.dxf",
                        category = "Mechanical Engineering",
                        icon = Icons.Default.Build,
                        accentColor = Color(0xFF00E5FF),
                        layersInfo = "Outline, Center Bore, Bolt Holes, Specs",
                        onOpen = {
                            viewModel.loadSampleDxf()
                            onNavigateToDxfViewer()
                        }
                    )
                }

                item {
                    CadDrawingCard(
                        title = "Architectural House Layout",
                        subtitle = "architectural_floor_plan.dxf",
                        category = "Architectural & Civil",
                        icon = Icons.Default.Architecture,
                        accentColor = Color(0xFF10B981),
                        layersInfo = "Walls, Partitions, Doors, Room Names",
                        onOpen = {
                            viewModel.loadArchitecturalDxf()
                            onNavigateToDxfViewer()
                        }
                    )
                }

                item {
                    CadDrawingCard(
                        title = "CNC Profiler Bracket Plate",
                        subtitle = "cnc_bracket_plate.dxf",
                        category = "CNC Machining & Profiling",
                        icon = Icons.Default.GridView,
                        accentColor = Color(0xFFFFD700),
                        layersInfo = "Outer Contour, Mount Pins, Bearing, Specs",
                        onOpen = {
                            viewModel.loadCncBracketDxf()
                            onNavigateToDxfViewer()
                        }
                    )
                }

                // Recent DXF files
                if (dxfRecent.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "RECENT DXF DRAWINGS (${dxfRecent.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    items(dxfRecent) { fileItem ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.openUri(Uri.parse(fileItem.uriString), fileItem.name)
                                    onNavigateToDxfViewer()
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = fileItem.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(text = "${fileItem.fileType} • ${fileItem.lineOrFaceCount} elements", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF10B981))
                            }
                        }
                    }
                }
            } else {
                // ==================== DWG FOLDER TAB ====================
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2B48)),
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
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0284C7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "DWG Drawing Folder",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "फ़ोन मेमोरी व WhatsApp से कोई भी AutoCAD .dwg फ़ाइल खोलें",
                                    fontSize = 11.sp,
                                    color = Color(0xFFBAE6FD)
                                )
                            }
                            Button(
                                onClick = { dwgFilePicker.launch(arrayOf("*/*")) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                            ) {
                                Text("Browse DWG", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AutoCAD DWG Compatibility",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "सपोर्टेड वर्शन्स: AutoCAD R14, 2000, 2004, 2007, 2010, 2013, 2018+\n• बाइनरी DWG वेक्टर्स और एंटिटीज़ को ऑटो-कन्वर्ट करके तुरंत दिखाता है।\n• WhatsApp या SD कार्ड से आई .dwg फ़ाइलें एक क्लिक में खुलेंगी।",
                                fontSize = 11.sp,
                                color = Color(0xFFCBD5E1),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Recent DWG files
                if (dwgRecent.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "RECENT DWG DRAWINGS (${dwgRecent.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    items(dwgRecent) { fileItem ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.openUri(Uri.parse(fileItem.uriString), fileItem.name)
                                    onNavigateToDxfViewer()
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = fileItem.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(text = "${fileItem.fileType} • ${fileItem.lineOrFaceCount} elements", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00E5FF))
                            }
                        }
                    }
                } else {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("कोई DWG फ़ाइल अभी नहीं खोली गई", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Browse DWG बटन दबाकर अपने फ़ोन से .dwg फ़ाइल चुनें", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CadDrawingCard(
    title: String,
    subtitle: String,
    category: String,
    icon: ImageVector,
    accentColor: Color,
    layersInfo: String,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
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
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(accentColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(text = "DXF", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = accentColor)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "$category • $subtitle", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Layers: $layersInfo", fontSize = 10.sp, color = accentColor)
            }
            OutlinedButton(
                onClick = onOpen,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Open", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}
