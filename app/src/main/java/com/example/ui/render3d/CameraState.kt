package com.example.ui.render3d

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

class CameraState {
    var pitchDeg by mutableFloatStateOf(45f)
    var yawDeg by mutableFloatStateOf(-45f)
    var zoom by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)

    fun rotate(deltaYaw: Float, deltaPitch: Float) {
        yawDeg += deltaYaw
        pitchDeg = (pitchDeg + deltaPitch).coerceIn(-89f, 89f)
    }

    fun scaleZoom(factor: Float) {
        zoom = (zoom * factor).coerceIn(0.1f, 20f)
    }

    fun pan(dx: Float, dy: Float) {
        panX += dx
        panY += dy
    }

    fun reset() {
        pitchDeg = 45f
        yawDeg = -45f
        zoom = 1f
        panX = 0f
        panY = 0f
    }

    fun setTopView() {
        pitchDeg = 0f
        yawDeg = 0f
    }

    fun setFrontView() {
        pitchDeg = 90f
        yawDeg = 0f
    }

    fun setRightView() {
        pitchDeg = 90f
        yawDeg = -90f
    }

    fun setIsometricView() {
        pitchDeg = 35.264f
        yawDeg = -45f
    }

    /**
     * Precomputed projection constants for ultra-fast GPU/Canvas calculations with zero object allocation.
     */
    data class FastTransform(
        val cosYaw: Float,
        val sinYaw: Float,
        val cosPitch: Float,
        val sinPitch: Float,
        val finalScale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val centerX: Float,
        val centerY: Float,
        val centerZ: Float
    )

    fun getFastTransform(
        center: Vector3D,
        maxDim: Float,
        screenWidth: Float,
        screenHeight: Float
    ): FastTransform {
        val radYaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val radPitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val baseScale = (minOf(screenWidth, screenHeight) * 0.45f) / maxDim
        val finalScale = baseScale * zoom

        return FastTransform(
            cosYaw = kotlin.math.cos(radYaw),
            sinYaw = kotlin.math.sin(radYaw),
            cosPitch = kotlin.math.cos(radPitch),
            sinPitch = kotlin.math.sin(radPitch),
            finalScale = finalScale,
            offsetX = screenWidth / 2f + panX,
            offsetY = screenHeight / 2f + panY,
            centerX = center.x,
            centerY = center.y,
            centerZ = center.z
        )
    }

    /**
     * Projects 3D x,y,z directly to 2D x,y,z screen coordinates without allocating any objects.
     */
    fun projectFast(
        px: Float, py: Float, pz: Float,
        t: FastTransform,
        outResult: FloatArray // float array of size 3: [sx, sy, sz]
    ) {
        val tx = px - t.centerX
        val ty = py - t.centerY
        val tz = pz - t.centerZ

        // Yaw around Y
        val rx1 = tx * t.cosYaw + tz * t.sinYaw
        val ry1 = ty
        val rz1 = -tx * t.sinYaw + tz * t.cosYaw

        // Pitch around X
        val rx2 = rx1
        val ry2 = ry1 * t.cosPitch - rz1 * t.sinPitch
        val rz2 = ry1 * t.sinPitch + rz1 * t.cosPitch

        outResult[0] = t.offsetX + rx2 * t.finalScale
        outResult[1] = t.offsetY - ry2 * t.finalScale
        outResult[2] = rz2
    }

    /**
     * Projects a 3D point in model coordinates to 2D screen coordinates.
     */
    fun project(
        point: Vector3D,
        center: Vector3D,
        maxDim: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Vector3D {
        val t = getFastTransform(center, maxDim, screenWidth, screenHeight)
        val res = FloatArray(3)
        projectFast(point.x, point.y, point.z, t, res)
        return Vector3D(res[0], res[1], res[2])
    }
}
