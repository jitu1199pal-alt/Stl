package com.example.data.parser

import com.example.ui.render3d.BoundingBox3D
import com.example.ui.render3d.Triangle3D
import com.example.ui.render3d.Vector3D
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream
import kotlin.math.abs
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
    val rawHeights: Array<FloatArray>,
    val stlModel: StlModel,
    val depthScale: Float = 1.0f,
    val invertZ: Boolean = false,
    val flipY: Boolean = false,
    val flipX: Boolean = false,
    val transpose: Boolean = false,
    val gridResolution: Int = 220,
    val showBaseBlock: Boolean = true,
    val formatDescription: String = "16-bit ArtCAM Grayscale Heightmap"
) {
    val isInverted: Boolean get() = invertZ
    val isFlippedY: Boolean get() = flipY
    val isFlippedX: Boolean get() = flipX
    val isTransposed: Boolean get() = transpose
}

/**
 * Universal Industrial Parser for ArtCAM, Delcam, Carveco, and generic 2.5D CNC Relief files (.rlf, .art).
 * Supports:
 * - Binary 16-bit signed/unsigned short heightmaps (ArtCAM classic & standard)
 * - 32-bit IEEE 754 float heightmaps (ArtCAM calibrated/metric)
 * - 8-bit grayscale relief maps
 * - Zlib-compressed relief data streams
 * - BMP-embedded relief containers
 * - Text/ASCII relief header specifications ("RELIEF FILE ... pixels wide")
 * - Smart autocorrelation pitch detection to recover exact row width for unknown/headerless files
 * - Bilinear downsampling for artifact-free high-detail 3D mesh reconstruction
 */
object RlfParser {

