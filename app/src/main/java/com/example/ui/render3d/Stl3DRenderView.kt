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
    meshColor: Color = Color(0xFFD37554), // Default Copper / Terracotta relief color like reference
    showBoundingBox: Boolean = false,
    showTriadAxis: Boolean = true,
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
        val p1Arr = remember { FloatArray(3) }
        val p2Arr = remember { FloatArray(3) }
        val p3Arr = remember { FloatArray(3) }

        val bucketCount = 16
        val pathBuckets = remember { Array(bucketCount) { Path() } }
        val wireframePath = remember { Path() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val bounds = model.bounds
            val center = bounds.center()
            val maxDim = bounds.maxDimension

            val fastTransform = cameraState.getFastTransform(center, maxDim, width, height)

            // Reset path buckets
            for (b in 0 until bucketCount) {
                pathBuckets[b].reset()
            }
            wireframePath.reset()

            val triangles = model.triangles
            val count = triangles.size

            // Fast 3D Light Direction from Top-Right-Front
            val lx = 0.35f; val ly = 0.55f; val lz = 0.75f

            var i = 0
            while (i < count) {
                val tri = triangles[i]

                // Project vertices
                cameraState.projectFast(tri.v1.x, tri.v1.y, tri.v1.z, fastTransform, p1Arr)
                cameraState.projectFast(tri.v2.x, tri.v2.y, tri.v2.z, fastTransform, p2Arr)
                cameraState.projectFast(tri.v3.x, tri.v3.y, tri.v3.z, fastTransform, p3Arr)

                // 2D Screen Space Bounding check
                val minPx = minOf(p1Arr[0], p2Arr[0], p3Arr[0])
                val maxPx = maxOf(p1Arr[0], p2Arr[0], p3Arr[0])
                val minPy = minOf(p1Arr[1], p2Arr[1], p3Arr[1])
                val maxPy = maxOf(p1Arr[1], p2Arr[1], p3Arr[1])

                if (maxPx >= 0 && minPx <= width && maxPy >= 0 && minPy <= height) {
                    // Transformed Normal for lighting
                    val nx = tri.normal.x; val ny = tri.normal.y; val nz = tri.normal.z

                    // Rotate normal
                    val rnx1 = nx * fastTransform.cosYaw + nz * fastTransform.sinYaw
                    val rny1 = ny
                    val rnz1 = -nx * fastTransform.sinYaw + nz * fastTransform.cosYaw

                    val rnx2 = rnx1
                    val rny2 = rny1 * fastTransform.cosPitch - rnz1 * fastTransform.sinPitch
                    val rnz2 = rny1 * fastTransform.sinPitch + rnz1 * fastTransform.cosPitch

                    // Lighting Dot Product
                    val dotVal = kotlin.math.abs(rnx2 * lx + rny2 * ly + rnz2 * lz)
                    val intensity = (0.22f + 0.78f * dotVal).coerceIn(0.15f, 1.0f)

                    val bucketIdx = ((intensity - 0.15f) / 0.85f * (bucketCount - 0.01f)).toInt().coerceIn(0, bucketCount - 1)

                    val targetPath = if (renderMode == StlRenderMode.WIREFRAME) wireframePath else pathBuckets[bucketIdx]
                    targetPath.moveTo(p1Arr[0], p1Arr[1])
                    targetPath.lineTo(p2Arr[0], p2Arr[1])
                    targetPath.lineTo(p3Arr[0], p3Arr[1])
                    targetPath.close()
                }

                i++
            }

            // Draw solid shaded surfaces by bucket in 16 GPU calls
            if (renderMode != StlRenderMode.WIREFRAME) {
                for (b in 0 until bucketCount) {
                    val bucketRatio = b / (bucketCount - 1f)
                    val bIntensity = 0.15f + bucketRatio * 0.85f
                    // Specular highlight boost for metallic relief
                    val spec = if (bucketRatio > 0.75f) (bucketRatio - 0.75f) * 0.45f else 0f

                    val bColor = Color(
                        red = (meshColor.red * bIntensity + spec).coerceIn(0f, 1f),
                        green = (meshColor.green * bIntensity + spec).coerceIn(0f, 1f),
                        blue = (meshColor.blue * bIntensity + spec).coerceIn(0f, 1f),
                        alpha = if (renderMode == StlRenderMode.TRANSPARENT) 0.45f else 1f
                    )

                    drawPath(pathBuckets[b], color = bColor)
                }
            } else {
                drawPath(wireframePath, color = meshColor, style = Stroke(width = 0.8f))
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

            // Bottom-Left XYZ Axis Orientation Triad Indicator
            if (showTriadAxis) {
                val triadCenterX = 45f
                val triadCenterY = height - 45f
                val triadLen = 30f

                // Transform unit axis vectors
                val x_rx1 = fastTransform.cosYaw
                val x_ry1 = 0f
                val x_rz1 = -fastTransform.sinYaw
                val x_rx2 = x_rx1
                val x_ry2 = x_ry1 * fastTransform.cosPitch - x_rz1 * fastTransform.sinPitch

                val y_rx1 = 0f
                val y_ry1 = 1f
                val y_rz1 = 0f
                val y_rx2 = y_rx1
                val y_ry2 = y_ry1 * fastTransform.cosPitch - y_rz1 * fastTransform.sinPitch

                val z_rx1 = fastTransform.sinYaw
                val z_ry1 = 0f
                val z_rz1 = fastTransform.cosYaw
                val z_rx2 = z_rx1
                val z_ry2 = z_ry1 * fastTransform.cosPitch - z_rz1 * fastTransform.sinPitch

                // X Axis (Red)
                drawLine(
                    color = Color(0xFFEF4444),
                    start = Offset(triadCenterX, triadCenterY),
                    end = Offset(triadCenterX + x_rx2 * triadLen, triadCenterY - x_ry2 * triadLen),
                    strokeWidth = 3f
                )
                // Y Axis (Green)
                drawLine(
                    color = Color(0xFF10B981),
                    start = Offset(triadCenterX, triadCenterY),
                    end = Offset(triadCenterX + y_rx2 * triadLen, triadCenterY - y_ry2 * triadLen),
                    strokeWidth = 3f
                )
                // Z Axis (Blue)
                drawLine(
                    color = Color(0xFF3B82F6),
                    start = Offset(triadCenterX, triadCenterY),
                    end = Offset(triadCenterX + z_rx2 * triadLen, triadCenterY - z_ry2 * triadLen),
                    strokeWidth = 3f
                )
            }
        }
    }
}
