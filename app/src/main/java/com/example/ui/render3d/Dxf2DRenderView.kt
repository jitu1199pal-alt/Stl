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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.parser.DxfEntity
import com.example.data.parser.DxfModel
import com.example.data.parser.DxfParser
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class CadViewMode {
    VECTOR_CRISP,   // Razor-sharp 100% vector lines on black CAD canvas (zero blur, identical to GstarCAD!)
    CAD_DARK_HD,    // High-contrast inverted CAD bitmap with sharpened white & colored lines
    ORIGINAL_PREVIEW // Original embedded preview bitmap
}

@Composable
fun Dxf2DRenderView(
    model: DxfModel,
    visibleLayers: Set<String>,
    cameraState: CameraState = remember { CameraState().apply { pitchDeg = 0f; yawDeg = 0f } },
    showGrid: Boolean = true,
    cadViewMode: CadViewMode = CadViewMode.VECTOR_CRISP,
    modifier: Modifier = Modifier
) {
    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    val bitmapPaint = remember {
        Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
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
        val p3Arr = remember { FloatArray(3) }

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

            fun resolveEntityColor(entity: DxfEntity): Color {
                if (entity.color != null && entity.color != 0) {
                    val c = entity.color!!
                    val a = (c ushr 24) and 0xFF
                    val r = (c ushr 16) and 0xFF
                    val g = (c ushr 8) and 0xFF
                    val b = c and 0xFF
                    // Avoid pure black lines on black background
                    if (r < 25 && g < 25 && b < 25) {
                        return Color.White
                    }
                    return Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = if (a > 0) a / 255f else 1f)
                }

                val upper = entity.layer.uppercase()
                return when {
                    upper.contains("RED") || upper.contains("DIM") || upper.contains("TITLE") -> Color(0xFFEF4444)
                    upper.contains("BLUE") || upper.contains("FRAME") || upper.contains("BOUND") -> Color(0xFF3B82F6)
                    upper.contains("WHITE") || upper.contains("GEOM") || upper.contains("TORAN") || upper.contains("PILLAR") || upper.contains("CIRCLE") -> Color(0xFFFFFFFF)
                    upper.contains("CYAN") -> Color(0xFF00E5FF)
                    upper.contains("YELLOW") -> Color(0xFFFFD700)
                    upper.contains("GREEN") -> Color(0xFF10B981)
                    else -> {
                        val idx = model.layers.indexOf(entity.layer).coerceAtLeast(0)
                        layerColors[idx % layerColors.size]
                    }
                }
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

            // Dynamic stroke width ensuring lines stay thin and razor sharp across any zoom
            val strokeWidthPx = (1.5f * (cameraState.zoom / 1.5f).coerceIn(0.7f, 1.8f)).coerceIn(1.0f, 2.5f)

            // Select active bitmap based on cadViewMode
            val activeBitmap = when (cadViewMode) {
                CadViewMode.CAD_DARK_HD -> model.enhancedBitmap ?: model.previewBitmap
                CadViewMode.ORIGINAL_PREVIEW -> model.previewBitmap
                CadViewMode.VECTOR_CRISP -> if (model.entities.isEmpty()) (model.enhancedBitmap ?: model.previewBitmap) else null
            }

            activeBitmap?.let { bitmap ->
                cameraState.projectFast(bounds.minX, bounds.maxY, 0f, fastTransform, p1Arr)
                cameraState.projectFast(bounds.maxX, bounds.minY, 0f, fastTransform, p2Arr)
                val left = min(p1Arr[0], p2Arr[0])
                val top = min(p1Arr[1], p2Arr[1])
                val right = max(p1Arr[0], p2Arr[0])
                val bottom = max(p1Arr[1], p2Arr[1])
                val destRect = android.graphics.RectF(left, top, right, bottom)
                drawContext.canvas.nativeCanvas.drawBitmap(bitmap, null, destRect, bitmapPaint)
            }

            // Render CAD Vector Entities
            for (entity in model.entities) {
                if (visibleLayers.isNotEmpty() && !visibleLayers.contains(entity.layer)) continue

                val color = resolveEntityColor(entity)
                val strokeStyle = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)

                when (entity) {
                    is DxfEntity.Line -> {
                        cameraState.projectFast(entity.start.x, entity.start.y, entity.start.z, fastTransform, p1Arr)
                        cameraState.projectFast(entity.end.x, entity.end.y, entity.end.z, fastTransform, p2Arr)
                        drawLine(color, Offset(p1Arr[0], p1Arr[1]), Offset(p2Arr[0], p2Arr[1]), strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
                    }
                    is DxfEntity.Circle -> {
                        cameraState.projectFast(entity.center.x, entity.center.y, entity.center.z, fastTransform, p1Arr)
                        cameraState.projectFast(entity.center.x + entity.radius, entity.center.y, entity.center.z, fastTransform, p2Arr)
                        val radiusPx = abs(p2Arr[0] - p1Arr[0])
                        drawCircle(color, radius = radiusPx, center = Offset(p1Arr[0], p1Arr[1]), style = strokeStyle)
                    }
                    is DxfEntity.Arc -> {
                        path.reset()
                        var startRad = Math.toRadians(entity.startAngleDeg.toDouble())
                        var endRad = Math.toRadians(entity.endAngleDeg.toDouble())
                        while (endRad <= startRad) {
                            endRad += 2.0 * Math.PI
                        }
                        val sweep = endRad - startRad
                        val steps = max(32, (sweep / (Math.PI / 32.0)).toInt()).coerceAtMost(128)
                        var first = true

                        for (step in 0..steps) {
                            val t = step / steps.toFloat()
                            val ang = startRad + t * sweep
                            val ax = entity.center.x + entity.radius * cos(ang).toFloat()
                            val ay = entity.center.y + entity.radius * sin(ang).toFloat()
                            cameraState.projectFast(ax, ay, entity.center.z, fastTransform, p1Arr)

                            if (first) {
                                path.moveTo(p1Arr[0], p1Arr[1])
                                first = false
                            } else {
                                path.lineTo(p1Arr[0], p1Arr[1])
                            }
                        }
                        drawPath(path, color, style = strokeStyle)
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
                            drawPath(path, color, style = strokeStyle)
                        }
                    }
                    is DxfEntity.Spline -> {
                        val smooth = DxfParser.interpolateSpline(entity.controlPoints, entity.isClosed)
                        if (smooth.isNotEmpty()) {
                            path.reset()
                            cameraState.projectFast(smooth[0].x, smooth[0].y, smooth[0].z, fastTransform, p1Arr)
                            path.moveTo(p1Arr[0], p1Arr[1])
                            for (idx in 1 until smooth.size) {
                                cameraState.projectFast(smooth[idx].x, smooth[idx].y, smooth[idx].z, fastTransform, p1Arr)
                                path.lineTo(p1Arr[0], p1Arr[1])
                            }
                            if (entity.isClosed) path.close()
                            drawPath(path, color, style = strokeStyle)
                        }
                    }
                    is DxfEntity.Ellipse -> {
                        path.reset()
                        val majorLen = entity.majorAxis.length().toDouble()
                        val minorLen = (majorLen * entity.axisRatio.toDouble()).coerceAtLeast(0.001)
                        val rotAngle = atan2(entity.majorAxis.y.toDouble(), entity.majorAxis.x.toDouble())
                        val cosR = cos(rotAngle)
                        val sinR = sin(rotAngle)

                        val steps = 64
                        for (step in 0..steps) {
                            val theta = 2.0 * Math.PI * step / steps
                            val lx = majorLen * cos(theta)
                            val ly = minorLen * sin(theta)
                            val wx = (entity.center.x + lx * cosR - ly * sinR).toFloat()
                            val wy = (entity.center.y + lx * sinR + ly * cosR).toFloat()
                            cameraState.projectFast(wx, wy, entity.center.z, fastTransform, p1Arr)
                            if (step == 0) {
                                path.moveTo(p1Arr[0], p1Arr[1])
                            } else {
                                path.lineTo(p1Arr[0], p1Arr[1])
                            }
                        }
                        path.close()
                        drawPath(path, color, style = strokeStyle)
                    }
                    is DxfEntity.TextEntity -> {
                        cameraState.projectFast(entity.position.x, entity.position.y, entity.position.z, fastTransform, p1Arr)
                        val fontSizePx = (entity.height * fastTransform.finalScale).coerceIn(8f, 250f)
                        textPaint.textSize = fontSizePx
                        textPaint.color = color.toArgb()
                        drawContext.canvas.nativeCanvas.drawText(entity.text, p1Arr[0], p1Arr[1], textPaint)
                    }
                    is DxfEntity.Dimension -> {
                        // Dimension line between defPoint1 and defPoint2
                        cameraState.projectFast(entity.defPoint1.x, entity.defPoint1.y, entity.defPoint1.z, fastTransform, p1Arr)
                        cameraState.projectFast(entity.defPoint2.x, entity.defPoint2.y, entity.defPoint2.z, fastTransform, p2Arr)
                        drawLine(color, Offset(p1Arr[0], p1Arr[1]), Offset(p2Arr[0], p2Arr[1]), strokeWidth = strokeWidthPx)

                        // Dimension text at textPoint
                        if (entity.text.isNotBlank()) {
                            cameraState.projectFast(entity.textPoint.x, entity.textPoint.y, entity.textPoint.z, fastTransform, p3Arr)
                            textPaint.textSize = (14f * fastTransform.finalScale).coerceIn(8f, 100f)
                            textPaint.color = color.toArgb()
                            drawContext.canvas.nativeCanvas.drawText(entity.text, p3Arr[0], p3Arr[1], textPaint)
                        }
                    }
                    is DxfEntity.Leader -> {
                        if (entity.vertices.size >= 2) {
                            path.reset()
                            cameraState.projectFast(entity.vertices[0].x, entity.vertices[0].y, entity.vertices[0].z, fastTransform, p1Arr)
                            path.moveTo(p1Arr[0], p1Arr[1])
                            for (idx in 1 until entity.vertices.size) {
                                cameraState.projectFast(entity.vertices[idx].x, entity.vertices[idx].y, entity.vertices[idx].z, fastTransform, p1Arr)
                                path.lineTo(p1Arr[0], p1Arr[1])
                            }
                            drawPath(path, color, style = strokeStyle)
                        }
                    }
                    is DxfEntity.Solid -> {
                        // Render 3 or 4 point solids as stroked/thin outlines to avoid opaque black occlusion
                        if (entity.points.size >= 3) {
                            path.reset()
                            cameraState.projectFast(entity.points[0].x, entity.points[0].y, entity.points[0].z, fastTransform, p1Arr)
                            path.moveTo(p1Arr[0], p1Arr[1])
                            for (idx in 1 until entity.points.size) {
                                cameraState.projectFast(entity.points[idx].x, entity.points[idx].y, entity.points[idx].z, fastTransform, p1Arr)
                                path.lineTo(p1Arr[0], p1Arr[1])
                            }
                            path.close()
                            drawPath(path, color, style = strokeStyle)
                        }
                    }
                    is DxfEntity.Hatch -> {
                        // Render hatch boundary loops cleanly without opaque black block fills
                        for (loop in entity.boundaryLoops) {
                            if (loop.size >= 2) {
                                path.reset()
                                cameraState.projectFast(loop[0].x, loop[0].y, loop[0].z, fastTransform, p1Arr)
                                path.moveTo(p1Arr[0], p1Arr[1])
                                for (idx in 1 until loop.size) {
                                    cameraState.projectFast(loop[idx].x, loop[idx].y, loop[idx].z, fastTransform, p1Arr)
                                    path.lineTo(p1Arr[0], p1Arr[1])
                                }
                                path.close()
                                drawPath(path, color.copy(alpha = 0.85f), style = strokeStyle)
                            }
                        }
                    }
                }
            }
        }
    }
}