    fun parse(
        fileName: String,
        inputStream: InputStream,
        depthScale: Float = 1.0f,
        invertZ: Boolean = false,
        flipY: Boolean = false,
        flipX: Boolean = false,
        transpose: Boolean = false,
        gridResolution: Int = 220
    ): RlfModel {
        val buffered = BufferedInputStream(inputStream, 262144)
        val initialBytes = buffered.readBytes()

        if (initialBytes.isEmpty()) {
            throw IllegalArgumentException("Relief file is empty")
        }

        // 1. Check for Zlib compressed stream in payload
        val allBytes = tryDecompressZlib(initialBytes)

        // 2. Multi-strategy Format & Dimension Detection
        val detection = detectReliefFormat(allBytes)
        val rawGridW = detection.gridWidth
        val rawGridH = detection.gridHeight
        val dataOffset = detection.dataOffset
        val isFloat32 = detection.isFloat32
        val isShort16 = detection.isShort16
        val isUnsigned16 = detection.isUnsigned16
        val isBigEndian = detection.isBigEndian
        val detectedPhysW = detection.physicalWidthMm
        val detectedPhysH = detection.physicalHeightMm

        // 3. Extract Raw 2D Heightmap Grid
        val byteBuffer = ByteBuffer.wrap(allBytes).order(
            if (isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        )
        val elementSize = when {
            isFloat32 -> 4
            isShort16 -> 2
            else -> 1
        }

        val rawHeights = Array(rawGridW) { FloatArray(rawGridH) }
        val sampleValues = ArrayList<Float>(min(rawGridW * rawGridH, 10000))

        for (y in 0 until rawGridH) {
            for (x in 0 until rawGridW) {
                val idx = dataOffset + (y * rawGridW + x) * elementSize
                var zVal = 0f
                if (idx + elementSize <= allBytes.size) {
                    zVal = when {
                        isFloat32 -> {
                            val f = byteBuffer.getFloat(idx)
                            if (f.isNaN() || f.isInfinite() || f < -100000f || f > 100000f) 0f else f
                        }
                        isShort16 -> {
                            if (isUnsigned16) {
                                val b1 = allBytes[idx].toInt() and 0xFF
                                val b2 = allBytes[idx + 1].toInt() and 0xFF
                                if (isBigEndian) ((b1 shl 8) or b2).toFloat()
                                else ((b2 shl 8) or b1).toFloat()
                            } else {
                                byteBuffer.getShort(idx).toFloat()
                            }
                        }
                        else -> {
                            (allBytes[idx].toInt() and 0xFF).toFloat()
                        }
                    }
                }
                rawHeights[x][y] = zVal
                if ((x + y) % 17 == 0 && sampleValues.size < 10000) {
                    sampleValues.add(zVal)
                }
            }
        }

        // 4. Determine Dynamic Range & Filter Background Outliers
        sampleValues.sort()
        val p1 = if (sampleValues.isNotEmpty()) sampleValues[(sampleValues.size * 0.01f).toInt()] else 0f
        val p99 = if (sampleValues.isNotEmpty()) sampleValues[(sampleValues.size * 0.99f).toInt()] else 10f
        var minVal = if (p99 - p1 > 0.001f) p1 else (sampleValues.firstOrNull() ?: 0f)
        var maxVal = if (p99 - p1 > 0.001f) p99 else (sampleValues.lastOrNull() ?: 10f)

        if (maxVal <= minVal) maxVal = minVal + 10f
        val valRange = (maxVal - minVal).coerceAtLeast(0.001f)

        // Standard CNC carving physical depth
        val baseDepthMm = if (isFloat32 && (maxVal - minVal) in 0.5f..150f) {
            maxVal - minVal
        } else {
            14.0f // Standard ArtCAM relief carving target depth (14 mm)
        }
        val targetDepthMm = baseDepthMm * depthScale

        // Normalize raw heights into calibrated millimeters [0, targetDepthMm]
        for (x in 0 until rawGridW) {
            for (y in 0 until rawGridH) {
                val clamped = rawHeights[x][y].coerceIn(minVal, maxVal)
                rawHeights[x][y] = ((clamped - minVal) / valRange) * targetDepthMm
            }
        }

        // Physical dimensions
        val sizeX = if (detectedPhysW > 0f) detectedPhysW else (rawGridW * 0.35f).coerceIn(40f, 600f)
        val sizeY = if (detectedPhysH > 0f) detectedPhysH else (rawGridH * 0.35f).coerceIn(40f, 600f)

        // 5. Build Initial 3D Mesh
        val stl = buildMesh(
            fileName = fileName,
            rawHeights = rawHeights,
            rawW = rawGridW,
            rawH = rawGridH,
            sizeX = sizeX,
            sizeY = sizeY,
            targetDepthMm = targetDepthMm,
            depthScale = depthScale,
            invertZ = invertZ,
            flipY = flipY,
            flipX = flipX,
            transpose = transpose,
            targetResolution = gridResolution
        )

        return RlfModel(
            fileName = fileName,
            widthMm = sizeX,
            heightMm = sizeY,
            maxReliefHeightMm = targetDepthMm,
            gridWidth = rawGridW,
            gridHeight = rawGridH,
            rawHeights = rawHeights,
            stlModel = stl,
            depthScale = depthScale,
            invertZ = invertZ,
            flipY = flipY,
            flipX = flipX,
            transpose = transpose,
            gridResolution = gridResolution
        )
    }

    fun rebuildModel(
        model: RlfModel,
        depthScale: Float = model.depthScale,
        invertZ: Boolean = model.invertZ,
        flipY: Boolean = model.flipY,
        flipX: Boolean = model.flipX,
        transpose: Boolean = model.transpose,
        gridResolution: Int = model.gridResolution,
        isInverted: Boolean = invertZ,
        isFlippedY: Boolean = flipY,
        isFlippedX: Boolean = flipX,
        isTransposed: Boolean = transpose,
        showBaseBlock: Boolean = model.showBaseBlock
    ): RlfModel {
        val actualInvertZ = if (isInverted != model.invertZ) isInverted else invertZ
        val actualFlipY = if (isFlippedY != model.flipY) isFlippedY else flipY
        val actualFlipX = if (isFlippedX != model.flipX) isFlippedX else flipX
        val actualTranspose = if (isTransposed != model.transpose) isTransposed else transpose

        val stl = buildMesh(
            fileName = model.fileName,
            rawHeights = model.rawHeights,
            rawW = model.gridWidth,
            rawH = model.gridHeight,
            sizeX = model.widthMm,
            sizeY = model.heightMm,
            targetDepthMm = model.maxReliefHeightMm,
            depthScale = depthScale,
            invertZ = actualInvertZ,
            flipY = actualFlipY,
            flipX = actualFlipX,
            transpose = actualTranspose,
            targetResolution = gridResolution
        )
        return model.copy(
            stlModel = stl,
            depthScale = depthScale,
            invertZ = actualInvertZ,
            flipY = actualFlipY,
            flipX = actualFlipX,
            transpose = actualTranspose,
            gridResolution = gridResolution,
            showBaseBlock = showBaseBlock
        )
    }

    /**
     * Fast in-memory mesh builder for instant user adjustments (Flip, Invert Z, Depth Scale, Quality).
     */
    fun buildMesh(
        fileName: String,
        rawHeights: Array<FloatArray>,
        rawW: Int,
        rawH: Int,
        sizeX: Float,
        sizeY: Float,
        targetDepthMm: Float,
        depthScale: Float = 1.0f,
        invertZ: Boolean = false,
        flipY: Boolean = false,
        flipX: Boolean = false,
        transpose: Boolean = false,
        targetResolution: Int = 220
    ): StlModel {
        val effectiveW = if (transpose) rawH else rawW
        val effectiveH = if (transpose) rawW else rawH
        val effectiveSizeX = if (transpose) sizeY else sizeX
        val effectiveSizeY = if (transpose) sizeX else sizeY

        val stepX = max(1, ceil(effectiveW.toFloat() / targetResolution).toInt())
        val stepY = max(1, ceil(effectiveH.toFloat() / targetResolution).toInt())

        val gridW = (effectiveW + stepX - 1) / stepX
        val gridH = (effectiveH + stepY - 1) / stepY

        val displayGrid = Array(gridW) { FloatArray(gridH) }

        // Bilinear / Area-averaged downsampling to retain fine carvings, text & floral outlines
        for (gx in 0 until gridW) {
            val startX = gx * stepX
            val endX = min(effectiveW, startX + stepX)

            for (gy in 0 until gridH) {
                val startY = gy * stepY
                val endY = min(effectiveH, startY + stepY)

                var sumZ = 0f
                var count = 0
                for (ex in startX until endX) {
                    for (ey in startY until endY) {
                        // Apply orientations: transpose, flipX, flipY
                        val srcX = if (flipX) (effectiveW - 1 - ex) else ex
                        val srcY = if (flipY) (effectiveH - 1 - ey) else ey

                        val actualX = if (transpose) srcY else srcX
                        val actualY = if (transpose) srcX else srcY

                        if (actualX in 0 until rawW && actualY in 0 until rawH) {
                            sumZ += rawHeights[actualX][actualY]
                            count++
                        }
                    }
                }

                var finalZ = if (count > 0) (sumZ / count) * depthScale else 0f
                if (invertZ) {
                    finalZ = (targetDepthMm * depthScale) - finalZ
                }
                displayGrid[gx][gy] = finalZ
            }
        }

        // Generate Watertight Solid 3D Relief Mesh (Surface + 4 Border Skirts + Base Floor)
        val triangles = ArrayList<Triangle3D>((gridW - 1) * (gridH - 1) * 2 + (gridW + gridH) * 4 + 4)
        val dx = effectiveSizeX / (gridW - 1).coerceAtLeast(1)
        val dy = effectiveSizeY / (gridH - 1).coerceAtLeast(1)
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

        // 1. Top Relief Sculpted Surface
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

        // 2. Four Solid Box Skirt Walls
        // South wall (y = 0)
        for (x in 0 until gridW - 1) {
            val t1 = Vector3D(x * dx, 0f, displayGrid[x][0])
            val t2 = Vector3D((x + 1) * dx, 0f, displayGrid[x + 1][0])
            val b1 = Vector3D(x * dx, 0f, baseZ)
            val b2 = Vector3D((x + 1) * dx, 0f, baseZ)
            addTri(b1, t2, t1)
            addTri(b1, b2, t2)
        }
        // North wall (y = maxY)
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
        // East wall (x = maxX)
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

        // 3. Flat Bottom Base Plate
        val bot00 = Vector3D(0f, 0f, baseZ)
        val bot10 = Vector3D(effectiveSizeX, 0f, baseZ)
        val bot11 = Vector3D(effectiveSizeX, effectiveSizeY, baseZ)
        val bot01 = Vector3D(0f, effectiveSizeY, baseZ)
        addTri(bot00, bot11, bot10)
        addTri(bot00, bot01, bot11)

        if (bMinX > bMaxX) { bMinX = 0f; bMaxX = effectiveSizeX }
        if (bMinY > bMaxY) { bMinY = 0f; bMaxY = effectiveSizeY }
        if (bMinZ > bMaxZ) { bMinZ = 0f; bMaxZ = targetDepthMm * depthScale }

        return StlModel(
            fileName = fileName,
            triangles = triangles,
            bounds = BoundingBox3D(bMinX, bMaxX, bMinY, bMaxY, bMinZ, bMaxZ),
            faceCount = triangles.size,
            surfaceAreaMm2 = effectiveSizeX * effectiveSizeY * 2f + (effectiveSizeX + effectiveSizeY) * 2f * (targetDepthMm * depthScale),
            volumeMm3 = effectiveSizeX * effectiveSizeY * (targetDepthMm * depthScale * 0.75f)
        )
    }

    private data class FormatDetection(
        val gridWidth: Int,
        val gridHeight: Int,
        val dataOffset: Int,
        val isFloat32: Boolean,
        val isShort16: Boolean,
        val isUnsigned16: Boolean,
        val isBigEndian: Boolean,
        val physicalWidthMm: Float,
        val physicalHeightMm: Float
    )

    private fun detectReliefFormat(bytes: ByteArray): FormatDetection {
        val totalSize = bytes.size

        // Strategy A: Check BMP Container ('BM')
        if (bytes.size > 54 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) {
            val bmpBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val offset = bmpBuf.getInt(10).coerceIn(54, totalSize - 1)
            val w = abs(bmpBuf.getInt(18))
            val h = abs(bmpBuf.getInt(22))
            val bpp = bmpBuf.getShort(28).toInt() and 0xFFFF
            if (w in 16..8192 && h in 16..8192) {
                return FormatDetection(
                    gridWidth = w,
                    gridHeight = h,
                    dataOffset = offset,
                    isFloat32 = false,
                    isShort16 = bpp >= 16,
                    isUnsigned16 = true,
                    isBigEndian = false,
                    physicalWidthMm = w * 0.35f,
                    physicalHeightMm = h * 0.35f
                )
            }
        }

        // Strategy B: Check Text/ASCII Header ("RELIEF FILE", "pixels wide")
        val textHeader = detectTextHeader(bytes)
        if (textHeader != null) {
            return textHeader
        }

        // Strategy C: Comprehensive Binary Header Search (Little & Big Endian)
        val binaryHeader = detectBinaryHeader(bytes)
        if (binaryHeader != null) {
            return binaryHeader
        }

        // Strategy D: Autocorrelation Pitch & Stride Detection (Peak Vertical Coherence)
        val autoDetected = detectViaAutocorrelation(bytes)
        return autoDetected
    }

    private fun detectTextHeader(bytes: ByteArray): FormatDetection? {
        val limit = min(bytes.size, 4096)
        val text = String(bytes, 0, limit, Charsets.US_ASCII)

        if (text.contains("RELIEF", ignoreCase = true) || text.contains("ArtCAM", ignoreCase = true) || text.contains("pixels", ignoreCase = true)) {
            val widthMatch = Regex("(\\d+)\\s*(?:pixels\\s*wide|wide|cols|columns|x)", RegexOption.IGNORE_CASE).find(text)
            val heightMatch = Regex("(\\d+)\\s*(?:pixels\\s*high|high|rows|lines|y)", RegexOption.IGNORE_CASE).find(text)

            val w = widthMatch?.groupValues?.get(1)?.toIntOrNull()
            val h = heightMatch?.groupValues?.get(1)?.toIntOrNull()

            if (w != null && h != null && w in 16..8192 && h in 16..8192) {
                // Find end of text header (double newline or binary transition)
                var headerEnd = text.indexOf("\n\n")
                if (headerEnd == -1) headerEnd = text.indexOf("\r\n\r\n")
                val offset = if (headerEnd != -1) headerEnd + 2 else 256

                return FormatDetection(
                    gridWidth = w,
                    gridHeight = h,
                    dataOffset = min(offset, bytes.size - 1),
                    isFloat32 = false,
                    isShort16 = true,
                    isUnsigned16 = true,
                    isBigEndian = false,
                    physicalWidthMm = w * 0.35f,
                    physicalHeightMm = h * 0.35f
                )
            }
        }
        return null
    }

    private fun detectBinaryHeader(bytes: ByteArray): FormatDetection? {
        val totalSize = bytes.size
        for (isBig in listOf(false, true)) {
            val order = if (isBig) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
            val buffer = ByteBuffer.wrap(bytes).order(order)

            val scanLimit = min(bytes.size - 16, 4096)
            for (i in 0 until scanLimit step 2) {
                val w32 = buffer.getInt(i)
                val h32 = buffer.getInt(i + 4)

                if (w32 in 20..8192 && h32 in 20..8192) {
                    val floatPayload = w32.toLong() * h32.toLong() * 4L
                    val shortPayload = w32.toLong() * h32.toLong() * 2L
                    val bytePayload = w32.toLong() * h32.toLong()

                    // Check if short (16-bit) or float (32-bit) payload fits within remaining bytes
                    if (shortPayload in 1000..totalSize.toLong()) {
                        // Candidate offset directly follows header
                        val offset = findOptimalDataOffset(bytes, i + 8, w32, h32, 2)
                        return FormatDetection(
                            gridWidth = w32,
                            gridHeight = h32,
                            dataOffset = offset,
                            isFloat32 = false,
                            isShort16 = true,
                            isUnsigned16 = true,
                            isBigEndian = isBig,
                            physicalWidthMm = w32 * 0.35f,
                            physicalHeightMm = h32 * 0.35f
                        )
                    } else if (floatPayload in 1000..totalSize.toLong()) {
                        val offset = findOptimalDataOffset(bytes, i + 8, w32, h32, 4)
                        return FormatDetection(
                            gridWidth = w32,
                            gridHeight = h32,
                            dataOffset = offset,
                            isFloat32 = true,
                            isShort16 = false,
                            isUnsigned16 = false,
                            isBigEndian = isBig,
                            physicalWidthMm = w32 * 0.35f,
                            physicalHeightMm = h32 * 0.35f
                        )
                    } else if (bytePayload in 1000..totalSize.toLong()) {
                        val offset = findOptimalDataOffset(bytes, i + 8, w32, h32, 1)
                        return FormatDetection(
                            gridWidth = w32,
                            gridHeight = h32,
                            dataOffset = offset,
                            isFloat32 = false,
                            isShort16 = false,
                            isUnsigned16 = true,
                            isBigEndian = isBig,
                            physicalWidthMm = w32 * 0.35f,
                            physicalHeightMm = h32 * 0.35f
                        )
                    }
                }

                // Also check 16-bit short headers (e.g. Delcam older versions)
                val w16 = buffer.getShort(i).toInt() and 0xFFFF
                val h16 = buffer.getShort(i + 2).toInt() and 0xFFFF
                if (w16 in 30..4096 && h16 in 30..4096) {
                    val shortPayload = w16.toLong() * h16.toLong() * 2L
                    if (shortPayload in 1000..totalSize.toLong()) {
                        val offset = findOptimalDataOffset(bytes, i + 4, w16, h16, 2)
                        return FormatDetection(
                            gridWidth = w16,
                            gridHeight = h16,
                            dataOffset = offset,
                            isFloat32 = false,
                            isShort16 = true,
                            isUnsigned16 = true,
                            isBigEndian = isBig,
                            physicalWidthMm = w16 * 0.35f,
                            physicalHeightMm = h16 * 0.35f
                        )
                    }
                }
            }
        }
        return null
    }

    /**
     * Determines optimal data start offset (aligned to 64, 128, 256, 512 or tail aligned).
     */
    private fun findOptimalDataOffset(bytes: ByteArray, minOffset: Int, w: Int, h: Int, elemSize: Int): Int {
        val totalSize = bytes.size
        val payload = w * h * elemSize
        val tailOffset = totalSize - payload
        if (tailOffset in minOffset..minOffset + 2048) {
            return tailOffset
        }
        // Common CAD aligned offsets
        for (align in listOf(64, 128, 256, 512, 1024)) {
            if (align >= minOffset && align + payload <= totalSize) {
                return align
            }
        }
        return minOffset.coerceIn(0, totalSize - 1)
    }

    /**
     * Autocorrelation Pitch & Stride Detection:
     * If no header gave dimensions, relief surfaces have high vertical correlation (smooth gradient)
     * only when tested stride == actual width W.
     */
    private fun detectViaAutocorrelation(bytes: ByteArray): FormatDetection {
        val totalSize = bytes.size
        val offset = if (totalSize > 512) 64 else 0
        val payload = totalSize - offset
        val shortCount = payload / 2

        if (shortCount < 400) {
            val side = sqrt(shortCount.toDouble()).toInt().coerceAtLeast(10)
            return FormatDetection(side, side, offset, false, true, true, false, side * 0.35f, side * 0.35f)
        }

        // Test candidate aspect ratios and standard CNC width steps
        val candidateWidths = LinkedHashSet<Int>()
        val baseSide = sqrt(shortCount.toDouble()).toInt()
        candidateWidths.add(baseSide) // 1:1

        // Standard 4:3, 16:9, 3:2, 2:1, 3:1, 1:2, 2:3, 3:4 aspect ratios
        val aspects = listOf(
            4.0 / 3.0, 3.0 / 2.0, 16.0 / 9.0, 2.0 / 1.0, 5.0 / 2.0, 3.0 / 1.0,
            3.0 / 4.0, 2.0 / 3.0, 9.0 / 16.0, 1.0 / 2.0
        )
        for (asp in aspects) {
            val w = sqrt(shortCount.toDouble() * asp).toInt()
            if (w in 30..4000) candidateWidths.add(w)
        }

        // Standard ArtCAM presets
        for (preset in listOf(128, 200, 256, 300, 360, 400, 500, 512, 600, 640, 720, 800, 1000, 1024, 1200, 1280, 1440, 1600, 1920, 2000, 2048)) {
            if (preset < shortCount / 20) candidateWidths.add(preset)
        }

        // Evaluate vertical smoothness (sum of absolute differences between adjacent rows)
        var bestWidth = baseSide
        var minDiff = Double.MAX_VALUE

        val samplePoints = 1200
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        for (candW in candidateWidths) {
            val candH = shortCount / candW
            if (candH < 20 || candW < 20) continue

            var diffSum = 0.0
            val step = max(1, (shortCount - candW - 1) / samplePoints)

            for (p in 0 until samplePoints) {
                val idx1 = offset + (p * step) * 2
                val idx2 = offset + (p * step + candW) * 2
                if (idx2 + 2 <= totalSize) {
                    val v1 = (bytes[idx1].toInt() and 0xFF) or ((bytes[idx1 + 1].toInt() and 0xFF) shl 8)
                    val v2 = (bytes[idx2].toInt() and 0xFF) or ((bytes[idx2 + 1].toInt() and 0xFF) shl 8)
                    diffSum += abs(v1 - v2)
                }
            }

            if (diffSum < minDiff) {
                minDiff = diffSum
                bestWidth = candW
            }
        }

        val finalH = (shortCount / bestWidth).coerceIn(10, 4000)
        return FormatDetection(
            gridWidth = bestWidth,
            gridHeight = finalH,
            dataOffset = offset,
            isFloat32 = false,
            isShort16 = true,
            isUnsigned16 = true,
            isBigEndian = false,
            physicalWidthMm = bestWidth * 0.35f,
            physicalHeightMm = finalH * 0.35f
        )
    }

    private fun tryDecompressZlib(bytes: ByteArray): ByteArray {
        // Look for zlib magic 0x78 0x9C, 0x78 0x01, or 0x78 0xDA in the first 256 bytes
        for (i in 0 until min(bytes.size - 2, 256)) {
            if (bytes[i] == 0x78.toByte() &&
                (bytes[i + 1] == 0x9C.toByte() || bytes[i + 1] == 0x01.toByte() || bytes[i + 1] == 0xDA.toByte())
            ) {
                try {
                    val inflater = InflaterInputStream(ByteArrayInputStream(bytes, i, bytes.size - i))
                    val out = ByteArrayOutputStream(bytes.size * 2)
                    val buffer = ByteArray(32768)
                    var read = inflater.read(buffer)
                    while (read > 0) {
                        out.write(buffer, 0, read)
                        read = inflater.read(buffer)
                    }
                    val decompressed = out.toByteArray()
                    if (decompressed.size > bytes.size) {
                        return decompressed
                    }
                } catch (_: Exception) {}
            }
        }
        return bytes
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
