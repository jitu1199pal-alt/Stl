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

            val fastTransform = cameraState.getFastTransform(center, maxDim, width, height)
            val lightDir = Vector3D(0.5f, 0.8f, 1.0f).normalize()

            val triangles = model.triangles
            val count = triangles.size

            // For huge meshes (>15,000 triangles), stride rendering to guarantee 60 FPS with zero lag
            val stride = when {
                count > 100_000 -> 8
                count > 50_000 -> 4
                count > 15_000 -> 2
                else -> 1
            }

            var i = 0
            while (i < count) {
                val tri = triangles[i]

                cameraState.projectFast(tri.v1.x, tri.v1.y, tri.v1.z, fastTransform, p1Arr)
                cameraState.projectFast(tri.v2.x, tri.v2.y, tri.v2.z, fastTransform, p2Arr)
                cameraState.projectFast(tri.v3.x, tri.v3.y, tri.v3.z, fastTransform, p3Arr)

                path.reset()
                path.moveTo(p1Arr[0], p1Arr[1])
                path.lineTo(p2Arr[0], p2Arr[1])
                path.lineTo(p3Arr[0], p3Arr[1])
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
                        if (count <= 10_000) {
                            drawPath(path, color = meshColor.copy(alpha = 0.2f), style = Stroke(width = 0.5f))
                        }
                    }
                    StlRenderMode.WIREFRAME -> {
                        drawPath(path, color = meshColor, style = Stroke(width = 1f))
                    }
                    StlRenderMode.TRANSPARENT -> {
                        drawPath(path, color = meshColor.copy(alpha = 0.35f))
                        if (count <= 10_000) {
                            drawPath(path, color = meshColor, style = Stroke(width = 0.8f))
                        }
                    }
                    StlRenderMode.BOUNDING_BOX -> {
                        drawPath(path, color = meshColor.copy(alpha = 0.15f))
                    }
                }

                i += stride
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
