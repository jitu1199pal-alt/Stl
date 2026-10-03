package com.example.data.parser

import com.example.ui.render3d.BoundingBox3D
import com.example.ui.render3d.Vector3D
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

sealed class DxfEntity {
    abstract val layer: String
    open val color: Int? get() = null

    data class Line(
        override val layer: String,
        val start: Vector3D,
        val end: Vector3D,
        override val color: Int? = null,
        val lineWeight: Float = 1f
    ) : DxfEntity()

    data class Circle(
        override val layer: String,
        val center: Vector3D,
        val radius: Float,
        override val color: Int? = null
    ) : DxfEntity() {
        val centerX: Float get() = center.x
        val centerY: Float get() = center.y
    }

    data class Arc(
        override val layer: String,
        val center: Vector3D,
        val radius: Float,
        val startAngleDeg: Float,
        val endAngleDeg: Float,
        override val color: Int? = null
    ) : DxfEntity() {
        val centerX: Float get() = center.x
        val centerY: Float get() = center.y
        val startAngle: Float get() = startAngleDeg
        val endAngle: Float get() = endAngleDeg
        val sweepAngle: Float get() {
            var s = endAngleDeg - startAngleDeg
            if (s < 0f) s += 360f
            return s
        }
    }

    data class Polyline(
        override val layer: String,
        val points: List<Vector3D>,
        val isClosed: Boolean,
        override val color: Int? = null,
        val isFilled: Boolean = false
    ) : DxfEntity()

    data class TextEntity(
        override val layer: String,
        val position: Vector3D,
        val text: String,
        val height: Float,
        val rotationDeg: Float = 0f,
        override val color: Int? = null
    ) : DxfEntity()

    data class Ellipse(
        override val layer: String,
        val center: Vector3D,
        val majorAxis: Vector3D,
        val axisRatio: Float,
        val startParam: Float = 0f,
        val endParam: Float = (2 * Math.PI).toFloat(),
        override val color: Int? = null
    ) : DxfEntity()

    data class Spline(
        override val layer: String,
        val controlPoints: List<Vector3D>,
        val isClosed: Boolean = false,
        override val color: Int? = null
    ) : DxfEntity() {
        val points: List<Vector3D> get() = controlPoints
    }

    data class Hatch(
        override val layer: String,
        val boundaryLoops: List<List<Vector3D>>,
        val isSolid: Boolean = false,
        override val color: Int? = null
    ) : DxfEntity()

    data class Dimension(
        override val layer: String,
        val defPoint1: Vector3D,
        val defPoint2: Vector3D,
        val textPoint: Vector3D,
        val text: String,
        override val color: Int? = null
    ) : DxfEntity()

    data class Leader(
        override val layer: String,
        val vertices: List<Vector3D>,
        val text: String? = null,
        override val color: Int? = null
    ) : DxfEntity()

    data class Solid(
        override val layer: String,
        val points: List<Vector3D>,
        override val color: Int? = null
    ) : DxfEntity()
}

data class RawVertex(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    var bulge: Float = 0f
)

data class BlockDefinition(
    val name: String,
    val basePoint: Vector3D,
    val entities: List<DxfEntity>
)

data class DxfModel(
    val fileName: String,
    val entities: List<DxfEntity>,
    val layers: List<String>,
    val bounds: BoundingBox3D,
    val totalEntityCount: Int = entities.size,
    val previewBitmap: android.graphics.Bitmap? = null,
    val enhancedBitmap: android.graphics.Bitmap? = null,
    val detectedTexts: List<String> = emptyList(),
    val entityStats: Map<String, Int> = emptyMap(),
    val dwgVersion: String? = null,
    val debugReport: String? = null,
    val fileSize: Long = 0L,
    val blockCount: Int = 0,
    val rawContent: String? = null
) {
    fun getRawOrGeneratedDxf(): String {
        if (!rawContent.isNullOrBlank()) return rawContent
        return DxfParser.generateDxfText(this)
    }
}

object DxfParser {

    /**
     * Standard AutoCAD Color Index (ACI) to 32-bit ARGB color mapping.
     */
    fun aciToColor(aci: Int): Int {
        return when (aci) {
            1 -> 0xFFFF0000.toInt() // Red
            2 -> 0xFFFFFF00.toInt() // Yellow
            3 -> 0xFF00FF00.toInt() // Green
            4 -> 0xFF00FFFF.toInt() // Cyan
            5 -> 0xFF0000FF.toInt() // Blue
            6 -> 0xFFFF00FF.toInt() // Magenta
            7 -> 0xFFFFFFFF.toInt() // White / Black in dark mode
            8 -> 0xFF808080.toInt() // Dark Gray
            9 -> 0xFFC0C0C0.toInt() // Light Gray
            10 -> 0xFFFF0000.toInt()
            11 -> 0xFFFF7F7F.toInt()
            12 -> 0xFFCC0000.toInt()
            30 -> 0xFFFF7F00.toInt() // Orange
            40 -> 0xFFFFFF00.toInt()
            50 -> 0xFF7FFF00.toInt()
            else -> {
                if (aci in 1..255) {
                    val h = (aci % 24) * 15f
                    val s = 1.0f
                    val v = 1.0f
                    android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
                } else {
                    0xFFFFFFFF.toInt()
                }
            }
        }
    }

