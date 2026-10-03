package com.example.cad.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.cad.model.*
import com.example.cad.native.CadNativeBridge
import com.example.data.parser.DxfParser
import com.example.data.parser.DwgParser
import com.example.ui.render3d.Vector3D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

/**
 * Universal CAD Engine Abstraction Interface.
 *
 * Designed for modular plug-and-play CAD SDK backends:
 * - ODA Drawings SDK (Open Design Alliance) for commercial DWG compliance
 * - LibreDWG / libdxfrw open-source C++ NDK engine
 * - Built-in High-Precision Standalone Vector Engine with zero dependencies
 */
interface CadEngine {
    val engineName: String
    val isNativeAccelerated: Boolean

    suspend fun open(context: Context, uri: Uri, name: String? = null): CadDocument
    fun close(documentId: Long)
    fun getDocument(documentId: Long): CadDocument?
    fun getLayers(documentId: Long): List<CadLayer>
    fun setLayerVisibility(documentId: Long, layerName: String, isVisible: Boolean)
    fun getEntities(documentId: Long): List<CadEntity>
    fun getDrawingBounds(documentId: Long): CadBounds
    fun findText(documentId: Long, query: String): List<CadTextMatch>
    fun measureDistance(documentId: Long, p1: Vector3D, p2: Vector3D): DistanceResult
    fun measureArea(documentId: Long, points: List<Vector3D>): AreaResult
}

/**
 * Factory providing the active CadEngine backend.
 */
object CadEngineFactory {
    fun createEngine(): CadEngine {
        return DefaultCadEngine
    }
}

/**
 * Production implementation of [CadEngine] orchestrating NDK C++ native bridge
 * and high-performance offline vector parser.
 */
object DefaultCadEngine : CadEngine {
    private const val TAG = "DefaultCadEngine"
    private val nextDocId = AtomicLong(1L)
    private val openDocuments = ConcurrentHashMap<Long, CadDocument>()

    override val engineName: String
        get() = if (CadNativeBridge.isAvailable()) "Native CAD C++ Engine (libcadconverter)" else "High-Precision Vector CAD Engine"

    override val isNativeAccelerated: Boolean
        get() = CadNativeBridge.isAvailable()

    override suspend fun open(context: Context, uri: Uri, name: String?): CadDocument = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val docId = nextDocId.getAndIncrement()

        val fileName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "drawing.dxf"
        val isDwg = fileName.endsWith(".dwg", ignoreCase = true)

