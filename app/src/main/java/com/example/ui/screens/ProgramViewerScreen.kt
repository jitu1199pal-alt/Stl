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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.render3d.CameraState
import com.example.ui.render3d.Toolpath3DRenderView
import com.example.ui.render3d.ToolpathColorMode
import com.example.ui.render3d.ToolpathDisplayMode
import com.example.ui.render3d.ToolpathLineWidth
import com.example.ui.viewmodel.ActiveModel
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramViewerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val activeModel by viewModel.activeModel.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentSegmentIdx by viewModel.currentSegmentIndex.collectAsState()
    val speedMultiplier by viewModel.speedMultiplier.collectAsState()
    val elapsedTime by viewModel.elapsedTimeSeconds.collectAsState()

    val gcodeModel = (activeModel as? ActiveModel.GCode)?.model

    val cameraState = remember { CameraState() }
    var showGCodeSheet by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }

    // Rendering modes
    var colorMode by remember { mutableStateOf(ToolpathColorMode.RELIEF_GOLD) }
    var displayMode by remember { mutableStateOf(ToolpathDisplayMode.FULL_TOOLPATH) }
    var lineWidth by remember {
        mutableStateOf(
            if ((gcodeModel?.segments?.size ?: 0) > 8000) ToolpathLineWidth.FINE else ToolpathLineWidth.NORMAL
        )
    }
    var showRapids by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(true) }
    var showAxes by remember { mutableStateOf(true) }

    val sheetState = rememberModalBottomSheetState()
    val speedList = listOf(0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f, 512f, 1024f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = gcodeModel?.fileName ?: "Toolpath Simulator",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            color = Color.White
                        )
                        if (gcodeModel != null) {
                            Text(
                                text = "${gcodeModel.segments.size} Segments • Depth: %.1f to %.1f mm".format(
                                    gcodeModel.cuttingMinZ,
                                    gcodeModel.cuttingMaxZ
                                ),
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showOptionsSheet = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Display Settings", tint = Color(0xFFF59E0B))
                    }
                    IconButton(onClick = { showGCodeSheet = true }) {
                        Icon(Icons.Default.Code, contentDescription = "G-Code Inspector", tint = Color(0xFF00E5FF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF020617)
    ) { innerPadding ->
        if (gcodeModel == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No toolpath file loaded", color = Color.White)
            }
        } else {
            val totalSegments = gcodeModel.segments.size.coerceAtLeast(1)
            val safeIdx = currentSegmentIdx.coerceIn(0, totalSegments - 1)
            val activeSeg = gcodeModel.segments.getOrNull(safeIdx)
            val activePos = activeSeg?.end ?: com.example.ui.render3d.Vector3D(0f, 0f, 0f)

            val progress = ((safeIdx + 1) / totalSegments.toFloat()) * 100f

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 1. Dedicated Live Telemetry Status Strip (Zero Overlap with Viewport or Camera Buttons)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0B132B)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactCoordinateBadge("X", "%.2f".format(activePos.x), Color(0xFFEF4444))
                        CompactCoordinateBadge("Y", "%.2f".format(activePos.y), Color(0xFF10B981))
                        CompactCoordinateBadge("Z", "%.2f".format(activePos.z), Color(0xFF3B82F6))
                        Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color(0xFF334155)))
                        CompactMetricBadge("F", "${activeSeg?.feedRate?.toInt() ?: 1000}", "mm/m", Color(0xFFF59E0B))
                        CompactMetricBadge("S", "${(activeSeg?.rpm?.toInt() ?: 12000)}", "rpm", Color(0xFFA855F7))
                        CompactMetricBadge("T", "T${activeSeg?.toolNumber ?: 1}", "", Color(0xFFFFD700))
                    }
                }

                // 2. Interactive 3D Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF050B14))
                ) {
                    Toolpath3DRenderView(
                        model = gcodeModel,
                        currentSegmentIndex = safeIdx,
                        cameraState = cameraState,
                        colorMode = colorMode,
                        displayMode = displayMode,
                        lineWidth = lineWidth,
                        showGrid = showGrid,
                        showAxes = showAxes,
                        showRapids = showRapids,
                        showOriginMarker = true,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay: Floating Camera Presets & Quick Mode Bar (Top of Viewport)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Row A: Camera Views
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item { CameraPresetChip("ISO (NE)") { cameraState.setIsometricNE() } }
                            item { CameraPresetChip("SE") { cameraState.setIsometricSE() } }
                            item { CameraPresetChip("SW") { cameraState.setIsometricSW() } }
                            item { CameraPresetChip("NW") { cameraState.setIsometricNW() } }
                            item { CameraPresetChip("TOP (XY)") { cameraState.setTopView() } }
                            item { CameraPresetChip("FRONT") { cameraState.setFrontView() } }
                            item { CameraPresetChip("RIGHT") { cameraState.setRightView() } }
                            item { CameraPresetChip("FIT/RESET") { cameraState.reset() } }
                        }

                        // Row B: Display Mode & Quick Style Selectors
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Toggle Mode: Full 3D Path vs Simulate Cut
                            QuickToggleChip(
                                label = if (displayMode == ToolpathDisplayMode.FULL_TOOLPATH) "Full 3D View" else "Simulate Cut",
                                isHighlight = displayMode == ToolpathDisplayMode.FULL_TOOLPATH,
                                onClick = {
                                    displayMode = if (displayMode == ToolpathDisplayMode.FULL_TOOLPATH) {
                                        ToolpathDisplayMode.SIMULATION
                                    } else {
                                        ToolpathDisplayMode.FULL_TOOLPATH
                                    }
                                }
                            )

                            // Quick Color Mode Cycle Chip
                            QuickToggleChip(
                                label = colorMode.title,
                                isHighlight = true,
                                highlightColor = when (colorMode) {
                                    ToolpathColorMode.RELIEF_GOLD -> Color(0xFFF59E0B)
                                    ToolpathColorMode.Z_HEATMAP -> Color(0xFFEF4444)
                                    ToolpathColorMode.CNC_CYAN -> Color(0xFF00E5FF)
                                    ToolpathColorMode.MOTION_TYPE -> Color(0xFF10B981)
                                },
                                onClick = {
                                    val modes = ToolpathColorMode.values()
                                    val next = (colorMode.ordinal + 1) % modes.size
                                    colorMode = modes[next]
                                }
                            )

                            // Quick Line Width Chip
                            QuickToggleChip(
                                label = lineWidth.title,
                                isHighlight = false,
                                onClick = {
                                    val widths = ToolpathLineWidth.values()
                                    val next = (lineWidth.ordinal + 1) % widths.size
                                    lineWidth = widths[next]
                                }
                            )
                        }
                    }

                    // Overlay: Current G-Code Instruction / Line HUD (Bottom Left)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, bottom = 10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xDD0F172A))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Line ${activeSeg?.lineNumber ?: 1}: ${activeSeg?.rawText ?: ""}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E5FF),
                            maxLines = 1
                        )
                    }
                }

                // 3. Control Dock Panel (Bottom)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Time & Progress Bar Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Progress: %.1f%% (%d/%d)".format(progress, safeIdx + 1, totalSegments),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Time: %02d:%02d / %02d:%02d".format(
                                    (elapsedTime / 60).toInt(),
                                    (elapsedTime % 60).toInt(),
                                    (gcodeModel.estimatedTimeSeconds / 60).toInt(),
                                    (gcodeModel.estimatedTimeSeconds % 60).toInt()
                                ),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00E5FF)
                            )
                        }

                        // Progress Slider
                        Slider(
                            value = safeIdx.toFloat(),
                            onValueChange = { viewModel.seekToSegment(it.toInt()) },
                            valueRange = 0f..(totalSegments - 1).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF),
                                inactiveTrackColor = Color(0xFF334155)
                            )
                        )

                        // Playback Buttons & Speed Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.resetSimulation() }) {
                                Icon(Icons.Default.Replay, contentDescription = "Restart", tint = Color.White)
                            }
                            IconButton(onClick = { viewModel.seekToSegment(safeIdx - 1) }) {
                                Icon(Icons.Default.FastRewind, contentDescription = "Step Back", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    if (isPlaying) viewModel.pauseSimulation() else viewModel.playSimulation()
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(Color(0xFF00E5FF))
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.seekToSegment(safeIdx + 1) }) {
                                Icon(Icons.Default.FastForward, contentDescription = "Step Forward", tint = Color.White)
                            }
                            OutlinedButton(
                                onClick = { showSpeedDialog = !showSpeedDialog },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${speedMultiplier}x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                            }
                        }

                        // Speed Selection Chips
                        if (showSpeedDialog) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                speedList.take(6).forEach { spd ->
                                    SpeedChip(
                                        speed = spd,
                                        isSelected = speedMultiplier == spd,
                                        onSelect = {
                                            viewModel.setSpeedMultiplier(spd)
                                            showSpeedDialog = false
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                speedList.drop(6).take(7).forEach { spd ->
                                    SpeedChip(
                                        speed = spd,
                                        isSelected = speedMultiplier == spd,
                                        onSelect = {
                                            viewModel.setSpeedMultiplier(spd)
                                            showSpeedDialog = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Display Settings Bottom Sheet
            if (showOptionsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showOptionsSheet = false },
                    sheetState = sheetState,
                    containerColor = Color(0xFF0F172A)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "3D TOOLPATH DISPLAY SETTINGS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )

                        // Color Mode
                        Text("3D Color & Depth Gradient:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ToolpathColorMode.values().forEach { mode ->
                                val selected = colorMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) Color(0xFF00E5FF) else Color(0xFF1E293B))
                                        .clickable { colorMode = mode }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.Black else Color.White
                                    )
                                }
                            }
                        }

                        // Line Thickness
                        Text("Line Thickness (Hairline vs Normal):", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ToolpathLineWidth.values().forEach { widthMode ->
                                val selected = lineWidth == widthMode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) Color(0xFF00E5FF) else Color(0xFF1E293B))
                                        .clickable { lineWidth = widthMode }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = widthMode.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.Black else Color.White
                                    )
                                }
                            }
                        }

                        // Display Mode
                        Text("Viewing Mode:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ToolpathDisplayMode.values().forEach { dMode ->
                                val selected = displayMode == dMode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) Color(0xFF00E5FF) else Color(0xFF1E293B))
                                        .clickable { displayMode = dMode }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dMode.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.Black else Color.White
                                    )
                                }
                            }
                        }

                        // Toggles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showRapids = !showRapids },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (showRapids) "G0 Rapids: ON" else "G0 Rapids: OFF", fontSize = 11.sp, color = Color(0xFFF59E0B))
                            }
                            OutlinedButton(
                                onClick = { showGrid = !showGrid },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (showGrid) "Grid: ON" else "Grid: OFF", fontSize = 11.sp, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // 5. G-Code Inspector Sheet
            if (showGCodeSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showGCodeSheet = false },
                    sheetState = sheetState,
                    containerColor = Color(0xFF0F172A)
                ) {
                    val listState = rememberLazyListState()

                    LaunchedEffect(safeIdx) {
                        if (safeIdx in gcodeModel.segments.indices) {
                            listState.animateScrollToItem(safeIdx)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "G-CODE INSPECTOR",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(gcodeModel.segments.size) { index ->
                                val seg = gcodeModel.segments[index]
                                val isSelected = index == safeIdx

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "L${seg.lineNumber}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF64748B)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = seg.rawText,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${seg.motionType}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
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
private fun CompactCoordinateBadge(axis: String, value: String, accentColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E293B))
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Text(
            text = axis,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun CompactMetricBadge(label: String, value: String, unit: String, accentColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E293B))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = value,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White
        )
        if (unit.isNotEmpty()) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = unit,
                fontSize = 8.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
fun CameraPresetChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xDD1E293B))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 5.dp),
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
fun QuickToggleChip(
    label: String,
    isHighlight: Boolean = false,
    highlightColor: Color = Color(0xFF00E5FF),
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHighlight) highlightColor.copy(alpha = 0.2f) else Color(0xDD1E293B))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) highlightColor else Color.White
        )
    }
}

@Composable
fun SpeedChip(speed: Float, isSelected: Boolean, onSelect: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${speed}x",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.Black else Color.White
        )
    }
}
