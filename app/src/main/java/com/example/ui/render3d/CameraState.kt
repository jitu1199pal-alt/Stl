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
        zoom = (zoom * factor).coerceIn(0.02f, 80f)
    }

    fun zoomIn() {
        scaleZoom(1.30f)
    }

    fun zoomOut() {
        scaleZoom(1f / 1.30f)
    }

    fun setZoomLevel(newZoom: Float) {
        zoom = newZoom.coerceIn(0.02f, 80f)
    }

    fun pan(dx: Float, dy: Float) {
        panX += dx
        panY += dy
    }

    fun resetPan() {
        panX = 0f
        panY = 0f
    }

    fun fitToScreen() {
        zoom = 1f
        panX = 0f
        panY = 0f
        pitchDeg = 0f
        yawDeg = 0f
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
        panX = 0f
        panY = 0f
    }

    fun setBottomView() {
        pitchDeg = 0f
        yawDeg = 180f
        panX = 0f
        panY = 0f
    }

    fun setFrontView() {
        pitchDeg = 89.9f
        yawDeg = 0f
        panX = 0f
        panY = 0f
    }

    fun setBackView() {
        pitchDeg = 89.9f
        yawDeg = 180f
        panX = 0f
        panY = 0f
    }

    fun setLeftView() {
        pitchDeg = 89.9f
        yawDeg = 90f
        panX = 0f
        panY = 0f
    }

    fun setRightView() {
        pitchDeg = 89.9f
        yawDeg = -90f
        panX = 0f
        panY = 0f
    }

    fun setIsometricView() {
        setIsometricNE()
    }

    fun setIsometricNE() {
        pitchDeg = 35.264f
        yawDeg = -45f
        panX = 0f
        panY = 0f
    }

    fun setIsometricSE() {
        pitchDeg = 35.264f
        yawDeg = -135f
        panX = 0f
        panY = 0f
    }

    fun setIsometricSW() {
        pitchDeg = 35.264f
        yawDeg = 135f
        panX = 0f
        panY = 0f
    }

    fun setIsometricNW() {
        pitchDeg = 35.264f
        yawDeg = 45f
        panX = 0f
        panY = 0f
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
        screenHeight: Float,
        boundsSizeX: Float = maxDim,
        boundsSizeY: Float = maxDim
    ): FastTransform {
        val radYaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val radPitch = Math.toRadians(pitchDeg.toDouble()).toFloat()

        val baseScale = if (kotlin.math.abs(pitchDeg) < 1f && kotlin.math.abs(yawDeg) < 1f) {
            // 2D CAD Top View: Fit comfortably to available screen area (90% width, 88% height)
            val fitX = (screenWidth * 0.90f) / boundsSizeX.coerceAtLeast(0.1f)
            val fitY = (screenHeight * 0.88f) / boundsSizeY.coerceAtLeast(0.1f)
            minOf(fitX, fitY).coerceAtLeast(0.0001f)
        } else {
            // 3D Isometric View: Fit 80% of screen minimum dimension
            (minOf(screenWidth, screenHeight) * 0.80f) / maxDim.coerceAtLeast(0.1f)
        }
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
