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
    var selectedColor by remember { mutableStateOf(Color(0xFF00E5FF)) }

    val colors = listOf(
        Color(0xFF00E5FF), Color(0xFFFFD700), Color(0xFF10B981),
        Color(0xFFA855F7), Color(0xFFE2E8F0), Color(0xFFF43F5E)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stlModel?.fileName ?: "3D STL Viewer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        if (stlModel != null) {
                            Text(
                                text = "Faces: ${stlModel.faceCount} • Size: %.1fx%.1fx%.1f mm".format(
                                    stlModel.bounds.sizeX, stlModel.bounds.sizeY, stlModel.bounds.sizeZ
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF020617)
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
                // 3D Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0B132B))
                ) {
                    Stl3DRenderView(
                        model = stlModel,
                        cameraState = cameraState,
                        renderMode = renderMode,
                        meshColor = selectedColor,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay HUD Specs (Top Left)
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TelemetryBadge(label = "WIDTH (X)", value = "%.1f".format(stlModel.bounds.sizeX), unit = "mm", accentColor = Color(0xFFEF4444))
                            TelemetryBadge(label = "HEIGHT (Y)", value = "%.1f".format(stlModel.bounds.sizeY), unit = "mm", accentColor = Color(0xFF10B981))
                            TelemetryBadge(label = "DEPTH (Z)", value = "%.1f".format(stlModel.bounds.sizeZ), unit = "mm", accentColor = Color(0xFF3B82F6))
                        }
                    }
                }

                // Mesh Settings Dock
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
                        Text(
                            text = "RENDER MODE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StlRenderModeChip("Solid", StlRenderMode.SOLID, renderMode) { viewModel.setStlRenderMode(it) }
                            StlRenderModeChip("Wireframe", StlRenderMode.WIREFRAME, renderMode) { viewModel.setStlRenderMode(it) }
                            StlRenderModeChip("Transparent", StlRenderMode.TRANSPARENT, renderMode) { viewModel.setStlRenderMode(it) }
                            StlRenderModeChip("Box Cage", StlRenderMode.BOUNDING_BOX, renderMode) { viewModel.setStlRenderMode(it) }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "SURFACE COLOR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            colors.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            width = if (selectedColor == col) 3.dp else 0.dp,
                                            color = Color.White,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = col }
                                )
                            }
                        }
                    }
                }
            }
        }
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
