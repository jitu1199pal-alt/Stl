package com.example.data.parser

import com.example.ui.render3d.BoundingBox3D
import com.example.ui.render3d.Triangle3D
import com.example.ui.render3d.Vector3D
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

data class StlModel(
    val fileName: String,
    val triangles: List<Triangle3D>,
    val bounds: BoundingBox3D,
    val faceCount: Int,
    val surfaceAreaMm2: Float,
    val volumeMm3: Float
)

object StlParser {

    private const val MAX_DISPLAY_TRIANGLES = 120_000

    fun parse(fileName: String, inputStream: InputStream): StlModel {
        val bufferedStream = BufferedInputStream(inputStream, 131072) // 128KB buffer for rapid I/O
        bufferedStream.mark(512)

        val headerBytes = ByteArray(80)
        val readHeaderLen = bufferedStream.read(headerBytes, 0, 80)
        val countBytes = ByteArray(4)
        val readCountLen = bufferedStream.read(countBytes, 0, 4)

        var isAscii = false
        if (readHeaderLen >= 5) {
            val headerStr = String(headerBytes, 0, readHeaderLen.coerceAtMost(80), StandardCharsets.US_ASCII).lowercase()
            if (headerStr.startsWith("solid")) {
                val testBuffer = ByteBuffer.wrap(countBytes).order(ByteOrder.LITTLE_ENDIAN)
                val triCount = testBuffer.int
                if (triCount <= 0 || triCount > 20_000_000) {
                    isAscii = true
                }
            }
        }

        bufferedStream.reset()

        return if (isAscii) {
            parseAscii(fileName, bufferedStream)
        } else {
            parseBinary(fileName, bufferedStream)
        }
    }

    private fun parseBinary(fileName: String, inputStream: InputStream): StlModel {
        val header = ByteArray(80)
        inputStream.read(header, 0, 80)
        val countBytes = ByteArray(4)
        inputStream.read(countBytes, 0, 4)
        val countBuffer = ByteBuffer.wrap(countBytes).order(ByteOrder.LITTLE_ENDIAN)
        val numTriangles = countBuffer.int.coerceAtLeast(0)

        // Block sampling to ensure contiguous connected triangles without isolated dot-gaps
        val blockSize = 32
        val blockPeriod = if (numTriangles > MAX_DISPLAY_TRIANGLES) {
            ((numTriangles.toDouble() / MAX_DISPLAY_TRIANGLES) * blockSize).toInt().coerceAtLeast(blockSize)
        } else {
            blockSize
        }

        val estimatedDisplayCount = if (numTriangles <= MAX_DISPLAY_TRIANGLES) numTriangles else MAX_DISPLAY_TRIANGLES + 500
        val triangles = ArrayList<Triangle3D>(estimatedDisplayCount)

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        var totalArea = 0f
        var totalVolume = 0f

        val recordBuffer = ByteArray(50)
        val byteBuf = ByteBuffer.wrap(recordBuffer).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until numTriangles) {
            var readTotal = 0
            while (readTotal < 50) {
                val r = inputStream.read(recordBuffer, readTotal, 50 - readTotal)
                if (r < 0) break
                readTotal += r
            }
            if (readTotal < 50) break

            byteBuf.rewind()
            val nx = byteBuf.float; val ny = byteBuf.float; val nz = byteBuf.float
            val v1x = byteBuf.float; val v1y = byteBuf.float; val v1z = byteBuf.float
            val v2x = byteBuf.float; val v2y = byteBuf.float; val v2z = byteBuf.float
            val v3x = byteBuf.float; val v3y = byteBuf.float; val v3z = byteBuf.float

            minX = minOf(minX, v1x, v2x, v3x); maxX = maxOf(maxX, v1x, v2x, v3x)
            minY = minOf(minY, v1y, v2y, v3y); maxY = maxOf(maxY, v1y, v2y, v3y)
            minZ = minOf(minZ, v1z, v2z, v3z); maxZ = maxOf(maxZ, v1z, v2z, v3z)

            // Calculate Area & Volume statistics accurately for ALL triangles
            val crossX = (v2y - v1y) * (v3z - v1z) - (v2z - v1z) * (v3y - v1y)
            val crossY = (v2z - v1z) * (v3x - v1x) - (v2x - v1x) * (v3z - v1z)
            val crossZ = (v2x - v1x) * (v3y - v1y) - (v2y - v1y) * (v3x - v1x)
            val crossLen = kotlin.math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ)
            totalArea += crossLen * 0.5f