        var fileSize = 0L
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}

        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading bytes from $uri: ${e.message}")
            ByteArray(0)
        }

        if (fileSize <= 0L) fileSize = bytes.size.toLong()

        // 1. Try Native JNI Engine if available
        var nativeHandle: Long = 0L
        if (CadNativeBridge.isAvailable()) {
            try {
                val tempFile = File(context.cacheDir, "cad_temp_${docId}_${fileName}")
                tempFile.writeBytes(bytes)
                nativeHandle = CadNativeBridge.nativeOpenDocument(tempFile.absolutePath, isDwg)
                tempFile.delete()
            } catch (e: Throwable) {
                Log.w(TAG, "Native bridge open failed, using managed parser: ${e.message}")
            }
        }

        // 2. Parse Drawing Entities and Layers
        val doc: CadDocument = if (isDwg) {
            val dwgModel = DwgParser.parseBytes(
                fileName = fileName,
                uriString = uri.toString(),
                mimeType = "image/vnd.dwg",
                fileSize = fileSize,
                bytes = bytes
            )
            convertDxfModelToCadDocument(docId, dwgModel, isDwg = true, openDuration = System.currentTimeMillis() - startTime)
        } else {
            val dxfModel = DxfParser.parseBytes(
                name = fileName,
                bytes = bytes,
                uriString = uri.toString(),
                mimeType = "image/vnd.dxf",
                fileSize = fileSize
            )
            convertDxfModelToCadDocument(docId, dxfModel, isDwg = false, openDuration = System.currentTimeMillis() - startTime)
        }

        openDocuments[docId] = doc
        Log.i(TAG, "Document loaded successfully: '${doc.fileName}' (${doc.entities.size} entities, ${doc.layers.size} layers in ${doc.openDurationMs}ms)")
        doc
    }

    override fun close(documentId: Long) {
        val doc = openDocuments.remove(documentId)
        if (doc != null && CadNativeBridge.isAvailable()) {
            try {
                CadNativeBridge.nativeCloseDocument(documentId)
            } catch (_: Throwable) {}
        }
        Log.d(TAG, "Document $documentId closed.")
    }

    override fun getDocument(documentId: Long): CadDocument? = openDocuments[documentId]

    override fun getLayers(documentId: Long): List<CadLayer> {
        return openDocuments[documentId]?.layers?.values?.toList() ?: emptyList()
    }

    override fun setLayerVisibility(documentId: Long, layerName: String, isVisible: Boolean) {
        val doc = openDocuments[documentId] ?: return
        val currentLayer = doc.layers[layerName] ?: return
        val updatedLayer = currentLayer.copy(isVisible = isVisible)
        val updatedLayers = doc.layers.toMutableMap()
        updatedLayers[layerName] = updatedLayer

        openDocuments[documentId] = doc.copy(layers = updatedLayers)
    }

    override fun getEntities(documentId: Long): List<CadEntity> {
        return openDocuments[documentId]?.entities ?: emptyList()
    }

    override fun getDrawingBounds(documentId: Long): CadBounds {
        return openDocuments[documentId]?.bounds ?: CadBounds()
    }

    override fun findText(documentId: Long, query: String): List<CadTextMatch> {
        val doc = openDocuments[documentId] ?: return emptyList()
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<CadTextMatch>()
        val lowerQuery = query.trim().lowercase()

        for (e in doc.entities) {
            when (e) {
                is CadEntity.Text -> {
                    if (e.text.lowercase().contains(lowerQuery)) {
                        results.add(CadTextMatch(e.text, e.position, e.objectId, e.layer))
                    }
                }
                is CadEntity.MText -> {
                    if (e.text.lowercase().contains(lowerQuery)) {
                        results.add(CadTextMatch(e.text, e.position, e.objectId, e.layer))
                    }
                }
                is CadEntity.Dimension -> {
                    if (e.text.lowercase().contains(lowerQuery)) {
                        results.add(CadTextMatch(e.text, e.definitionPoint, e.objectId, e.layer))
                    }
                }
                is CadEntity.BlockReference -> {
                    for ((_, value) in e.attributes) {
                        if (value.lowercase().contains(lowerQuery)) {
                            results.add(CadTextMatch(value, e.insertionPoint, e.objectId, e.layer))
                        }
                    }
                }
                else -> {}
            }
        }
        return results
    }

    override fun measureDistance(documentId: Long, p1: Vector3D, p2: Vector3D): DistanceResult {
        val doc = openDocuments[documentId]
        val unit = doc?.units ?: CadUnits.MILLIMETERS

        val dx = (p2.x - p1.x).toDouble()
        val dy = (p2.y - p1.y).toDouble()
        val dz = (p2.z - p1.z).toDouble()
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        val angleDeg = Math.toDegrees(atan2(dy, dx))

        return DistanceResult(
            distance = dist,
            deltaX = dx,
            deltaY = dy,
            deltaZ = dz,
            angleDeg = if (angleDeg < 0) angleDeg + 360.0 else angleDeg,
            unitSymbol = unit.symbol
        )
    }

    override fun measureArea(documentId: Long, points: List<Vector3D>): AreaResult {
        val doc = openDocuments[documentId]
        val unit = doc?.units ?: CadUnits.MILLIMETERS

        if (points.size < 3) {
            return AreaResult(0.0, 0.0, unit.symbol, points.size)
        }

        // Shoelace algorithm for 2D polygon area
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

        val area = abs(areaSum) / 2.0
        return AreaResult(
            area = area,
            perimeter = perimeterSum,
            unitSymbol = unit.symbol,
            pointCount = points.size
        )
    }

    private fun convertDxfModelToCadDocument(
        docId: Long,
        model: com.example.data.parser.DxfModel,
        isDwg: Boolean,
        openDuration: Long
    ): CadDocument {
        var entityIdSeq = 1L
        val cadEntities = mutableListOf<CadEntity>()
        val layerMap = LinkedHashMap<String, CadLayer>()

        for (lyrName in model.layers) {
            layerMap[lyrName] = CadLayer(
                name = lyrName,
                color = 0xFFFFFFFF.toInt(),
                isVisible = true,
                isLocked = false,
                isCurrent = lyrName == "0"
            )
        }

        if (!layerMap.containsKey("0")) {
            layerMap["0"] = CadLayer("0", 0xFFFFFFFF.toInt(), isVisible = true, isCurrent = true)
        }

        for (e in model.entities) {
            val objId = entityIdSeq++
            when (e) {
                is com.example.data.parser.DxfEntity.Line -> {
                    cadEntities.add(
                        CadEntity.Line(
                            objectId = objId,
                            layer = e.layer,
                            start = e.start,
                            end = e.end,
                            color = e.color
                        )
                    )
                }
                is com.example.data.parser.DxfEntity.Circle -> {
                    cadEntities.add(
                        CadEntity.Circle(
                            objectId = objId,
                            layer = e.layer,
                            center = e.center,
                            radius = e.radius,
                            color = e.color
                        )
                    )
                }
                is com.example.data.parser.DxfEntity.Arc -> {
                    cadEntities.add(
                        CadEntity.Arc(
                            objectId = objId,
                            layer = e.layer,
                            center = e.center,
                            radius = e.radius,
                            startAngleDeg = e.startAngleDeg,
                            endAngleDeg = e.endAngleDeg,
                            color = e.color
                        )
                    )
                }
                is com.example.data.parser.DxfEntity.Polyline -> {
                    cadEntities.add(
                        CadEntity.Polyline(
                            objectId = objId,
                            layer = e.layer,
                            vertices = e.points,
                            isClosed = e.isClosed,
                            color = e.color
                        )
                    )
                }
                is com.example.data.parser.DxfEntity.Spline -> {
                    cadEntities.add(
                        CadEntity.Spline(
                            objectId = objId,
                            layer = e.layer,
                            controlPoints = e.controlPoints,
                            isClosed = e.isClosed,
                            color = e.color
                        )
                    )
                }
                is com.example.data.parser.DxfEntity.TextEntity -> {
                    cadEntities.add(
                        CadEntity.Text(
                            objectId = objId,
                            layer = e.layer,
                            text = e.text,
                            position = e.position,
                            height = e.height,
                            rotationDeg = e.rotationDeg,
                            color = e.color
                        )
                    )
                }
                is com.example.data.parser.DxfEntity.Dimension -> {
                    cadEntities.add(
                        CadEntity.Dimension(
                            objectId = objId,
                            layer = e.layer,
                            definitionPoint = e.defPoint1,
                            textMiddlePoint = e.textPoint,
                            text = e.text,
                            color = e.color
                        )
                    )
                }
                is com.example.data.parser.DxfEntity.Leader -> {
                    cadEntities.add(
                        CadEntity.Leader(
                            objectId = objId,
                            layer = e.layer,
                            points = e.vertices,
                            color = e.color
                        )
                    )
                }
                is com.example.data.parser.DxfEntity.Hatch -> {
                    cadEntities.add(
                        CadEntity.Hatch(
                            objectId = objId,
                            layer = e.layer,
                            patternName = "SOLID",
                            boundaryLoops = e.boundaryLoops,
                            solidFill = e.isSolid,
                            color = e.color
                        )
                    )
                }
                is com.example.data.parser.DxfEntity.Solid -> {
                    cadEntities.add(
                        CadEntity.Solid(
                            objectId = objId,
                            layer = e.layer,
                            points = e.points,
                            color = e.color
                        )
                    )
                }
                is com.example.data.parser.DxfEntity.Ellipse -> {
                    cadEntities.add(
                        CadEntity.Ellipse(
                            objectId = objId,
                            layer = e.layer,
                            center = e.center,
                            majorAxisVector = e.majorAxis,
                            axisRatio = e.axisRatio,
                            startParam = e.startParam,
                            endParam = e.endParam,
                            color = e.color
                        )
                    )
                }
            }
        }

        val bounds = CadBounds(
            minX = model.bounds.minX,
            maxX = model.bounds.maxX,
            minY = model.bounds.minY,
            maxY = model.bounds.maxY,
            minZ = model.bounds.minZ,
            maxZ = model.bounds.maxZ
        )

        return CadDocument(
            id = docId,
            fileName = model.fileName,
            format = if (isDwg) "DWG" else "DXF",
            dwgVersion = model.dwgVersion ?: if (isDwg) "DWG 2018" else "AutoCAD DXF R2000",
            fileSize = model.fileSize,
            units = CadUnits.MILLIMETERS,
            bounds = bounds,
            layers = layerMap,
            entities = cadEntities,
            rawDxfContent = model.getRawOrGeneratedDxf(),
            openDurationMs = openDuration
        )
    }
}
