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
import com.example.data.parser.StlModel
import kotlin.math.max

enum class StlRenderMode {
    SOLID,
    WIREFRAME,
    TRANSPARENT,
    BOUNDING_BOX
}

@Composable
fun Stl3DRenderView(
    model: StlModel,
    cameraState: CameraState = remember { CameraState() },
    renderMode: StlRenderMode = StlRenderMode.SOLID,
    meshColor: Color = Color(0xFF00E5FF),
    showBoundingBox: Boolean = true,
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

            val lightDir = Vector3D(0.5f, 0.8f, 1.0f).normalize()

            // Sort triangles by depth (Painter's algorithm for correct 3D display)
            val projectedTriangles = model.triangles.map { tri ->
                val p1 = cameraState.project(tri.v1, center, maxDim, width, height)
                val p2 = cameraState.project(tri.v2, center, maxDim, width, height)
                val p3 = cameraState.project(tri.v3, center, maxDim, width, height)
                val avgZ = (p1.z + p2.z + p3.z) / 3f
                Triple(tri, listOf(p1, p2, p3), avgZ)
            }.sortedBy { it.third }

            val path = Path()

            for ((tri, pts, _) in projectedTriangles) {
                val p1 = pts[0]; val p2 = pts[1]; val p3 = pts[2]

                path.reset()
                path.moveTo(p1.x, p1.y)
                path.lineTo(p2.x, p2.y)
                path.lineTo(p3.x, p3.y)
                path.close()

                when (renderMode) {
                    StlRenderMode.SOLID -> {
                        val intensity = max(0.2f, tri.normal.dot(lightDir))
                        val shadedColor = meshColor.copy(
                            red = meshColor.red * intensity,
                            green = meshColor.green * intensity,
                            blue = meshColor.blue * intensity,
                            alpha = 1f
                        )
                        drawPath(path, color = shadedColor)
                        drawPath(path, color = meshColor.copy(alpha = 0.2f), style = Stroke(width = 0.5f))
                    }
                    StlRenderMode.WIREFRAME -> {
                        drawPath(path, color = meshColor, style = Stroke(width = 1f))
                    }
                    StlRenderMode.TRANSPARENT -> {
                        drawPath(path, color = meshColor.copy(alpha = 0.35f))
                        drawPath(path, color = meshColor, style = Stroke(width = 0.8f))
                    }
                    StlRenderMode.BOUNDING_BOX -> {
                        drawPath(path, color = meshColor.copy(alpha = 0.15f))
                    }
                }
            }

            // Draw Bounding Box Cage if enabled
            if (showBoundingBox || renderMode == StlRenderMode.BOUNDING_BOX) {
                val b = bounds
                val v000 = cameraState.project(Vector3D(b.minX, b.minY, b.minZ), center, maxDim, width, height)
                val v100 = cameraState.project(Vector3D(b.maxX, b.minY, b.minZ), center, maxDim, width, height)
                val v110 = cameraState.project(Vector3D(b.maxX, b.maxY, b.minZ), center, maxDim, width, height)
                val v010 = cameraState.project(Vector3D(b.minX, b.maxY, b.minZ), center, maxDim, width, height)

                val v001 = cameraState.project(Vector3D(b.minX, b.minY, b.maxZ), center, maxDim, width, height)
                val v101 = cameraState.project(Vector3D(b.maxX, b.minY, b.maxZ), center, maxDim, width, height)
                val v111 = cameraState.project(Vector3D(b.maxX, b.maxY, b.maxZ), center, maxDim, width, height)
                val v011 = cameraState.project(Vector3D(b.minX, b.maxY, b.maxZ), center, maxDim, width, height)

                val cageColor = Color(0xFFFFD700)
                val boxLines = listOf(
                    v000 to v100, v100 to v110, v110 to v010, v010 to v000,
                    v001 to v101, v101 to v111, v111 to v011, v011 to v001,
                    v000 to v001, v100 to v101, v110 to v111, v010 to v011
                )
                for ((start, end) in boxLines) {
                    drawLine(
                        color = cageColor,
                        start = Offset(start.x, start.y),
                        end = Offset(end.x, end.y),
                        strokeWidth = 1.5f
                    )
                }
            }
        }
    }
}
