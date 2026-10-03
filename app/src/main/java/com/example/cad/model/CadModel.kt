package com.example.cad.model

import android.net.Uri
import com.example.ui.render3d.Vector3D

/**
 * Normalized 3D Bounding Box for CAD drawings.
 */
data class CadBounds(
    val minX: Float = 0f,
    val maxX: Float = 0f,
    val minY: Float = 0f,
    val maxY: Float = 0f,
    val minZ: Float = 0f,
    val maxZ: Float = 0f
) {
    val width: Float get() = (maxX - minX).coerceAtLeast(0.001f)
    val height: Float get() = (maxY - minY).coerceAtLeast(0.001f)
    val depth: Float get() = (maxZ - minZ).coerceAtLeast(0f)
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
    val centerZ: Float get() = (minZ + maxZ) / 2f
}

/**
 * CAD Drawing Units based on standard AutoCAD $INSUNITS.
 */
enum class CadUnits(val code: Int, val symbol: String, val scaleToMm: Float) {
    UNITLESS(0, "unit", 1.0f),
    INCHES(1, "in", 25.4f),
    FEET(2, "ft", 304.8f),
    MILES(3, "mi", 1609344.0f),
    MILLIMETERS(4, "mm", 1.0f),
    CENTIMETERS(5, "cm", 10.0f),
    METERS(6, "m", 1000.0f),
    KILOMETERS(7, "km", 1000000.0f),
    MICROINCHES(8, "µin", 0.0000254f),
    MILS(9, "mil", 0.0254f),
    YARDS(10, "yd", 914.4f),
    ANGSTROMS(11, "Å", 1e-7f),
    NANOMETERS(12, "nm", 1e-6f),
    MICRONS(13, "µm", 0.001f),
    DECIMETERS(14, "dm", 100.0f);

    companion object {
        fun fromCode(code: Int): CadUnits = values().firstOrNull { it.code == code } ?: MILLIMETERS
    }
}

/**
 * CAD Layer representation.
 */
data class CadLayer(
    val name: String,
    val color: Int = 0xFFFFFFFF.toInt(),
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val isCurrent: Boolean = false,
    val linetype: String = "CONTINUOUS",
    val lineweight: Float = 1.0f
)

/**
 * Common attributes for all CAD entities.
 */
sealed class CadEntity {
    abstract val objectId: Long
    abstract val layer: String
    abstract val color: Int? // Null = ByLayer
    abstract val linetype: String?
    abstract val lineweight: Float
    abstract val isVisible: Boolean