            val v = (v1x * (v2y * v3z - v3y * v2z) +
                    v2x * (v3y * v1z - v1y * v3z) +
                    v3x * (v1y * v2z - v2y * v1z)) / 6f
            totalVolume += v

            // Keep contiguous blocks so surface remains solid and seamless
            if (numTriangles <= MAX_DISPLAY_TRIANGLES || (i % blockPeriod) < blockSize) {
                if (triangles.size < MAX_DISPLAY_TRIANGLES) {
                    val v1 = Vector3D(v1x, v1y, v1z)
                    val v2 = Vector3D(v2x, v2y, v2z)
                    val v3 = Vector3D(v3x, v3y, v3z)

                    val normal = if (nx == 0f && ny == 0f && nz == 0f) {
                        if (crossLen > 0.00001f) {
                            Vector3D(crossX / crossLen, crossY / crossLen, crossZ / crossLen)
                        } else {
                            Vector3D(0f, 0f, 1f)
                        }
                    } else {
                        Vector3D(nx, ny, nz).normalize()
                    }

                    triangles.add(Triangle3D(v1, v2, v3, normal))
                }
            }
        }

        if (minX > maxX) { minX = 0f; maxX = 10f; minY = 0f; maxY = 10f; minZ = 0f; maxZ = 10f }

        return StlModel(
            fileName = fileName,
            triangles = triangles,
            bounds = BoundingBox3D(minX, maxX, minY, maxY, minZ, maxZ),
            faceCount = numTriangles,
            surfaceAreaMm2 = totalArea,
            volumeMm3 = abs(totalVolume)
        )
    }

    private fun parseAscii(fileName: String, inputStream: InputStream): StlModel {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 131072)
        val triangles = mutableListOf<Triangle3D>()

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        var currentNormal = Vector3D(0f, 0f, 1f)
        val vertices = ArrayList<Vector3D>(3)
        var totalFacetCount = 0

        var line = reader.readLine()
        while (line != null) {
            val trimmed = line.trim().lowercase()
            if (trimmed.startsWith("facet normal")) {
                val parts = trimmed.split(Regex("""\s+"""))
                if (parts.size >= 5) {
                    val nx = parts[2].toFloatOrNull() ?: 0f
                    val ny = parts[3].toFloatOrNull() ?: 0f
                    val nz = parts[4].toFloatOrNull() ?: 1f
                    currentNormal = Vector3D(nx, ny, nz)
                }
            } else if (trimmed.startsWith("vertex")) {
                val parts = trimmed.split(Regex("""\s+"""))
                if (parts.size >= 4) {
                    val vx = parts[1].toFloatOrNull() ?: 0f
                    val vy = parts[2].toFloatOrNull() ?: 0f
                    val vz = parts[3].toFloatOrNull() ?: 0f
                    val v = Vector3D(vx, vy, vz)
                    vertices.add(v)

                    minX = minOf(minX, vx); maxX = maxOf(maxX, vx)
                    minY = minOf(minY, vy); maxY = maxOf(maxY, vy)
                    minZ = minOf(minZ, vz); maxZ = maxOf(maxZ, vz)
                }
            } else if (trimmed.startsWith("endfacet")) {
                totalFacetCount++
                if (vertices.size >= 3) {
                    // Cap ASCII display triangles if ASCII file is massive
                    if (triangles.size < MAX_DISPLAY_TRIANGLES) {
                        triangles.add(Triangle3D(vertices[0], vertices[1], vertices[2], currentNormal))
                    }
                }
                vertices.clear()
            }
            line = reader.readLine()
        }

        if (minX > maxX) { minX = 0f; maxX = 10f; minY = 0f; maxY = 10f; minZ = 0f; maxZ = 10f }

        return StlModel(
            fileName = fileName,
            triangles = triangles,
            bounds = BoundingBox3D(minX, maxX, minY, maxY, minZ, maxZ),
            faceCount = totalFacetCount,
            surfaceAreaMm2 = 0f,
            volumeMm3 = 0f
        )
    }
}
