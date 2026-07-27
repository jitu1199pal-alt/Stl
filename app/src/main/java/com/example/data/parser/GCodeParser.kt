package com.example.data.parser

import com.example.ui.render3d.BoundingBox3D
import com.example.ui.render3d.Vector3D
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
    val toolsUsed: List<Int>
)

object GCodeParser {

    fun parse(fileName: String, content: String): ToolpathModel {
        val lines = content.lines()
        val segments = mutableListOf<ToolpathSegment>()

        var currX = 0f
        var currY = 0f
        var currZ = 0f

        var currFeed = 1000f // default mm/min
        var currRpm = 12000f // default RPM
        var currTool = 1
        var currMotion = MotionType.RAPID_G0
        var isAbsolute = true
        var isMetric = true // G21 mm

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        fun updateBounds(x: Float, y: Float, z: Float) {
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
        }

        updateBounds(0f, 0f, 0f)

        var totalLength = 0f
        var totalEstSeconds = 0f
        var maxF = currFeed
        var maxS = currRpm
        val toolsSet = mutableSetOf<Int>()

        for ((idx, line) in lines.withIndex()) {
            val trimmed = line.uppercase().trim().split(";")[0].split("(")[0].trim()
            if (trimmed.isEmpty()) continue

            val lineNo = idx + 1

            // Check Units & Modes
            if (trimmed.contains("G20")) isMetric = false
            if (trimmed.contains("G21")) isMetric = true
            if (trimmed.contains("G90")) isAbsolute = true
            if (trimmed.contains("G91")) isAbsolute = false

            // Extract Feed, RPM, Tool
            val feedMatch = Regex("""F([0-9.]+)""").find(trimmed)
            if (feedMatch != null) {
                currFeed = feedMatch.groupValues[1].toFloatOrNull() ?: currFeed
                if (currFeed > maxF) maxF = currFeed
            }

            val rpmMatch = Regex("""S([0-9.]+)""").find(trimmed)
            if (rpmMatch != null) {
                currRpm = rpmMatch.groupValues[1].toFloatOrNull() ?: currRpm
                if (currRpm > maxS) maxS = currRpm
            }

            val toolMatch = Regex("""T([0-9]+)""").find(trimmed)
            if (toolMatch != null) {
                currTool = toolMatch.groupValues[1].toIntOrNull() ?: currTool
                toolsSet.add(currTool)
            }

            // Motion commands
            var newMotion = currMotion
            if (Regex("""\bG0*0\b""").containsMatchIn(trimmed)) newMotion = MotionType.RAPID_G0
            else if (Regex("""\bG0*1\b""").containsMatchIn(trimmed)) newMotion = MotionType.CUTTING_G1
            else if (Regex("""\bG0*2\b""").containsMatchIn(trimmed)) newMotion = MotionType.ARC_CW_G2
            else if (Regex("""\bG0*3\b""").containsMatchIn(trimmed)) newMotion = MotionType.ARC_CCW_G3

            currMotion = newMotion

            val xVal = Regex("""X(-?[0-9.]+)""").find(trimmed)?.groupValues?.get(1)?.toFloatOrNull()
            val yVal = Regex("""Y(-?[0-9.]+)""").find(trimmed)?.groupValues?.get(1)?.toFloatOrNull()
            val zVal = Regex("""Z(-?[0-9.]+)""").find(trimmed)?.groupValues?.get(1)?.toFloatOrNull()

            val iVal = Regex("""I(-?[0-9.]+)""").find(trimmed)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val jVal = Regex("""J(-?[0-9.]+)""").find(trimmed)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

            if (xVal != null || yVal != null || zVal != null) {
                var targetX = if (xVal != null) (if (isAbsolute) xVal else currX + xVal) else currX
                var targetY = if (yVal != null) (if (isAbsolute) yVal else currY + yVal) else currY
                var targetZ = if (zVal != null) (if (isAbsolute) zVal else currZ + zVal) else currZ

                // Convert inch to mm if G20
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

                    val steps = 12
                    val arcList = mutableListOf<Vector3D>()
                    for (step in 0..steps) {
                        val t = step / steps.toFloat()
                        val ang = startAngle + t * (endAngle - startAngle)
                        val ax = centerX + radius * cos(ang)
                        val ay = centerY + radius * sin(ang)
                        val az = currZ + t * (targetZ - currZ)
                        arcList.add(Vector3D(ax, ay, az))
                        updateBounds(ax, ay, az)
                    }
                    arcPts = arcList
                    segLength = abs(endAngle - startAngle) * radius
                } else {
                    updateBounds(targetX, targetY, targetZ)
                }

                totalLength += segLength
                // Speed time calculation (feed in mm/min, rapid speed assumed ~ 3000 mm/min)
                val speedMmMin = if (currMotion == MotionType.RAPID_G0) 3000f else currFeed.coerceAtLeast(100f)
                val estSecs = (segLength / speedMmMin) * 60f
                totalEstSeconds += estSecs

                segments.add(
                    ToolpathSegment(
                        lineNumber = lineNo,
                        rawText = trimmed,
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

        if (minX > maxX) { minX = 0f; maxX = 100f }
        if (minY > maxY) { minY = 0f; maxY = 100f }
        if (minZ > maxZ) { minZ = -10f; maxZ = 10f }

        return ToolpathModel(
            fileName = fileName,
            rawLines = lines,
            segments = segments,
            bounds = BoundingBox3D(minX, maxX, minY, maxY, minZ, maxZ),
            totalLengthMm = totalLength,
            estimatedTimeSeconds = totalEstSeconds,
            maxFeedRate = maxF,
            maxRpm = maxS,
            toolsUsed = toolsSet.toList().sorted()
        )
    }
}
