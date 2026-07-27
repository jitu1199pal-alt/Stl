package com.example.data.parser

import com.example.ui.render3d.BoundingBox3D
import com.example.ui.render3d.Vector3D
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.math.cos
import kotlin.math.sin

sealed class DxfEntity {
    data class Line(val layer: String, val start: Vector3D, val end: Vector3D) : DxfEntity()
    data class Circle(val layer: String, val center: Vector3D, val radius: Float) : DxfEntity()
    data class Arc(val layer: String, val center: Vector3D, val radius: Float, val startAngleDeg: Float, val endAngleDeg: Float) : DxfEntity()
    data class Polyline(val layer: String, val points: List<Vector3D>, val isClosed: Boolean) : DxfEntity()
    data class TextEntity(val layer: String, val position: Vector3D, val text: String, val height: Float) : DxfEntity()
}

data class DxfModel(
    val fileName: String,
    val entities: List<DxfEntity>,
    val layers: List<String>,
    val bounds: BoundingBox3D
)

object DxfParser {

    fun parse(fileName: String, content: String): DxfModel {
        return parseStream(fileName, content.byteInputStream())
    }

    fun parseStream(fileName: String, inputStream: InputStream): DxfModel {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val entities = mutableListOf<DxfEntity>()
        val layerSet = mutableSetOf<String>()

        var currentType = ""
        var currentLayer = "0"

        var x1 = 0f; var y1 = 0f; var z1 = 0f
        var x2 = 0f; var y2 = 0f; var z2 = 0f
        var radius = 0f
        var startAngle = 0f; var endAngle = 0f
        var textValue = ""
        var polyPoints = mutableListOf<Vector3D>()
        var polyClosed = false

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        fun updateBounds(x: Float, y: Float, z: Float = 0f) {
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
        }

        fun finalizeEntity() {
            layerSet.add(currentLayer)
            when (currentType) {
                "LINE" -> {
                    updateBounds(x1, y1, z1)
                    updateBounds(x2, y2, z2)
                    entities.add(DxfEntity.Line(currentLayer, Vector3D(x1, y1, z1), Vector3D(x2, y2, z2)))
                }
                "CIRCLE" -> {
                    updateBounds(x1 - radius, y1 - radius, z1)
                    updateBounds(x1 + radius, y1 + radius, z1)
                    entities.add(DxfEntity.Circle(currentLayer, Vector3D(x1, y1, z1), radius))
                }
                "ARC" -> {
                    updateBounds(x1 - radius, y1 - radius, z1)
                    updateBounds(x1 + radius, y1 + radius, z1)
                    entities.add(DxfEntity.Arc(currentLayer, Vector3D(x1, y1, z1), radius, startAngle, endAngle))
                }
                "LWPOLYLINE", "POLYLINE" -> {
                    if (polyPoints.isNotEmpty()) {
                        for (pt in polyPoints) updateBounds(pt.x, pt.y, pt.z)
                        entities.add(DxfEntity.Polyline(currentLayer, polyPoints.toList(), polyClosed))
                    }
                }
                "TEXT", "MTEXT" -> {
                    if (textValue.isNotEmpty()) {
                        updateBounds(x1, y1, z1)
                        entities.add(DxfEntity.TextEntity(currentLayer, Vector3D(x1, y1, z1), textValue, radius.coerceAtLeast(1f)))
                    }
                }
            }
            // reset
            x1 = 0f; y1 = 0f; z1 = 0f; x2 = 0f; y2 = 0f; z2 = 0f
            radius = 0f; startAngle = 0f; endAngle = 0f
            textValue = ""
            polyPoints = mutableListOf()
            polyClosed = false
        }

        var lineCode = reader.readLine()
        while (lineCode != null) {
            val lineValue = reader.readLine() ?: break
            val code = lineCode.trim().toIntOrNull()
            val value = lineValue.trim()

            if (code == 0) {
                finalizeEntity()
                currentType = value.uppercase()
            } else if (code == 8) {
                currentLayer = value
            } else if (code == 10) x1 = value.toFloatOrNull() ?: x1
            else if (code == 20) y1 = value.toFloatOrNull() ?: y1
            else if (code == 30) z1 = value.toFloatOrNull() ?: z1
            else if (code == 11) x2 = value.toFloatOrNull() ?: x2
            else if (code == 21) y2 = value.toFloatOrNull() ?: y2
            else if (code == 31) z2 = value.toFloatOrNull() ?: z2
            else if (code == 40) radius = value.toFloatOrNull() ?: radius
            else if (code == 50) startAngle = value.toFloatOrNull() ?: startAngle
            else if (code == 51) endAngle = value.toFloatOrNull() ?: endAngle
            else if (code == 1) textValue = value
            else if (code == 70 && (currentType == "LWPOLYLINE" || currentType == "POLYLINE")) {
                val flag = value.toIntOrNull() ?: 0
                polyClosed = (flag and 1) != 0
            } else if (code == 10 && (currentType == "LWPOLYLINE" || currentType == "POLYLINE")) {
                polyPoints.add(Vector3D(x1, y1, z1))
            }

            lineCode = reader.readLine()
        }
        finalizeEntity()

        if (minX > maxX) { minX = 0f; maxX = 100f; minY = 0f; maxY = 100f; minZ = 0f; maxZ = 0f }

        return DxfModel(
            fileName = fileName,
            entities = entities,
            layers = layerSet.ifEmpty { setOf("0") }.toList().sorted(),
            bounds = BoundingBox3D(minX, maxX, minY, maxY, minZ, maxZ)
        )
    }
}
