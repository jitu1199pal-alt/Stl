package com.example.data.parser

import com.example.ui.render3d.BoundingBox3D
import com.example.ui.render3d.Vector3D
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

sealed class DxfEntity {
    data class Line(val layer: String, val start: Vector3D, val end: Vector3D) : DxfEntity()
    data class Circle(val layer: String, val center: Vector3D, val radius: Float) : DxfEntity()
    data class Arc(val layer: String, val center: Vector3D, val radius: Float, val startAngleDeg: Float, val endAngleDeg: Float) : DxfEntity()
    data class Polyline(val layer: String, val points: List<Vector3D>, val isClosed: Boolean) : DxfEntity()
    data class TextEntity(val layer: String, val position: Vector3D, val text: String, val height: Float) : DxfEntity()
    data class Ellipse(val layer: String, val center: Vector3D, val majorAxis: Vector3D, val axisRatio: Float) : DxfEntity()
}

data class DxfModel(
    val fileName: String,
    val entities: List<DxfEntity>,
    val layers: List<String>,
    val bounds: BoundingBox3D,
    val totalEntityCount: Int = entities.size
)

object DxfParser {

    fun parse(fileName: String, content: String): DxfModel {
        return parseStream(fileName, content.byteInputStream())
    }

    fun parseStream(fileName: String, inputStream: InputStream): DxfModel {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 262144)
        val modelEntities = ArrayList<DxfEntity>(4096)
        val paperEntities = ArrayList<DxfEntity>(512)
        val layerSet = LinkedHashSet<String>()

        // Block definition storage: blockName -> list of entities defined in local coordinates
        val blockDefinitions = HashMap<String, MutableList<DxfEntity>>()
        var currentBlockName = ""
        var currentBlockEntities: MutableList<DxfEntity>? = null

        var currentSection = ""
        var currentType = ""
        var currentLayer = "0"
        var isPaperSpace = false

        // Temporary CAD attribute holders
        var x1 = 0f; var y1 = 0f; var z1 = 0f
        var x2 = 0f; var y2 = 0f; var z2 = 0f
        var x3 = 0f; var y3 = 0f; var z3 = 0f
        var x4 = 0f; var y4 = 0f; var z4 = 0f
        var radius = 0f
        var startAngle = 0f; var endAngle = 0f
        var textValue = ""
        var blockNameRef = ""
        var insertScaleX = 1f; var insertScaleY = 1f; var insertScaleZ = 1f
        var insertRotDeg = 0f

        // Polyline / vertex collection
        val polyPoints = ArrayList<Vector3D>(256)
        var polyClosed = false
        var currentPolyX = 0f
        var currentPolyY = 0f
        var currentPolyZ = 0f
        var hasPolyX = false

        fun resetEntityFields() {
            x1 = 0f; y1 = 0f; z1 = 0f
            x2 = 0f; y2 = 0f; z2 = 0f
            x3 = 0f; y3 = 0f; z3 = 0f
            x4 = 0f; y4 = 0f; z4 = 0f
            radius = 0f; startAngle = 0f; endAngle = 0f
            textValue = ""
            blockNameRef = ""
            insertScaleX = 1f; insertScaleY = 1f; insertScaleZ = 1f
            insertRotDeg = 0f
            isPaperSpace = false
            polyPoints.clear()
            polyClosed = false
            hasPolyX = false
            currentPolyX = 0f; currentPolyY = 0f; currentPolyZ = 0f
        }

        fun addEntity(entity: DxfEntity) {
            if (currentBlockEntities != null) {
                currentBlockEntities?.add(entity)
            } else if (isPaperSpace) {
                paperEntities.add(entity)
            } else {
                modelEntities.add(entity)
            }
        }

