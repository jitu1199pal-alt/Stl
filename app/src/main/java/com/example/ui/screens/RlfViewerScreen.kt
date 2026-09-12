package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Refresh
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
fun RlfViewerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val activeModel by viewModel.activeModel.collectAsState()
    val renderMode by viewModel.stlRenderMode.collectAsState()
    val rlfModel = (activeModel as? ActiveModel.RLF)?.model

    val cameraState = remember { CameraState() }
    var selectedColor by remember { mutableStateOf(Color(0xFFDA984B)) } // Warm Wood Bronze Relief finish
    var activeTab by remember { mutableStateOf("View") }

    val colors = listOf(
        Color(0xFFDA984B), // Wood Gold / Teak
        Color(0xFF8D6E63), // Dark Walnut Wood
        Color(0xFFD37554), // Copper Clay
        Color(0xFFE2E8F0), // CNC Aluminum Steel
        Color(0xFFFFD700), // Polished Brass
        Color(0xFF00E5FF), // Cyan CAD
        Color(0xFF10B981)  // Jade
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = rlfModel?.fileName ?: "ArtCAM Relief Viewer (.rlf)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = "3D Solid CNC Box & Carving Relief Surface",
                            fontSize = 11.sp,
                            color = Color(0xFFDA984B)
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
        if (rlfModel == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No ArtCAM .rlf relief file loaded", color = Color.White)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // CAD Menu Tab Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF334155))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RlfMenuTabButton("View", activeTab == "View") { activeTab = "View" }
                    RlfMenuTabButton("Style", activeTab == "Style") { activeTab = "Style" }
                    RlfMenuTabButton("Relief Box", activeTab == "Relief Box") { activeTab = "Relief Box" }
                    RlfMenuTabButton("Specs", activeTab == "Specs") { activeTab = "Specs" }
                }

                // Active Tab Bar Actions
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
                            RlfQuickActionButton("Iso NE") { cameraState.setIsometricNE() }
                            RlfQuickActionButton("Iso SE") { cameraState.setIsometricSE() }
                            RlfQuickActionButton("Top (XY)") { cameraState.setTopView() }
                            RlfQuickActionButton("Front (XZ)") { cameraState.setFrontView() }
                            RlfQuickActionButton("Right (YZ)") { cameraState.setRightView() }
                            RlfQuickActionButton("Reset") { cameraState.reset() }
                        }
                        "Style" -> {
                            StlRenderModeChip("Solid Shaded", StlRenderMode.SOLID, renderMode) { viewModel.setStlRenderMode(it) }
                            StlRenderModeChip("Wireframe", StlRenderMode.WIREFRAME, renderMode) { viewModel.setStlRenderMode(it) }
                            StlRenderModeChip("Ghost", StlRenderMode.TRANSPARENT, renderMode) { viewModel.setStlRenderMode(it) }
                            StlRenderModeChip("Box Bounds", StlRenderMode.BOUNDING_BOX, renderMode) { viewModel.setStlRenderMode(it) }
                        }
                        "Relief Box" -> {
                            RlfQuickActionButton("Box Sample") { viewModel.loadSampleRlf() }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "Solid Workpiece Skirt Enabled",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        "Specs" -> {
                            TelemetryBadge(label = "Length X", value = "%.1f".format(rlfModel.widthMm), unit = "mm", accentColor = Color(0xFFEF4444))
                            TelemetryBadge(label = "Width Y", value = "%.1f".format(rlfModel.heightMm), unit = "mm", accentColor = Color(0xFF10B981))
                            TelemetryBadge(label = "Height Z", value = "%.1f".format(rlfModel.maxReliefHeightMm), unit = "mm", accentColor = Color(0xFF3B82F6))
                        }
                    }
                }

                // 3D Canvas Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF131D31))
                ) {
                    Stl3DRenderView(
                        model = rlfModel.stlModel,
                        cameraState = cameraState,
                        renderMode = renderMode,
                        meshColor = selectedColor,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Color Palette Chooser (Floating Top Right)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color(0xB3000000), shape = RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colors.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .clickable { selectedColor = col }
                            )
                        }
                    }

                    // Floating 3D Navigation Controls (Floating Bottom Left)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .background(Color(0xB3000000), shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FloatingViewPill("TOP") { cameraState.setTopView() }
                        FloatingViewPill("FRONT") { cameraState.setFrontView() }
                        FloatingViewPill("ISO") { cameraState.setIsometricNE() }
                        FloatingViewPill("FIT") { cameraState.reset() }
                    }
                }

                // Bottom Status Bar with Exact Dimensions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = rlfModel.fileName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Solid 3D Workpiece • ${rlfModel.gridWidth}x${rlfModel.gridHeight} Height Grid • ${rlfModel.stlModel.faceCount} Polygons",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF334155), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "%.1f x %.1f x %.1f mm".format(rlfModel.widthMm, rlfModel.heightMm, rlfModel.maxReliefHeightMm),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingViewPill(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF334155))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun RlfMenuTabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Color(0xFF475569) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
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
fun RlfQuickActionButton(
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
