package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.TelemetryBadge
import com.example.ui.render3d.CameraState
import com.example.ui.render3d.Stl3DRenderView
import com.example.ui.render3d.StlRenderMode
import com.example.ui.viewmodel.ActiveModel
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StlViewerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val activeModel by viewModel.activeModel.collectAsState()
    val renderMode by viewModel.stlRenderMode.collectAsState()
    val stlModel = (activeModel as? ActiveModel.STL)?.model

    val cameraState = remember { CameraState() }
    var selectedColor by remember { mutableStateOf(Color(0xFFD37554)) } // Default copper terracotta relief color
    var activeTab by remember { mutableStateOf("View") } // File, View, Mode, Tools

    val colors = listOf(
        Color(0xFFD37554), // Copper Clay Bronze
        Color(0xFFDA984B), // Gold Wood
        Color(0xFFE2E8F0), // Silver Steel
        Color(0xFF00E5FF), // CAD Cyan
        Color(0xFF10B981), // Emerald
        Color(0xFF64748B)  // Dark Slate
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stlModel?.fileName ?: "3D STL Viewer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        if (stlModel == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No STL model loaded", color = Color.White)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // CAD Menu Tab Bar (File, View, Mode, Tools)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF334155))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CadMenuTabButton("File", activeTab == "File") { activeTab = "File" }
                    CadMenuTabButton("View", activeTab == "View") { activeTab = "View" }
                    CadMenuTabButton("Mode", activeTab == "Mode") { activeTab = "Mode" }
                    CadMenuTabButton("Tools", activeTab == "Tools") { activeTab = "Tools" }
                }

                // Active Tab Toolbar Options
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (activeTab) {
                        "View" -> {
                            CadQuickActionButton("Iso") { cameraState.setIsometricView() }
                            CadQuickActionButton("Top (XY)") { cameraState.setTopView() }
                            CadQuickActionButton("Front (XZ)") { cameraState.setFrontView() }
                            CadQuickActionButton("Right (YZ)") { cameraState.setRightView() }
                            CadQuickActionButton("Reset") { cameraState.reset() }
                        }
                        "Mode" -> {
                            StlRenderModeChip("Solid", StlRenderMode.SOLID, renderMode) { viewModel.setStlRenderMode(it) }
                            StlRenderModeChip("Wireframe", StlRenderMode.WIREFRAME, renderMode) { viewModel.setStlRenderMode(it) }
                            StlRenderModeChip("Transparent", StlRenderMode.TRANSPARENT, renderMode) { viewModel.setStlRenderMode(it) }
                            StlRenderModeChip("Box", StlRenderMode.BOUNDING_BOX, renderMode) { viewModel.setStlRenderMode(it) }
                        }
                        "File" -> {
                            CadQuickActionButton("Sample Model") { viewModel.loadSampleStl() }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "Size: ${stlModel.triangles.size} rendering faces",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        "Tools" -> {
                            TelemetryBadge(label = "X", value = "%.1f".format(stlModel.bounds.sizeX), unit = "mm", accentColor = Color(0xFFEF4444))
                            TelemetryBadge(label = "Y", value = "%.1f".format(stlModel.bounds.sizeY), unit = "mm", accentColor = Color(0xFF10B981))
                            TelemetryBadge(label = "Z", value = "%.1f".format(stlModel.bounds.sizeZ), unit = "mm", accentColor = Color(0xFF3B82F6))
                        }
                    }
                }

                // 3D CAD Canvas Container (CAD Blue Studio Background)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF4B89C8)) // Classic CAD Blue Background
                ) {
                    Stl3DRenderView(
                        model = stlModel,
                        cameraState = cameraState,
                        renderMode = renderMode,
                        meshColor = selectedColor,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Color Palette Chooser overlay (top right)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color(0x80000000), shape = RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colors.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (selectedColor == col) 2.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = col }
                            )
                        }
                    }
                }

                // Bottom Status Bar matching CAD Software
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stlModel.fileName}   ${stlModel.faceCount} faces   %.1f x %.1f x %.1f mm".format(
                            stlModel.bounds.sizeX, stlModel.bounds.sizeY, stlModel.bounds.sizeZ
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun CadMenuTabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Color(0xFF475569) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else Color(0xFFCBD5E1)
        )
    }
}

@Composable
fun CadQuickActionButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF334155))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
fun StlRenderModeChip(
    label: String,
    mode: StlRenderMode,
    currentMode: StlRenderMode,
    onSelect: (StlRenderMode) -> Unit
) {
    val isSelected = mode == currentMode
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B))
            .clickable { onSelect(mode) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.Black else Color.White
        )
    }
}