        fun finalizeCurrentEntity() {
            if (currentLayer.isNotBlank()) layerSet.add(currentLayer)

            when (currentType) {
                "LINE" -> {
                    val entity = DxfEntity.Line(currentLayer, Vector3D(x1, y1, z1), Vector3D(x2, y2, z2))
                    addEntity(entity)
                }
                "CIRCLE" -> {
                    if (radius > 0f) {
                        val entity = DxfEntity.Circle(currentLayer, Vector3D(x1, y1, z1), radius)
                        addEntity(entity)
                    }
                }
                "ARC" -> {
                    if (radius > 0f) {
                        val entity = DxfEntity.Arc(currentLayer, Vector3D(x1, y1, z1), radius, startAngle, endAngle)
                        addEntity(entity)
                    }
                }
                "LWPOLYLINE" -> {
                    if (hasPolyX) {
                        polyPoints.add(Vector3D(currentPolyX, currentPolyY, currentPolyZ))
                        hasPolyX = false
                    }
                    if (polyPoints.isNotEmpty()) {
                        val entity = DxfEntity.Polyline(currentLayer, ArrayList(polyPoints), polyClosed)
                        addEntity(entity)
                    }
                }
                "POLYLINE" -> {
                    // AutoCAD classic polyline vertices were captured via child VERTEX records
                    if (polyPoints.isNotEmpty()) {
                        val entity = DxfEntity.Polyline(currentLayer, ArrayList(polyPoints), polyClosed)
                        addEntity(entity)
                    }
                }
                "SPLINE" -> {
                    if (polyPoints.isNotEmpty()) {
                        val entity = DxfEntity.Polyline(currentLayer, ArrayList(polyPoints), isClosed = false)
                        addEntity(entity)
                    }
                }
                "SOLID", "3DFACE", "TRACE" -> {
                    val pts = if (x3 == x4 && y3 == y4) {
                        listOf(Vector3D(x1, y1, z1), Vector3D(x2, y2, z2), Vector3D(x3, y3, z3))
                    } else {
                        listOf(Vector3D(x1, y1, z1), Vector3D(x2, y2, z2), Vector3D(x3, y3, z3), Vector3D(x4, y4, z4))
                    }
                    addEntity(DxfEntity.Polyline(currentLayer, pts, isClosed = true))
                }
                "POINT" -> {
                    addEntity(DxfEntity.Circle(currentLayer, Vector3D(x1, y1, z1), radius = 0.5f))
                }
                "TEXT", "MTEXT" -> {
                    if (textValue.isNotBlank()) {
                        addEntity(DxfEntity.TextEntity(currentLayer, Vector3D(x1, y1, z1), textValue, radius.coerceAtLeast(2f)))
                    }
                }
                "ELLIPSE" -> {
                    val majorVec = if (x2 != 0f || y2 != 0f || z2 != 0f) Vector3D(x2, y2, z2) else Vector3D(radius.coerceAtLeast(1f), 0f, 0f)
                    val ratio = if (radius in 0.001f..1f) radius else 0.5f
                    addEntity(DxfEntity.Ellipse(currentLayer, Vector3D(x1, y1, z1), majorVec, ratio))
                }
                "INSERT" -> {
                    // Expand block reference into model space
                    val block = blockDefinitions[blockNameRef]
                    if (block != null && block.isNotEmpty()) {
                        val rotRad = Math.toRadians(insertRotDeg.toDouble()).toFloat()
                        val cosR = cos(rotRad)
                        val sinR = sin(rotRad)

                        for (src in block) {
                            when (src) {
                                is DxfEntity.Line -> {
                                    val tx1 = transformBlockPoint(src.start, x1, y1, z1, insertScaleX, insertScaleY, insertScaleZ, cosR, sinR)
                                    val tx2 = transformBlockPoint(src.end, x1, y1, z1, insertScaleX, insertScaleY, insertScaleZ, cosR, sinR)
                                    addEntity(DxfEntity.Line(currentLayer, tx1, tx2))
                                }
                                is DxfEntity.Circle -> {
                                    val tc = transformBlockPoint(src.center, x1, y1, z1, insertScaleX, insertScaleY, insertScaleZ, cosR, sinR)
                                    addEntity(DxfEntity.Circle(currentLayer, tc, src.radius * abs(insertScaleX)))
                                }
                                is DxfEntity.Arc -> {
                                    val tc = transformBlockPoint(src.center, x1, y1, z1, insertScaleX, insertScaleY, insertScaleZ, cosR, sinR)
                                    addEntity(DxfEntity.Arc(currentLayer, tc, src.radius * abs(insertScaleX), src.startAngleDeg + insertRotDeg, src.endAngleDeg + insertRotDeg))
                                }
                                is DxfEntity.Polyline -> {
                                    val tpts = src.points.map { transformBlockPoint(it, x1, y1, z1, insertScaleX, insertScaleY, insertScaleZ, cosR, sinR) }
                                    addEntity(DxfEntity.Polyline(currentLayer, tpts, src.isClosed))
                                }
                                is DxfEntity.TextEntity -> {
                                    val tp = transformBlockPoint(src.position, x1, y1, z1, insertScaleX, insertScaleY, insertScaleZ, cosR, sinR)
                                    addEntity(DxfEntity.TextEntity(currentLayer, tp, src.text, src.height * abs(insertScaleY)))
                                }
                                is DxfEntity.Ellipse -> {
                                    val tc = transformBlockPoint(src.center, x1, y1, z1, insertScaleX, insertScaleY, insertScaleZ, cosR, sinR)
                                    addEntity(DxfEntity.Circle(currentLayer, tc, src.majorAxis.length() * abs(insertScaleX)))
                                }
                            }
                        }
                    }
                }
            }

            resetEntityFields()
        }

