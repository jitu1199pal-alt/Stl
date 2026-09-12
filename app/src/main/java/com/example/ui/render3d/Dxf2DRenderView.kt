package com.example.ui.render3d

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { cameraState.fitToScreen() }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val pointerCount = event.changes.size
                            if (pointerCount == 1) {
                                val change = event.changes.first()
                                if (change.pressed) {
                                    val drag = change.position - change.previousPosition
                                    if (drag != Offset.Zero) {
                                        cameraState.pan(drag.x, drag.y)
                                        change.consume()
                                    }
                                }
                            } else if (pointerCount >= 2) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (zoomChange != 1f) {
                                    cameraState.scaleZoom(zoomChange)
                                }
                                if (panChange != Offset.Zero) {
                                    cameraState.pan(panChange.x, panChange.y)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
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

            val fastTransform = cameraState.getFastTransform(
                center = center,
                maxDim = maxDim,
                screenWidth = width,
                screenHeight = height,
                boundsSizeX = bounds.sizeX,
                boundsSizeY = bounds.sizeY
            )

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
                val gridStep = (maxDim / 10f).coerceAtLeast(5f)
                val gridColor = Color(0x2594A3B8)

                for (i in -15..15) {
                    val x = center.x + i * gridStep
                    cameraState.projectFast(x, bounds.minY - maxDim * 0.5f, 0f, fastTransform, p1Arr)
                    cameraState.projectFast(x, bounds.maxY + maxDim * 0.5f, 0f, fastTransform, p2Arr)
                    drawLine(gridColor, Offset(p1Arr[0], p1Arr[1]), Offset(p2Arr[0], p2Arr[1]), strokeWidth = 1f)
                }
                for (j in -15..15) {
                    val y = center.y + j * gridStep
                    cameraState.projectFast(bounds.minX - maxDim * 0.5f, y, 0f, fastTransform, p1Arr)
                    cameraState.projectFast(bounds.maxX + maxDim * 0.5f, y, 0f, fastTransform, p2Arr)
                    drawLine(gridColor, Offset(p1Arr[0], p1Arr[1]), Offset(p2Arr[0], p2Arr[1]), strokeWidth = 1f)
                }
            }

            val strokeWidthPx = (2.2f * cameraState.zoom).coerceIn(1.5f, 6.0f)

            // Draw DXF Entities
            for (entity in model.entities) {
                val layerName = when (entity) {
                    is DxfEntity.Line -> entity.layer
                    is DxfEntity.Circle -> entity.layer
                    is DxfEntity.Arc -> entity.layer
                    is DxfEntity.Polyline -> entity.layer
                    is DxfEntity.TextEntity -> entity.layer
                    is DxfEntity.Ellipse -> entity.layer
                }

                if (visibleLayers.isNotEmpty() && !visibleLayers.contains(layerName)) continue

                val color = getLayerColor(layerName)

                when (entity) {
                    is DxfEntity.Line -> {
                        cameraState.projectFast(entity.start.x, entity.start.y, entity.start.z, fastTransform, p1Arr)
                        cameraState.projectFast(entity.end.x, entity.end.y, entity.end.z, fastTransform, p2Arr)
                        drawLine(color, Offset(p1Arr[0], p1Arr[1]), Offset(p2Arr[0], p2Arr[1]), strokeWidth = strokeWidthPx)
                    }
                    is DxfEntity.Circle -> {
                        cameraState.projectFast(entity.center.x, entity.center.y, entity.center.z, fastTransform, p1Arr)
                        cameraState.projectFast(entity.center.x + entity.radius, entity.center.y, entity.center.z, fastTransform, p2Arr)
                        val radiusPx = kotlin.math.abs(p2Arr[0] - p1Arr[0])
                        drawCircle(color, radius = radiusPx, center = Offset(p1Arr[0], p1Arr[1]), style = Stroke(width = strokeWidthPx))
                    }
                    is DxfEntity.Ellipse -> {
                        val r = entity.majorAxis.length()
                        cameraState.projectFast(entity.center.x, entity.center.y, entity.center.z, fastTransform, p1Arr)
                        cameraState.projectFast(entity.center.x + r, entity.center.y, entity.center.z, fastTransform, p2Arr)
                        val radiusPx = kotlin.math.abs(p2Arr[0] - p1Arr[0]).coerceAtLeast(3f)
                        drawCircle(color, radius = radiusPx, center = Offset(p1Arr[0], p1Arr[1]), style = Stroke(width = strokeWidthPx))
                    }
                    is DxfEntity.Arc -> {
                        path.reset()
                        val steps = 24
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
                        drawPath(path, color, style = Stroke(width = strokeWidthPx))
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
                            drawPath(path, color, style = Stroke(width = strokeWidthPx))
                        }
                    }
                    is DxfEntity.TextEntity -> {
                        cameraState.projectFast(entity.position.x, entity.position.y, entity.position.z, fastTransform, p1Arr)
                        val fontSizePx = (entity.height * fastTransform.finalScale).coerceIn(12f, 48f)
                        textPaint.textSize = fontSizePx
                        textPaint.color = color.toArgb()
                        drawContext.canvas.nativeCanvas.drawText(entity.text, p1Arr[0], p1Arr[1], textPaint)
                    }
                }
            }
        }
    }
}
