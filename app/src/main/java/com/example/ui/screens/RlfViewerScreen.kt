package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
    var activeTab by remember { mutableStateOf("Tuning") }

    val materials = listOf(
        Pair("Teak Wood", Color(0xFFDA984B)),
        Pair("Walnut Wood", Color(0xFF8D6E63)),
        Pair("Polished Brass", Color(0xFFFFD700)),
        Pair("Antique Bronze", Color(0xFFB87333)),
        Pair("Aluminum", Color(0xFFE2E8F0)),
        Pair("CAD Cyan", Color(0xFF00E5FF)),
        Pair("Jade", Color(0xFF10B981)),
        Pair("Slate Grey", Color(0xFF64748B))
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
                            text = rlfModel?.formatDescription ?: "3D Relief Carving Surface",
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
                    RlfMenuTabButton("Relief Tuning", activeTab == "Tuning") { activeTab = "Tuning" }
                    RlfMenuTabButton("View Angles", activeTab == "View") { activeTab = "View" }
                    RlfMenuTabButton("Style & Colors", activeTab == "Style") { activeTab = "Style" }
                    RlfMenuTabButton("Specs", activeTab == "Specs") { activeTab = "Specs" }
                }

                // Active Tab Bar Actions (Horizontally Scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (activeTab) {
                        "Tuning" -> {
                            // Depth Scale Chips
                            Text("Depth:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                            listOf(0.25f, 0.5f, 1.0f, 2.0f, 4.0f, 8.0f).forEach { scale ->
                                val isSelected = (rlfModel.depthScale - scale) in -0.05f..0.05f
                                RlfToggleChip(
                                    label = "${scale}x",
                                    isActive = isSelected
                                ) {
                                    viewModel.updateRlfSettings(depthScale = scale)
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Invert Z (Emboss vs Engrave)
                            RlfToggleChip(
                                label = if (rlfModel.isInverted) "Engraved (Recessed)" else "Embossed (Raised)",
                                isActive = rlfModel.isInverted
                            ) {
                                viewModel.updateRlfSettings(isInverted = !rlfModel.isInverted)
                            }

                            // Flip Y (CNC Orientation)
                            RlfToggleChip(
                                label = if (rlfModel.isFlippedY) "Y-Flipped (CNC Rev)" else "Y-Normal",
                                isActive = rlfModel.isFlippedY
                            ) {
                                viewModel.updateRlfSettings(isFlippedY = !rlfModel.isFlippedY)
                            }

                            // Mirror X
                            RlfToggleChip(
                                label = if (rlfModel.isFlippedX) "X-Mirrored" else "X-Normal",
                                isActive = rlfModel.isFlippedX
                            ) {
                                viewModel.updateRlfSettings(isFlippedX = !rlfModel.isFlippedX)
                            }

                            // Transpose (Swap XY)
                            RlfToggleChip(
                                label = if (rlfModel.isTransposed) "XY Swapped" else "XY Normal",
                                isActive = rlfModel.isTransposed
                            ) {
                                viewModel.updateRlfSettings(isTransposed = !rlfModel.isTransposed)
                            }

                            // Workpiece Base Block
                            RlfToggleChip(
                                label = if (rlfModel.showBaseBlock) "Solid Stock Block" else "Surface Only",
                                isActive = rlfModel.showBaseBlock
                            ) {
                                viewModel.updateRlfSettings(showBaseBlock = !rlfModel.showBaseBlock)
                            }

                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quality:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                            listOf(150 to "Standard", 220 to "High", 320 to "Ultra").forEach { (res, name) ->
                                val isSelected = rlfModel.gridResolution == res
                                RlfToggleChip(
                                    label = name,
                                    isActive = isSelected
                                ) {
                                    viewModel.updateRlfSettings(gridResolution = res)
                                }
                            }
                        }
                        "View" -> {
                            RlfQuickActionButton("Iso NE") { cameraState.setIsometricNE() }
                            RlfQuickActionButton("Iso SE") { cameraState.setIsometricSE() }
                            RlfQuickActionButton("Top (XY)") { cameraState.setTopView() }
                            RlfQuickActionButton("Front (XZ)") { cameraState.setFrontView() }
                            RlfQuickActionButton("Right (YZ)") { cameraState.setRightView() }
                            RlfQuickActionButton("Reset Fit") { cameraState.reset() }
                        }
                        "Style" -> {
                            StlRenderModeChip("Solid Shaded", StlRenderMode.SOLID, renderMode) { viewModel.setStlRenderMode(it) }
                            StlRenderModeChip("Wireframe", StlRenderMode.WIREFRAME, renderMode) { viewModel.setStlRenderMode(it) }
                            StlRenderModeChip("Ghost", StlRenderMode.TRANSPARENT, renderMode) { viewModel.setStlRenderMode(it) }
                            StlRenderModeChip("Box Bounds", StlRenderMode.BOUNDING_BOX, renderMode) { viewModel.setStlRenderMode(it) }
                        }
                        "Specs" -> {
                            TelemetryBadge(label = "Length X", value = "%.1f".format(rlfModel.widthMm), unit = "mm", accentColor = Color(0xFFEF4444))
                            TelemetryBadge(label = "Width Y", value = "%.1f".format(rlfModel.heightMm), unit = "mm", accentColor = Color(0xFF10B981))
                            TelemetryBadge(label = "Relief Z", value = "%.1f".format(rlfModel.maxReliefHeightMm), unit = "mm", accentColor = Color(0xFF3B82F6))
                            TelemetryBadge(label = "Grid", value = "${rlfModel.gridWidth}x${rlfModel.gridHeight}", unit = "pts", accentColor = Color(0xFFDA984B))
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

                    // Material Color Palette Chooser (Floating Top Right)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color(0xB3000000), shape = RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        materials.forEach { (_, col) ->
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
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
                            text = "${rlfModel.formatDescription} • ${rlfModel.gridWidth}x${rlfModel.gridHeight} • ${rlfModel.stlModel.faceCount} Polygons",
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

@Composable
fun RlfToggleChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) Color(0xFF0284C7) else Color(0xFF334155))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) Color.White else Color(0xFFCBD5E1)
        )
    }
}