    fun parse(fileName: String, content: String): DxfModel {
        return parseStream(fileName, content.byteInputStream(), StandardCharsets.UTF_8).copy(rawContent = content)
    }

    /**
     * Safe group reader that reads (code, value) pairs without risk of phase desynchronization.
     */
    private class GroupReader(stream: InputStream, charset: Charset) {
        private val reader = BufferedReader(InputStreamReader(stream, charset), 262144)

        fun nextGroup(): Pair<Int, String>? {
            var code: Int? = null
            while (true) {
                val line = reader.readLine() ?: return null
                val clean = line.replace("\uFEFF", "").trim()
                if (clean.isEmpty()) continue
                val parsed = clean.toIntOrNull()
                if (parsed != null) {
                    code = parsed
                    break
                }
                // If line is not an integer, skip it to prevent phase shift
            }
            val valLine = reader.readLine() ?: return null
            return Pair(code!!, valLine.trim())
        }
    }

    fun parseStream(
        fileName: String,
        inputStream: InputStream,
        charset: Charset = StandardCharsets.UTF_8
    ): DxfModel {
        val groupReader = GroupReader(inputStream, charset)
        val modelEntities = ArrayList<DxfEntity>(4096)
        val paperEntities = ArrayList<DxfEntity>(512)
        val layerSet = LinkedHashSet<String>()
        val layerColorsMap = HashMap<String, Int>()
        val stats = mutableMapOf<String, Int>()

        // Case-insensitive block definitions
        val blockDefinitions = TreeMap<String, BlockDefinition>(String.CASE_INSENSITIVE_ORDER)
        var currentBlockName = ""
        var currentBlockBaseX = 0f
        var currentBlockBaseY = 0f
        var currentBlockBaseZ = 0f
        var currentBlockEntities: MutableList<DxfEntity>? = null

        var currentSection = ""
        var currentTable = ""
        var tableCurrentLayerName = ""
        var currentType = ""
        var currentLayer = "0"
        var isPaperSpace = false
        var entityColor: Int? = null

        // Coordinates & attributes
        var x1 = 0f; var y1 = 0f; var z1 = 0f
        var x2 = 0f; var y2 = 0f; var z2 = 0f
        var x3 = 0f; var y3 = 0f; var z3 = 0f
        var x4 = 0f; var y4 = 0f; var z4 = 0f
        var radius = 0f
        var startAngle = 0f; var endAngle = 0f
        val textBuilder = StringBuilder()
        var blockNameRef = ""
        var insertScaleX = 1f; var insertScaleY = 1f; var insertScaleZ = 1f
        var insertRotDeg = 0f

        // Polyline / vertex collection
        val rawPolyVertices = ArrayList<RawVertex>(512)
        val splinePoints = ArrayList<Vector3D>(256)
        val hatchLoops = ArrayList<MutableList<Vector3D>>()
        var currentHatchLoop = ArrayList<Vector3D>()
        var isHatchSolid = false
        var polyClosed = false
        var currentPolyX = 0f
        var currentPolyY = 0f
        var currentPolyZ = 0f
        var currentBulge = 0f
        var hasPolyX = false
        var currentSplineX = 0f
        var currentSplineY = 0f
        var currentSplineZ = 0f
        var hasSplineX = false

        fun resetEntityFields() {
            x1 = 0f; y1 = 0f; z1 = 0f
            x2 = 0f; y2 = 0f; z2 = 0f
            x3 = 0f; y3 = 0f; z3 = 0f
            x4 = 0f; y4 = 0f; z4 = 0f
            radius = 0f; startAngle = 0f; endAngle = 0f
            textBuilder.clear()
            blockNameRef = ""
            insertScaleX = 1f; insertScaleY = 1f; insertScaleZ = 1f
            insertRotDeg = 0f
            isPaperSpace = false
            entityColor = null
            rawPolyVertices.clear()
            splinePoints.clear()
            hatchLoops.clear()
            currentHatchLoop = ArrayList()
            isHatchSolid = false
            polyClosed = false
            hasPolyX = false
            hasSplineX = false
            currentPolyX = 0f; currentPolyY = 0f; currentPolyZ = 0f; currentBulge = 0f
            currentSplineX = 0f; currentSplineY = 0f; currentSplineZ = 0f
        }

        fun recordStat(name: String) {
            stats[name] = (stats[name] ?: 0) + 1
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

        // Recursive block expansion helper with base point offset and depth limit
        fun expandBlock(
            blockDef: BlockDefinition,
            insX: Float,
            insY: Float,
            insZ: Float,
            scX: Float,
            scY: Float,
            scZ: Float,
            rotDeg: Float,
            parentLayer: String,
            depth: Int
        ) {
            if (depth > 16) return
            val rotRad = Math.toRadians(rotDeg.toDouble()).toFloat()
            val cosR = cos(rotRad)
            val sinR = sin(rotRad)
            val bx = blockDef.basePoint.x
            val by = blockDef.basePoint.y
            val bz = blockDef.basePoint.z

            for (src in blockDef.entities) {
                val effectiveLayer = if (src.layer == "0") parentLayer else src.layer
                if (effectiveLayer.isNotBlank()) layerSet.add(effectiveLayer)

                when (src) {
                    is DxfEntity.Line -> {
                        val p1 = transformBlockPoint(src.start, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR)
                        val p2 = transformBlockPoint(src.end, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR)
                        addEntity(DxfEntity.Line(effectiveLayer, p1, p2, src.color ?: entityColor))
                    }
                    is DxfEntity.Circle -> {
                        val tc = transformBlockPoint(src.center, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR)
                        addEntity(DxfEntity.Circle(effectiveLayer, tc, src.radius * abs(scX), src.color ?: entityColor))
                    }
                    is DxfEntity.Arc -> {
                        val tc = transformBlockPoint(src.center, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR)
                        addEntity(DxfEntity.Arc(effectiveLayer, tc, src.radius * abs(scX), src.startAngleDeg + rotDeg, src.endAngleDeg + rotDeg, src.color ?: entityColor))
                    }
                    is DxfEntity.Polyline -> {
                        val tpts = src.points.map { transformBlockPoint(it, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR) }
                        addEntity(DxfEntity.Polyline(effectiveLayer, tpts, src.isClosed, src.color ?: entityColor, src.isFilled))
                    }
                    is DxfEntity.TextEntity -> {
                        val tp = transformBlockPoint(src.position, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR)
                        addEntity(DxfEntity.TextEntity(effectiveLayer, tp, src.text, src.height * abs(scY), src.rotationDeg + rotDeg, src.color ?: entityColor))
                    }
                    is DxfEntity.Ellipse -> {
                        val tc = transformBlockPoint(src.center, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR)
                        val tMajor = transformBlockPoint(src.majorAxis, 0f, 0f, 0f, 0f, 0f, 0f, scX, scY, scZ, cosR, sinR)
                        addEntity(DxfEntity.Ellipse(effectiveLayer, tc, tMajor, src.axisRatio, src.startParam, src.endParam, src.color ?: entityColor))
                    }
                    is DxfEntity.Spline -> {
                        val tpts = src.controlPoints.map { transformBlockPoint(it, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR) }
                        addEntity(DxfEntity.Spline(effectiveLayer, tpts, src.isClosed, src.color ?: entityColor))
                    }
                    is DxfEntity.Hatch -> {
                        val tLoops = src.boundaryLoops.map { loop ->
                            loop.map { transformBlockPoint(it, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR) }
                        }
                        addEntity(DxfEntity.Hatch(effectiveLayer, tLoops, src.isSolid, src.color ?: entityColor))
                    }
                    is DxfEntity.Dimension -> {
                        val dp1 = transformBlockPoint(src.defPoint1, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR)
                        val dp2 = transformBlockPoint(src.defPoint2, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR)
                        val tp = transformBlockPoint(src.textPoint, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR)
                        addEntity(DxfEntity.Dimension(effectiveLayer, dp1, dp2, tp, src.text, src.color ?: entityColor))
                    }
                    is DxfEntity.Leader -> {
                        val tpts = src.vertices.map { transformBlockPoint(it, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR) }
                        addEntity(DxfEntity.Leader(effectiveLayer, tpts, src.text, src.color ?: entityColor))
                    }
                    is DxfEntity.Solid -> {
                        val tpts = src.points.map { transformBlockPoint(it, bx, by, bz, insX, insY, insZ, scX, scY, scZ, cosR, sinR) }
                        addEntity(DxfEntity.Solid(effectiveLayer, tpts, src.color ?: entityColor))
                    }
                }
            }
        }

        fun finalizeCurrentEntity() {
            if (currentLayer.isNotBlank()) layerSet.add(currentLayer)

            val resolvedColor = entityColor ?: layerColorsMap[currentLayer]

            when (currentType) {
                "LINE" -> {
                    recordStat("LINE")
                    val entity = DxfEntity.Line(currentLayer, Vector3D(x1, y1, z1), Vector3D(x2, y2, z2), resolvedColor)
                    addEntity(entity)
                }
                "POINT" -> {
                    recordStat("POINT")
                    addEntity(DxfEntity.Circle(currentLayer, Vector3D(x1, y1, z1), radius = 0.5f, color = resolvedColor))
                }
                "CIRCLE" -> {
                    if (radius > 0f) {
                        recordStat("CIRCLE")
                        val entity = DxfEntity.Circle(currentLayer, Vector3D(x1, y1, z1), radius, resolvedColor)
                        addEntity(entity)
                    }
                }
                "ARC" -> {
                    if (radius > 0f) {
                        recordStat("ARC")
                        val entity = DxfEntity.Arc(currentLayer, Vector3D(x1, y1, z1), radius, startAngle, endAngle, resolvedColor)
                        addEntity(entity)
                    }
                }
                "LWPOLYLINE", "POLYLINE" -> {
                    if (hasPolyX) {
                        rawPolyVertices.add(RawVertex(currentPolyX, currentPolyY, currentPolyZ, currentBulge))
                        hasPolyX = false
                    }
                    if (rawPolyVertices.isNotEmpty()) {
                        recordStat("LWPOLYLINE")
                        val smoothPoints = expandPolylineWithBulges(rawPolyVertices, polyClosed)
                        if (smoothPoints.isNotEmpty()) {
                            val entity = DxfEntity.Polyline(currentLayer, smoothPoints, polyClosed, resolvedColor)
                            addEntity(entity)
                        }
                    }
                }
                "SPLINE" -> {
                    if (hasSplineX) {
                        splinePoints.add(Vector3D(currentSplineX, currentSplineY, currentSplineZ))
                        hasSplineX = false
                    }
                    if (splinePoints.isNotEmpty()) {
                        recordStat("SPLINE")
                        val entity = DxfEntity.Spline(currentLayer, ArrayList(splinePoints), isClosed = polyClosed, color = resolvedColor)
                        addEntity(entity)
                    }
                }
                "SOLID", "TRACE", "3DFACE" -> {
                    recordStat("SOLID")
                    val pts = if ((x3 == x4 && y3 == y4) || (x4 == 0f && y4 == 0f && z4 == 0f)) {
                        listOf(Vector3D(x1, y1, z1), Vector3D(x2, y2, z2), Vector3D(x3, y3, z3))
                    } else {
                        listOf(Vector3D(x1, y1, z1), Vector3D(x2, y2, z2), Vector3D(x4, y4, z4), Vector3D(x3, y3, z3))
                    }
                    addEntity(DxfEntity.Solid(currentLayer, pts, resolvedColor))
                }
                "HATCH" -> {
                    recordStat("HATCH")
                    if (currentHatchLoop.isNotEmpty()) {
                        hatchLoops.add(currentHatchLoop)
                    }
                    if (hatchLoops.isNotEmpty()) {
                        addEntity(DxfEntity.Hatch(currentLayer, hatchLoops, isHatchSolid, resolvedColor))
                    }
                }
                "TEXT", "MTEXT" -> {
                    val txt = textBuilder.toString().trim()
                    if (txt.isNotBlank()) {
                        recordStat(if (currentType == "MTEXT") "MTEXT" else "TEXT")
                        val h = if (radius > 0f) radius else 2.5f
                        addEntity(DxfEntity.TextEntity(currentLayer, Vector3D(x1, y1, z1), txt, h, rotationDeg = startAngle, color = resolvedColor))
                    }
                }
                "DIMENSION" -> {
                    recordStat("DIMENSION")
                    addEntity(DxfEntity.Dimension(
                        layer = currentLayer,
                        defPoint1 = Vector3D(x1, y1, z1),
                        defPoint2 = Vector3D(x2, y2, z2),
                        textPoint = Vector3D(x3, y3, z3),
                        text = textBuilder.toString().trim(),
                        color = resolvedColor
                    ))
                }
                "LEADER" -> {
                    recordStat("LEADER")
                    if (splinePoints.isNotEmpty()) {
                        addEntity(DxfEntity.Leader(currentLayer, ArrayList(splinePoints), textBuilder.toString().trim(), resolvedColor))
                    }
                }
                "ELLIPSE" -> {
                    recordStat("ELLIPSE")
                    val majorVec = if (x2 != 0f || y2 != 0f || z2 != 0f) Vector3D(x2, y2, z2) else Vector3D(radius.coerceAtLeast(1f), 0f, 0f)
                    val ratio = if (radius in 0.001f..1f) radius else 0.5f
                    addEntity(DxfEntity.Ellipse(currentLayer, Vector3D(x1, y1, z1), majorVec, ratio, startAngle, endAngle, resolvedColor))
                }
                "INSERT" -> {
                    recordStat("INSERT")
                    val block = blockDefinitions[blockNameRef] ?: blockDefinitions[blockNameRef.uppercase()]
                    if (block != null) {
                        expandBlock(
                            blockDef = block,
                            insX = x1,
                            insY = y1,
                            insZ = z1,
                            scX = if (insertScaleX != 0f) insertScaleX else 1f,
                            scY = if (insertScaleY != 0f) insertScaleY else 1f,
                            scZ = if (insertScaleZ != 0f) insertScaleZ else 1f,
                            rotDeg = insertRotDeg,
                            parentLayer = currentLayer,
                            depth = 0
                        )
                    }
                }
                else -> {
                    if (currentType.isNotBlank() && currentType !in listOf("SECTION", "ENDSEC", "BLOCK", "ENDBLK", "TABLE", "ENDTAB")) {
                        recordStat("UNSUPPORTED")
                    }
                }
            }

            resetEntityFields()
        }

        while (true) {
            val group = groupReader.nextGroup() ?: break
            val code = group.first
            val value = group.second

            if (code == 0) {
                // Next entity or section structural token
                if (!(currentType == "POLYLINE" && (value == "VERTEX" || value == "SEQEND"))) {
                    finalizeCurrentEntity()
                }

                when (value) {
                    "SECTION" -> {
                        currentSection = ""
                    }
                    "ENDSEC" -> {
                        currentSection = ""
                        currentType = ""
                        currentTable = ""
                    }
                    "TABLE" -> {
                        currentTable = ""
                    }
                    "ENDTAB" -> {
                        currentTable = ""
                        tableCurrentLayerName = ""
                    }
                    "BLOCK" -> {
                        recordStat("BLOCK")
                        currentType = "BLOCK"
                        currentBlockEntities = ArrayList(256)
                        currentBlockName = ""
                        currentBlockBaseX = 0f
                        currentBlockBaseY = 0f
                        currentBlockBaseZ = 0f
                    }
                    "ENDBLK" -> {
                        finalizeCurrentEntity()
                        if (currentBlockName.isNotBlank() && currentBlockEntities != null) {
                            val def = BlockDefinition(
                                name = currentBlockName,
                                basePoint = Vector3D(currentBlockBaseX, currentBlockBaseY, currentBlockBaseZ),
                                entities = currentBlockEntities ?: emptyList()
                            )
                            blockDefinitions[currentBlockName] = def
                            blockDefinitions[currentBlockName.uppercase()] = def
                        }
                        currentBlockEntities = null
                        currentBlockName = ""
                        currentType = ""
                    }
                    "VERTEX" -> {
                        if (hasPolyX) {
                            rawPolyVertices.add(RawVertex(currentPolyX, currentPolyY, currentPolyZ, currentBulge))
                            hasPolyX = false
                            currentBulge = 0f
                        }
                    }
                    "SEQEND" -> {
                        if (currentType == "POLYLINE") {
                            finalizeCurrentEntity()
                        }
                        currentType = ""
                    }
                    else -> {
                        currentType = value
                    }
                }
            } else if (code == 2 && currentSection.isEmpty()) {
                currentSection = value
            } else if (code == 2 && currentSection == "TABLES" && currentTable.isEmpty()) {
                currentTable = value
            } else if (currentSection == "TABLES" && currentTable == "LAYER") {
                when (code) {
                    2 -> {
                        if (value.isNotBlank()) {
                            layerSet.add(value)
                            tableCurrentLayerName = value
                        }
                    }
                    62 -> {
                        val aci = kotlin.math.abs(value.toIntOrNull() ?: 7)
                        if (tableCurrentLayerName.isNotBlank()) {
                            layerColorsMap[tableCurrentLayerName] = aciToColor(aci)
                        }
                    }
                }
            } else if (currentType == "BLOCK") {
                when (code) {
                    2 -> currentBlockName = value
                    10 -> currentBlockBaseX = value.toFloatOrNull() ?: 0f
                    20 -> currentBlockBaseY = value.toFloatOrNull() ?: 0f
                    30 -> currentBlockBaseZ = value.toFloatOrNull() ?: 0f
                }
            } else if (currentType == "LWPOLYLINE" || currentType == "POLYLINE") {
                when (code) {
                    8 -> currentLayer = value
                    62 -> entityColor = aciToColor(value.toIntOrNull() ?: 7)
                    420 -> entityColor = (value.toIntOrNull() ?: 0) or 0xFF000000.toInt()
                    67 -> isPaperSpace = (value == "1")
                    70 -> polyClosed = ((value.toIntOrNull() ?: 0) and 1) != 0
                    10 -> {
                        if (hasPolyX) {
                            rawPolyVertices.add(RawVertex(currentPolyX, currentPolyY, currentPolyZ, currentBulge))
                            currentBulge = 0f
                        }
                        currentPolyX = value.toFloatOrNull() ?: 0f
                        hasPolyX = true
                    }
                    20 -> currentPolyY = value.toFloatOrNull() ?: 0f
                    30 -> currentPolyZ = value.toFloatOrNull() ?: 0f
                    42 -> currentBulge = value.toFloatOrNull() ?: 0f
                }
            } else if (currentType == "SPLINE" || currentType == "LEADER") {
                when (code) {
                    8 -> currentLayer = value
                    62 -> entityColor = aciToColor(value.toIntOrNull() ?: 7)
                    420 -> entityColor = (value.toIntOrNull() ?: 0) or 0xFF000000.toInt()
                    70 -> polyClosed = ((value.toIntOrNull() ?: 0) and 1) != 0
                    10 -> {
                        if (hasSplineX) {
                            splinePoints.add(Vector3D(currentSplineX, currentSplineY, currentSplineZ))
                        }
                        currentSplineX = value.toFloatOrNull() ?: 0f
                        hasSplineX = true
                    }
                    20 -> currentSplineY = value.toFloatOrNull() ?: 0f
                    30 -> currentSplineZ = value.toFloatOrNull() ?: 0f
                    1, 3 -> textBuilder.append(value)
                }
            } else if (currentType == "HATCH") {
                when (code) {
                    8 -> currentLayer = value
                    62 -> entityColor = aciToColor(value.toIntOrNull() ?: 7)
                    420 -> entityColor = (value.toIntOrNull() ?: 0) or 0xFF000000.toInt()
                    70 -> isHatchSolid = (value == "1")
                    92 -> {
                        if (currentHatchLoop.isNotEmpty()) {
                            hatchLoops.add(currentHatchLoop)
                            currentHatchLoop = ArrayList()
                        }
                    }
                    10 -> currentHatchLoop.add(Vector3D(value.toFloatOrNull() ?: 0f, 0f, 0f))
                    20 -> {
                        if (currentHatchLoop.isNotEmpty()) {
                            val last = currentHatchLoop.last()
                            currentHatchLoop[currentHatchLoop.size - 1] = Vector3D(last.x, value.toFloatOrNull() ?: 0f, last.z)
                        }
                    }
                }
            } else {
                when (code) {
                    8 -> currentLayer = value
                    62 -> entityColor = aciToColor(value.toIntOrNull() ?: 7)
                    420 -> entityColor = (value.toIntOrNull() ?: 0) or 0xFF000000.toInt()
                    67 -> isPaperSpace = (value == "1")
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
                    1, 3 -> textBuilder.append(value)
                }
            }
        }
        finalizeCurrentEntity()

        // Choose entities to render: prefer Model Space, then Paper Space, then Block definitions
        var finalEntities: List<DxfEntity> = if (modelEntities.isNotEmpty()) modelEntities else paperEntities
        if (finalEntities.isEmpty() && blockDefinitions.isNotEmpty()) {
            finalEntities = blockDefinitions.values.flatMap { it.entities }
        }

        val bounds = computeRobustBounds(finalEntities)
        val sortedLayers = if (layerSet.isEmpty()) listOf("0") else layerSet.toList().sorted()
        val standardKeys = listOf("LINE", "ARC", "CIRCLE", "LWPOLYLINE", "POLYLINE", "SPLINE", "HATCH", "INSERT", "TEXT", "MTEXT", "DIMENSION")
        val completeStats = standardKeys.associateWith { stats[it] ?: 0 } + stats

        val detectedTexts = finalEntities.filterIsInstance<DxfEntity.TextEntity>().map { it.text.trim() }.filter { it.isNotBlank() }.distinct()

        val debugReport = buildString {
            appendLine("Selected file:")
            appendLine(fileName)
            appendLine()
            appendLine("File size:")
            appendLine("0 bytes")
            appendLine()
            appendLine("Entity count:")
            appendLine("${finalEntities.size}")
            appendLine()
            appendLine("Layer count:")
            appendLine("${sortedLayers.size}")
            appendLine()
            appendLine("Block count:")
            appendLine("${blockDefinitions.size}")
            appendLine()
            appendLine("Model bounds:")
            appendLine("[${bounds.minX}, ${bounds.maxX}, ${bounds.minY}, ${bounds.maxY}]")
            appendLine()
            appendLine("Entity types:")
            for (k in standardKeys) {
                appendLine("$k = ${completeStats[k] ?: 0}")
            }
        }

        return DxfModel(
            fileName = fileName,
            entities = finalEntities,
            layers = sortedLayers,
            bounds = bounds,
            totalEntityCount = finalEntities.size,
            detectedTexts = detectedTexts,
            entityStats = completeStats,
            debugReport = debugReport,
            blockCount = blockDefinitions.size
        )
    }

    fun parseBytes(
        name: String,
        bytes: ByteArray,
        uriString: String? = null,
        mimeType: String? = null,
        fileSize: Long = bytes.size.toLong()
    ): DxfModel {
        if (bytes.isEmpty()) {
            return DxfModel(
                fileName = name,
                entities = emptyList(),
                layers = listOf("0"),
                bounds = BoundingBox3D(0f, 100f, 0f, 100f, 0f, 0f),
                fileSize = fileSize,
                debugReport = "Selected file:\n$name\n\nFile size:\n$fileSize bytes\n\nEntity count:\n0\n\nLayer count:\n1\n\nBlock count:\n0\n\nModel bounds:\n[0.0, 100.0, 0.0, 100.0]"
            )
        }

        // Automatic charset detection
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> StandardCharsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> StandardCharsets.UTF_16BE
            bytes.size >= 4 && bytes[1] == 0.toByte() && bytes[3] == 0.toByte() -> StandardCharsets.UTF_16LE
            else -> StandardCharsets.UTF_8
        }

        var rawText: String? = null
        try {
            rawText = String(bytes, charset)
        } catch (_: Exception) {
            try {
                rawText = String(bytes, StandardCharsets.ISO_8859_1)
            } catch (_: Exception) {}
        }

        val effectiveCharset = if (rawText != null) charset else StandardCharsets.ISO_8859_1
        val model = parseStream(name, bytes.inputStream(), effectiveCharset)
        val updatedReport = model.debugReport?.replace("File size:\n0 bytes", "File size:\n$fileSize bytes") ?: model.debugReport
        return model.copy(fileSize = fileSize, debugReport = updatedReport, rawContent = rawText)
    }

