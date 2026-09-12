package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.TelemetryBadge
import com.example.ui.render3d.CameraState
import com.example.ui.render3d.Dxf2DRenderView
import com.example.ui.viewmodel.ActiveModel
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DxfViewerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val activeModel by viewModel.activeModel.collectAsState()
    val visibleLayers by viewModel.dxfVisibleLayers.collectAsState()
    val dxfModel = (activeModel as? ActiveModel.DXF)?.model

    val cameraState = remember { CameraState().apply { pitchDeg = 0f; yawDeg = 0f } }
    var showGrid by remember { mutableStateOf(true) }
    var showLayersSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = dxfModel?.fileName ?: "AutoCAD DXF Viewer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        if (dxfModel != null) {
                            Text(
                                text = "Entities: ${dxfModel.entities.size} • Layers: ${dxfModel.layers.size} • Zoom: ${(cameraState.zoom * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("dxf_back_btn")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { cameraState.fitToScreen() },
                        modifier = Modifier.testTag("dxf_top_fit_btn")
                    ) {
                        Icon(
                            Icons.Default.FitScreen,
                            contentDescription = "Fit to Screen",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                    IconButton(
                        onClick = { showGrid = !showGrid },
                        modifier = Modifier.testTag("dxf_grid_toggle_btn")
                    ) {
                        Icon(
                            if (showGrid) Icons.Default.GridOn else Icons.Default.GridOff,
                            contentDescription = "Toggle Grid",
                            tint = if (showGrid) Color(0xFFFFD700) else Color(0xFF64748B)
                        )
                    }
                    IconButton(
                        onClick = { showLayersSheet = true },
                        modifier = Modifier.testTag("dxf_layers_btn")
                    ) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = "Layers Filter",
                            tint = Color(0xFF10B981)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF020617)
    ) { innerPadding ->
        if (dxfModel == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No DXF file loaded", color = Color.White)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Main CAD Canvas Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0B1120))
                ) {
                    Dxf2DRenderView(
                        model = dxfModel,
                        visibleLayers = visibleLayers,
                        cameraState = cameraState,
                        showGrid = showGrid,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay Dimensions & Zoom Badges (Top Left)
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TelemetryBadge(
                                label = "BOUNDS X",
                                value = "%.1f".format(dxfModel.bounds.sizeX),
                                unit = "mm",
                                accentColor = Color(0xFF10B981)
                            )
                            TelemetryBadge(
                                label = "BOUNDS Y",
                                value = "%.1f".format(dxfModel.bounds.sizeY),
                                unit = "mm",
                                accentColor = Color(0xFF00E5FF)
                            )
                            TelemetryBadge(
                                label = "ZOOM",
                                value = "${(cameraState.zoom * 100).toInt()}",
                                unit = "%",
                                accentColor = Color(0xFFFFD700)
                            )
                        }
                    }

                    // Floating CAD Navigation & Zoom Controls (Right Side)
                    Card(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xEE1E293B)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Zoom In (+) Button
                            IconButton(
                                onClick = { cameraState.zoomIn() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("dxf_zoom_in_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Zoom In (+)",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // Zoom readout pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F172A))
                                    .clickable { cameraState.fitToScreen() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${(cameraState.zoom * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                            }

                            // Zoom Out (-) Button
                            IconButton(
                                onClick = { cameraState.zoomOut() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("dxf_zoom_out_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Zoom Out (-)",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier
                                    .width(32.dp)
                                    .padding(vertical = 4.dp),
                                color = Color(0xFF334155)
                            )

                            // Fit to Screen Button
                            IconButton(
                                onClick = { cameraState.fitToScreen() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("dxf_fit_screen_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CropFree,
                                    contentDescription = "Fit to Screen",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Reset Pan / Center View Button
                            IconButton(
                                onClick = { cameraState.resetPan() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("dxf_recenter_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CenterFocusStrong,
                                    contentDescription = "Center View",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Gesture Hint at bottom of canvas
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC0F172A))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "💡 Pinch / Tap +/- to Zoom • Drag to Pan",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Bottom CAD Control Strip & Zoom Presets
                Surface(
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        // Interactive Zoom Slider Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { cameraState.zoomOut() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ZoomOut,
                                    contentDescription = "Zoom Out",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Slider(
                                value = cameraState.zoom,
                                onValueChange = { cameraState.setZoomLevel(it) },
                                valueRange = 0.2f..6.0f,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dxf_zoom_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF10B981),
                                    activeTrackColor = Color(0xFF10B981),
                                    inactiveTrackColor = Color(0xFF334155)
                                )
                            )

                            IconButton(
                                onClick = { cameraState.zoomIn() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ZoomIn,
                                    contentDescription = "Zoom In",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Text(
                                text = "${(cameraState.zoom * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                modifier = Modifier.width(46.dp),
                                textAlign = TextAlign.End
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick Zoom Preset Buttons Row
                        val scrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ZoomPresetChip(
                                label = "FIT SCREEN",
                                isSelected = false,
                                accentColor = Color(0xFFFFD700)
                            ) {
                                cameraState.fitToScreen()
                            }

                            ZoomPresetChip(
                                label = "50%",
                                isSelected = (cameraState.zoom in 0.45f..0.55f),
                                accentColor = Color(0xFF94A3B8)
                            ) {
                                cameraState.setZoomLevel(0.5f)
                            }

                            ZoomPresetChip(
                                label = "100%",
                                isSelected = (cameraState.zoom in 0.95f..1.05f),
                                accentColor = Color(0xFF00E5FF)
                            ) {
                                cameraState.setZoomLevel(1.0f)
                            }

                            ZoomPresetChip(
                                label = "150%",
                                isSelected = (cameraState.zoom in 1.45f..1.55f),
                                accentColor = Color(0xFF10B981)
                            ) {
                                cameraState.setZoomLevel(1.5f)
                            }

                            ZoomPresetChip(
                                label = "200%",
                                isSelected = (cameraState.zoom in 1.95f..2.05f),
                                accentColor = Color(0xFF10B981)
                            ) {
                                cameraState.setZoomLevel(2.0f)
                            }

                            ZoomPresetChip(
                                label = "300%",
                                isSelected = (cameraState.zoom in 2.95f..3.05f),
                                accentColor = Color(0xFFA855F7)
                            ) {
                                cameraState.setZoomLevel(3.0f)
                            }

                            ZoomPresetChip(
                                label = "500%",
                                isSelected = (cameraState.zoom in 4.95f..5.05f),
                                accentColor = Color(0xFFF43F5E)
                            ) {
                                cameraState.setZoomLevel(5.0f)
                            }
                        }
                    }
                }
            }

            // Layer Management Bottom Sheet
            if (showLayersSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showLayersSheet = false },
                    sheetState = sheetState,
                    containerColor = Color(0xFF0F172A)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AUTOCAD LAYERS (${dxfModel.layers.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        for (l in dxfModel.layers) {
                                            if (!visibleLayers.contains(l)) viewModel.toggleDxfLayer(l)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Show All", fontSize = 11.sp, color = Color(0xFF00E5FF))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(dxfModel.layers) { layerName ->
                                val isChecked = visibleLayers.contains(layerName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleDxfLayer(layerName) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { viewModel.toggleDxfLayer(layerName) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF10B981),
                                            uncheckedColor = Color(0xFF64748B)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = layerName,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
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

@Composable
private fun ZoomPresetChip(
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.25f) else Color(0xFF1E293B))
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor else Color(0xFF334155),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) accentColor else Color.White
        )
    }
}
