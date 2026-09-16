package com.example.data.parser

import com.example.ui.render3d.BoundingBox3D
import com.example.ui.render3d.Vector3D
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

enum class MotionType {
    RAPID_G0,
    CUTTING_G1,
    ARC_CW_G2,
    ARC_CCW_G3,
    DWELL
}

data class ToolpathSegment(
    val lineNumber: Int,
    val rawText: String,
    val start: Vector3D,
    val end: Vector3D,
    val motionType: MotionType,
    val feedRate: Float,
    val rpm: Float,
    val toolNumber: Int,
    val lengthMm: Float,
    val arcPoints: List<Vector3D> = emptyList()
)

data class ToolpathModel(
    val fileName: String,
    val rawLines: List<String>,
    val segments: List<ToolpathSegment>,
    val bounds: BoundingBox3D,
    val totalLengthMm: Float,
    val estimatedTimeSeconds: Float,
    val maxFeedRate: Float,
    val maxRpm: Float,
    val toolsUsed: List<Int>,
    val totalGCodeLines: Int = rawLines.size,
    val cuttingMinZ: Float = bounds.minZ,
    val cuttingMaxZ: Float = bounds.maxZ
)

object GCodeParser {

    private const val MAX_RENDER_SEGMENTS = 35_000

    fun parse(fileName: String, content: String): ToolpathModel {
        return parseStream(fileName, content.byteInputStream())
    }

    fun parseStream(fileName: String, inputStream: InputStream): ToolpathModel {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 262144)
        val rawLinesPreview = ArrayList<String>(1024)

        var currX = 0f
        var currY = 0f
        var currZ = 0f

        var currFeed = 1200f // mm/min
        var currRpm = 18000f // RPM
        var currTool = 1
        var currMotion = MotionType.RAPID_G0
        var isAbsolute = true
        var isMetric = true // G21 mm

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var cutMinZ = Float.MAX_VALUE; var cutMaxZ = -Float.MAX_VALUE

        fun updateBounds(x: Float, y: Float, z: Float, isCutting: Boolean) {
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            if (isCutting) {
                if (z < cutMinZ) cutMinZ = z
                if (z > cutMaxZ) cutMaxZ = z
            }
        }

        var totalLength = 0f
        var totalEstSeconds = 0f
        var maxF = currFeed
        var maxS = currRpm
        val toolsSet = mutableSetOf<Int>()

        // Fast temporary buffer to collect segments
        val collectedSegments = ArrayList<ToolpathSegment>(4096)

        var lineNo = 0
        var lineString = reader.readLine()