    /**
     * Serializes DxfModel entities into standard AutoCAD DXF ASCII format.
     */
    fun generateDxfText(model: DxfModel): String {
        val sb = StringBuilder(65536)
        sb.append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        sb.append("0\nSECTION\n2\nENTITIES\n")
        for (e in model.entities) {
            when (e) {
                is DxfEntity.Line -> {
                    sb.append("0\nLINE\n8\n${e.layer}\n")
                    sb.append("10\n${e.start.x}\n20\n${e.start.y}\n30\n${e.start.z}\n")
                    sb.append("11\n${e.end.x}\n21\n${e.end.y}\n31\n${e.end.z}\n")
                }
                is DxfEntity.Circle -> {
                    sb.append("0\nCIRCLE\n8\n${e.layer}\n")
                    sb.append("10\n${e.center.x}\n20\n${e.center.y}\n30\n${e.center.z}\n")
                    sb.append("40\n${e.radius}\n")
                }
                is DxfEntity.Arc -> {
                    sb.append("0\nARC\n8\n${e.layer}\n")
                    sb.append("10\n${e.center.x}\n20\n${e.center.y}\n30\n${e.center.z}\n")
                    sb.append("40\n${e.radius}\n")
                    sb.append("50\n${e.startAngleDeg}\n51\n${e.endAngleDeg}\n")
                }
                is DxfEntity.Polyline -> {
                    sb.append("0\nLWPOLYLINE\n8\n${e.layer}\n")
                    sb.append("90\n${e.points.size}\n")
                    sb.append("70\n${if (e.isClosed) 1 else 0}\n")
                    for (p in e.points) {
                        sb.append("10\n${p.x}\n20\n${p.y}\n")
                    }
                }
                is DxfEntity.TextEntity -> {
                    sb.append("0\nTEXT\n8\n${e.layer}\n")
                    sb.append("10\n${e.position.x}\n20\n${e.position.y}\n30\n${e.position.z}\n")
                    sb.append("40\n${e.height}\n")
                    sb.append("50\n${e.rotationDeg}\n")
                    sb.append("1\n${e.text}\n")
                }
                is DxfEntity.Solid -> {
                    sb.append("0\nSOLID\n8\n${e.layer}\n")
                    if (e.points.size >= 3) {
                        sb.append("10\n${e.points[0].x}\n20\n${e.points[0].y}\n30\n${e.points[0].z}\n")
                        sb.append("11\n${e.points[1].x}\n21\n${e.points[1].y}\n31\n${e.points[1].z}\n")
                        sb.append("12\n${e.points[2].x}\n22\n${e.points[2].y}\n32\n${e.points[2].z}\n")
                        val p4 = if (e.points.size >= 4) e.points[3] else e.points[2]
                        sb.append("13\n${p4.x}\n23\n${p4.y}\n33\n${p4.z}\n")
                    }
                }
                else -> {
                    // Lines fallback
                }
            }
        }
        sb.append("0\nENDSEC\n0\nEOF\n")
        return sb.toString()
    }

