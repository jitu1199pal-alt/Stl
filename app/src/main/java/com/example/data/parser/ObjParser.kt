package com.example.data.parser

import com.example.ui.render3d.BoundingBox3D
import com.example.ui.render3d.Triangle3D
import com.example.ui.render3d.Vector3D
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * High-performance Wavefront OBJ 3D Model Parser.
 * Supports:
 * - Vertices (v x y z)
 * - Normals (vn nx ny nz)
 * - Faces (f v1/vt1/vn1 v2/vt2/vn2 v3/vt3/vn3 ...)
 * - Automatic triangulation of quads and n-gons
 * - Relative negative vertex indices
 * - Pre-allocated native FloatBuffers for high-FPS OpenGL 3D rendering
 */
object ObjParser {

    fun parse(fileName: String, inputStream: InputStream): StlModel {
        val vertices = ArrayList<Vector3D>(16384)
        val normals = ArrayList<Vector3D>(16384)
        val triangles = ArrayList<Triangle3D>(16384)

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 65536)
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line?.trim() ?: continue
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            // 1. Vertex line: v x y z
            if (trimmed.startsWith("v ") || trimmed.startsWith("v\t")) {
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 4) {
                    try {
                        val x = parts[1].toFloat()
                        val y = parts[2].toFloat()
                        val z = parts[3].toFloat()
                        val v = Vector3D(x, y, z)
                        vertices.add(v)

                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                        if (z < minZ) minZ = z
                        if (z > maxZ) maxZ = z
                    } catch (_: NumberFormatException) {}
                }
            }
            // 2. Normal line: vn nx ny nz
            else if (trimmed.startsWith("vn ") || trimmed.startsWith("vn\t")) {
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 4) {
                    try {
                        val nx = parts[1].toFloat()
                        val ny = parts[2].toFloat()
                        val nz = parts[3].toFloat()
                        normals.add(Vector3D(nx, ny, nz))
                    } catch (_: NumberFormatException) {}
                }
            }
            // 3. Face line: f v1/vt1/vn1 v2/vt2/vn2 v3/vt3/vn3 [v4 ...]
            else if (trimmed.startsWith("f ") || trimmed.startsWith("f\t")) {
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size < 4) continue

                val faceVertexIndices = ArrayList<Int>(parts.size - 1)
                val faceNormalIndices = ArrayList<Int>(parts.size - 1)

                for (i in 1 until parts.size) {
                    val token = parts[i]
                    if (token.isEmpty()) continue
                    val subTokens = token.split("/")
                    val vIdxStr = subTokens[0]
                    if (vIdxStr.isNotEmpty()) {
                        try {
                            var vIdx = vIdxStr.toInt()
                            // Convert 1-based index (or negative relative index) to 0-based
                            if (vIdx < 0) {
                                vIdx = vertices.size + vIdx
                            } else {
                                vIdx -= 1
                            }
                            if (vIdx in vertices.indices) {
                                faceVertexIndices.add(vIdx)

                                var nIdx = -1
                                if (subTokens.size >= 3 && subTokens[2].isNotEmpty()) {
                                    var parsedN = subTokens[2].toInt()
                                    if (parsedN < 0) {
                                        parsedN = normals.size + parsedN
                                    } else {
                                        parsedN -= 1
                                    }
                                    if (parsedN in normals.indices) {
                                        nIdx = parsedN
                                    }
                                }
                                faceNormalIndices.add(nIdx)
                            }
                        } catch (_: NumberFormatException) {}
                    }
                }

                // Triangulate face using triangle fan (v0, vi, vi+1)
                if (faceVertexIndices.size >= 3) {
                    val v0 = vertices[faceVertexIndices[0]]
                    for (i in 1 until faceVertexIndices.size - 1) {
                        val v1 = vertices[faceVertexIndices[i]]
                        val v2 = vertices[faceVertexIndices[i + 1]]

                        // Compute face normal or use specified normal
                        val n0Idx = faceNormalIndices.getOrNull(0) ?: -1
                        val normal = if (n0Idx in normals.indices) {
                            normals[n0Idx]
                        } else {
                            computeFaceNormal(v0, v1, v2)
                        }

                        triangles.add(Triangle3D(normal, v0, v1, v2))
                    }
                }
            }
        }

        if (minX == Float.MAX_VALUE) {
            minX = -10f; maxX = 10f
            minY = -10f; maxY = 10f
            minZ = -10f; maxZ = 10f
        }

        val bounds = BoundingBox3D(minX, maxX, minY, maxY, minZ, maxZ)

        // Compute surface area & volume
        var totalArea = 0f
        var totalVolume = 0.0
        for (tri in triangles) {
            val ax = tri.v2.x - tri.v1.x
            val ay = tri.v2.y - tri.v1.y
            val az = tri.v2.z - tri.v1.z

            val bx = tri.v3.x - tri.v1.x
            val by = tri.v3.y - tri.v1.y
            val bz = tri.v3.z - tri.v1.z

            val cx = ay * bz - az * by
            val cy = az * bx - ax * bz
            val cz = ax * by - ay * bx

            totalArea += 0.5f * sqrt(cx * cx + cy * cy + cz * cz)

            val v321 = tri.v3.x * tri.v2.y * tri.v1.z
            val v231 = tri.v2.x * tri.v3.y * tri.v1.z
            val v312 = tri.v3.x * tri.v1.y * tri.v2.z
            val v132 = tri.v1.x * tri.v3.y * tri.v2.z
            val v213 = tri.v2.x * tri.v1.y * tri.v3.z
            val v123 = tri.v1.x * tri.v2.y * tri.v3.z
            totalVolume += (1.0 / 6.0) * (-v321 + v231 + v312 - v132 - v213 + v123)
        }

        // Build pre-allocated native FloatBuffers for zero-lag rendering
        val triCount = triangles.size
        val vertexBuf = ByteBuffer.allocateDirect(triCount * 3 * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        val normalBuf = ByteBuffer.allocateDirect(triCount * 3 * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        for (tri in triangles) {
            vertexBuf.put(tri.v1.x); vertexBuf.put(tri.v1.y); vertexBuf.put(tri.v1.z)
            vertexBuf.put(tri.v2.x); vertexBuf.put(tri.v2.y); vertexBuf.put(tri.v2.z)
            vertexBuf.put(tri.v3.x); vertexBuf.put(tri.v3.y); vertexBuf.put(tri.v3.z)

            val n = tri.normal
            normalBuf.put(n.x); normalBuf.put(n.y); normalBuf.put(n.z)
            normalBuf.put(n.x); normalBuf.put(n.y); normalBuf.put(n.z)
            normalBuf.put(n.x); normalBuf.put(n.y); normalBuf.put(n.z)
        }
        vertexBuf.position(0)
        normalBuf.position(0)

        return StlModel(
            fileName = fileName,
            triangles = triangles,
            bounds = bounds,
            faceCount = triangles.size,
            surfaceAreaMm2 = totalArea,
            volumeMm3 = abs(totalVolume).toFloat(),
            vertexBuffer = vertexBuf,
            normalBuffer = normalBuf
        )
    }

    private fun computeFaceNormal(v1: Vector3D, v2: Vector3D, v3: Vector3D): Vector3D {
        val ax = v2.x - v1.x
        val ay = v2.y - v1.y
        val az = v2.z - v1.z

        val bx = v3.x - v1.x
        val by = v3.y - v1.y
        val bz = v3.z - v1.z

        var nx = ay * bz - az * by
        var ny = az * bx - ax * bz
        var nz = ax * by - ay * bx

        val len = sqrt(nx * nx + ny * ny + nz * nz)
        if (len > 0.000001f) {
            nx /= len
            ny /= len
            nz /= len
        } else {
            nz = 1f
        }
        return Vector3D(nx, ny, nz)
    }
}