        while (lineString != null) {
            lineNo++
            if (lineNo <= 1200) {
                rawLinesPreview.add(lineString)
            }

            val commentIdx = lineString.indexOf(';').let { if (it >= 0) it else lineString.indexOf('(') }
            val cleanLine = (if (commentIdx >= 0) lineString.substring(0, commentIdx) else lineString).uppercase().trim()

            if (cleanLine.isNotEmpty()) {
                if (cleanLine.contains("G20")) isMetric = false
                if (cleanLine.contains("G21")) isMetric = true
                if (cleanLine.contains("G90")) isAbsolute = true
                if (cleanLine.contains("G91")) isAbsolute = false

                val fVal = extractFloat(cleanLine, 'F')
                if (fVal != null) {
                    currFeed = fVal
                    if (currFeed > maxF) maxF = currFeed
                }

                val sVal = extractFloat(cleanLine, 'S')
                if (sVal != null) {
                    currRpm = sVal
                    if (currRpm > maxS) maxS = currRpm
                }

                val tVal = extractFloat(cleanLine, 'T')?.toInt()
                if (tVal != null) {
                    currTool = tVal
                    toolsSet.add(currTool)
                }

                // Fast Motion Detection
                if (cleanLine.contains("G00") || cleanLine.contains("G0 ") || cleanLine.endsWith("G0") || cleanLine.contains("G0X") || cleanLine.contains("G0Y") || cleanLine.contains("G0Z")) {
                    currMotion = MotionType.RAPID_G0
                } else if (cleanLine.contains("G01") || cleanLine.contains("G1 ") || cleanLine.endsWith("G1") || cleanLine.contains("G1X") || cleanLine.contains("G1Y") || cleanLine.contains("G1Z")) {
                    currMotion = MotionType.CUTTING_G1
                } else if (cleanLine.contains("G02") || cleanLine.contains("G2 ") || cleanLine.endsWith("G2")) {
                    currMotion = MotionType.ARC_CW_G2
                } else if (cleanLine.contains("G03") || cleanLine.contains("G3 ") || cleanLine.endsWith("G3")) {
                    currMotion = MotionType.ARC_CCW_G3
                }

                val xVal = extractFloat(cleanLine, 'X')
                val yVal = extractFloat(cleanLine, 'Y')
                val zVal = extractFloat(cleanLine, 'Z')
                val iVal = extractFloat(cleanLine, 'I') ?: 0f
                val jVal = extractFloat(cleanLine, 'J') ?: 0f

                if (xVal != null || yVal != null || zVal != null) {
                    var targetX = if (xVal != null) (if (isAbsolute) xVal else currX + xVal) else currX
                    var targetY = if (yVal != null) (if (isAbsolute) yVal else currY + yVal) else currY
                    var targetZ = if (zVal != null) (if (isAbsolute) zVal else currZ + zVal) else currZ

                    if (!isMetric) {
                        targetX *= 25.4f
                        targetY *= 25.4f
                        targetZ *= 25.4f
                    }

                    val startVec = Vector3D(currX, currY, currZ)
                    val endVec = Vector3D(targetX, targetY, targetZ)
                    var segLength = (endVec - startVec).length()
                    var arcPts = emptyList<Vector3D>()

                    if (currMotion == MotionType.ARC_CW_G2 || currMotion == MotionType.ARC_CCW_G3) {
                        val centerX = currX + (if (!isMetric) iVal * 25.4f else iVal)
                        val centerY = currY + (if (!isMetric) jVal * 25.4f else jVal)
                        val radius = sqrt((currX - centerX) * (currX - centerX) + (currY - centerY) * (currY - centerY))

                        val startAngle = atan2(currY - centerY, currX - centerX)
                        var endAngle = atan2(targetY - centerY, targetX - centerX)

                        val isCw = currMotion == MotionType.ARC_CW_G2
                        if (isCw && endAngle >= startAngle) endAngle -= (2 * PI).toFloat()
                        if (!isCw && endAngle <= startAngle) endAngle += (2 * PI).toFloat()

                        val isCut = currMotion != MotionType.RAPID_G0
                        val steps = 8
                        val arcList = ArrayList<Vector3D>(steps + 1)
                        for (step in 0..steps) {
                            val t = step / steps.toFloat()
                            val ang = startAngle + t * (endAngle - startAngle)
                            val ax = centerX + radius * cos(ang)
                            val ay = centerY + radius * sin(ang)
                            val az = currZ + t * (targetZ - currZ)
                            arcList.add(Vector3D(ax, ay, az))
                            updateBounds(ax, ay, az, isCut)
                        }
                        arcPts = arcList
                        segLength = abs(endAngle - startAngle) * radius
                    } else {
                        val isCut = currMotion != MotionType.RAPID_G0
                        updateBounds(targetX, targetY, targetZ, isCut)
                    }

                    totalLength += segLength
                    val speedMmMin = if (currMotion == MotionType.RAPID_G0) 3000f else currFeed.coerceAtLeast(100f)
                    val estSecs = (segLength / speedMmMin) * 60f
                    totalEstSeconds += estSecs

                    collectedSegments.add(
                        ToolpathSegment(
                            lineNumber = lineNo,
                            rawText = if (lineNo <= 1200) cleanLine else "",
                            start = startVec,
                            end = endVec,
                            motionType = currMotion,
                            feedRate = currFeed,
                            rpm = currRpm,
                            toolNumber = currTool,
                            lengthMm = segLength,
                            arcPoints = arcPts
                        )
                    )

                    currX = targetX
                    currY = targetY
                    currZ = targetZ
                }
            }

            lineString = reader.readLine()
        }

        // Decimate uniformly if collected segments exceed memory rendering threshold
        val finalSegments: List<ToolpathSegment> = if (collectedSegments.size <= MAX_RENDER_SEGMENTS) {
            collectedSegments
        } else {
            val step = kotlin.math.ceil(collectedSegments.size.toDouble() / MAX_RENDER_SEGMENTS).toInt().coerceAtLeast(2)
            val decimated = ArrayList<ToolpathSegment>(MAX_RENDER_SEGMENTS + 10)
            for (idx in 0 until collectedSegments.size step step) {
                decimated.add(collectedSegments[idx])
            }
            if (decimated.lastOrNull() != collectedSegments.last()) {
                decimated.add(collectedSegments.last())
            }
            decimated
        }

        if (minX > maxX) { minX = 0f; maxX = 100f }
        if (minY > maxY) { minY = 0f; maxY = 100f }
        if (minZ > maxZ) { minZ = -10f; maxZ = 10f }

        val finalCutMinZ = if (cutMinZ <= cutMaxZ) cutMinZ else minZ
        val finalCutMaxZ = if (cutMinZ <= cutMaxZ) cutMaxZ else maxZ

        return ToolpathModel(
            fileName = fileName,
            rawLines = rawLinesPreview,
            segments = finalSegments,
            bounds = BoundingBox3D(minX, maxX, minY, maxY, minZ, maxZ),
            totalLengthMm = totalLength,
            estimatedTimeSeconds = totalEstSeconds,
            maxFeedRate = maxF,
            maxRpm = maxS,
            toolsUsed = toolsSet.toList().sorted(),
            totalGCodeLines = lineNo,
            cuttingMinZ = finalCutMinZ,
            cuttingMaxZ = finalCutMaxZ
        )
    }

    private fun extractFloat(line: String, key: Char): Float? {
        val idx = line.indexOf(key)
        if (idx < 0) return null
        var start = idx + 1
        while (start < line.length && line[start].isWhitespace()) start++
        if (start >= line.length) return null
        var end = start
        if (line[end] == '+' || line[end] == '-') end++
        while (end < line.length && (line[end].isDigit() || line[end] == '.')) end++
        return if (end > start) line.substring(start, end).toFloatOrNull() else null
    }
}
