package com.example.cad.measurement

import com.example.cad.model.AreaResult
import com.example.cad.model.CadUnits
import com.example.cad.model.DistanceResult
import com.example.ui.render3d.Vector3D
import kotlin.math.*

enum class MeasureMode {
    NONE,
    DISTANCE,
    CONTINUOUS_DISTANCE,
    AREA,
    COORDINATES
}

/**
 * High-precision CAD Measurement engine.
 */
object MeasurementManager {

    fun calculateDistance(p1: Vector3D, p2: Vector3D, unit: CadUnits = CadUnits.MILLIMETERS): DistanceResult {
        val dx = (p2.x - p1.x).toDouble()
        val dy = (p2.y - p1.y).toDouble()
        val dz = (p2.z - p1.z).toDouble()
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        var angle = Math.toDegrees(atan2(dy, dx))
        if (angle < 0) angle += 360.0

        return DistanceResult(
            distance = dist,
            deltaX = dx,
            deltaY = dy,
            deltaZ = dz,
            angleDeg = angle,
            unitSymbol = unit.symbol
        )
    }

    fun calculatePolylineLength(points: List<Vector3D>, unit: CadUnits = CadUnits.MILLIMETERS): DistanceResult {
        if (points.size < 2) {
            return DistanceResult(0.0, 0.0, 0.0, 0.0, 0.0, unit.symbol)
        }

        var total = 0.0
        for (i in 0 until points.size - 1) {
            val dx = (points[i + 1].x - points[i].x).toDouble()
            val dy = (points[i + 1].y - points[i].y).toDouble()
            val dz = (points[i + 1].z - points[i].z).toDouble()
            total += sqrt(dx * dx + dy * dy + dz * dz)
        }

        val totalDx = (points.last().x - points.first().x).toDouble()
        val totalDy = (points.last().y - points.first().y).toDouble()
        val totalDz = (points.last().z - points.first().z).toDouble()
        var angle = Math.toDegrees(atan2(totalDy, totalDx))
        if (angle < 0) angle += 360.0

        return DistanceResult(
            distance = total,
            deltaX = totalDx,
            deltaY = totalDy,
            deltaZ = totalDz,
            angleDeg = angle,
            unitSymbol = unit.symbol
        )
    }

    fun calculateArea(points: List<Vector3D>, unit: CadUnits = CadUnits.MILLIMETERS): AreaResult {
        if (points.size < 3) {
            return AreaResult(0.0, 0.0, unit.symbol, points.size)
        }

        var areaSum = 0.0
        var perimeterSum = 0.0
        val n = points.size

        for (i in 0 until n) {
            val j = (i + 1) % n
            val p1 = points[i]
            val p2 = points[j]

            areaSum += (p1.x.toDouble() * p2.y.toDouble()) - (p2.x.toDouble() * p1.y.toDouble())
            val segDx = (p2.x - p1.x).toDouble()
            val segDy = (p2.y - p1.y).toDouble()
            perimeterSum += sqrt(segDx * segDx + segDy * segDy)
        }

        val area = abs(areaSum) * 0.5
        return AreaResult(
            area = area,
            perimeter = perimeterSum,
            unitSymbol = unit.symbol,
            pointCount = points.size
        )
    }
}
