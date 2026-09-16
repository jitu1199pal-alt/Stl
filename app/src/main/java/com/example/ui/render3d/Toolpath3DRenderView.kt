package com.example.ui.render3d

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.parser.MotionType
import com.example.data.parser.ToolpathModel
import kotlin.math.cos
import kotlin.math.sin

enum class ToolpathColorMode(val title: String) {
    RELIEF_GOLD("3D Relief"),
    Z_HEATMAP("Depth Map"),
    CNC_CYAN("CNC Cyan"),
    MOTION_TYPE("Motion")
}

enum class ToolpathDisplayMode(val title: String) {
    SIMULATION("Simulate Cut"),
    FULL_TOOLPATH("Full 3D Path")
}

enum class ToolpathLineWidth(val title: String, val strokeWidth: Float) {
    FINE("Fine (0.8px)", 0.8f),
    NORMAL("Normal (1.5px)", 1.5f),
    BOLD("Bold (2.5px)", 2.5f)
}

@Composable
fun Toolpath3DRenderView(
    model: ToolpathModel,
    currentSegmentIndex: Int,
    cameraState: CameraState = remember { CameraState() },
    colorMode: ToolpathColorMode = ToolpathColorMode.RELIEF_GOLD,
    displayMode: ToolpathDisplayMode = ToolpathDisplayMode.SIMULATION,
    lineWidth: ToolpathLineWidth = ToolpathLineWidth.FINE,
    showGrid: Boolean = true,
    showAxes: Boolean = true,
    showToolHead: Boolean = true,
    showRapids: Boolean = true,
    showOriginMarker: Boolean = true,
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
                    }
                    if (pan != Offset.Zero) {
                        if (zoomFactor == 1f) {
                            cameraState.rotate(
                                deltaYaw = pan.x * 0.4f,
                                deltaPitch = pan.y * 0.4f
                            )
                        } else {
                            cameraState.pan(pan.x, pan.y)
                        }
                    }
                }
            }
    ) {
        // 10 height buckets for smooth, fast GPU depth shading
        val numBuckets = 10
        val bucketPaths = remember { Array(numBuckets) { Path() } }
        val rapidPath = remember { Path() }
        val futureCutPath = remember { Path() }
        val activePath = remember { Path() }

        val p1Arr = remember { FloatArray(3) }
        val p2Arr = remember { FloatArray(3) }

        // Predefined high-grade color palettes for depth maps
        val goldPalette = remember {
            arrayOf(
                Color(0xFF3E1F06), // Deepest cut: Dark bronze shadow
                Color(0xFF5A2A08),
                Color(0xFF7A3A0B),
                Color(0xFF9A4C10),
                Color(0xFFBA6016),
                Color(0xFFD6771F),
                Color(0xFFED932B),
                Color(0xFFF6B03D),
                Color(0xFFFBD058),
                Color(0xFFFFF176)  // Surface: Highlight gold
            )
        }

        val heatmapPalette = remember {
            arrayOf(
                Color(0xFF4338CA), // Deepest: Indigo
                Color(0xFF2563EB), // Blue
                Color(0xFF0284C7), // Sky Blue
                Color(0xFF06B6D4), // Cyan
                Color(0xFF10B981), // Emerald
                Color(0xFF84CC16), // Lime
                Color(0xFFEAB308), // Yellow
                Color(0xFFF97316), // Orange
                Color(0xFFEF4444), // Coral Red
                Color(0xFFDC2626)  // Surface: Bright Red
            )
        }

        val cyanPalette = remember {
            arrayOf(
                Color(0xFF075985), // Deepest: Deep Ocean
                Color(0xFF0284C7),
                Color(0xFF0EA5E9),
                Color(0xFF06B6D4),
                Color(0xFF22D3EE),
                Color(0xFF38BDF8),
                Color(0xFF67E8F9),
                Color(0xFFA5F3FC),
                Color(0xFFBAE6FD),
                Color(0xFFE0F2FE)  // Surface: Crisp White-Cyan
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val bounds = model.bounds
            val center = bounds.center()
            val maxDim = bounds.maxDimension.coerceAtLeast(0.1f)

            val fastTransform = cameraState.getFastTransform(center, maxDim, width, height)

            // 1. Draw Ground Grid (at minimum Z floor)
            if (showGrid) {
                val gridStep = maxDim / 10f
                val gridColor = Color(0x28475569)
                val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

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

            // 2. Draw Subtle Workpiece Origin (X0 Y0 Z0 datum)
            if (showOriginMarker) {
                cameraState.projectFast(0f, 0f, 0f, fastTransform, p1Arr)
                val ox = p1Arr[0]; val oy = p1Arr[1]
                if (ox in -50f..(width + 50f) && oy in -50f..(height + 50f)) {
                    drawCircle(Color(0xCCEF4444), radius = 5f, center = Offset(ox, oy), style = Stroke(width = 1.5f))
                    drawLine(Color(0xFFEF4444), Offset(ox - 8f, oy), Offset(ox + 8f, oy), strokeWidth = 1.5f)
                    drawLine(Color(0xFFEF4444), Offset(ox, oy - 8f), Offset(ox, oy + 8f), strokeWidth = 1.5f)
                }
            }

            // 3. Batched Multi-Bucket Z-Depth Path Generation
            for (i in 0 until numBuckets) {
                bucketPaths[i].reset()
            }
            rapidPath.reset()
            futureCutPath.reset()
            activePath.reset()

            val segments = model.segments
            val totalSegs = segments.size
            val isFullView = displayMode == ToolpathDisplayMode.FULL_TOOLPATH

            // Depth calculation range (focuses specifically on cutting moves)
            val cutMinZ = model.cuttingMinZ
            val cutMaxZ = model.cuttingMaxZ
            val cutRangeZ = (cutMaxZ - cutMinZ).coerceAtLeast(0.001f)

            var idx = 0
            while (idx < totalSegs) {
                val seg = segments[idx]
                val isCompleted = isFullView || (idx < currentSegmentIndex)
                val isCurrent = idx == currentSegmentIndex

                val isRapid = seg.motionType == MotionType.RAPID_G0

                if (isCurrent) {
                    // Active segment currently being cut by tool
                    if (seg.arcPoints.isNotEmpty()) {
                        var first = true
                        for (pt in seg.arcPoints) {
                            cameraState.projectFast(pt.x, pt.y, pt.z, fastTransform, p1Arr)
                            if (first) {
                                activePath.moveTo(p1Arr[0], p1Arr[1])
                                first = false
                            } else {
                                activePath.lineTo(p1Arr[0], p1Arr[1])
                            }
                        }
                    } else {
                        cameraState.projectFast(seg.start.x, seg.start.y, seg.start.z, fastTransform, p1Arr)
                        cameraState.projectFast(seg.end.x, seg.end.y, seg.end.z, fastTransform, p2Arr)
                        activePath.moveTo(p1Arr[0], p1Arr[1])
                        activePath.lineTo(p2Arr[0], p2Arr[1])
                    }
                } else if (isRapid) {
                    if (showRapids) {
                        cameraState.projectFast(seg.start.x, seg.start.y, seg.start.z, fastTransform, p1Arr)
                        cameraState.projectFast(seg.end.x, seg.end.y, seg.end.z, fastTransform, p2Arr)
                        rapidPath.moveTo(p1Arr[0], p1Arr[1])
                        rapidPath.lineTo(p2Arr[0], p2Arr[1])
                    }
                } else if (!isCompleted) {
                    // Future uncut segment in simulation mode (subtle wireframe)
                    if (seg.arcPoints.isNotEmpty()) {
                        var first = true
                        for (pt in seg.arcPoints) {
                            cameraState.projectFast(pt.x, pt.y, pt.z, fastTransform, p1Arr)
                            if (first) {
                                futureCutPath.moveTo(p1Arr[0], p1Arr[1])
                                first = false
                            } else {
                                futureCutPath.lineTo(p1Arr[0], p1Arr[1])
                            }
                        }
                    } else {
                        cameraState.projectFast(seg.start.x, seg.start.y, seg.start.z, fastTransform, p1Arr)
                        cameraState.projectFast(seg.end.x, seg.end.y, seg.end.z, fastTransform, p2Arr)
                        futureCutPath.moveTo(p1Arr[0], p1Arr[1])
                        futureCutPath.lineTo(p2Arr[0], p2Arr[1])
                    }
                } else {
                    // Cutting move completed: Assign to height bucket based on Z depth
                    val segZ = seg.end.z
                    val zRatio = ((segZ - cutMinZ) / cutRangeZ).coerceIn(0f, 1f)
                    val bucketIndex = (zRatio * (numBuckets - 1)).toInt().coerceIn(0, numBuckets - 1)
                    val targetPath = bucketPaths[bucketIndex]

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
                }

                idx++
            }

            // 4. Render Batched Paths with Selected Style
            val cutStrokeWidth = lineWidth.strokeWidth

            // A. Draw Rapid Paths (G0)
            if (showRapids) {
                val dashedRapidEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                drawPath(
                    rapidPath,
                    color = Color(0x60F97316),
                    style = Stroke(width = 1.0f, pathEffect = dashedRapidEffect)
                )
            }

            // B. Draw Future Cut Paths (Simulate Mode: subtle graphite skeleton)
            if (!isFullView) {
                drawPath(
                    futureCutPath,
                    color = Color(0x4064748B),
                    style = Stroke(width = 0.7f, cap = StrokeCap.Butt)
                )
            }

            // C. Draw Completed Cut Paths in 10 Z-Depth Buckets (60 FPS GPU hardware draw)
            for (b in 0 until numBuckets) {
                val bucketColor = when (colorMode) {
                    ToolpathColorMode.RELIEF_GOLD -> goldPalette[b]
                    ToolpathColorMode.Z_HEATMAP -> heatmapPalette[b]
                    ToolpathColorMode.CNC_CYAN -> cyanPalette[b]
                    ToolpathColorMode.MOTION_TYPE -> Color(0xFF10B981) // Consistent cutting green
                }

                drawPath(
                    bucketPaths[b],
                    color = bucketColor,
                    style = Stroke(width = cutStrokeWidth, cap = StrokeCap.Butt)
                )
            }

            // D. Draw Active Cutting Segment (Glowing amber/white indicator)
            drawPath(
                activePath,
                color = Color(0xFFF59E0B),
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )

            // 5. Draw Animated 3D CNC Tool Bit Cutter
            if (showToolHead && model.segments.isNotEmpty()) {
                val safeIdx = currentSegmentIndex.coerceIn(0, model.segments.size - 1)
                val activeSeg = model.segments[safeIdx]
                val toolPos = activeSeg.end

                val toolTip = cameraState.project(toolPos, center, maxDim, width, height)
                val toolSpindleTop = cameraState.project(
                    toolPos + Vector3D(0f, 0f, maxDim * 0.12f),
                    center,
                    maxDim,
                    width,
                    height
                )

                // Tool Cone Cutter (Conical / Ball nose cutter)
                val toolConePath = Path().apply {
                    moveTo(toolTip.x, toolTip.y)
                    lineTo(toolSpindleTop.x - 10f, toolSpindleTop.y)
                    lineTo(toolSpindleTop.x + 10f, toolSpindleTop.y)
                    close()
                }

                drawPath(toolConePath, color = Color(0xFFF59E0B))
                // Flute edge highlight
                drawLine(
                    color = Color(0xFFFDE047),
                    start = Offset(toolTip.x, toolTip.y),
                    end = Offset(toolSpindleTop.x, toolSpindleTop.y),
                    strokeWidth = 2f
                )
                // Red cutting contact point
                drawCircle(color = Color(0xFFEF4444), radius = 5f, center = Offset(toolTip.x, toolTip.y))
                drawCircle(color = Color.White, radius = 2f, center = Offset(toolTip.x, toolTip.y))
            }

            // 6. Corner CAD Axis Gizmo (Bottom-Left Orientation Triad)
            if (showAxes) {
                drawCadAxisGizmo(cameraState, height)
            }
        }
    }
}

/**
 * Draws a professional, non-intrusive CAD orientation triad in the bottom-left viewport corner.
 */
private fun DrawScope.drawCadAxisGizmo(cameraState: CameraState, screenHeight: Float) {
    val gizmoCenter = Offset(50f, screenHeight - 65f)
    val gizmoRadius = 26f

    // Subtle dark circular backdrop
    drawCircle(
        color = Color(0xD00F172A),
        radius = 34f,
        center = gizmoCenter
    )
    drawCircle(
        color = Color(0x40334155),
        radius = 34f,
        center = gizmoCenter,
        style = Stroke(width = 1.2f)
    )

    val radYaw = Math.toRadians(cameraState.yawDeg.toDouble()).toFloat()
    val radPitch = Math.toRadians(cameraState.pitchDeg.toDouble()).toFloat()
    val cosYaw = cos(radYaw)
    val sinYaw = sin(radYaw)
    val cosPitch = cos(radPitch)
    val sinPitch = sin(radPitch)

    fun projectAxis(vx: Float, vy: Float, vz: Float): Offset {
        val rx1 = vx * cosYaw + vz * sinYaw
        val ry1 = vy
        val rz1 = -vx * sinYaw + vz * cosYaw

        val rx2 = rx1
        val ry2 = ry1 * cosPitch - rz1 * sinPitch

        return Offset(
            gizmoCenter.x + rx2 * gizmoRadius,
            gizmoCenter.y - ry2 * gizmoRadius
        )
    }

    val xPt = projectAxis(1f, 0f, 0f)
    val yPt = projectAxis(0f, 1f, 0f)
    val zPt = projectAxis(0f, 0f, 1f)

    // X axis - Red
    drawLine(Color(0xFFEF4444), gizmoCenter, xPt, strokeWidth = 2.5f, cap = StrokeCap.Round)
    drawCircle(Color(0xFFEF4444), radius = 3.5f, center = xPt)

    // Y axis - Green
    drawLine(Color(0xFF10B981), gizmoCenter, yPt, strokeWidth = 2.5f, cap = StrokeCap.Round)
    drawCircle(Color(0xFF10B981), radius = 3.5f, center = yPt)

    // Z axis - Blue
    drawLine(Color(0xFF3B82F6), gizmoCenter, zPt, strokeWidth = 2.5f, cap = StrokeCap.Round)
    drawCircle(Color(0xFF3B82F6), radius = 3.5f, center = zPt)
}
