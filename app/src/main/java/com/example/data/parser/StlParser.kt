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
import kotlin.math.ceil
import kotlin.math.sqrt

data class StlModel(
    val fileName: String,
    val triangles: List<Triangle3D>,
    val bounds: BoundingBox3D,
    val faceCount: Int,
    val surfaceAreaMm2: Float,
    val volumeMm3: Float,
    val vertexBuffer: java.nio.FloatBuffer? = null,
    val normalBuffer: java.nio.FloatBuffer? = null
) {
    fun getOrBuildVertexBuffer(): java.nio.FloatBuffer {
        val existing = vertexBuffer
        if (existing != null) {
            existing.position(0)
            return existing
        }
        val count = triangles.size
        val buf = ByteBuffer.allocateDirect(count * 3 * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        for (tri in triangles) {
            buf.put(tri.v1.x); buf.put(tri.v1.y); buf.put(tri.v1.z)
            buf.put(tri.v2.x); buf.put(tri.v2.y); buf.put(tri.v2.z)
            buf.put(tri.v3.x); buf.put(tri.v3.y); buf.put(tri.v3.z)
        }
        buf.position(0)
        return buf
    }

    fun getOrBuildNormalBuffer(): java.nio.FloatBuffer {
        val existing = normalBuffer
        if (existing != null) {
            existing.position(0)
            return existing
        }
        val count = triangles.size
        val buf = ByteBuffer.allocateDirect(count * 3 * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        for (tri in triangles) {
            val n = tri.normal
            for (k in 0 until 3) {
                buf.put(n.x); buf.put(n.y); buf.put(n.z)
            }
        }
        buf.position(0)
        return buf
    }
}

object StlParser {

    fun parse(fileName: String, inputStream: InputStream): StlModel {
        val bufferedStream = BufferedInputStream(inputStream, 262144)
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

        if (numTriangles == 0) {
            return StlModel(
                fileName = fileName,
                triangles = emptyList(),
                bounds = BoundingBox3D(0f, 10f, 0f, 10f, 0f, 10f),
                faceCount = 0,
                surfaceAreaMm2 = 0f,
                volumeMm3 = 0f
            )
        }

        // Direct native buffers for OpenGL ES rendering - ZERO heap overhead, holds 100% of all triangles!
        val vBuf = ByteBuffer.allocateDirect(numTriangles * 3 * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        val nBuf = ByteBuffer.allocateDirect(numTriangles * 3 * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        val trianglesList = ArrayList<Triangle3D>(if (numTriangles <= 60_000) numTriangles else 0)

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        var totalArea = 0f
        var totalVolume = 0f

        // Fast block reading (500 triangles = 25,000 bytes per chunk)
        val chunkSize = 500
        val recordSize = 50
        val blockBytes = ByteArray(chunkSize * recordSize)
        val byteBuf = ByteBuffer.wrap(blockBytes).order(ByteOrder.LITTLE_ENDIAN)

        var trianglesRead = 0
        while (trianglesRead < numTriangles) {
            val toRead = minOf(chunkSize, numTriangles - trianglesRead)
            val bytesNeeded = toRead * recordSize
            var bytesRead = 0
            while (bytesRead < bytesNeeded) {
                val r = inputStream.read(blockBytes, bytesRead, bytesNeeded - bytesRead)
                if (r < 0) break
                bytesRead += r
            }
            val recordsInThisBlock = bytesRead / recordSize
            if (recordsInThisBlock == 0) break

            byteBuf.position(0)
            for (idx in 0 until recordsInThisBlock) {
                val nx = byteBuf.float
                val ny = byteBuf.float
                val nz = byteBuf.float
                val v1x = byteBuf.float
                val v1y = byteBuf.float
                val v1z = byteBuf.float
                val v2x = byteBuf.float
                val v2y = byteBuf.float
                val v2z = byteBuf.float
                val v3x = byteBuf.float
                val v3y = byteBuf.float
                val v3z = byteBuf.float
                byteBuf.short // attribute byte count

                minX = minOf(minX, v1x, v2x, v3x); maxX = maxOf(maxX, v1x, v2x, v3x)
                minY = minOf(minY, v1y, v2y, v3y); maxY = maxOf(maxY, v1y, v2y, v3y)
                minZ = minOf(minZ, v1z, v2z, v3z); maxZ = maxOf(maxZ, v1z, v2z, v3z)

                // Cross product for normal & area
                val crossX = (v2y - v1y) * (v3z - v1z) - (v2z - v1z) * (v3y - v1y)
                val crossY = (v2z - v1z) * (v3x - v1x) - (v2x - v1x) * (v3z - v1z)
                val crossZ = (v2x - v1x) * (v3y - v1y) - (v2y - v1y) * (v3x - v1x)
                val crossLen = sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ)
                totalArea += crossLen * 0.5f

                val v = (v1x * (v2y * v3z - v3y * v2z) +
                        v2x * (v3y * v1z - v1y * v3z) +
                        v3x * (v1y * v2z - v2y * v1z)) / 6f
                totalVolume += v

                // Put 3 vertices into vertexBuffer
                vBuf.put(v1x); vBuf.put(v1y); vBuf.put(v1z)
                vBuf.put(v2x); vBuf.put(v2y); vBuf.put(v2z)
                vBuf.put(v3x); vBuf.put(v3y); vBuf.put(v3z)

                // Determine normal
                val finalNx: Float
                val finalNy: Float
                val finalNz: Float
                if (nx == 0f && ny == 0f && nz == 0f) {
                    if (crossLen > 1e-6f) {
                        finalNx = crossX / crossLen
                        finalNy = crossY / crossLen
                        finalNz = crossZ / crossLen
                    } else {
                        finalNx = 0f; finalNy = 0f; finalNz = 1f
                    }
                } else {
                    val nlen = sqrt(nx * nx + ny * ny + nz * nz)
                    if (nlen > 1e-6f) {
                        finalNx = nx / nlen; finalNy = ny / nlen; finalNz = nz / nlen
                    } else {
                        finalNx = 0f; finalNy = 0f; finalNz = 1f
                    }
                }

                // Put normal 3 times for the 3 vertices
                for (k in 0 until 3) {
                    nBuf.put(finalNx); nBuf.put(finalNy); nBuf.put(finalNz)
                }

                if (numTriangles <= 60_000) {
                    trianglesList.add(
                        Triangle3D(
                            Vector3D(v1x, v1y, v1z),
                            Vector3D(v2x, v2y, v2z),
                            Vector3D(v3x, v3y, v3z),
                            Vector3D(finalNx, finalNy, finalNz)
                        )
                    )
                }
            }
            trianglesRead += recordsInThisBlock
            if (recordsInThisBlock < toRead) break
        }

        vBuf.position(0)
        nBuf.position(0)

        if (minX > maxX) { minX = 0f; maxX = 10f; minY = 0f; maxY = 10f; minZ = 0f; maxZ = 10f }

        return StlModel(
            fileName = fileName,
            triangles = trianglesList,
            bounds = BoundingBox3D(minX, maxX, minY, maxY, minZ, maxZ),
            faceCount = trianglesRead,
            surfaceAreaMm2 = totalArea,
            volumeMm3 = abs(totalVolume),
            vertexBuffer = vBuf,
            normalBuffer = nBuf
        )
    }

    private fun parseAscii(fileName: String, inputStream: InputStream): StlModel {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 262144)
        val verticesList = ArrayList<Float>(16384)
        val normalsList = ArrayList<Float>(16384)
        val trianglesList = ArrayList<Triangle3D>(2048)

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        var currentNormal = Vector3D(0f, 0f, 1f)
        val currentVertices = ArrayList<Vector3D>(3)
        var totalFaces = 0
        var totalArea = 0f
        var totalVolume = 0f

        var line = reader.readLine()
        while (line != null) {
            val trimmed = line.trim().lowercase()
            if (trimmed.startsWith("facet normal")) {
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 4) {
                    val nx = parts[2].toFloatOrNull() ?: 0f
                    val ny = parts[3].toFloatOrNull() ?: 0f
                    val nz = parts[4].toFloatOrNull() ?: 1f
                    currentNormal = Vector3D(nx, ny, nz).normalize()
                }
                currentVertices.clear()
            } else if (trimmed.startsWith("vertex")) {
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 4) {
                    val vx = parts[1].toFloatOrNull() ?: 0f
                    val vy = parts[2].toFloatOrNull() ?: 0f
                    val vz = parts[3].toFloatOrNull() ?: 0f
                    currentVertices.add(Vector3D(vx, vy, vz))
                    minX = minOf(minX, vx); maxX = maxOf(maxX, vx)
                    minY = minOf(minY, vy); maxY = maxOf(maxY, vy)
                    minZ = minOf(minZ, vz); maxZ = maxOf(maxZ, vz)
                }
            } else if (trimmed.startsWith("endfacet")) {
                if (currentVertices.size == 3) {
                    totalFaces++
                    val v1 = currentVertices[0]
                    val v2 = currentVertices[1]
                    val v3 = currentVertices[2]

                    val crossX = (v2.y - v1.y) * (v3.z - v1.z) - (v2.z - v1.z) * (v3.y - v1.y)
                    val crossY = (v2.z - v1.z) * (v3.x - v1.x) - (v2.x - v1.x) * (v3.z - v1.z)
                    val crossZ = (v2.x - v1.x) * (v3.y - v1.y) - (v2.y - v1.y) * (v3.x - v1.x)
                    val crossLen = sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ)
                    totalArea += crossLen * 0.5f

                    val v = (v1.x * (v2.y * v3.z - v3.y * v2.z) +
                            v2.x * (v3.y * v1.z - v1.y * v3.z) +
                            v3.x * (v1.y * v2.z - v2.y * v1.z)) / 6f
                    totalVolume += v

                    // Add to raw float buffers
                    verticesList.add(v1.x); verticesList.add(v1.y); verticesList.add(v1.z)
                    verticesList.add(v2.x); verticesList.add(v2.y); verticesList.add(v2.z)
                    verticesList.add(v3.x); verticesList.add(v3.y); verticesList.add(v3.z)

                    val normToUse = if (currentNormal.length() > 0.001f) currentNormal else {
                        if (crossLen > 1e-6f) Vector3D(crossX / crossLen, crossY / crossLen, crossZ / crossLen)
                        else Vector3D(0f, 0f, 1f)
                    }

                    for (k in 0 until 3) {
                        normalsList.add(normToUse.x); normalsList.add(normToUse.y); normalsList.add(normToUse.z)
                    }

                    if (totalFaces <= 60_000) {
                        trianglesList.add(Triangle3D(v1, v2, v3, normToUse))
                    }
                }
            }
            line = reader.readLine()
        }

        if (minX > maxX) { minX = 0f; maxX = 10f; minY = 0f; maxY = 10f; minZ = 0f; maxZ = 10f }

        val vBuf = ByteBuffer.allocateDirect(verticesList.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        for (f in verticesList) vBuf.put(f)
        vBuf.position(0)

        val nBuf = ByteBuffer.allocateDirect(normalsList.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        for (f in normalsList) nBuf.put(f)
        nBuf.position(0)

        return StlModel(
            fileName = fileName,
            triangles = trianglesList,
            bounds = BoundingBox3D(minX, maxX, minY, maxY, minZ, maxZ),
            faceCount = totalFaces,
            surfaceAreaMm2 = totalArea,
            volumeMm3 = abs(totalVolume),
            vertexBuffer = vBuf,
            normalBuffer = nBuf
        )
    }
}
