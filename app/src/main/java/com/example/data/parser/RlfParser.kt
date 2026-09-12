package com.example.data.parser

import com.example.ui.render3d.BoundingBox3D
import com.example.ui.render3d.Triangle3D
import com.example.ui.render3d.Vector3D
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
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

    fun parse(
        fileName: String,
        inputStream: InputStream,
        depthScale: Float = 1.0f,
        invertZ: Boolean = false
    ): RlfModel {
        val buffered = BufferedInputStream(inputStream, 262144)
        val allBytes = buffered.readBytes()

        if (allBytes.isEmpty()) {
            throw IllegalArgumentException("Relief file is empty")
        }

        // 1. Detect Dimensions & Header Offset from binary stream
        val detection = detectReliefFormat(allBytes)
        val rawGridW = detection.gridWidth
        val rawGridH = detection.gridHeight
        val dataOffset = detection.dataOffset
        val isFloat32 = detection.isFloat32
        val isShort16 = detection.isShort16
        val detectedPhysicalW = detection.physicalWidthMm
        val detectedPhysicalH = detection.physicalHeightMm

        // 2. Read raw height values from the byte stream
        val rawHeights = Array(rawGridW) { FloatArray(rawGridH) }
        val buffer = ByteBuffer.wrap(allBytes).order(ByteOrder.LITTLE_ENDIAN)

        val elementSize = when {
            isFloat32 -> 4
            isShort16 -> 2
            else -> 1
        }

        var minVal = Float.MAX_VALUE
        var maxVal = -Float.MAX_VALUE

        for (y in 0 until rawGridH) {
            for (x in 0 until rawGridW) {
                val idx = dataOffset + (y * rawGridW + x) * elementSize
                var zVal = 0f
                if (idx + elementSize <= allBytes.size) {
                    zVal = when {
                        isFloat32 -> {
                            val f = buffer.getFloat(idx)
                            if (f.isNaN() || f.isInfinite() || f < -100000f || f > 100000f) 0f else f
                        }
                        isShort16 -> {
                            val s = buffer.getShort(idx).toInt()
                            s.toFloat()
                        }
                        else -> {
                            (allBytes[idx].toInt() and 0xFF).toFloat()
                        }
                    }
                }
                rawHeights[x][y] = zVal
                if (zVal < minVal) minVal = zVal
                if (zVal > maxVal) maxVal = zVal
            }
        }

        if (minVal == Float.MAX_VALUE) minVal = 0f
        if (maxVal <= minVal) maxVal = minVal + 10f

        val valRange = (maxVal - minVal).coerceAtLeast(0.001f)
        val targetDepthMm = if (isFloat32 && maxVal in 0.1f..250f && minVal in -50f..50f) {
            valRange
        } else {
            // Standard ArtCAM 16-bit relief depth mapping
            12.0f
        } * depthScale

        // Normalize heights to [0, targetDepthMm] or preserve calibrated physical relief
        for (x in 0 until rawGridW) {
            for (y in 0 until rawGridH) {
                var normZ = ((rawHeights[x][y] - minVal) / valRange) * targetDepthMm
                if (invertZ) {
                    normZ = targetDepthMm - normZ
                }
                rawHeights[x][y] = normZ
            }
        }

        // 3. Grid decimation for interactive 60 FPS 3D rendering
        val maxDisplayPoints = 120
        val stepX = max(1, ceil(rawGridW.toFloat() / maxDisplayPoints).toInt())
        val stepY = max(1, ceil(rawGridH.toFloat() / maxDisplayPoints).toInt())

        val gridW = (rawGridW + stepX - 1) / stepX
        val gridH = (rawGridH + stepY - 1) / stepY

        val displayGrid = Array(gridW) { FloatArray(gridH) }
        for (gx in 0 until gridW) {
            val rx = min(rawGridW - 1, gx * stepX)
            for (gy in 0 until gridH) {
                val ry = min(rawGridH - 1, gy * stepY)
                displayGrid[gx][gy] = rawHeights[rx][ry]
            }
        }

        val sizeX = if (detectedPhysicalW > 0f) detectedPhysicalW else (rawGridW * 0.4f).coerceIn(40f, 600f)
        val sizeY = if (detectedPhysicalH > 0f) detectedPhysicalH else (rawGridH * 0.4f).coerceIn(40f, 600f)

        // 4. Generate Solid 3D Relief Mesh with Top Surface + 4 Box Walls + Base
        val triangles = ArrayList<Triangle3D>((gridW - 1) * (gridH - 1) * 2 + (gridW + gridH) * 4 + 4)
        val dx = sizeX / (gridW - 1).coerceAtLeast(1)
        val dy = sizeY / (gridH - 1).coerceAtLeast(1)
        val baseZ = 0f

        var bMinX = Float.MAX_VALUE; var bMaxX = -Float.MAX_VALUE
        var bMinY = Float.MAX_VALUE; var bMaxY = -Float.MAX_VALUE
        var bMinZ = Float.MAX_VALUE; var bMaxZ = -Float.MAX_VALUE

        fun addTri(p1: Vector3D, p2: Vector3D, p3: Vector3D) {
            val norm = computeNormal(p1, p2, p3)
            triangles.add(Triangle3D(p1, p2, p3, norm))
            listOf(p1, p2, p3).forEach { pt ->
                if (pt.x < bMinX) bMinX = pt.x; if (pt.x > bMaxX) bMaxX = pt.x
                if (pt.y < bMinY) bMinY = pt.y; if (pt.y > bMaxY) bMaxY = pt.y
                if (pt.z < bMinZ) bMinZ = pt.z; if (pt.z > bMaxZ) bMaxZ = pt.z
            }
        }

        // Top Relief Surface
        for (x in 0 until gridW - 1) {
            for (y in 0 until gridH - 1) {
                val p1 = Vector3D(x * dx, y * dy, displayGrid[x][y])
                val p2 = Vector3D((x + 1) * dx, y * dy, displayGrid[x + 1][y])
                val p3 = Vector3D((x + 1) * dx, (y + 1) * dy, displayGrid[x + 1][y + 1])
                val p4 = Vector3D(x * dx, (y + 1) * dy, displayGrid[x][y + 1])

                addTri(p1, p2, p3)
                addTri(p1, p3, p4)
            }
        }

        // 4 Side Vertical Walls (gives the relief true solid box geometry)
        // South wall (y = 0)
        for (x in 0 until gridW - 1) {
            val t1 = Vector3D(x * dx, 0f, displayGrid[x][0])
            val t2 = Vector3D((x + 1) * dx, 0f, displayGrid[x + 1][0])
            val b1 = Vector3D(x * dx, 0f, baseZ)
            val b2 = Vector3D((x + 1) * dx, 0f, baseZ)
            addTri(b1, t2, t1)
            addTri(b1, b2, t2)
        }
        // North wall (y = max)
        val maxYIdx = gridH - 1
        val maxYPos = maxYIdx * dy
        for (x in 0 until gridW - 1) {
            val t1 = Vector3D(x * dx, maxYPos, displayGrid[x][maxYIdx])
            val t2 = Vector3D((x + 1) * dx, maxYPos, displayGrid[x + 1][maxYIdx])
            val b1 = Vector3D(x * dx, maxYPos, baseZ)
            val b2 = Vector3D((x + 1) * dx, maxYPos, baseZ)
            addTri(t1, t2, b1)
            addTri(t2, b2, b1)
        }
        // West wall (x = 0)
        for (y in 0 until gridH - 1) {
            val t1 = Vector3D(0f, y * dy, displayGrid[0][y])
            val t2 = Vector3D(0f, (y + 1) * dy, displayGrid[0][y + 1])
            val b1 = Vector3D(0f, y * dy, baseZ)
            val b2 = Vector3D(0f, (y + 1) * dy, baseZ)
            addTri(t1, b2, b1)
            addTri(t1, t2, b2)
        }
        // East wall (x = max)
        val maxXIdx = gridW - 1
        val maxXPos = maxXIdx * dx
        for (y in 0 until gridH - 1) {
            val t1 = Vector3D(maxXPos, y * dy, displayGrid[maxXIdx][y])
            val t2 = Vector3D(maxXPos, (y + 1) * dy, displayGrid[maxXIdx][y + 1])
            val b1 = Vector3D(maxXPos, y * dy, baseZ)
            val b2 = Vector3D(maxXPos, (y + 1) * dy, baseZ)
            addTri(b1, b2, t1)
            addTri(b2, t2, t1)
        }

        // Bottom Plate
        val bot00 = Vector3D(0f, 0f, baseZ)
        val bot10 = Vector3D(sizeX, 0f, baseZ)
        val bot11 = Vector3D(sizeX, sizeY, baseZ)
        val bot01 = Vector3D(0f, sizeY, baseZ)
        addTri(bot00, bot11, bot10)
        addTri(bot00, bot01, bot11)

        if (bMinX > bMaxX) { bMinX = 0f; bMaxX = sizeX }
        if (bMinY > bMaxY) { bMinY = 0f; bMaxY = sizeY }
        if (bMinZ > bMaxZ) { bMinZ = 0f; bMaxZ = targetDepthMm }

        val stlModel = StlModel(
            fileName = fileName,
            triangles = triangles,
            bounds = BoundingBox3D(bMinX, bMaxX, bMinY, bMaxY, bMinZ, bMaxZ),
            faceCount = triangles.size,
            surfaceAreaMm2 = sizeX * sizeY * 2f + (sizeX + sizeY) * 2f * targetDepthMm,
            volumeMm3 = sizeX * sizeY * (targetDepthMm * 0.75f)
        )

        return RlfModel(
            fileName = fileName,
            widthMm = sizeX,
            heightMm = sizeY,
            maxReliefHeightMm = targetDepthMm,
            gridWidth = rawGridW,
            gridHeight = rawGridH,
            stlModel = stlModel
        )
    }

    private data class FormatDetection(
        val gridWidth: Int,
        val gridHeight: Int,
        val dataOffset: Int,
        val isFloat32: Boolean,
        val isShort16: Boolean,
        val physicalWidthMm: Float,
        val physicalHeightMm: Float
    )

    private fun detectReliefFormat(bytes: ByteArray): FormatDetection {
        val totalSize = bytes.size
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        var bestW = 0
        var bestH = 0
        var bestOffset = 0
        var isFloat = false
        var isShort = true
        var physW = 0f
        var physH = 0f

        // Search for explicit dimension headers in the first 2048 bytes
        val scanLimit = min(bytes.size - 8, 2048)
        for (i in 0 until scanLimit step 2) {
            if (i + 8 <= bytes.size) {
                val w = buffer.getInt(i)
                val h = buffer.getInt(i + 4)
                if (w in 20..4096 && h in 20..4096) {
                    val floatPayload = w.toLong() * h.toLong() * 4L
                    val shortPayload = w.toLong() * h.toLong() * 2L
                    val remainingBytes = totalSize - (i + 8)

                    if (floatPayload in (remainingBytes - 4096)..(remainingBytes + 4096) && floatPayload > 0) {
                        bestW = w; bestH = h; bestOffset = (totalSize - floatPayload.toInt()).coerceAtLeast(i + 8)
                        isFloat = true; isShort = false
                        break
                    } else if (shortPayload in (remainingBytes - 4096)..(remainingBytes + 4096) && shortPayload > 0) {
                        bestW = w; bestH = h; bestOffset = (totalSize - shortPayload.toInt()).coerceAtLeast(i + 8)
                        isFloat = false; isShort = true
                        break
                    }
                }
            }

            if (i + 4 <= bytes.size && bestW == 0) {
                val w = buffer.getShort(i).toInt() and 0xFFFF
                val h = buffer.getShort(i + 2).toInt() and 0xFFFF
                if (w in 20..4096 && h in 20..4096) {
                    val floatPayload = w.toLong() * h.toLong() * 4L
                    val shortPayload = w.toLong() * h.toLong() * 2L
                    val remainingBytes = totalSize - (i + 4)

                    if (floatPayload in (remainingBytes - 4096)..(remainingBytes + 4096) && floatPayload > 0) {
                        bestW = w; bestH = h; bestOffset = (totalSize - floatPayload.toInt()).coerceAtLeast(i + 4)
                        isFloat = true; isShort = false
                        break
                    } else if (shortPayload in (remainingBytes - 4096)..(remainingBytes + 4096) && shortPayload > 0) {
                        bestW = w; bestH = h; bestOffset = (totalSize - shortPayload.toInt()).coerceAtLeast(i + 4)
                        isFloat = false; isShort = true
                        break
                    }
                }
            }
        }

        // Try reading physical dimensions
        if (bestOffset > 0 && bestOffset <= 512) {
            for (p in 0 until min(bestOffset, 128) step 4) {
                val f1 = buffer.getFloat(p)
                if (f1 in 10f..2500f && p + 8 <= bestOffset) {
                    val f2 = buffer.getFloat(p + 4)
                    if (f2 in 10f..2500f) {
                        physW = f1
                        physH = f2
                        break
                    }
                }
            }
        }

        // Deduce grid dimensions if no explicit match
        if (bestW == 0 || bestH == 0) {
            // Check common ArtCAM header sizes (64 or 0 bytes)
            val payload = (totalSize - 64).coerceAtLeast(totalSize)
            val shortCount = payload / 2
            val side = sqrt(shortCount.toDouble()).toInt().coerceAtLeast(20)
            bestW = side
            bestH = side
            bestOffset = (totalSize - (side * side * 2)).coerceAtLeast(0)
            isShort = true
            isFloat = false
        }

        return FormatDetection(
            gridWidth = bestW.coerceIn(10, 4000),
            gridHeight = bestH.coerceIn(10, 4000),
            dataOffset = bestOffset.coerceIn(0, totalSize - 1),
            isFloat32 = isFloat,
            isShort16 = isShort,
            physicalWidthMm = physW,
            physicalHeightMm = physH
        )
    }

    private fun computeNormal(p1: Vector3D, p2: Vector3D, p3: Vector3D): Vector3D {
        val u = p2 - p1
        val v = p3 - p1
        val nx = u.y * v.z - u.z * v.y
        val ny = u.z * v.x - u.x * v.z
        val nz = u.x * v.y - u.y * v.x
        val len = sqrt(nx * nx + ny * ny + nz * nz)
        return if (len > 0.00001f) Vector3D(nx / len, ny / len, nz / len) else Vector3D(0f, 0f, 1f)
    }
}
