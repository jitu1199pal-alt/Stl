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
     * Projects a 3D point in model coordinates to 2D screen coordinates.
     */
    fun project(
        point: Vector3D,
        center: Vector3D,
        maxDim: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Vector3D {
        // Center the point around model center
        val translated = point - center

        // Apply Rotations (Yaw around Z/Y, Pitch around X)
        val radYaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val radPitch = Math.toRadians(pitchDeg.toDouble()).toFloat()

        val rotY = Matrix4.rotationY(radYaw)
        val rotX = Matrix4.rotationX(radPitch)

        val rotated = rotX.multiply(rotY.multiply(translated))

        // Scale to viewport bounds
        val baseScale = (minOf(screenWidth, screenHeight) * 0.45f) / maxDim
        val finalScale = baseScale * zoom

        val sx = screenWidth / 2f + panX + rotated.x * finalScale
        val sy = screenHeight / 2f + panY - rotated.y * finalScale // Y inverted in screen coords

        return Vector3D(sx, sy, rotated.z)
    }
}