        var lineCode = reader.readLine()
        while (lineCode != null) {
            val lineValue = reader.readLine() ?: break
            val code = lineCode.trim().toIntOrNull() ?: -999
            val value = lineValue.trim()

            if (code == 0) {
                val newType = value.uppercase()

                // Check for Section transitions
                if (newType == "SECTION") {
                    finalizeCurrentEntity()
                    currentSection = ""
                    currentType = ""
                } else if (newType == "ENDSEC") {
                    finalizeCurrentEntity()
                    currentSection = ""
                    currentType = ""
                } else if (newType == "BLOCK") {
                    finalizeCurrentEntity()
                    currentBlockName = ""
                    currentBlockEntities = ArrayList()
                    currentType = "BLOCK"
                } else if (newType == "ENDBLK") {
                    finalizeCurrentEntity()
                    if (currentBlockName.isNotEmpty() && currentBlockEntities != null) {
                        blockDefinitions[currentBlockName] = currentBlockEntities!!
                    }
                    currentBlockEntities = null
                    currentBlockName = ""
                    currentType = ""
                } else if (newType == "VERTEX") {
                    if (hasPolyX) {
                        polyPoints.add(Vector3D(currentPolyX, currentPolyY, currentPolyZ))
                        currentPolyX = 0f
                        currentPolyY = 0f
                        currentPolyZ = 0f
                        hasPolyX = false
                    }
                    // Keep currentType as POLYLINE, vertex coordinates will follow
                } else if (newType == "SEQEND") {
                    if (hasPolyX) {
                        polyPoints.add(Vector3D(currentPolyX, currentPolyY, currentPolyZ))
                        hasPolyX = false
                    }
                    finalizeCurrentEntity()
                    currentType = ""
                } else {
                    finalizeCurrentEntity()
                    currentType = newType
                }
            } else if (code == 2 && (currentSection.isEmpty() || currentType == "BLOCK")) {
                if (currentType == "BLOCK") {
                    currentBlockName = value
                } else {
                    currentSection = value.uppercase()
                }
            } else if (code == 8) {
                currentLayer = value
            } else if (code == 67) {
                // 1 = Paper Space (layout sheet), 0 = Model Space
                isPaperSpace = (value.toIntOrNull() ?: 0) == 1
            } else if (currentType == "LWPOLYLINE") {
                when (code) {
                    10 -> {
                        if (hasPolyX) {
                            polyPoints.add(Vector3D(currentPolyX, currentPolyY, currentPolyZ))
                        }
                        currentPolyX = value.toFloatOrNull() ?: 0f
                        hasPolyX = true
                    }
                    20 -> currentPolyY = value.toFloatOrNull() ?: 0f
                    30 -> currentPolyZ = value.toFloatOrNull() ?: 0f
                    70 -> {
                        val flag = value.toIntOrNull() ?: 0
                        polyClosed = (flag and 1) != 0
                    }
                }
            } else if (currentType == "POLYLINE") {
                when (code) {
                    70 -> {
                        val flag = value.toIntOrNull() ?: 0
                        polyClosed = (flag and 1) != 0
                    }
                    10 -> {
                        if (hasPolyX) {
                            polyPoints.add(Vector3D(currentPolyX, currentPolyY, currentPolyZ))
                        }
                        currentPolyX = value.toFloatOrNull() ?: 0f
                        currentPolyY = 0f
                        currentPolyZ = 0f
                        hasPolyX = true
                    }
                    20 -> currentPolyY = value.toFloatOrNull() ?: 0f
                    30 -> currentPolyZ = value.toFloatOrNull() ?: 0f
                }
            } else if (currentType == "SPLINE") {
                when (code) {
                    10, 11 -> currentPolyX = value.toFloatOrNull() ?: 0f
                    20, 21 -> currentPolyY = value.toFloatOrNull() ?: 0f
                    30, 31 -> {
                        currentPolyZ = value.toFloatOrNull() ?: 0f
                        polyPoints.add(Vector3D(currentPolyX, currentPolyY, currentPolyZ))
                    }
                }
            } else {
                when (code) {
                    10 -> x1 = value.toFloatOrNull() ?: x1
                    20 -> y1 = value.toFloatOrNull() ?: y1
                    30 -> z1 = value.toFloatOrNull() ?: z1
                    11 -> x2 = value.toFloatOrNull() ?: x2
                    21 -> y2 = value.toFloatOrNull() ?: y2
                    31 -> z2 = value.toFloatOrNull() ?: z2
                    12 -> x3 = value.toFloatOrNull() ?: x3
                    22 -> y3 = value.toFloatOrNull() ?: y3
                    32 -> z3 = value.toFloatOrNull() ?: z3
                    13 -> x4 = value.toFloatOrNull() ?: x4
                    23 -> y4 = value.toFloatOrNull() ?: y4
                    33 -> z4 = value.toFloatOrNull() ?: z4
                    40 -> radius = value.toFloatOrNull() ?: radius
                    50 -> {
                        if (currentType == "INSERT") insertRotDeg = value.toFloatOrNull() ?: 0f
                        else startAngle = value.toFloatOrNull() ?: startAngle
                    }
                    51 -> endAngle = value.toFloatOrNull() ?: endAngle
                    41 -> insertScaleX = value.toFloatOrNull() ?: 1f
                    42 -> insertScaleY = value.toFloatOrNull() ?: 1f
                    43 -> insertScaleZ = value.toFloatOrNull() ?: 1f
                    2 -> if (currentType == "INSERT") blockNameRef = value
                    1 -> textValue = value
                }
            }

            lineCode = reader.readLine()
        }
        finalizeCurrentEntity()