    /**
     * Expands a polyline segment with bulge arcs mathematically.
     */
    fun expandPolylineWithBulges(vertices: List<RawVertex>, isClosed: Boolean): List<Vector3D> {
        if (vertices.isEmpty()) return emptyList()
        if (vertices.size == 1) return listOf(Vector3D(vertices[0].x, vertices[0].y, vertices[0].z))

        val result = ArrayList<Vector3D>(vertices.size * 4)
        val count = if (isClosed) vertices.size else vertices.size - 1

        for (i in 0 until count) {
            val v1 = vertices[i]
            val v2 = vertices[(i + 1) % vertices.size]
            val b = v1.bulge

            result.add(Vector3D(v1.x, v1.y, v1.z))

            // If segment has a curved bulge, interpolate mathematically exact circular arc
            if (abs(b) >= 1e-4f) {
                val dx = (v2.x - v1.x).toDouble()
                val dy = (v2.y - v1.y).toDouble()
                val chord = hypot(dx, dy)

                if (chord > 1e-5) {
                    val ux = dx / chord
                    val uy = dy / chord
                    // Normal vector pointing to the LEFT of the segment
                    val nx = -uy
                    val ny = ux

                    val mx = (v1.x + v2.x) * 0.5
                    val my = (v1.y + v2.y) * 0.5

                    val theta = 4.0 * kotlin.math.atan(b.toDouble())
                    val halfTheta = theta * 0.5
                    val sinHalfTheta = sin(halfTheta)
                    val cosHalfTheta = cos(halfTheta)

                    val factor = chord / (2.0 * sinHalfTheta)
                    val steps = max(12, (abs(theta) / (Math.PI / 24.0)).toInt()).coerceAtMost(64)

                    for (s in 1 until steps) {
                        val alpha = s.toDouble() / steps
                        val gamma = (alpha - 0.5) * theta
                        val along = factor * sin(gamma)
                        val height = factor * (cos(gamma) - cosHalfTheta)

                        val px = (mx + ux * along + nx * height).toFloat()
                        val py = (my + uy * along + ny * height).toFloat()
                        val pz = v1.z + (v2.z - v1.z) * alpha.toFloat()
                        result.add(Vector3D(px, py, pz))
                    }
                }
            }
        }

        if (!isClosed) {
            val last = vertices.last()
            result.add(Vector3D(last.x, last.y, last.z))
        }

        return result
    }

