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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.ui.render3d.CadViewMode
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
import androidx.compose.runtime.LaunchedEffect
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
    var showTextsSheet by remember { mutableStateOf(false) }
    var cadViewMode by remember { mutableStateOf(CadViewMode.VECTOR_CRISP) }
    val sheetState = rememberModalBottomSheetState()

    // Automatically fit to screen whenever a DXF model is loaded or changed
    LaunchedEffect(dxfModel) {
        if (dxfModel != null) {
            cameraState.fitToScreen()
        }
    }

    val isDwgFile = dxfModel?.fileName?.endsWith(".dwg", ignoreCase = true) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = dxfModel?.fileName ?: "AutoCAD Viewer",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isDwgFile) Color(0xFF00E5FF) else Color(0xFF10B981),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (isDwgFile) "DWG" else "DXF",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                        if (dxfModel != null) {
                            val subtitleText = if (isDwgFile) {
                                when (cadViewMode) {
                                    CadViewMode.VECTOR_CRISP -> "Vector CAD Mode (${dxfModel.entities.size} Entities) • Zoom: ${(cameraState.zoom * 100).toInt()}%"
                                    CadViewMode.CAD_DARK_HD -> "CAD Dark HD Mode • Zoom: ${(cameraState.zoom * 100).toInt()}%"
                                    CadViewMode.ORIGINAL_PREVIEW -> "Original Preview • Zoom: ${(cameraState.zoom * 100).toInt()}%"
                                }
                            } else {
                                "Entities: ${dxfModel.entities.size} • Layers: ${dxfModel.layers.size} • Zoom: ${(cameraState.zoom * 100).toInt()}%"
                            }
                            Text(
                                text = subtitleText,
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
                // DWG CAD View Mode Switcher Strip
                if (isDwgFile || dxfModel.previewBitmap != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CadModeChip(
                                title = "📐 Clear Vector (वेक्टर साफ़)",
                                isSelected = cadViewMode == CadViewMode.VECTOR_CRISP,
                                accentColor = Color(0xFF00E5FF),
                                onClick = { cadViewMode = CadViewMode.VECTOR_CRISP }
                            )
                            CadModeChip(
                                title = "✨ CAD Dark HD",
                                isSelected = cadViewMode == CadViewMode.CAD_DARK_HD,
                                accentColor = Color(0xFF10B981),
                                onClick = { cadViewMode = CadViewMode.CAD_DARK_HD }
                            )
                            CadModeChip(
                                title = "📄 Original",
                                isSelected = cadViewMode == CadViewMode.ORIGINAL_PREVIEW,
                                accentColor = Color(0xFFFFD700),
                                onClick = { cadViewMode = CadViewMode.ORIGINAL_PREVIEW }
                            )
                        }

                        if (dxfModel.detectedTexts.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clickable { showTextsSheet = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.TextFields,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Text (${dxfModel.detectedTexts.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                            }
                        }
                    }
                }

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
                        cadViewMode = cadViewMode,
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
                                label = "FORMAT",
                                value = if (isDwgFile) "DWG" else "DXF",
                                unit = "CAD",
                                accentColor = if (isDwgFile) Color(0xFF00E5FF) else Color(0xFF10B981)
                            )
                            if (dxfModel.entities.isNotEmpty() && cadViewMode == CadViewMode.VECTOR_CRISP) {
                                TelemetryBadge(
                                    label = "ENTITIES",
                                    value = "${dxfModel.entities.size}",
                                    unit = "VEC",
                                    accentColor = Color(0xFF10B981)
                                )
                            }
                            TelemetryBadge(
                                label = if (isDwgFile && cadViewMode != CadViewMode.VECTOR_CRISP && dxfModel.previewBitmap != null) "WIDTH" else "BOUNDS X",
                                value = if (isDwgFile && cadViewMode != CadViewMode.VECTOR_CRISP && dxfModel.previewBitmap != null) "${dxfModel.previewBitmap.width}" else "%.1f".format(dxfModel.bounds.sizeX),
                                unit = if (isDwgFile && cadViewMode != CadViewMode.VECTOR_CRISP && dxfModel.previewBitmap != null) "px" else "mm",
                                accentColor = Color(0xFF10B981)
                            )
                            TelemetryBadge(
                                label = if (isDwgFile && cadViewMode != CadViewMode.VECTOR_CRISP && dxfModel.previewBitmap != null) "HEIGHT" else "BOUNDS Y",
                                value = if (isDwgFile && cadViewMode != CadViewMode.VECTOR_CRISP && dxfModel.previewBitmap != null) "${dxfModel.previewBitmap.height}" else "%.1f".format(dxfModel.bounds.sizeY),
                                unit = if (isDwgFile && cadViewMode != CadViewMode.VECTOR_CRISP && dxfModel.previewBitmap != null) "px" else "mm",
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

                    if (isDwgFile && dxfModel.previewBitmap == null && dxfModel.entities.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xF01E293B)),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "AutoCAD DWG Drawing",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = dxfModel.fileName,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF00E5FF)
                                )
                                HorizontalDivider(color = Color(0xFF334155))
                                Text(
                                    text = "Detected CAD Layers (${dxfModel.layers.size}):",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                                Text(
                                    text = dxfModel.layers.joinToString(" • "),
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    textAlign = TextAlign.Center
                                )
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "💡 Tip: Is DWG file me embedded preview thumbnail nahi hai (AutoCAD THUMBSAVE=0). AutoCAD me THUMBSAVE=1 karke save karein ya direct vector ke liye DXF me export karein.",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFFD700),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
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
                                value = cameraState.zoom.coerceIn(0.1f, 30.0f),
                                onValueChange = { cameraState.setZoomLevel(it) },
                                valueRange = 0.1f..30.0f,
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
                                text = if (cameraState.zoom >= 10f) "${cameraState.zoom.toInt()}x" else "${(cameraState.zoom * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                modifier = Modifier.width(52.dp),
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
                                label = "FIT SCREEN (स्क्रीन फ़िट)",
                                isSelected = (cameraState.zoom in 0.95f..1.05f && cameraState.panX == 0f && cameraState.panY == 0f),
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
                                label = "200%",
                                isSelected = (cameraState.zoom in 1.95f..2.05f),
                                accentColor = Color(0xFF10B981)
                            ) {
                                cameraState.setZoomLevel(2.0f)
                            }

                            ZoomPresetChip(
                                label = "500%",
                                isSelected = (cameraState.zoom in 4.95f..5.05f),
                                accentColor = Color(0xFFA855F7)
                            ) {
                                cameraState.setZoomLevel(5.0f)
                            }

                            ZoomPresetChip(
                                label = "1000% (10x)",
                                isSelected = (cameraState.zoom in 9.5f..10.5f),
                                accentColor = Color(0xFFF59E0B)
                            ) {
                                cameraState.setZoomLevel(10.0f)
                            }

                            ZoomPresetChip(
                                label = "2500% (25x)",
                                isSelected = (cameraState.zoom in 24.5f..25.5f),
                                accentColor = Color(0xFFF43F5E)
                            ) {
                                cameraState.setZoomLevel(25.0f)
                            }

                            ZoomPresetChip(
                                label = "5000% (50x)",
                                isSelected = (cameraState.zoom in 49.5f..50.5f),
                                accentColor = Color(0xFFEC4899)
                            ) {
                                cameraState.setZoomLevel(50.0f)
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

        if (showTextsSheet && dxfModel != null) {
            val clipboardManager = LocalClipboardManager.current
            ModalBottomSheet(
                onDismissRequest = { showTextsSheet = false },
                containerColor = Color(0xFF1E293B)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Detected CAD Texts & Notes",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${dxfModel.detectedTexts.size} Found",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }

                    Text(
                        text = "AutoCAD drawing me se extract ki gayi strings aur dimensions:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(dxfModel.detectedTexts) { itemText ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = itemText,
                                    fontSize = 13.sp,
                                    color = Color(0xFFE2E8F0),
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(itemText)) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy text",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CadModeChip(
    title: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.22f) else Color(0xFF1E293B))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else Color(0xFF334155),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) accentColor else Color(0xFF94A3B8)
        )
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
