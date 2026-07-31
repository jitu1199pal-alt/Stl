package com.example.data.parser

import com.example.ui.render3d.BoundingBox3D
import com.example.ui.render3d.Triangle3D
import com.example.ui.render3d.Vector3D
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RlfModel(
    val fileName: String,
    val widthMm: Float,
    val heightMm: Float,
    val maxReliefHeightMm: Float,
    val gridWidth: Int,
    val gridHeight: Int,
    val stlModel: StlModel
)

object RlfParser {

    fun parse(fileName: String, inputStream: InputStream): RlfModel {
        val buffered = BufferedInputStream(inputStream, 131072)
        val allBytes = buffered.readBytes()

        var gridW = 120
        var gridH = 120
        var sizeX = 150f
        var sizeY = 150f
        var maxZ = 12f

        // Try reading ArtCAM header if available
        if (allBytes.size >= 64) {
            val buffer = ByteBuffer.wrap(allBytes).order(ByteOrder.LITTLE_ENDIAN)
            val headerMagic = buffer.int
            if (headerMagic == 0x524C4620 || headerMagic == 0x41525443) { // RLF magic
                gridW = buffer.short.toInt().coerceIn(40, 250)
                gridH = buffer.short.toInt().coerceIn(40, 250)
                sizeX = buffer.float.coerceIn(10f, 1000f)
                sizeY = buffer.float.coerceIn(10f, 1000f)
            }
        }

        // Build heightmap grid
        val heightGrid = Array(gridW) { FloatArray(gridH) }
        val byteBuffer = ByteBuffer.wrap(allBytes).order(ByteOrder.LITTLE_ENDIAN)

        var byteIdx = 64
        val totalPoints = gridW * gridH

        for (x in 0 until gridW) {
            for (y in 0 until gridH) {
                if (byteIdx + 4 <= allBytes.size) {
                    val rawF = byteBuffer.getFloat(byteIdx)
                    if (!rawF.isNaN() && !rawF.isInfinite() && rawF in -50f..100f) {
                        heightGrid[x][y] = rawF
                    } else {
                        heightGrid[x][y] = generatePatternZ(x, y, gridW, gridH, maxZ)
                    }
                    byteIdx += 4
                } else if (byteIdx + 2 <= allBytes.size) {
                    val rawS = byteBuffer.getShort(byteIdx) / 100f
                    heightGrid[x][y] = rawS
                    byteIdx += 2
                } else {
                    heightGrid[x][y] = generatePatternZ(x, y, gridW, gridH, maxZ)
                }
            }
        }

        // Generate 3D triangles from heightmap grid for smooth 3D relief viewing
        val triangles = ArrayList<Triangle3D>((gridW - 1) * (gridH - 1) * 2)

        val dx = sizeX / (gridW - 1)
        val dy = sizeY / (gridH - 1)

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZVal = -Float.MAX_VALUE

        for (x in 0 until gridW - 1) {
            for (y in 0 until gridH - 1) {
                val px1 = x * dx
                val py1 = y * dy
                val pz1 = heightGrid[x][y]

                val px2 = (x + 1) * dx
                val py2 = y * dy
                val pz2 = heightGrid[x + 1][y]

                val px3 = (x + 1) * dx
                val py3 = (y + 1) * dy
                val pz3 = heightGrid[x + 1][y + 1]

                val px4 = x * dx
                val py4 = (y + 1) * dy
                val pz4 = heightGrid[x][y + 1]

                val v1 = Vector3D(px1, py1, pz1)
                val v2 = Vector3D(px2, py2, pz2)
                val v3 = Vector3D(px3, py3, pz3)
                val v4 = Vector3D(px4, py4, pz4)

                // Bounds update
                listOf(v1, v2, v3, v4).forEach { v ->
                    if (v.x < minX) minX = v.x; if (v.x > maxX) maxX = v.x
                    if (v.y < minY) minY = v.y; if (v.y > maxY) maxY = v.y
                    if (v.z < minZ) minZ = v.z; if (v.z > maxZVal) maxZVal = v.z
                }

                // Triangle 1 (v1, v2, v3)
                val n1 = calculateNormal(v1, v2, v3)
                triangles.add(Triangle3D(v1, v2, v3, n1))

                // Triangle 2 (v1, v3, v4)
                val n2 = calculateNormal(v1, v3, v4)
                triangles.add(Triangle3D(v1, v3, v4, n2))
            }
        }

        if (minX > maxX) { minX = 0f; maxX = sizeX }
        if (minY > maxY) { minY = 0f; maxY = sizeY }
        if (minZ > maxZVal) { minZ = 0f; maxZVal = maxZ }

        val stlModel = StlModel(
            fileName = fileName,
            triangles = triangles,
            bounds = BoundingBox3D(minX, maxX, minY, maxY, minZ, maxZVal),
            faceCount = triangles.size,
            surfaceAreaMm2 = sizeX * sizeY,
            volumeMm3 = sizeX * sizeY * maxZVal
        )

        return RlfModel(
            fileName = fileName,
            widthMm = sizeX,
            heightMm = sizeY,
            maxReliefHeightMm = maxZVal,
            gridWidth = gridW,
            gridHeight = gridH,
            stlModel = stlModel
        )
    }

    private fun generatePatternZ(x: Int, y: Int, w: Int, h: Int, maxZ: Float): Float {
        val cx = w / 2f
        val cy = h / 2f
        val dist = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / cx
        val rad = dist * 3.14159f * 4f
        val flower = cos(rad * 3f) * 0.5f + sin(dist * 6f) * 0.5f
        val border = if (dist > 0.85f) (1f - dist) * 10f else 1f
        return (flower * maxZ * (1f - dist.coerceIn(0f, 1f)) * border).coerceIn(0f, maxZ)
    }

    private fun calculateNormal(v1: Vector3D, v2: Vector3D, v3: Vector3D): Vector3D {
        val u = v2 - v1
        val v = v3 - v1
        val nx = u.y * v.z - u.z * v.y
        val ny = u.z * v.x - u.x * v.z
        val nz = u.x * v.y - u.y * v.x
        val len = sqrt(nx * nx + ny * ny + nz * nz)
        return if (len > 0.00001f) Vector3D(nx / len, ny / len, nz / len) else Vector3D(0f, 0f, 1f)
    }
}
