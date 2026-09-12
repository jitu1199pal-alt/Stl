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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AdMobBanner
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadFolderScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToDxfViewer: () -> Unit
) {
    val recentFiles by viewModel.recentFiles.collectAsState()
    val cadRecent = recentFiles.filter { it.fileType == "DXF" || it.name.endsWith(".dxf", ignoreCase = true) || it.name.endsWith(".dwg", ignoreCase = true) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = it.lastPathSegment?.substringAfterLast('/') ?: "autocad_drawing.dxf"
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
                            text = "AutoCAD Drawings Folder",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "ऑटोकेड ड्राइंग्स फ़ोल्डर • 2D/3D DXF CAD Viewer",
                            fontSize = 11.sp,
                            color = Color(0xFF10B981)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Open CAD", tint = Color(0xFF10B981))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Import Folder Card
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
                                text = "Open AutoCAD Drawing",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Load any .dxf or AutoCAD drawing from phone folders or SD card",
                                fontSize = 11.sp,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                        Button(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("Browse", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Folder Sections: Built-in AutoCAD Drawing Library
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AUTOCAD BLUEPRINT TEMPLATES & DRAWINGS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Drawing 1: Mechanical Flange
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

            // Drawing 2: Architectural Floor Plan
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

            // Drawing 3: CNC Slotted Bracket
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

            // Recent AutoCAD Drawings Section
            if (cadRecent.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "RECENT AUTOCAD DRAWINGS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                }

                items(cadRecent.size) { idx ->
                    val file = cadRecent[idx]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val uri = android.net.Uri.parse(file.uriString)
                                    viewModel.openUri(uri, file.name)
                                    onNavigateToDxfViewer()
                                } catch (e: Exception) {
                                    viewModel.loadSampleDxf()
                                    onNavigateToDxfViewer()
                                }
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F766E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "AutoCAD DXF • ${file.lineOrFaceCount} CAD entities",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = "Open", tint = Color(0xFF10B981))
                        }
                    }
                }
            }

            // Info guide card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "💡 AutoCAD Compatibility Guide",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Supports DXF from AutoCAD (R12 up to 2024), SolidWorks, DraftSight, LibreCAD, CorelDRAW, and Vectric Aspire.\n• Real-time layer control: toggle wall, dimension, and cutting layers independently.\n• Smooth 2-finger zoom and pan navigation.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 16.sp
                        )
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
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "$category • $subtitle",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Layers: $layersInfo",
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}