    data class Point(
        override val objectId: Long,
        override val layer: String,
        val point: Vector3D,
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()

    data class Line(
        override val objectId: Long,
        override val layer: String,
        val start: Vector3D,
        val end: Vector3D,
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()

    data class Circle(
        override val objectId: Long,
        override val layer: String,
        val center: Vector3D,
        val radius: Float,
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()

    data class Arc(
        override val objectId: Long,
        override val layer: String,
        val center: Vector3D,
        val radius: Float,
        val startAngleDeg: Float,
        val endAngleDeg: Float,
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity() {
        val sweepAngleDeg: Float
            get() {
                var diff = endAngleDeg - startAngleDeg
                while (diff <= 0f) diff += 360f
                return diff
            }
    }

    data class Ellipse(
        override val objectId: Long,
        override val layer: String,
        val center: Vector3D,
        val majorAxisVector: Vector3D,
        val axisRatio: Float,
        val startParam: Float = 0f,
        val endParam: Float = (2.0 * Math.PI).toFloat(),
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()

    data class Polyline(
        override val objectId: Long,
        override val layer: String,
        val vertices: List<Vector3D>,
        val isClosed: Boolean = false,
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()

    data class Spline(
        override val objectId: Long,
        override val layer: String,
        val controlPoints: List<Vector3D>,
        val fitPoints: List<Vector3D> = emptyList(),
        val degree: Int = 3,
        val isClosed: Boolean = false,
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()

    data class Text(
        override val objectId: Long,
        override val layer: String,
        val text: String,
        val position: Vector3D,
        val height: Float = 2.5f,
        val rotationDeg: Float = 0f,
        val styleName: String = "STANDARD",
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()

    data class MText(
        override val objectId: Long,
        override val layer: String,
        val text: String,
        val position: Vector3D,
        val height: Float = 2.5f,
        val rotationDeg: Float = 0f,
        val rectWidth: Float = 0f,
        val styleName: String = "STANDARD",
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()

    data class BlockReference(
        override val objectId: Long,
        override val layer: String,
        val blockName: String,
        val insertionPoint: Vector3D,
        val scale: Vector3D = Vector3D(1f, 1f, 1f),
        val rotationDeg: Float = 0f,
        val attributes: Map<String, String> = emptyMap(),
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()

    data class Dimension(
        override val objectId: Long,
        override val layer: String,
        val definitionPoint: Vector3D,
        val textMiddlePoint: Vector3D,
        val text: String = "",
        val measurement: Double = 0.0,
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()

    data class Leader(
        override val objectId: Long,
        override val layer: String,
        val points: List<Vector3D>,
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()

    data class Hatch(
        override val objectId: Long,
        override val layer: String,
        val patternName: String,
        val boundaryLoops: List<List<Vector3D>>,
        val solidFill: Boolean = false,
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()

    data class Face3D(
        override val objectId: Long,
        override val layer: String,
        val corners: List<Vector3D>,
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()

    data class Solid(
        override val objectId: Long,
        override val layer: String,
        val points: List<Vector3D>,
        override val color: Int? = null,
        override val linetype: String? = null,
        override val lineweight: Float = 1.0f,
        override val isVisible: Boolean = true
    ) : CadEntity()
}

/**
 * Block definition storing cached reusable geometry.
 */
data class CadBlock(
    val name: String,
    val basePoint: Vector3D = Vector3D(0f, 0f, 0f),
    val entities: List<CadEntity> = emptyList(),
    val isAnonymous: Boolean = false,
    val isXref: Boolean = false
)

/**
 * External Reference (XREF).
 */
data class CadXref(
    val name: String,
    val path: String,
    val isLoaded: Boolean = false,
    val resolvedUri: Uri? = null,
    val errorMessage: String? = null
)

/**
 * Linetype definition.
 */
data class CadLinetype(
    val name: String,
    val description: String = "",
    val pattern: List<Float> = emptyList()
)

/**
 * Text Style definition.
 */
data class CadTextStyle(
    val name: String,
    val fontFileName: String = "",
    val bigFontFileName: String = "",
    val textSize: Float = 0f
)

/**
 * Viewport / Camera state.
 */
data class CadViewport(
    val width: Int = 1080,
    val height: Int = 1920,
    val cameraX: Float = 0f,
    val cameraY: Float = 0f,
    val zoom: Float = 1.0f,
    val rotationDeg: Float = 0f
)

/**
 * Text search query result match.
 */
data class CadTextMatch(
    val text: String,
    val position: Vector3D,
    val entityId: Long,
    val layerName: String
)

/**
 * Measurement Results.
 */
data class DistanceResult(
    val distance: Double,
    val deltaX: Double,
    val deltaY: Double,
    val deltaZ: Double,
    val angleDeg: Double,
    val unitSymbol: String
)

data class AreaResult(
    val area: Double,
    val perimeter: Double,
    val unitSymbol: String,
    val pointCount: Int
)

/**
 * Structured CAD error types.
 */
sealed class CadError : Throwable() {
    data class UnsupportedVersion(val version: String, override val message: String) : CadError()
    data class CorruptedFile(override val message: String) : CadError()
    data class MissingXref(val xrefName: String, override val message: String) : CadError()
    data class MissingFont(val fontName: String, override val message: String) : CadError()
    data class UnsupportedEntity(val entityName: String, override val message: String) : CadError()
    data class OutOfMemory(override val message: String) : CadError()
    data class IoError(override val message: String) : CadError()
}

/**
 * Complete normalized CAD Document model.
 */
data class CadDocument(
    val id: Long,
    val fileName: String,
    val uriString: String? = null,
    val format: String = "DXF", // "DWG" or "DXF"
    val dwgVersion: String? = null,
    val fileSize: Long = 0L,
    val units: CadUnits = CadUnits.MILLIMETERS,
    val bounds: CadBounds = CadBounds(),
    val layers: Map<String, CadLayer> = emptyMap(),
    val entities: List<CadEntity> = emptyList(),
    val blocks: Map<String, CadBlock> = emptyMap(),
    val xrefs: List<CadXref> = emptyList(),
    val textStyles: Map<String, CadTextStyle> = emptyMap(),
    val linetypes: Map<String, CadLinetype> = emptyMap(),
    val rawDxfContent: String? = null,
    val openDurationMs: Long = 0L
) {
    val visibleEntityCount: Int
        get() = entities.count { entity ->
            val lyr = layers[entity.layer]
            (lyr?.isVisible ?: true) && entity.isVisible
        }
}