        // Choose entities to render: prefer Model Space entities. If none, fallback to Paper Space
        val finalEntities = if (modelEntities.isNotEmpty()) modelEntities else paperEntities
        val bounds = computeRobustBounds(finalEntities)

        return DxfModel(
            fileName = fileName,
            entities = finalEntities,
            layers = if (layerSet.isEmpty()) listOf("0") else layerSet.toList().sorted(),
            bounds = bounds
        )
    }

    private fun transformBlockPoint(
        p: Vector3D,
        insertX: Float,
        insertY: Float,
        insertZ: Float,
        scaleX: Float,
        scaleY: Float,
        scaleZ: Float,
        cosR: Float,
        sinR: Float
    ): Vector3D {
        val sx = p.x * scaleX
        val sy = p.y * scaleY
        val sz = p.z * scaleZ
        val rx = sx * cosR - sy * sinR
        val ry = sx * sinR + sy * cosR
        return Vector3D(rx + insertX, ry + insertY, sz + insertZ)
    }

    /**
     * Computes a highly robust bounding box for CAD models.
     * Uses outlier rejection to eliminate stray (0,0) origin points, header extremes,
     * or distant paper margins so the model is perfectly centered and scaled to the user's screen.
     */
    fun computeRobustBounds(entities: List<DxfEntity>): BoundingBox3D {
        if (entities.isEmpty()) {
            return BoundingBox3D(0f, 100f, 0f, 100f, 0f, 0f)
        }

        val allPoints = ArrayList<Vector3D>(entities.size * 2)
        for (entity in entities) {
            when (entity) {
                is DxfEntity.Line -> {
                    allPoints.add(entity.start)
                    allPoints.add(entity.end)
                }
                is DxfEntity.Circle -> {
                    allPoints.add(Vector3D(entity.center.x - entity.radius, entity.center.y - entity.radius, entity.center.z))
                    allPoints.add(Vector3D(entity.center.x + entity.radius, entity.center.y + entity.radius, entity.center.z))
                }
                is DxfEntity.Arc -> {
                    allPoints.add(Vector3D(entity.center.x - entity.radius, entity.center.y - entity.radius, entity.center.z))
                    allPoints.add(Vector3D(entity.center.x + entity.radius, entity.center.y + entity.radius, entity.center.z))
                }
                is DxfEntity.Polyline -> {
                    allPoints.addAll(entity.points)
                }
                is DxfEntity.TextEntity -> {
                    allPoints.add(entity.position)
                }
                is DxfEntity.Ellipse -> {
                    val r = entity.majorAxis.length()
                    allPoints.add(Vector3D(entity.center.x - r, entity.center.y - r, entity.center.z))
                    allPoints.add(Vector3D(entity.center.x + r, entity.center.y + r, entity.center.z))
                }
            }
        }

        // Discard NaN, Infinite, and extreme astronomical coordinates (> 10^8)
        val validPoints = allPoints.filter {
            !it.x.isNaN() && !it.x.isInfinite() &&
            !it.y.isNaN() && !it.y.isInfinite() &&
            abs(it.x) < 1e8f && abs(it.y) < 1e8f
        }

        if (validPoints.isEmpty()) {
            return BoundingBox3D(0f, 100f, 0f, 100f, 0f, 0f)
        }

        if (validPoints.size < 6) {
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
            for (p in validPoints) {
                if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
                if (p.z < minZ) minZ = p.z; if (p.z > maxZ) maxZ = p.z
            }
            val sizeX = maxOf(maxX - minX, 1f)
            val sizeY = maxOf(maxY - minY, 1f)
            return BoundingBox3D(minX, minX + sizeX, minY, minY + sizeY, minZ, maxZ)
        }

        // Statistical Outlier Rejection:
        // AutoCAD drawings frequently have an isolated point at (0,0,0) or a border at 0,0
        // while the entire mechanical/architectural drawing is far away (e.g. at 50,000, 20,000).
        val sortedX = validPoints.map { it.x }.sorted()
        val sortedY = validPoints.map { it.y }.sorted()

        val n = sortedX.size
        val q1Idx = (n * 0.05f).toInt().coerceIn(0, n - 1)
        val q3Idx = (n * 0.95f).toInt().coerceIn(0, n - 1)

        val q1X = sortedX[q1Idx]
        val q3X = sortedX[q3Idx]
        val spanX = maxOf(q3X - q1X, 1f)

        val q1Y = sortedY[q1Idx]
        val q3Y = sortedY[q3Idx]
        val spanY = maxOf(q3Y - q1Y, 1f)

        // Trim outliers more than 4 times the core span away
        val inliersX = sortedX.filter { it >= (q1X - 4f * spanX) && it <= (q3X + 4f * spanX) }
        val inliersY = sortedY.filter { it >= (q1Y - 4f * spanY) && it <= (q3Y + 4f * spanY) }

        val minX = inliersX.firstOrNull() ?: sortedX.first()
        val maxX = inliersX.lastOrNull() ?: sortedX.last()
        val minY = inliersY.firstOrNull() ?: sortedY.first()
        val maxY = inliersY.lastOrNull() ?: sortedY.last()

        val safeSizeX = if ((maxX - minX) <= 0.001f) 10f else (maxX - minX)
        val safeSizeY = if ((maxY - minY) <= 0.001f) 10f else (maxY - minY)

        return BoundingBox3D(minX, minX + safeSizeX, minY, minY + safeSizeY, 0f, 0f)
    }
}
