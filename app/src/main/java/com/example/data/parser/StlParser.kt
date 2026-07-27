package com.example.data.parser

import com.example.ui.render3d.BoundingBox3D
import com.example.ui.render3d.Triangle3D
import com.example.ui.render3d.Vector3D
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    fun parse(fileName: String, inputStream: InputStream): StlModel {
        val bytes = inputStream.readBytes()
        val streamLength = bytes.size

        // Check if binary or ASCII
        val isAscii = if (streamLength > 84) {
            val headerString = String(bytes, 0, 80.coerceAtMost(streamLength), Charsets.US_ASCII).lowercase()
            headerString.startsWith("solid") && !isLikelyBinary(bytes)
        } else false

        return if (isAscii) {
            parseAscii(fileName, String(bytes, Charsets.UTF_8))
        } else {
            parseBinary(fileName, bytes)
        }
    }

    private fun isLikelyBinary(bytes: ByteArray): Boolean {
        if (bytes.size < 84) return false
        val buffer = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN)
        val numTriangles = buffer.int
        val expectedSize = 84 + numTriangles * 50
        return abs(bytes.size - expectedSize) < 100
    }

    private fun parseBinary(fileName: String, bytes: ByteArray): StlModel {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(80) // Skip 80 byte header
        val count = buffer.int

        val triangles = ArrayList<Triangle3D>(count.coerceAtMost(100000))

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        var totalArea = 0f
        var totalVolume = 0f

        for (i in 0 until count) {
            if (buffer.remaining() < 50) break

            val nx = buffer.float
            val ny = buffer.float
            val nz = buffer.float

            val v1x = buffer.float; val v1y = buffer.float; val v1z = buffer.float
            val v2x = buffer.float; val v2y = buffer.float; val v2z = buffer.float
            val v3x = buffer.float; val v3y = buffer.float; val v3z = buffer.float

            buffer.short // attribute byte count

            val v1 = Vector3D(v1x, v1y, v1z)
            val v2 = Vector3D(v2x, v2y, v2z)
            val v3 = Vector3D(v3x, v3y, v3z)

            minX = minOf(minX, v1x, v2x, v3x); maxX = maxOf(maxX, v1x, v2x, v3x)
            minY = minOf(minY, v1y, v2y, v3y); maxY = maxOf(maxY, v1y, v2y, v3y)
            minZ = minOf(minZ, v1z, v2z, v3z); maxZ = maxOf(maxZ, v1z, v2z, v3z)

            val normal = if (nx == 0f && ny == 0f && nz == 0f) {
                (v2 - v1).cross(v3 - v1).normalize()
            } else {
                Vector3D(nx, ny, nz).normalize()
            }

            // Area calculation
            val cross = (v2 - v1).cross(v3 - v1)
            val area = cross.length() * 0.5f
            totalArea += area

            // Signed volume calculation (Divergence theorem)
            val v = (v1.x * (v2.y * v3.z - v3.y * v2.z) +
                    v2.x * (v3.y * v1.z - v1.y * v3.z) +
                    v3.x * (v1.y * v2.z - v2.y * v1.z)) / 6f
            totalVolume += v

            triangles.add(Triangle3D(v1, v2, v3, normal))
        }

        if (minX > maxX) { minX = 0f; maxX = 10f; minY = 0f; maxY = 10f; minZ = 0f; maxZ = 10f }

        return StlModel(
            fileName = fileName,
            triangles = triangles,
            bounds = BoundingBox3D(minX, maxX, minY, maxY, minZ, maxZ),
            faceCount = triangles.size,
            surfaceAreaMm2 = totalArea,
            volumeMm3 = abs(totalVolume)
        )
    }

    private fun parseAscii(fileName: String, content: String): StlModel {
        val lines = content.lines()
        val triangles = mutableListOf<Triangle3D>()

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        var currentNormal = Vector3D(0f, 0f, 1f)
        val vertices = mutableListOf<Vector3D>()

        for (line in lines) {
            val trimmed = line.trim().lowercase()
            if (trimmed.startsWith("facet normal")) {
                val parts = trimmed.split(Regex("""\s+"""))
                if (parts.size >= 4) {
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
                if (vertices.size >= 3) {
                    triangles.add(Triangle3D(vertices[0], vertices[1], vertices[2], currentNormal))
                }
                vertices.clear()
            }
        }

        if (minX > maxX) { minX = 0f; maxX = 10f; minY = 0f; maxY = 10f; minZ = 0f; maxZ = 10f }

        return StlModel(
            fileName = fileName,
            triangles = triangles,
            bounds = BoundingBox3D(minX, maxX, minY, maxY, minZ, maxZ),
            faceCount = triangles.size,
            surfaceAreaMm2 = 0f,
            volumeMm3 = 0f
        )
    }
}
