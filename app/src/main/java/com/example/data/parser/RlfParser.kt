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

data class ActiveBounds(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int
) {
    val width: Int get() = (maxX - minX + 1).coerceAtLeast(1)
    val height: Int get() = (maxY - minY + 1).coerceAtLeast(1)
}

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
    val formatDescription: String = "16-bit ArtCAM Grayscale Heightmap",
    val originalWidth: Int = gridWidth,
    val originalHeight: Int = gridHeight,
    val customStrideDelta: Int = 0,
    val raw1DValues: FloatArray? = null,
    val isSigned16: Boolean = true,
    val cropToRelief: Boolean = false,
    val activeBounds: ActiveBounds? = null,
    val rawBytes: ByteArray? = null
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

        return extractHeightGrid(
            allBytes = allBytes,
            detection = detection,
            fileName = fileName,
            depthScale = depthScale,
            invertZ = invertZ,
            flipY = flipY,
            flipX = flipX,
            transpose = transpose,
            gridResolution = gridResolution,
            customStrideDelta = 0,
            forceSigned16 = true,
            cropToRelief = false
        )
    }

    private fun extractHeightGrid(
        allBytes: ByteArray,
        detection: FormatDetection,
        fileName: String,
        depthScale: Float = 1.0f,
        invertZ: Boolean = false,
        flipY: Boolean = false,
        flipX: Boolean = false,
        transpose: Boolean = false,
        gridResolution: Int = 220,
        customStrideDelta: Int = 0,
        forceSigned16: Boolean = true,
        cropToRelief: Boolean = false
    ): RlfModel {
        val rawGridW = (detection.gridWidth + customStrideDelta).coerceIn(20, 16384)
        val rawGridH = detection.gridHeight
        val dataOffset = detection.dataOffset
        val isFloat32 = detection.isFloat32
        val isShort16 = detection.isShort16
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
        val sampleValues = ArrayList<Float>(min(rawGridW * rawGridH, 20000))
        val isMasked = Array(rawGridW) { BooleanArray(rawGridH) }

        for (y in 0 until rawGridH) {
            for (x in 0 until rawGridW) {
                val idx = dataOffset + (y * rawGridW + x) * elementSize
                var zVal = 0f
                var masked = false

                if (idx + elementSize <= allBytes.size) {
                    when {
                        isFloat32 -> {
                            val f = byteBuffer.getFloat(idx)
                            if (f.isNaN() || f.isInfinite() || f < -100000f || f > 100000f) {
                                zVal = 0f
                                masked = true
                            } else {
                                zVal = f
                            }
                        }
                        isShort16 -> {
                            if (forceSigned16) {
                                val sVal = byteBuffer.getShort(idx).toFloat()
                                // ArtCAM transparent mask / background sentinel (0x8000 = -32768 or values < -30000)
                                if (sVal <= -30000f || sVal == -32768f) {
                                    zVal = 0f
                                    masked = true
                                } else {
                                    zVal = sVal
                                }
                            } else {
                                val uVal = (byteBuffer.getShort(idx).toInt() and 0xFFFF).toFloat()
                                if (uVal >= 65530f) {
                                    zVal = 0f
                                    masked = true
                                } else {
                                    zVal = uVal
                                }
                            }
                        }
                        else -> {
                            zVal = (allBytes[idx].toInt() and 0xFF).toFloat()
                        }
                    }
                }
                rawHeights[x][y] = zVal
                isMasked[x][y] = masked
                if (!masked && (x + y) % 17 == 0 && sampleValues.size < 20000) {
                    sampleValues.add(zVal)
                }
            }
        }

        // 4. Determine Dynamic Range & Filter Background Outliers
        val sortedSamples = sampleValues.sorted()
        val p1 = if (sortedSamples.isNotEmpty()) sortedSamples[(sortedSamples.size * 0.01f).toInt()] else 0f
        val p99 = if (sortedSamples.isNotEmpty()) sortedSamples[(sortedSamples.size * 0.99f).toInt()] else 10f
        var minVal = p1
        var maxVal = p99

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
                if (isMasked[x][y]) {
                    rawHeights[x][y] = 0f
                } else {
                    val clamped = rawHeights[x][y].coerceIn(minVal, maxVal)
                    rawHeights[x][y] = ((clamped - minVal) / valRange) * targetDepthMm
                }
            }
        }

        // Detect active carving area (ignoring flat empty canvas margins)
        var minActiveX = rawGridW - 1
        var maxActiveX = 0
        var minActiveY = rawGridH - 1
        var maxActiveY = 0
        val carvingThreshold = targetDepthMm * 0.02f

        for (y in 0 until rawGridH) {
            for (x in 0 until rawGridW) {
                if (rawHeights[x][y] > carvingThreshold && !isMasked[x][y]) {
                    if (x < minActiveX) minActiveX = x
                    if (x > maxActiveX) maxActiveX = x
                    if (y < minActiveY) minActiveY = y
                    if (y > maxActiveY) maxActiveY = y
                }
            }
        }

        val hasActiveRelief = (maxActiveX > minActiveX) && (maxActiveY > minActiveY)
        val padX = if (hasActiveRelief) ((maxActiveX - minActiveX) * 0.03f).toInt().coerceAtLeast(2) else 0
        val padY = if (hasActiveRelief) ((maxActiveY - minActiveY) * 0.03f).toInt().coerceAtLeast(2) else 0
        val cropMinX = if (hasActiveRelief) (minActiveX - padX).coerceIn(0, rawGridW - 1) else 0
        val cropMaxX = if (hasActiveRelief) (maxActiveX + padX).coerceIn(0, rawGridW - 1) else (rawGridW - 1)
        val cropMinY = if (hasActiveRelief) (minActiveY - padY).coerceIn(0, rawGridH - 1) else 0
        val cropMaxY = if (hasActiveRelief) (maxActiveY + padY).coerceIn(0, rawGridH - 1) else (rawGridH - 1)

        val activeBounds = ActiveBounds(cropMinX, cropMaxX, cropMinY, cropMaxY)

        // Select mesh grid (Full canvas vs Focused on active deity carving)
        val meshHeights: Array<FloatArray>
        val meshW: Int
        val meshH: Int
        val meshSizeX: Float
        val meshSizeY: Float

        if (cropToRelief && hasActiveRelief && (activeBounds.width < rawGridW * 0.95f || activeBounds.height < rawGridH * 0.95f)) {
            meshW = activeBounds.width
            meshH = activeBounds.height
            meshHeights = Array(meshW) { FloatArray(meshH) }
            for (y in 0 until meshH) {
                for (x in 0 until meshW) {
                    meshHeights[x][y] = rawHeights[cropMinX + x][cropMinY + y]
                }
            }
            meshSizeX = (meshW * 0.35f).coerceIn(40f, 600f)
            meshSizeY = (meshH * 0.35f).coerceIn(40f, 600f)
        } else {
            meshW = rawGridW
            meshH = rawGridH
            meshHeights = rawHeights
            meshSizeX = if (detectedPhysW > 0f) detectedPhysW else (rawGridW * 0.35f).coerceIn(40f, 600f)
            meshSizeY = if (detectedPhysH > 0f) detectedPhysH else (rawGridH * 0.35f).coerceIn(40f, 600f)
        }

        // 5. Build 3D Mesh
        val stl = buildMesh(
            fileName = fileName,
            rawHeights = meshHeights,
            rawW = meshW,
            rawH = meshH,
            sizeX = meshSizeX,
            sizeY = meshSizeY,
            targetDepthMm = targetDepthMm,
            depthScale = depthScale,
            invertZ = invertZ,
            flipY = flipY,
            flipX = flipX,
            transpose = transpose,
            targetResolution = gridResolution,
            showBaseBlock = true
        )

        val raw1DValues = FloatArray(rawGridW * rawGridH)
        var pIdx = 0
        for (y in 0 until rawGridH) {
            for (x in 0 until rawGridW) {
                raw1DValues[pIdx++] = rawHeights[x][y]
            }
        }

        return RlfModel(
            fileName = fileName,
            widthMm = meshSizeX,
            heightMm = meshSizeY,
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
            gridResolution = gridResolution,
            showBaseBlock = true,
            originalWidth = detection.gridWidth,
            originalHeight = detection.gridHeight,
            customStrideDelta = customStrideDelta,
            raw1DValues = raw1DValues,
            isSigned16 = forceSigned16,
            cropToRelief = cropToRelief,
            activeBounds = activeBounds,
            rawBytes = allBytes
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
        showBaseBlock: Boolean = model.showBaseBlock,
        strideDelta: Int = model.customStrideDelta,
        isSigned16: Boolean = model.isSigned16,
        cropToRelief: Boolean = model.cropToRelief
    ): RlfModel {
        val actualInvertZ = if (isInverted != model.invertZ) isInverted else invertZ
        val actualFlipY = if (isFlippedY != model.flipY) isFlippedY else flipY
        val actualFlipX = if (isFlippedX != model.flipX) isFlippedX else flipX
        val actualTranspose = if (isTransposed != model.transpose) isTransposed else transpose

        // If raw bytes are stored and core data format/crop/stride changed, re-extract cleanly
        if (model.rawBytes != null && (isSigned16 != model.isSigned16 || cropToRelief != model.cropToRelief || strideDelta != model.customStrideDelta)) {
            val detection = detectReliefFormat(model.rawBytes)
            return extractHeightGrid(
                allBytes = model.rawBytes,
                detection = detection,
                fileName = model.fileName,
                depthScale = depthScale,
                invertZ = actualInvertZ,
                flipY = actualFlipY,
                flipX = actualFlipX,
                transpose = actualTranspose,
                gridResolution = gridResolution,
                customStrideDelta = strideDelta,
                forceSigned16 = isSigned16,
                cropToRelief = cropToRelief
            ).copy(
                showBaseBlock = showBaseBlock
            )
        }

        var activeHeights = model.rawHeights
        var activeW = model.gridWidth
        var activeH = model.gridHeight
        var activeSizeX = model.widthMm
        var activeSizeY = model.heightMm

        if (cropToRelief && model.activeBounds != null && (model.activeBounds.width < model.gridWidth * 0.95f || model.activeBounds.height < model.gridHeight * 0.95f)) {
            val bounds = model.activeBounds
            activeW = bounds.width
            activeH = bounds.height
            val cropped = Array(activeW) { FloatArray(activeH) }
            for (y in 0 until activeH) {
                for (x in 0 until activeW) {
                    cropped[x][y] = model.rawHeights[bounds.minX + x][bounds.minY + y]
                }
            }
            activeHeights = cropped
            activeSizeX = (activeW * 0.35f).coerceIn(40f, 600f)
            activeSizeY = (activeH * 0.35f).coerceIn(40f, 600f)
        } else if (strideDelta != model.customStrideDelta && model.raw1DValues != null) {
            val newW = (model.originalWidth + strideDelta).coerceIn(20, 10000)
            val newH = (model.raw1DValues.size / newW).coerceIn(20, 10000)
            val reHeights = Array(newW) { FloatArray(newH) }
            for (y in 0 until newH) {
                for (x in 0 until newW) {
                    val idx = y * newW + x
                    if (idx < model.raw1DValues.size) {
                        reHeights[x][y] = model.raw1DValues[idx]
                    }
                }
            }
            activeHeights = reHeights
            activeW = newW
            activeH = newH
            activeSizeX = (newW * 0.35f).coerceIn(40f, 600f)
            activeSizeY = (newH * 0.35f).coerceIn(40f, 600f)
        }

        val stl = buildMesh(
            fileName = model.fileName,
            rawHeights = activeHeights,
            rawW = activeW,
            rawH = activeH,
            sizeX = activeSizeX,
            sizeY = activeSizeY,
            targetDepthMm = model.maxReliefHeightMm,
            depthScale = depthScale,
            invertZ = actualInvertZ,
            flipY = actualFlipY,
            flipX = actualFlipX,
            transpose = actualTranspose,
            targetResolution = gridResolution,
            showBaseBlock = showBaseBlock
        )

        return model.copy(
            stlModel = stl,
            widthMm = activeSizeX,
            heightMm = activeSizeY,
            gridWidth = activeW,
            gridHeight = activeH,
            rawHeights = activeHeights,
            depthScale = depthScale,
            invertZ = actualInvertZ,
            flipY = actualFlipY,
            flipX = actualFlipX,
            transpose = actualTranspose,
            gridResolution = gridResolution,
            showBaseBlock = showBaseBlock,
            customStrideDelta = strideDelta,
            isSigned16 = isSigned16,
            cropToRelief = cropToRelief
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
        targetResolution: Int = 220,
        showBaseBlock: Boolean = true
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

        // 2. Four Solid Box Skirt Walls & Base Floor (if enabled)
        if (showBaseBlock) {
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
        }

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
            val bmpFileSize = (bmpBuf.getInt(2).toLong() and 0xFFFFFFFFL)
            val offset = bmpBuf.getInt(10).coerceIn(54, totalSize - 1)
            val w = abs(bmpBuf.getInt(18))
            val h = abs(bmpBuf.getInt(22))
            val bpp = bmpBuf.getShort(28).toInt() and 0xFFFF

            // If the BMP size is small compared to the file (e.g. < 40% of total file),
            // it is merely an embedded 2D preview thumbnail in an ArtCAM container.
            // The actual 3D 16-bit relief is stored AFTER the BMP!
            if (bmpFileSize < totalSize * 0.40 && totalSize > 65536) {
                val startAfterBmp = bmpFileSize.toInt().coerceIn(54, totalSize - 1)
                val remainingBytes = bytes.copyOfRange(startAfterBmp, bytes.size)
                val detectedAfterBmp = detectBinaryHeader(remainingBytes)
                    ?: detectViaAutocorrelation(remainingBytes)
                return detectedAfterBmp.copy(
                    dataOffset = startAfterBmp + detectedAfterBmp.dataOffset
                )
            } else if (w in 16..16384 && h in 16..16384) {
                return FormatDetection(
                    gridWidth = w,
                    gridHeight = h,
                    dataOffset = offset,
                    isFloat32 = false,
                    isShort16 = bpp >= 16,
                    isUnsigned16 = false,
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

        // Strategy C: Comprehensive Scored Binary Header Search (Little & Big Endian)
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

            if (w != null && h != null && w in 16..16384 && h in 16..16384) {
                var headerEnd = text.indexOf("\n\n")
                if (headerEnd == -1) headerEnd = text.indexOf("\r\n\r\n")
                val offset = if (headerEnd != -1) headerEnd + 2 else 256

                return FormatDetection(
                    gridWidth = w,
                    gridHeight = h,
                    dataOffset = min(offset, bytes.size - 1),
                    isFloat32 = false,
                    isShort16 = true,
                    isUnsigned16 = false,
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
        var bestCandidate: FormatDetection? = null
        var bestScore = -1.0

        val scanLimit = min(bytes.size - 16, 8192)

        for (isBig in listOf(false, true)) {
            val order = if (isBig) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
            val buffer = ByteBuffer.wrap(bytes).order(order)

            for (i in 0 until scanLimit step 2) {
                // Test 1: Adjacent 32-bit ints (w, h)
                val w32 = buffer.getInt(i)
                val h32 = buffer.getInt(i + 4)
                scoreCandidate(w32, h32, i + 8, isBig, totalSize, bytes) { cand, score ->
                    if (score > bestScore) {
                        bestScore = score
                        bestCandidate = cand
                    }
                }

                // Test 2: Separated 32-bit ints (e.g. w, physW, h, physH)
                if (i + 12 <= scanLimit) {
                    val wSep = buffer.getInt(i)
                    val hSep = buffer.getInt(i + 8)
                    scoreCandidate(wSep, hSep, i + 12, isBig, totalSize, bytes) { cand, score ->
                        if (score > bestScore) {
                            bestScore = score
                            bestCandidate = cand
                        }
                    }
                }

                // Test 3: Adjacent 16-bit shorts
                val w16 = buffer.getShort(i).toInt() and 0xFFFF
                val h16 = buffer.getShort(i + 2).toInt() and 0xFFFF
                scoreCandidate(w16, h16, i + 4, isBig, totalSize, bytes) { cand, score ->
                    if (score > bestScore) {
                        bestScore = score
                        bestCandidate = cand
                    }
                }

                // Test 4: Adjacent 32-bit floats
                val wf = buffer.getFloat(i).toInt()
                val hf = buffer.getFloat(i + 4).toInt()
                scoreCandidate(wf, hf, i + 8, isBig, totalSize, bytes) { cand, score ->
                    if (score > bestScore) {
                        bestScore = score
                        bestCandidate = cand
                    }
                }
            }
        }

        // Only accept candidate if it has significant coverage (> 25% of file or high score)
        return if (bestScore >= 25.0) bestCandidate else null
    }

    private inline fun scoreCandidate(
        w: Int,
        h: Int,
        headerOffset: Int,
        isBig: Boolean,
        totalSize: Int,
        bytes: ByteArray,
        onCandidate: (FormatDetection, Double) -> Unit
    ) {
        if (w !in 32..16384 || h !in 32..16384) return
        val ar = w.toDouble() / h.toDouble()
        if (ar < 0.05 || ar > 20.0) return

        // ArtCAM uses primarily 16-bit short (2 bytes), sometimes 32-bit float (4 bytes), or 8-bit
        for (elemSize in listOf(2, 4, 1)) {
            val payload = w.toLong() * h.toLong() * elemSize.toLong()
            if (payload <= 0 || payload > totalSize.toLong()) continue

            val coverage = (payload.toDouble() / totalSize.toDouble()) * 100.0
            if (coverage < 15.0) continue // Reject tiny thumbnails and icons!

            val tailOffset = totalSize - payload
            var score = coverage

            // If tail-aligned (payload fills the file almost to the end)
            if (tailOffset in 0..16384) {
                score += 300.0
            }
            // Standard ArtCAM 16-bit short heightmap bonus
            if (elemSize == 2) {
                score += 40.0
            }
            // Realistic deity carving aspect ratio (0.3 to 3.0)
            if (ar in 0.3..3.0) {
                score += 20.0
            }

            val optimalOffset = findOptimalDataOffset(bytes, headerOffset, w, h, elemSize)

            val cand = FormatDetection(
                gridWidth = w,
                gridHeight = h,
                dataOffset = optimalOffset,
                isFloat32 = (elemSize == 4),
                isShort16 = (elemSize == 2),
                isUnsigned16 = false,
                isBigEndian = isBig,
                physicalWidthMm = w * 0.35f,
                physicalHeightMm = h * 0.35f
            )
            onCandidate(cand, score)
        }
    }

    /**
     * Determines optimal data start offset (aligned to 64, 128, 256, 512 or tail aligned).
     */
    private fun findOptimalDataOffset(bytes: ByteArray, minOffset: Int, w: Int, h: Int, elemSize: Int): Int {
        val totalSize = bytes.size
        val payload = w.toLong() * h.toLong() * elemSize.toLong()
        val tailOffset = (totalSize - payload).toInt()
        if (tailOffset in minOffset..minOffset + 4096) {
            return tailOffset
        }
        for (align in listOf(64, 72, 128, 256, 512, 1024, 2048, 4096)) {
            if (align >= minOffset && align + payload <= totalSize) {
                return align
            }
        }
        return minOffset.coerceIn(0, totalSize - 1)
    }

    /**
     * Autocorrelation Pitch & Stride Detection:
     * High vertical correlation (smooth gradient) only occurs when tested stride == actual width W.
     */
    private fun detectViaAutocorrelation(bytes: ByteArray): FormatDetection {
        val totalSize = bytes.size
        val offset = if (totalSize > 512) 64 else 0
        val payload = totalSize - offset
        val shortCount = payload / 2

        if (shortCount < 400) {
            val side = sqrt(shortCount.toDouble()).toInt().coerceAtLeast(10)
            return FormatDetection(side, side, offset, false, true, false, false, side * 0.35f, side * 0.35f)
        }

        // Test candidate aspect ratios and fine search around them
        val candidateWidths = LinkedHashSet<Int>()
        val baseSide = sqrt(shortCount.toDouble()).toInt()
        candidateWidths.add(baseSide) // 1:1

        val aspects = listOf(
            1.0, 4.0 / 3.0, 3.0 / 2.0, 16.0 / 9.0, 2.0 / 1.0, 5.0 / 2.0, 3.0 / 1.0,
            3.0 / 4.0, 2.0 / 3.0, 9.0 / 16.0, 1.0 / 2.0, 2.0 / 5.0, 1.0 / 3.0
        )
        for (asp in aspects) {
            val centerW = sqrt(shortCount.toDouble() * asp).toInt()
            for (delta in -10..10) {
                val w = centerW + delta
                if (w in 30..8000) candidateWidths.add(w)
            }
        }

        // Standard presets and fine neighborhood
        for (preset in listOf(128, 200, 256, 300, 360, 400, 500, 512, 600, 640, 720, 800, 960, 1000, 1024, 1080, 1200, 1280, 1440, 1500, 1600, 1800, 1920, 2000, 2048, 2400)) {
            if (preset < shortCount / 20) {
                candidateWidths.add(preset)
                candidateWidths.add(preset - 1)
                candidateWidths.add(preset + 1)
            }
        }

        var bestWidth = baseSide
        var minDiff = Double.MAX_VALUE

        val samplePoints = 1200

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

        val finalH = (shortCount / bestWidth).coerceIn(10, 8000)
        return FormatDetection(
            gridWidth = bestWidth,
            gridHeight = finalH,
            dataOffset = offset,
            isFloat32 = false,
            isShort16 = true,
            isUnsigned16 = false,
            isBigEndian = false,
            physicalWidthMm = bestWidth * 0.35f,
            physicalHeightMm = finalH * 0.35f
        )
    }

    private fun tryDecompressZlib(bytes: ByteArray): ByteArray {
        // Look for zlib magic in the first 8192 bytes
        val scanEnd = min(bytes.size - 2, 8192)
        for (i in 0 until scanEnd) {
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
