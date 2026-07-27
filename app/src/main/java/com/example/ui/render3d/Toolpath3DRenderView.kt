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
        val rapidPath = remember { Path() }
        val completedCutPath = remember { Path() }
        val futureCutPath = remember { Path() }
        val activePath = remember { Path() }

        val p1Arr = remember { FloatArray(3) }
        val p2Arr = remember { FloatArray(3) }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val bounds = model.bounds
            val center = bounds.center()
            val maxDim = bounds.maxDimension

            val fastTransform = cameraState.getFastTransform(center, maxDim, width, height)

            // Draw Ground Grid
            if (showGrid) {
                val gridStep = maxDim / 10f
                val gridColor = Color(0x33475569)
                val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                for (i in -5..5) {
                    val x = center.x + i * gridStep
                    cameraState.projectFast(x, bounds.minY, bounds.minZ, fastTransform, p1Arr)
                    cameraState.projectFast(x, bounds.maxY, bounds.minZ, fastTransform, p2Arr)

                    drawLine(
                        color = gridColor,
                        start = Offset(p1Arr[0], p1Arr[1]),
                        end = Offset(p2Arr[0], p2Arr[1]),
                        strokeWidth = 1f,
                        pathEffect = dashedEffect
                    )
                }
                for (j in -5..5) {
                    val y = center.y + j * gridStep
                    cameraState.projectFast(bounds.minX, y, bounds.minZ, fastTransform, p1Arr)
                    cameraState.projectFast(bounds.maxX, y, bounds.minZ, fastTransform, p2Arr)

                    drawLine(
                        color = gridColor,
                        start = Offset(p1Arr[0], p1Arr[1]),
                        end = Offset(p2Arr[0], p2Arr[1]),
                        strokeWidth = 1f,
                        pathEffect = dashedEffect
                    )
                }
            }

            // Draw XYZ Origin Triad Axes
            if (showAxes) {
                val axisLen = maxDim * 0.3f
                cameraState.projectFast(0f, 0f, 0f, fastTransform, p1Arr)
                val ox = p1Arr[0]; val oy = p1Arr[1]

                cameraState.projectFast(axisLen, 0f, 0f, fastTransform, p2Arr)
                drawLine(Color(0xFFEF4444), Offset(ox, oy), Offset(p2Arr[0], p2Arr[1]), strokeWidth = 4f, cap = StrokeCap.Round)

                cameraState.projectFast(0f, axisLen, 0f, fastTransform, p2Arr)
                drawLine(Color(0xFF10B981), Offset(ox, oy), Offset(p2Arr[0], p2Arr[1]), strokeWidth = 4f, cap = StrokeCap.Round)

                cameraState.projectFast(0f, 0f, axisLen, fastTransform, p2Arr)
                drawLine(Color(0xFF3B82F6), Offset(ox, oy), Offset(p2Arr[0], p2Arr[1]), strokeWidth = 4f, cap = StrokeCap.Round)
            }

            // Batched GPU Path Drawing for 60 FPS performance
            rapidPath.reset()
            completedCutPath.reset()
            futureCutPath.reset()
            activePath.reset()

            val segments = model.segments
            val totalSegs = segments.size
            val stride = when {
                totalSegs > 100_000 -> 8
                totalSegs > 40_000 -> 4
                totalSegs > 15_000 -> 2
                else -> 1
            }

            var idx = 0
            while (idx < totalSegs) {
                val seg = segments[idx]
                val isCompleted = idx <= currentSegmentIndex
                val isCurrent = idx == currentSegmentIndex

                val targetPath = when {
                    isCurrent -> activePath
                    seg.motionType == MotionType.RAPID_G0 -> rapidPath
                    isCompleted -> completedCutPath
                    else -> futureCutPath
                }

                if (seg.arcPoints.isNotEmpty()) {
                    var first = true
                    for (pt in seg.arcPoints) {
                        cameraState.projectFast(pt.x, pt.y, pt.z, fastTransform, p1Arr)
                        if (first) {
                            targetPath.moveTo(p1Arr[0], p1Arr[1])
                            first = false
                        } else {
                            targetPath.lineTo(p1Arr[0], p1Arr[1])
                        }
                    }
                } else {
                    cameraState.projectFast(seg.start.x, seg.start.y, seg.start.z, fastTransform, p1Arr)
                    cameraState.projectFast(seg.end.x, seg.end.y, seg.end.z, fastTransform, p2Arr)
                    targetPath.moveTo(p1Arr[0], p1Arr[1])
                    targetPath.lineTo(p2Arr[0], p2Arr[1])
                }

                idx += if (idx > currentSegmentIndex) stride else 1
            }

            // Draw Batched Paths in bulk
            val dashedRapidEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

            // Rapid Paths
            drawPath(rapidPath, color = Color(0x60F97316), style = Stroke(width = 1.5f, pathEffect = dashedRapidEffect))

            // Future Cut Paths
            drawPath(futureCutPath, color = Color(0x3006B6D4), style = Stroke(width = 1.5f))

            // Completed Cut Paths
            drawPath(completedCutPath, color = Color(0xFF06B6D4), style = Stroke(width = 3f, cap = StrokeCap.Round))

            // Active Cut Path
            drawPath(activePath, color = Color(0xFFEAB308), style = Stroke(width = 6f, cap = StrokeCap.Round))

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