    /**
     * Interpolates AutoCAD SPLINE vertices smoothly.
     */
    fun interpolateSpline(points: List<Vector3D>, isClosed: Boolean): List<Vector3D> {
        if (points.size <= 2) return points
        if (points.size >= 16) return points

        var current = points
        repeat(3) {
            val next = ArrayList<Vector3D>(current.size * 2)
            val n = current.size
            if (isClosed) {
                for (i in 0 until n) {
                    val p0 = current[i]
                    val p1 = current[(i + 1) % n]
                    next.add(Vector3D(0.75f * p0.x + 0.25f * p1.x, 0.75f * p0.y + 0.25f * p1.y, 0.75f * p0.z + 0.25f * p1.z))
                    next.add(Vector3D(0.25f * p0.x + 0.75f * p1.x, 0.25f * p0.y + 0.75f * p1.y, 0.25f * p0.z + 0.75f * p1.z))
                }
            } else {
                next.add(current.first())
                for (i in 0 until n - 1) {
                    val p0 = current[i]
                    val p1 = current[i + 1]
                    next.add(Vector3D(0.75f * p0.x + 0.25f * p1.x, 0.75f * p0.y + 0.25f * p1.y, 0.75f * p0.z + 0.25f * p1.z))
                    next.add(Vector3D(0.25f * p0.x + 0.75f * p1.x, 0.25f * p0.y + 0.75f * p1.y, 0.25f * p0.z + 0.75f * p1.z))
                }
                next.add(current.last())
            }
            current = next
        }
        return current
    }

