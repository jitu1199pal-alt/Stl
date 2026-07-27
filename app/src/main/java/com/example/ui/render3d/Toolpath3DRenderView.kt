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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.parser.MotionType
import com.example.data.parser.ToolpathModel

@Composable
fun Toolpath3DRenderView(
    model: ToolpathModel,
    currentSegmentIndex: Int,
    cameraState: CameraState = remember { CameraState() },
    showGrid: Boolean = true,
    showAxes: Boolean = true,
    showToolHead: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { cameraState.reset() }
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
                    cameraState.rotate(
                        deltaYaw = dragAmount.x * 0.4f,
                        deltaPitch = dragAmount.y * 0.4f
                    )
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val bounds = model.bounds
            val center = bounds.center()
            val maxDim = bounds.maxDimension

            // Draw Ground Grid
            if (showGrid) {
                val gridStep = maxDim / 10f
                val gridColor = Color(0x33475569)
                val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                for (i in -5..5) {
                    val x = center.x + i * gridStep
                    val start3D = Vector3D(x, bounds.minY, bounds.minZ)
                    val end3D = Vector3D(x, bounds.maxY, bounds.minZ)

                    val p1 = cameraState.project(start3D, center, maxDim, width, height)
                    val p2 = cameraState.project(end3D, center, maxDim, width, height)

                    drawLine(
                        color = gridColor,
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = 1f,
                        pathEffect = dashedEffect
                    )
                }
                for (j in -5..5) {
                    val y = center.y + j * gridStep
                    val start3D = Vector3D(bounds.minX, y, bounds.minZ)
                    val end3D = Vector3D(bounds.maxX, y, bounds.minZ)

                    val p1 = cameraState.project(start3D, center, maxDim, width, height)
                    val p2 = cameraState.project(end3D, center, maxDim, width, height)

                    drawLine(
                        color = gridColor,
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = 1f,
                        pathEffect = dashedEffect
                    )
                }
            }

            // Draw XYZ Origin Triad Axes
            if (showAxes) {
                val axisLen = maxDim * 0.3f
                val origin = cameraState.project(Vector3D(0f, 0f, 0f), center, maxDim, width, height)
                val xAxis = cameraState.project(Vector3D(axisLen, 0f, 0f), center, maxDim, width, height)
                val yAxis = cameraState.project(Vector3D(0f, axisLen, 0f), center, maxDim, width, height)
                val zAxis = cameraState.project(Vector3D(0f, 0f, axisLen), center, maxDim, width, height)

                // X Axis - Red
                drawLine(Color(0xFFEF4444), Offset(origin.x, origin.y), Offset(xAxis.x, xAxis.y), strokeWidth = 4f, cap = StrokeCap.Round)
                // Y Axis - Green
                drawLine(Color(0xFF10B981), Offset(origin.x, origin.y), Offset(yAxis.x, yAxis.y), strokeWidth = 4f, cap = StrokeCap.Round)
                // Z Axis - Blue
                drawLine(Color(0xFF3B82F6), Offset(origin.x, origin.y), Offset(zAxis.x, zAxis.y), strokeWidth = 4f, cap = StrokeCap.Round)
            }

            // Draw Toolpath Segments
            val dashedRapidEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

            var currentPosition = Vector3D(0f, 0f, 0f)

            for ((idx, seg) in model.segments.withIndex()) {
                val isCompleted = idx <= currentSegmentIndex
                val isCurrent = idx == currentSegmentIndex

                val pathColor = when {
                    isCurrent -> Color(0xFFEAB308) // Active cutting - Bright Yellow
                    isCompleted && seg.motionType == MotionType.RAPID_G0 -> Color(0xFFF97316) // Completed Rapid - Amber
                    isCompleted -> Color(0xFF06B6D4) // Completed Cutting - Neon Cyan
                    seg.motionType == MotionType.RAPID_G0 -> Color(0x40F97316) // Translucent Rapid
                    else -> Color(0x3006B6D4) // Translucent Cutting
                }

                val strokeWidth = when {
                    isCurrent -> 6f
                    isCompleted -> 3f
                    else -> 1.5f
                }

                if (seg.arcPoints.isNotEmpty()) {
                    val path = Path()
                    var first = true
                    for (pt in seg.arcPoints) {
                        val projected = cameraState.project(pt, center, maxDim, width, height)
                        if (first) {
                            path.moveTo(projected.x, projected.y)
                            first = false
                        } else {
                            path.lineTo(projected.x, projected.y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = pathColor,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    val p1 = cameraState.project(seg.start, center, maxDim, width, height)
                    val p2 = cameraState.project(seg.end, center, maxDim, width, height)

                    drawLine(
                        color = pathColor,
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                        pathEffect = if (seg.motionType == MotionType.RAPID_G0) dashedRapidEffect else null
                    )
                }

                if (isCurrent || (currentSegmentIndex in model.segments.indices && idx == currentSegmentIndex)) {
                    currentPosition = seg.end
                }
            }

            // Draw Animated 3D Cutter Bit
            if (showToolHead && model.segments.isNotEmpty()) {
                val activeSeg = if (currentSegmentIndex in model.segments.indices) model.segments[currentSegmentIndex] else model.segments.last()
                val toolPos = activeSeg.end

                val toolTip = cameraState.project(toolPos, center, maxDim, width, height)
                val toolTop = cameraState.project(toolPos + Vector3D(0f, 0f, maxDim * 0.15f), center, maxDim, width, height)

                // Tool Cone Cutter
                val toolConePath = Path().apply {
                    moveTo(toolTip.x, toolTip.y)
                    lineTo(toolTop.x - 12f, toolTop.y)
                    lineTo(toolTop.x + 12f, toolTop.y)
                    close()
                }

                drawPath(toolConePath, color = Color(0xFFF59E0B))
                drawCircle(color = Color(0xFFEF4444), radius = 6f, center = Offset(toolTip.x, toolTip.y))
            }
        }
    }
}
