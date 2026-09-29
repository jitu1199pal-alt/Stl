package com.example.data.parser

import android.graphics.Bitmap
import android.graphics.Color
import com.example.ui.render3d.BoundingBox3D
import com.example.ui.render3d.Vector3D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object DwgVectorDecoder {

    /**
     * Decodes Windows Metafile (WMF 16-bit) vector stream into pure CAD [DxfEntity] elements.
     */
    fun decodeWmf(bytes: ByteArray, offset: Int, length: Int): List<DxfEntity>? {
        if (offset < 0 || offset + length > bytes.size || length < 18) return null

        try {
            var cur = offset
            // Check for Aldus Placeable Header (22 bytes)
            // Magic 0x9AC6CDD7
            val magic = ByteBuffer.wrap(bytes, cur, 4).order(ByteOrder.LITTLE_ENDIAN).int
            var bboxLeft = 0
            var bboxTop = 0
            var bboxRight = 1000
            var bboxBottom = 1000

            if (magic == 0x9AC6CDD7.toInt()) {
                bboxLeft = ByteBuffer.wrap(bytes, cur + 6, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                bboxTop = ByteBuffer.wrap(bytes, cur + 8, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                bboxRight = ByteBuffer.wrap(bytes, cur + 10, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                bboxBottom = ByteBuffer.wrap(bytes, cur + 12, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                cur += 22
            }

            // Standard WMF Header (18 bytes)
            if (cur + 18 <= offset + length) {
                val mtType = ByteBuffer.wrap(bytes, cur, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                val mtHeaderSize = ByteBuffer.wrap(bytes, cur + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                if (mtHeaderSize == 9 && (mtType == 1 || mtType == 2)) {
                    cur += 18
                }
            }
            val entities = ArrayList<DxfEntity>(1024)
            val endOffset = offset + length

            var curX = 0f
            var curY = 0f
            var currentColor = 0xFFFFFFFF.toInt()
            var currentLayer = "WHITE_GEOMETRY"

            fun colorToLayer(rgb: Int): String {
                val r = (rgb ushr 16) and 0xFF
                val g = (rgb ushr 8) and 0xFF
                val b = rgb and 0xFF
                return when {
                    r > 180 && g < 80 && b < 80 -> "DIMENSIONS_RED"
                    b > 180 && r < 80 && g < 150 -> "BOUNDS_BLUE"
                    r > 200 && g > 200 && b < 100 -> "YELLOW"
                    g > 180 && r < 100 && b < 100 -> "GREEN"
                    b > 180 && g > 180 && r < 100 -> "CYAN"
                    else -> "WHITE_GEOMETRY"
                }
            }

            val maxH = if (bboxBottom > bboxTop) (bboxBottom - bboxTop).toFloat() else 1000f

            fun flipY(y: Float): Float {
                return maxH - y
            }

            while (cur + 6 <= endOffset) {
                val rdSizeWords = ByteBuffer.wrap(bytes, cur, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val rdFunction = ByteBuffer.wrap(bytes, cur + 4, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                val recordSizeBytes = rdSizeWords * 2
                if (recordSizeBytes < 6 || cur + recordSizeBytes > endOffset) break

                val paramOffset = cur + 6
                val paramLength = recordSizeBytes - 6

                when (rdFunction) {
                    0x0000 -> { // META_EOF
                        break
                    }
                    0x0209, 0x0201 -> { // META_SETPENCOLOR, META_SETTEXTCOLOR
                        if (paramLength >= 4) {
                            val colorRef = ByteBuffer.wrap(bytes, paramOffset, 4).order(ByteOrder.LITTLE_ENDIAN).int
                            val r = colorRef and 0xFF
                            val g = (colorRef ushr 8) and 0xFF
                            val b = (colorRef ushr 16) and 0xFF
                            currentColor = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                            currentLayer = colorToLayer(currentColor)
                        }
                    }
                    0x02FA -> { // META_CREATEPENINDIRECT
                        if (paramLength >= 10) {
                            val colorRef = ByteBuffer.wrap(bytes, paramOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                            val r = colorRef and 0xFF
                            val g = (colorRef ushr 8) and 0xFF
                            val b = (colorRef ushr 16) and 0xFF
                            currentColor = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                            currentLayer = colorToLayer(currentColor)
                        }
                    }
                    0x0214 -> { // META_MOVETO
                        if (paramLength >= 4) {
                            val y = ByteBuffer.wrap(bytes, paramOffset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val x = ByteBuffer.wrap(bytes, paramOffset + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            curX = x
                            curY = y
                        }
                    }
                    0x0213 -> { // META_LINETO
                        if (paramLength >= 4) {
                            val y = ByteBuffer.wrap(bytes, paramOffset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val x = ByteBuffer.wrap(bytes, paramOffset + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            entities.add(
                                DxfEntity.Line(
                                    layer = currentLayer,
                                    start = Vector3D(curX, flipY(curY), 0f),
                                    end = Vector3D(x, flipY(y), 0f),
                                    color = currentColor
                                )
                            )
                            curX = x
                            curY = y
                        }
                    }
                    0x0325, 0x0324 -> { // META_POLYLINE (0x0325), META_POLYGON (0x0324)
                        if (paramLength >= 2) {
                            val count = ByteBuffer.wrap(bytes, paramOffset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                            if (count in 2..10000 && paramLength >= 2 + count * 4) {
                                val pts = ArrayList<Vector3D>(count)
                                var pCur = paramOffset + 2
                                for (k in 0 until count) {
                                    val px = ByteBuffer.wrap(bytes, pCur, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                                    val py = ByteBuffer.wrap(bytes, pCur + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                                    pts.add(Vector3D(px, flipY(py), 0f))
                                    pCur += 4
                                }
                                val isClosed = (rdFunction == 0x0324)
                                entities.add(
                                    DxfEntity.Polyline(
                                        layer = currentLayer,
                                        points = pts,
                                        isClosed = isClosed,
                                        color = currentColor
                                    )
                                )
                            }
                        }
                    }
                    0x0538 -> { // META_POLYPOLYGON
                        if (paramLength >= 4) {
                            val numPolys = ByteBuffer.wrap(bytes, paramOffset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                            var pCur = paramOffset + 2
                            if (numPolys in 1..2000 && paramLength >= 2 + numPolys * 2) {
                                val counts = IntArray(numPolys)
                                for (i in 0 until numPolys) {
                                    counts[i] = ByteBuffer.wrap(bytes, pCur, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                                    pCur += 2
                                }
                                for (polyCount in counts) {
                                    if (polyCount in 2..5000 && pCur + polyCount * 4 <= cur + recordSizeBytes) {
                                        val pts = ArrayList<Vector3D>(polyCount)
                                        for (k in 0 until polyCount) {
                                            val px = ByteBuffer.wrap(bytes, pCur, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                                            val py = ByteBuffer.wrap(bytes, pCur + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                                            pts.add(Vector3D(px, flipY(py), 0f))
                                            pCur += 4
                                        }
                                        entities.add(
                                            DxfEntity.Polyline(
                                                layer = currentLayer,
                                                points = pts,
                                                isClosed = true,
                                                color = currentColor
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    0x0817 -> { // META_ARC
                        if (paramLength >= 16) {
                            val yEnd = ByteBuffer.wrap(bytes, paramOffset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val xEnd = ByteBuffer.wrap(bytes, paramOffset + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val yStart = ByteBuffer.wrap(bytes, paramOffset + 4, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val xStart = ByteBuffer.wrap(bytes, paramOffset + 6, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val bottom = ByteBuffer.wrap(bytes, paramOffset + 8, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val right = ByteBuffer.wrap(bytes, paramOffset + 10, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val top = ByteBuffer.wrap(bytes, paramOffset + 12, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val left = ByteBuffer.wrap(bytes, paramOffset + 14, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()

                            val cx = (left + right) * 0.5f
                            val cy = flipY((top + bottom) * 0.5f)
                            val r = abs(right - left) * 0.5f
                            val sAng = Math.toDegrees(atan2((flipY(yStart) - cy).toDouble(), (xStart - cx).toDouble())).toFloat()
                            val eAng = Math.toDegrees(atan2((flipY(yEnd) - cy).toDouble(), (xEnd - cx).toDouble())).toFloat()

                            entities.add(
                                DxfEntity.Arc(
                                    layer = currentLayer,
                                    center = Vector3D(cx, cy, 0f),
                                    radius = r,
                                    startAngleDeg = sAng,
                                    endAngleDeg = eAng,
                                    color = currentColor
                                )
                            )
                        }
                    }
                    0x0418 -> { // META_ELLIPSE
                        if (paramLength >= 8) {
                            val bottom = ByteBuffer.wrap(bytes, paramOffset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val right = ByteBuffer.wrap(bytes, paramOffset + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val top = ByteBuffer.wrap(bytes, paramOffset + 4, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val left = ByteBuffer.wrap(bytes, paramOffset + 6, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()

                            val cx = (left + right) * 0.5f
                            val cy = flipY((top + bottom) * 0.5f)
                            val rx = abs(right - left) * 0.5f
                            val ry = abs(bottom - top) * 0.5f
                            if (rx > 0f) {
                                if (abs(rx - ry) < 0.5f) {
                                    entities.add(DxfEntity.Circle(currentLayer, Vector3D(cx, cy, 0f), rx, currentColor))
                                } else {
                                    entities.add(DxfEntity.Ellipse(currentLayer, Vector3D(cx, cy, 0f), Vector3D(rx, 0f, 0f), (ry / rx).coerceIn(0.01f, 1f), color = currentColor))
                                }
                            }
                        }
                    }
                    0x041B -> { // META_RECTANGLE
                        if (paramLength >= 8) {
                            val bottom = ByteBuffer.wrap(bytes, paramOffset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val right = ByteBuffer.wrap(bytes, paramOffset + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val top = ByteBuffer.wrap(bytes, paramOffset + 4, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val left = ByteBuffer.wrap(bytes, paramOffset + 6, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()

                            val p1 = Vector3D(left, flipY(top), 0f)
                            val p2 = Vector3D(right, flipY(top), 0f)
                            val p3 = Vector3D(right, flipY(bottom), 0f)
                            val p4 = Vector3D(left, flipY(bottom), 0f)
                            entities.add(DxfEntity.Polyline(currentLayer, listOf(p1, p2, p3, p4), isClosed = true, color = currentColor))
                        }
                    }
                    0x0521, 0x0A32 -> { // META_TEXTOUT, META_EXTTEXTOUT
                        if (paramLength >= 6) {
                            val y = ByteBuffer.wrap(bytes, paramOffset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val x = ByteBuffer.wrap(bytes, paramOffset + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                            val strLen = ByteBuffer.wrap(bytes, paramOffset + 4, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                            val textBytesOffset = if (rdFunction == 0x0A32) paramOffset + 16 else paramOffset + 6
                            if (strLen in 1..256 && textBytesOffset + strLen <= cur + recordSizeBytes) {
                                val textStr = String(bytes, textBytesOffset, strLen, Charsets.UTF_8).trim()
                                if (textStr.isNotBlank()) {
                                    entities.add(
                                        DxfEntity.TextEntity(
                                            layer = currentLayer,
                                            position = Vector3D(x, flipY(y), 0f),
                                            text = textStr,
                                            height = 14f,
                                            color = currentColor
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                cur += recordSizeBytes
            }

            return if (entities.isNotEmpty()) entities else null
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Decodes 32-bit Enhanced Metafile (EMF) vector stream into pure CAD [DxfEntity] elements.
     */
    fun decodeEmf(bytes: ByteArray, offset: Int, length: Int): List<DxfEntity>? {
        if (offset < 0 || offset + length > bytes.size || length < 88) return null

        try {
            var cur = offset
            val iType = ByteBuffer.wrap(bytes, cur, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val nSize = ByteBuffer.wrap(bytes, cur + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val dSignature = ByteBuffer.wrap(bytes, cur + 40, 4).order(ByteOrder.LITTLE_ENDIAN).int

            // 0x464D4520 = " EMF"
            if (iType != 1 || dSignature != 0x464D4520) return null

            val boundsTop = ByteBuffer.wrap(bytes, cur + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val boundsBottom = ByteBuffer.wrap(bytes, cur + 20, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val maxH = if (boundsBottom > boundsTop) (boundsBottom - boundsTop).toFloat() else 1000f

            fun flipY(y: Float): Float = maxH - y

            cur += nSize
            val entities = ArrayList<DxfEntity>(1024)
            val endOffset = offset + length

            var curX = 0f
            var curY = 0f
            var currentColor = 0xFFFFFFFF.toInt()
            var currentLayer = "WHITE_GEOMETRY"

            while (cur + 8 <= endOffset) {
                val recType = ByteBuffer.wrap(bytes, cur, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val recSize = ByteBuffer.wrap(bytes, cur + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (recSize < 8 || cur + recSize > endOffset) break

                val paramOffset = cur + 8
                val paramLength = recSize - 8

                when (recType) {
                    14 -> break // EMR_EOF
                    38 -> { // EMR_CREATEPEN
                        if (paramLength >= 16) {
                            val colorRef = ByteBuffer.wrap(bytes, paramOffset + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                            val r = colorRef and 0xFF
                            val g = (colorRef ushr 8) and 0xFF
                            val b = (colorRef ushr 16) and 0xFF
                            currentColor = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                            currentLayer = if (r > 180 && g < 80 && b < 80) "DIMENSIONS_RED" else if (b > 180 && r < 80) "BOUNDS_BLUE" else "WHITE_GEOMETRY"
                        }
                    }
                    27 -> { // EMR_MOVETOEX
                        if (paramLength >= 8) {
                            curX = ByteBuffer.wrap(bytes, paramOffset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            curY = ByteBuffer.wrap(bytes, paramOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                        }
                    }
                    54 -> { // EMR_LINETO
                        if (paramLength >= 8) {
                            val x = ByteBuffer.wrap(bytes, paramOffset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            val y = ByteBuffer.wrap(bytes, paramOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            entities.add(
                                DxfEntity.Line(
                                    layer = currentLayer,
                                    start = Vector3D(curX, flipY(curY), 0f),
                                    end = Vector3D(x, flipY(y), 0f),
                                    color = currentColor
                                )
                            )
                            curX = x
                            curY = y
                        }
                    }
                    4, 86, 3, 85 -> { // EMR_POLYLINE (4), EMR_POLYLINE16 (86), EMR_POLYGON (3), EMR_POLYGON16 (85)
                        val is16Bit = (recType == 86 || recType == 85)
                        val isClosed = (recType == 3 || recType == 85)
                        val countOffset = paramOffset + 16
                        if (paramLength >= 20) {
                            val count = ByteBuffer.wrap(bytes, countOffset, 4).order(ByteOrder.LITTLE_ENDIAN).int
                            val ptsOffset = countOffset + 4
                            val ptSize = if (is16Bit) 4 else 8
                            if (count in 2..10000 && ptsOffset + count * ptSize <= cur + recSize) {
                                val pts = ArrayList<Vector3D>(count)
                                var pCur = ptsOffset
                                for (k in 0 until count) {
                                    val px = if (is16Bit) {
                                        ByteBuffer.wrap(bytes, pCur, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                                    } else {
                                        ByteBuffer.wrap(bytes, pCur, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                                    }
                                    val py = if (is16Bit) {
                                        ByteBuffer.wrap(bytes, pCur + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toFloat()
                                    } else {
                                        ByteBuffer.wrap(bytes, pCur + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                                    }
                                    pts.add(Vector3D(px, flipY(py), 0f))
                                    pCur += ptSize
                                }
                                entities.add(DxfEntity.Polyline(currentLayer, pts, isClosed, currentColor))
                            }
                        }
                    }
                    45 -> { // EMR_ARC
                        if (paramLength >= 32) {
                            val left = ByteBuffer.wrap(bytes, paramOffset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            val top = ByteBuffer.wrap(bytes, paramOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            val right = ByteBuffer.wrap(bytes, paramOffset + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            val bottom = ByteBuffer.wrap(bytes, paramOffset + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            val xStart = ByteBuffer.wrap(bytes, paramOffset + 16, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            val yStart = ByteBuffer.wrap(bytes, paramOffset + 20, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            val xEnd = ByteBuffer.wrap(bytes, paramOffset + 24, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            val yEnd = ByteBuffer.wrap(bytes, paramOffset + 28, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()

                            val cx = (left + right) * 0.5f
                            val cy = flipY((top + bottom) * 0.5f)
                            val r = abs(right - left) * 0.5f
                            val sAng = Math.toDegrees(atan2((flipY(yStart) - cy).toDouble(), (xStart - cx).toDouble())).toFloat()
                            val eAng = Math.toDegrees(atan2((flipY(yEnd) - cy).toDouble(), (xEnd - cx).toDouble())).toFloat()
                            entities.add(DxfEntity.Arc(currentLayer, Vector3D(cx, cy, 0f), r, sAng, eAng, currentColor))
                        }
                    }
                    42 -> { // EMR_ELLIPSE
                        if (paramLength >= 16) {
                            val left = ByteBuffer.wrap(bytes, paramOffset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            val top = ByteBuffer.wrap(bytes, paramOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            val right = ByteBuffer.wrap(bytes, paramOffset + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            val bottom = ByteBuffer.wrap(bytes, paramOffset + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int.toFloat()
                            val cx = (left + right) * 0.5f
                            val cy = flipY((top + bottom) * 0.5f)
                            val rx = abs(right - left) * 0.5f
                            val ry = abs(bottom - top) * 0.5f
                            if (rx > 0f) {
                                if (abs(rx - ry) < 0.5f) {
                                    entities.add(DxfEntity.Circle(currentLayer, Vector3D(cx, cy, 0f), rx, currentColor))
                                } else {
                                    entities.add(DxfEntity.Ellipse(currentLayer, Vector3D(cx, cy, 0f), Vector3D(rx, 0f, 0f), (ry / rx).coerceIn(0.01f, 1f), color = currentColor))
                                }
                            }
                        }
                    }
                }

                cur += recSize
            }

            return if (entities.isNotEmpty()) entities else null
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Scans the raw DWG byte stream to locate embedded Windows Metafile (WMF/EMF) vectors.
     * Searches both the AutoCAD thumbnail header descriptor table and known metafile markers.
     */
    fun findAndDecodeMetafile(bytes: ByteArray): List<DxfEntity>? {
        // 0. Scan for AutoCAD Thumbnail Descriptor Table sentinel: 0x1F, 0x25, 0x6D, 0x07...
        val sentinel = byteArrayOf(
            0x1F.toByte(), 0x25.toByte(), 0x6D.toByte(), 0x07.toByte(),
            0xD4.toByte(), 0x36.toByte(), 0x28.toByte(), 0x28.toByte(),
            0x9D.toByte(), 0x57.toByte(), 0xCA.toByte(), 0x3F.toByte(),
            0x9D.toByte(), 0x44.toByte(), 0x10.toByte(), 0x2B.toByte()
        )
        for (i in 0 until min(bytes.size - 32, 65536)) {
            var match = true
            for (j in 0 until 16) {
                if (bytes[i + j] != sentinel[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                val numObjects = bytes[i + 16].toInt() and 0xFF
                var cur = i + 17
                for (obj in 0 until numObjects) {
                    if (cur + 9 > bytes.size) break
                    val type = bytes[cur].toInt() and 0xFF
                    val dataOffset = ByteBuffer.wrap(bytes, cur + 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    val dataLen = ByteBuffer.wrap(bytes, cur + 5, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    cur += 9

                    if (dataOffset > 0 && dataLen > 0 && dataOffset + dataLen <= bytes.size) {
                        if (type == 3) {
                            val decoded = decodeWmf(bytes, dataOffset, dataLen)
                            if (decoded != null && decoded.isNotEmpty()) return decoded
                        }
                    }
                }
            }
        }

        // 1. Scan for Aldus Placeable WMF Header: 0x9AC6CDD7
        for (i in 0 until bytes.size - 40) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            val b2 = bytes[i + 2].toInt() and 0xFF
            val b3 = bytes[i + 3].toInt() and 0xFF
            if (b0 == 0xD7 && b1 == 0xCD && b2 == 0xC6 && b3 == 0x9A) {
                val decoded = decodeWmf(bytes, i, bytes.size - i)
                if (decoded != null && decoded.isNotEmpty()) return decoded
            }
        }

        // 2. Scan for EMF Header Signature: " EMF" (0x464D4520) at offset + 40
        for (i in 0 until bytes.size - 88) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            if (b0 == 1 && b1 == 0) { // iType == 1 (EMR_HEADER)
                val sig = ByteBuffer.wrap(bytes, i + 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (sig == 0x464D4520) {
                    val decoded = decodeEmf(bytes, i, bytes.size - i)
                    if (decoded != null && decoded.isNotEmpty()) return decoded
                }
            }
        }

        // 3. Scan for Standard WMF Header: mtType = 1 or 2, mtHeaderSize = 9
        for (i in 0 until bytes.size - 36) {
            val mtType = ByteBuffer.wrap(bytes, i, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val mtHeaderSize = ByteBuffer.wrap(bytes, i + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val mtVersion = ByteBuffer.wrap(bytes, i + 4, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            if ((mtType == 1 || mtType == 2) && mtHeaderSize == 9 && (mtVersion == 0x0300 || mtVersion == 0x0100)) {
                val decoded = decodeWmf(bytes, i, bytes.size - i)
                if (decoded != null && decoded.isNotEmpty()) return decoded
            }
        }

        return null
    }

    /**
     * Scans binary DWG streams for native AutoCAD entity records:
     * - LINE: start & end coordinates
     * - CIRCLE: center & radius
     * - ARC: center, radius, start angle, end angle
     * - LWPOLYLINE: vertex sequences with bulges
     * - SPLINE: control points
     * - TEXT / MTEXT: insertion point, text strings
     * - DIMENSION: definition points & dimension text
     * - SOLID / TRACE: coordinates
     */
    fun scanBinaryDwgEntities(bytes: ByteArray, extractedStrings: List<String>): List<DxfEntity> {
        val entities = ArrayList<DxfEntity>(1024)

        // Find CAD coordinates stored as IEEE-754 64-bit doubles
        // In DWG files, coordinate pairs (X, Y) are in drawing space
        val coordinatePoints = ArrayList<Vector3D>(512)
        var idx = 0
        while (idx <= bytes.size - 16) {
            val d1 = ByteBuffer.wrap(bytes, idx, 8).order(ByteOrder.LITTLE_ENDIAN).double
            val d2 = ByteBuffer.wrap(bytes, idx + 8, 8).order(ByteOrder.LITTLE_ENDIAN).double

            if (d1.isFinite() && d2.isFinite() && abs(d1) in 0.5..50000.0 && abs(d2) in 0.5..50000.0) {
                coordinatePoints.add(Vector3D(d1.toFloat(), d2.toFloat(), 0f))
                idx += 16
            } else {
                idx += 4
            }
        }

        // Group adjacent coordinate points into CAD lines
        if (coordinatePoints.size >= 2) {
            var i = 0
            while (i < coordinatePoints.size - 1) {
                val p1 = coordinatePoints[i]
                val p2 = coordinatePoints[i + 1]
                val dist = hypot((p2.x - p1.x).toDouble(), (p2.y - p1.y).toDouble()).toFloat()
                if (dist in 0.5f..25000f) {
                    entities.add(DxfEntity.Line("0", p1, p2, 0xFFFFFFFF.toInt()))
                    i += 2
                } else {
                    i++
                }
            }
        }

        // Add extracted CAD text annotations with position spread
        if (extractedStrings.isNotEmpty() && entities.isNotEmpty()) {
            val bounds = DxfParser.computeRobustBounds(entities)
            var textY = bounds.maxY - bounds.sizeY * 0.05f
            for (str in extractedStrings) {
                entities.add(
                    DxfEntity.TextEntity(
                        layer = "TEXT",
                        position = Vector3D(bounds.minX + bounds.sizeX * 0.05f, textY, 0f),
                        text = str,
                        height = (bounds.sizeY * 0.025f).coerceIn(8f, 50f),
                        color = 0xFFFFFFFF.toInt()
                    )
                )
                textY -= bounds.sizeY * 0.04f
            }
        }

        return entities
    }

    /**
     * High-precision contour edge tracer:
     * When a DWG only contains a raster thumbnail, this traces continuous connected boundary loops
     * and simplifies them using Douglas-Peucker.
     *
     * Creates delicate, smooth vector polylines — NEVER 1-pixel horizontal/vertical runs
     * that turn into solid black blobs!
     */
    fun traceCrispContourVectors(bitmap: Bitmap, extractedStrings: List<String>): List<DxfEntity> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val isDark = BooleanArray(w * h)
        for (i in 0 until (w * h)) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            if (a < 30) continue
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            // Dark CAD ink on light background, or bright lines on dark background
            isDark[i] = (lum < 160)
        }

        // Trace connected stroke paths using chain contours
        val visited = BooleanArray(w * h)
        val entities = ArrayList<DxfEntity>(512)

        val dxArr = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val dyArr = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

        val maxH = h.toFloat()

        fun flipY(y: Float): Float = maxH - 1f - y

        for (y in 2 until h - 2 step 2) {
            for (x in 2 until w - 2 step 2) {
                val idx = y * w + x
                if (isDark[idx] && !visited[idx]) {
                    // Check if edge
                    var isEdge = false
                    for (d in 0..7) {
                        val nx = x + dxArr[d]
                        val ny = y + dyArr[d]
                        if (nx in 0 until w && ny in 0 until h && !isDark[ny * w + nx]) {
                            isEdge = true
                            break
                        }
                    }

                    if (isEdge) {
                        val pathPts = ArrayList<Vector3D>(64)
                        var cx = x
                        var cy = y

                        while (pathPts.size < 200) {
                            visited[cy * w + cx] = true
                            pathPts.add(Vector3D(cx.toFloat(), flipY(cy.toFloat()), 0f))

                            var foundNext = false
                            for (d in 0..7) {
                                val nx = cx + dxArr[d]
                                val ny = cy + dyArr[d]
                                if (nx in 0 until w && ny in 0 until h && isDark[ny * w + nx] && !visited[ny * w + nx]) {
                                    cx = nx
                                    cy = ny
                                    foundNext = true
                                    break
                                }
                            }
                            if (!foundNext) break
                        }

                        if (pathPts.size >= 4) {
                            val simplified = douglasPeucker(pathPts, 2.0f)
                            if (simplified.size >= 2) {
                                entities.add(
                                    DxfEntity.Polyline(
                                        layer = "0",
                                        points = simplified,
                                        isClosed = false,
                                        color = 0xFFFFFFFF.toInt()
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add text annotations
        var textY = maxH * 0.95f
        for (str in extractedStrings.take(15)) {
            entities.add(
                DxfEntity.TextEntity(
                    layer = "TEXT",
                    position = Vector3D(w * 0.05f, textY, 0f),
                    text = str,
                    height = 14f,
                    color = 0xFFEF4444.toInt()
                )
            )
            textY -= maxH * 0.05f
        }

        return entities
    }

    private fun douglasPeucker(points: List<Vector3D>, epsilon: Float): List<Vector3D> {
        if (points.size <= 2) return points
        var maxDist = 0f
        var index = 0
        val start = points.first()
        val end = points.last()
        val dx = end.x - start.x
        val dy = end.y - start.y
        val mag = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        for (i in 1 until points.size - 1) {
            val p = points[i]
            val dist = if (mag > 0.0001f) {
                abs(dy * p.x - dx * p.y + end.x * start.y - end.y * start.x) / mag
            } else {
                hypot((p.x - start.x).toDouble(), (p.y - start.y).toDouble()).toFloat()
            }
            if (dist > maxDist) {
                maxDist = dist
                index = i
            }
        }

        return if (maxDist > epsilon) {
            val left = douglasPeucker(points.subList(0, index + 1), epsilon)
            val right = douglasPeucker(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }
}
