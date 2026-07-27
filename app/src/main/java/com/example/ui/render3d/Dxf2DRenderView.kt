package com.example.ui.render3d

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.parser.DxfEntity
import com.example.data.parser.DxfModel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Dxf2DRenderView(
    model: DxfModel,
    visibleLayers: Set<String>,
    cameraState: CameraState = remember { CameraState().apply { pitchDeg = 0f; yawDeg = 0f } },
    showGrid: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { cameraState.reset(); cameraState.setTopView() }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomFactor, _ ->
                    if (zoomFactor != 1f) {
                        cameraState.scaleZoom(zoomFactor)
                    } else if (pan != Offset.Zero) {
                        cameraState.pan(pan.x, pan.y)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    cameraState.pan(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val bounds = model.bounds
            val center = bounds.center()
            val maxDim = bounds.maxDimension

            val layerColors = listOf(
                Color(0xFF00E5FF), Color(0xFFFFD700), Color(0xFF10B981),
                Color(0xFFF43F5E), Color(0xFFA855F7), Color(0xFF3B82F6)
            )

            fun getLayerColor(layer: String): Color {
                val idx = model.layers.indexOf(layer).coerceAtLeast(0)
                return layerColors[idx % layerColors.size]
            }

            // Draw CAD Grid
            if (showGrid) {
                val gridStep = maxDim / 10f
                val gridColor = Color(0x2294A3B8)

                for (i in -10..10) {
                    val x = center.x + i * gridStep
                    val p1 = cameraState.project(Vector3D(x, bounds.minY - maxDim, 0f), center, maxDim, width, height)
                    val p2 = cameraState.project(Vector3D(x, bounds.maxY + maxDim, 0f), center, maxDim, width, height)
                    drawLine(gridColor, Offset(p1.x, p1.y), Offset(p2.x, p2.y), strokeWidth = 1f)
                }
                for (j in -10..10) {
                    val y = center.y + j * gridStep
                    val p1 = cameraState.project(Vector3D(bounds.minX - maxDim, y, 0f), center, maxDim, width, height)
                    val p2 = cameraState.project(Vector3D(bounds.maxX + maxDim, y, 0f), center, maxDim, width, height)
                    drawLine(gridColor, Offset(p1.x, p1.y), Offset(p2.x, p2.y), strokeWidth = 1f)
                }
            }

            // Draw DXF Entities
            val path = Path()

            for (entity in model.entities) {
                val layerName = when (entity) {
                    is DxfEntity.Line -> entity.layer
                    is DxfEntity.Circle -> entity.layer
                    is DxfEntity.Arc -> entity.layer
                    is DxfEntity.Polyline -> entity.layer
                    is DxfEntity.TextEntity -> entity.layer
                }

                if (visibleLayers.isNotEmpty() && !visibleLayers.contains(layerName)) continue

                val color = getLayerColor(layerName)

                when (entity) {
                    is DxfEntity.Line -> {
                        val p1 = cameraState.project(entity.start, center, maxDim, width, height)
                        val p2 = cameraState.project(entity.end, center, maxDim, width, height)
                        drawLine(color, Offset(p1.x, p1.y), Offset(p2.x, p2.y), strokeWidth = 2.5f)
                    }
                    is DxfEntity.Circle -> {
                        val c = cameraState.project(entity.center, center, maxDim, width, height)
                        val rPoint = cameraState.project(entity.center + Vector3D(entity.radius, 0f, 0f), center, maxDim, width, height)
                        val radiusPx = kotlin.math.abs(rPoint.x - c.x)
                        drawCircle(color, radius = radiusPx, center = Offset(c.x, c.y), style = Stroke(width = 2.5f))
                    }
                    is DxfEntity.Arc -> {
                        path.reset()
                        val steps = 20
                        val startRad = Math.toRadians(entity.startAngleDeg.toDouble())
                        val endRad = Math.toRadians(entity.endAngleDeg.toDouble())
                        var first = true

                        for (step in 0..steps) {
                            val t = step / steps.toFloat()
                            val ang = startRad + t * (endRad - startRad)
                            val ax = entity.center.x + entity.radius * cos(ang).toFloat()
                            val ay = entity.center.y + entity.radius * sin(ang).toFloat()
                            val pt = cameraState.project(Vector3D(ax, ay, 0f), center, maxDim, width, height)

                            if (first) {
                                path.moveTo(pt.x, pt.y)
                                first = false
                            } else {
                                path.lineTo(pt.x, pt.y)
                            }
                        }
                        drawPath(path, color, style = Stroke(width = 2.5f))
                    }
                    is DxfEntity.Polyline -> {
                        if (entity.points.isNotEmpty()) {
                            path.reset()
                            val p0 = cameraState.project(entity.points[0], center, maxDim, width, height)
                            path.moveTo(p0.x, p0.y)

                            for (idx in 1 until entity.points.size) {
                                val pt = cameraState.project(entity.points[idx], center, maxDim, width, height)
                                path.lineTo(pt.x, pt.y)
                            }
                            if (entity.isClosed) path.close()
                            drawPath(path, color, style = Stroke(width = 2.5f))
                        }
                    }
                    is DxfEntity.TextEntity -> {
                        val p = cameraState.project(entity.position, center, maxDim, width, height)
                        drawCircle(color, radius = 4f, center = Offset(p.x, p.y))
                    }
                }
            }
        }
    }
}
