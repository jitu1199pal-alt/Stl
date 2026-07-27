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
        val path = remember { Path() }
        val p1Arr = remember { FloatArray(3) }
        val p2Arr = remember { FloatArray(3) }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val bounds = model.bounds
            val center = bounds.center()
            val maxDim = bounds.maxDimension

            val fastTransform = cameraState.getFastTransform(center, maxDim, width, height)

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
                    cameraState.projectFast(x, bounds.minY - maxDim, 0f, fastTransform, p1Arr)
                    cameraState.projectFast(x, bounds.maxY + maxDim, 0f, fastTransform, p2Arr)
                    drawLine(gridColor, Offset(p1Arr[0], p1Arr[1]), Offset(p2Arr[0], p2Arr[1]), strokeWidth = 1f)
                }
                for (j in -10..10) {
                    val y = center.y + j * gridStep
                    cameraState.projectFast(bounds.minX - maxDim, y, 0f, fastTransform, p1Arr)
                    cameraState.projectFast(bounds.maxX + maxDim, y, 0f, fastTransform, p2Arr)
                    drawLine(gridColor, Offset(p1Arr[0], p1Arr[1]), Offset(p2Arr[0], p2Arr[1]), strokeWidth = 1f)
                }
            }

            // Draw DXF Entities
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
                        cameraState.projectFast(entity.start.x, entity.start.y, entity.start.z, fastTransform, p1Arr)
                        cameraState.projectFast(entity.end.x, entity.end.y, entity.end.z, fastTransform, p2Arr)
                        drawLine(color, Offset(p1Arr[0], p1Arr[1]), Offset(p2Arr[0], p2Arr[1]), strokeWidth = 2.5f)
                    }
                    is DxfEntity.Circle -> {
                        cameraState.projectFast(entity.center.x, entity.center.y, entity.center.z, fastTransform, p1Arr)
                        cameraState.projectFast(entity.center.x + entity.radius, entity.center.y, entity.center.z, fastTransform, p2Arr)
                        val radiusPx = kotlin.math.abs(p2Arr[0] - p1Arr[0])
                        drawCircle(color, radius = radiusPx, center = Offset(p1Arr[0], p1Arr[1]), style = Stroke(width = 2.5f))
                    }
                    is DxfEntity.Arc -> {
                        path.reset()
                        val steps = 16
                        val startRad = Math.toRadians(entity.startAngleDeg.toDouble())
                        val endRad = Math.toRadians(entity.endAngleDeg.toDouble())
                        var first = true

                        for (step in 0..steps) {
                            val t = step / steps.toFloat()
                            val ang = startRad + t * (endRad - startRad)
                            val ax = entity.center.x + entity.radius * cos(ang).toFloat()
                            val ay = entity.center.y + entity.radius * sin(ang).toFloat()
                            cameraState.projectFast(ax, ay, 0f, fastTransform, p1Arr)

                            if (first) {
                                path.moveTo(p1Arr[0], p1Arr[1])
                                first = false
                            } else {
                                path.lineTo(p1Arr[0], p1Arr[1])
                            }
                        }
                        drawPath(path, color, style = Stroke(width = 2.5f))
                    }
                    is DxfEntity.Polyline -> {
                        if (entity.points.isNotEmpty()) {
                            path.reset()
                            cameraState.projectFast(entity.points[0].x, entity.points[0].y, entity.points[0].z, fastTransform, p1Arr)
                            path.moveTo(p1Arr[0], p1Arr[1])

                            for (idx in 1 until entity.points.size) {
                                cameraState.projectFast(entity.points[idx].x, entity.points[idx].y, entity.points[idx].z, fastTransform, p1Arr)
                                path.lineTo(p1Arr[0], p1Arr[1])
                            }
                            if (entity.isClosed) path.close()
                            drawPath(path, color, style = Stroke(width = 2.5f))
                        }
                    }
                    is DxfEntity.TextEntity -> {
                        cameraState.projectFast(entity.position.x, entity.position.y, entity.position.z, fastTransform, p1Arr)
                        drawCircle(color, radius = 4f, center = Offset(p1Arr[0], p1Arr[1]))
                    }
                }
            }
        }
    }
}
