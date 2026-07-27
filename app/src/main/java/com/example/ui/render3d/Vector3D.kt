package com.example.ui.render3d

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vector3D(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Vector3D) = Vector3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3D) = Vector3D(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vector3D(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = if (scalar != 0f) Vector3D(x / scalar, y / scalar, z / scalar) else this

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalize(): Vector3D {
        val len = length()
        return if (len > 0.00001f) Vector3D(x / len, y / len, z / len) else Vector3D(0f, 0f, 1f)
    }

    fun dot(other: Vector3D): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3D): Vector3D = Vector3D(
        x = y * other.z - z * other.y,
        y = z * other.x - x * other.z,
        z = x * other.y - y * other.x
    )
}

data class Triangle3D(
    val v1: Vector3D,
    val v2: Vector3D,
    val v3: Vector3D,
    val normal: Vector3D = (v2 - v1).cross(v3 - v1).normalize()
)

data class BoundingBox3D(
    val minX: Float = 0f,
    val maxX: Float = 0f,
    val minY: Float = 0f,
    val maxY: Float = 0f,
    val minZ: Float = 0f,
    val maxZ: Float = 0f
) {
    val sizeX: Float get() = abs(maxX - minX)
    val sizeY: Float get() = abs(maxY - minY)
    val sizeZ: Float get() = abs(maxZ - minZ)
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
    val centerZ: Float get() = (minZ + maxZ) / 2f
    val maxDimension: Float get() = maxOf(sizeX, sizeY, sizeZ).coerceAtLeast(0.001f)

    fun center(): Vector3D = Vector3D(centerX, centerY, centerZ)
}

/**
 * 4x4 Transformation Matrix for 3D Projection and Camera Transforms
 */
class Matrix4 private constructor(val m: FloatArray) {
    companion object {
        fun identity(): Matrix4 {
            val m = FloatArray(16)
            m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
            return Matrix4(m)
        }

        fun rotationX(angleRad: Float): Matrix4 {
            val c = cos(angleRad)
            val s = sin(angleRad)
            val m = identity().m
            m[5] = c; m[6] = -s
            m[9] = s; m[10] = c
            return Matrix4(m)
        }

        fun rotationY(angleRad: Float): Matrix4 {
            val c = cos(angleRad)
            val s = sin(angleRad)
            val m = identity().m
            m[0] = c; m[2] = s
            m[8] = -s; m[10] = c
            return Matrix4(m)
        }

        fun rotationZ(angleRad: Float): Matrix4 {
            val c = cos(angleRad)
            val s = sin(angleRad)
            val m = identity().m
            m[0] = c; m[1] = -s
            m[4] = s; m[5] = c
            return Matrix4(m)
        }
    }

    fun multiply(p: Vector3D): Vector3D {
        val x = m[0] * p.x + m[1] * p.y + m[2] * p.z + m[3]
        val y = m[4] * p.x + m[5] * p.y + m[6] * p.z + m[7]
        val z = m[8] * p.x + m[9] * p.y + m[10] * p.z + m[11]
        val w = m[12] * p.x + m[13] * p.y + m[14] * p.z + m[15]
        return if (w != 0f && w != 1f) Vector3D(x / w, y / w, z / w) else Vector3D(x, y, z)
    }
}