    private fun transformBlockPoint(
        p: Vector3D,
        baseX: Float,
        baseY: Float,
        baseZ: Float,
        insertX: Float,
        insertY: Float,
        insertZ: Float,
        scaleX: Float,
        scaleY: Float,
        scaleZ: Float,
        cosR: Float,
        sinR: Float
    ): Vector3D {
        val relX = p.x - baseX
        val relY = p.y - baseY
        val relZ = p.z - baseZ

        val sx = relX * scaleX
        val sy = relY * scaleY
        val sz = relZ * scaleZ

        val rx = sx * cosR - sy * sinR
        val ry = sx * sinR + sy * cosR

        return Vector3D(rx + insertX, ry + insertY, sz + insertZ)
    }

    fun computeRobustBounds(entities: List<DxfEntity>): BoundingBox3D {
        if (entities.isEmpty()) {
            return BoundingBox3D(0f, 100f, 0f, 100f, 0f, 0f)
        }

        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        fun updatePoint(x: Float, y: Float, z: Float = 0f) {
            if (x.isFinite() && abs(x) < 1e9f) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
            }
            if (y.isFinite() && abs(y) < 1e9f) {
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            if (z.isFinite() && abs(z) < 1e9f) {
                if (z < minZ) minZ = z
                if (z > maxZ) maxZ = z
            }
        }

        for (e in entities) {
            when (e) {
                is DxfEntity.Line -> {
                    updatePoint(e.start.x, e.start.y, e.start.z)
                    updatePoint(e.end.x, e.end.y, e.end.z)
                }
                is DxfEntity.Circle -> {
                    updatePoint(e.center.x - e.radius, e.center.y - e.radius, e.center.z)
                    updatePoint(e.center.x + e.radius, e.center.y + e.radius, e.center.z)
                }
                is DxfEntity.Arc -> {
                    updatePoint(e.center.x - e.radius, e.center.y - e.radius, e.center.z)
                    updatePoint(e.center.x + e.radius, e.center.y + e.radius, e.center.z)
                }
                is DxfEntity.Polyline -> {
                    for (p in e.points) updatePoint(p.x, p.y, p.z)
                }
                is DxfEntity.TextEntity -> {
                    updatePoint(e.position.x, e.position.y, e.position.z)
                    updatePoint(e.position.x + e.height * max(1, e.text.length) * 0.7f, e.position.y + e.height, e.position.z)
                }
                is DxfEntity.Ellipse -> {
                    val r = e.majorAxis.length()
                    updatePoint(e.center.x - r, e.center.y - r, e.center.z)
                    updatePoint(e.center.x + r, e.center.y + r, e.center.z)
                }
                is DxfEntity.Spline -> {
                    for (p in e.controlPoints) updatePoint(p.x, p.y, p.z)
                }
                is DxfEntity.Hatch -> {
                    for (loop in e.boundaryLoops) {
                        for (p in loop) updatePoint(p.x, p.y, p.z)
                    }
                }
                is DxfEntity.Dimension -> {
                    updatePoint(e.defPoint1.x, e.defPoint1.y, e.defPoint1.z)
                    updatePoint(e.defPoint2.x, e.defPoint2.y, e.defPoint2.z)
                    updatePoint(e.textPoint.x, e.textPoint.y, e.textPoint.z)
                }
                is DxfEntity.Leader -> {
                    for (p in e.vertices) updatePoint(p.x, p.y, p.z)
                }
                is DxfEntity.Solid -> {
                    for (p in e.points) updatePoint(p.x, p.y, p.z)
                }
            }
        }

        if (minX == Float.POSITIVE_INFINITY || maxX == Float.NEGATIVE_INFINITY) {
            minX = 0f; maxX = 100f
        }
        if (minY == Float.POSITIVE_INFINITY || maxY == Float.NEGATIVE_INFINITY) {
            minY = 0f; maxY = 100f
        }
        if (minZ == Float.POSITIVE_INFINITY || maxZ == Float.NEGATIVE_INFINITY) {
            minZ = 0f; maxZ = 0f
        }

        // Prevent degenerate 0-dimension bounds
        if (abs(maxX - minX) < 1e-4f) {
            minX -= 5f; maxX += 5f
        }
        if (abs(maxY - minY) < 1e-4f) {
            minY -= 5f; maxY += 5f
        }

        return BoundingBox3D(minX, maxX, minY, maxY, minZ, maxZ)
    }
}
