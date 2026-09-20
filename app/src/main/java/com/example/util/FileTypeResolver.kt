package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class CadFileType(val displayName: String, val badge: String) {
    STL("3D STL Model", "STL"),
    OBJ("Wavefront 3D OBJ", "OBJ"),
    RLF("ArtCAM 3D Relief", "RLF"),
    ART("ArtCAM 3D Model", "ART"),
    XML3D("Dassault 3DXML", "3DXML"),
    ASPIRE_3D("Vectric Aspire 3D", "ASPIRE"),
    DXF("AutoCAD DXF Drawing", "DXF"),
    DWG("AutoCAD DWG Drawing", "DWG"),
    TOOLPATH_GCODE("CNC Toolpath Program", "G-CODE"),
    EXCEL("Excel Spreadsheet", "EXCEL"),
    PDF("PDF Document", "PDF");

    val is3DModel: Boolean
        get() = this in listOf(STL, OBJ, RLF, ART, XML3D, ASPIRE_3D)
}

data class ResolvedFileInfo(
    val uri: Uri,
    val fileName: String,
    val fileType: CadFileType
)

object FileTypeResolver {

    /**
     * Resolves a file from an incoming Android Intent (e.g., tapped in WhatsApp,
     * shared via Android share sheet, or opened in external file manager).
     */
    fun resolveFromIntent(context: Context, intent: Intent): ResolvedFileInfo? {
        val uri: Uri = when {
            intent.data != null -> intent.data!!
            intent.hasExtra(Intent.EXTRA_STREAM) -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            intent.clipData != null && intent.clipData!!.itemCount > 0 -> {
                intent.clipData!!.getItemAt(0).uri
            }
            else -> null
        } ?: return null

        return resolve(context, uri, intent.type)
    }

    /**
     * Resolves display name and accurate CAD / 3D / Relief / Toolpath / Excel / PDF type for a given Content or File URI.
     */
    fun resolve(context: Context, uri: Uri, mimeType: String? = null): ResolvedFileInfo {
        var displayName = queryDisplayName(context, uri)
        if (displayName.isNullOrBlank()) {
            displayName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "whatsapp_file"
        }

        val detectedType = detectType(context, uri, displayName, mimeType)

        // Ensure displayName has a friendly and accurate extension matching the detected format
        val finalFileName = ensureProperExtension(displayName, detectedType)

        return ResolvedFileInfo(
            uri = uri,
            fileName = finalFileName,
            fileType = detectedType
        )
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx != -1) {
                        val size = cursor.getLong(idx)
                        if (size > 0L) return size
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                val len = afd.length
                if (len > 0L) return len
            }
        } catch (_: Exception) {}

        return -1L
    }

    /**
     * Identifies the file type using filename extension, MIME type, and deep byte inspection.
     */
    fun detectType(context: Context, uri: Uri, fileName: String, mimeType: String? = null): CadFileType {
        val lowerName = fileName.lowercase()

        // 1. Fast path: Filename extension check
        if (lowerName.endsWith(".pdf")) return CadFileType.PDF
        if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") ||
            lowerName.endsWith(".csv") || lowerName.endsWith(".tsv")) {
            return CadFileType.EXCEL
        }
        if (lowerName.endsWith(".stl")) return CadFileType.STL
        if (lowerName.endsWith(".obj")) return CadFileType.OBJ
        if (lowerName.endsWith(".rlf")) return CadFileType.RLF
        if (lowerName.endsWith(".art")) return CadFileType.ART
        if (lowerName.endsWith(".3dxml")) return CadFileType.XML3D
        if (lowerName.endsWith(".crv") || lowerName.endsWith(".crv3d") ||
            lowerName.endsWith(".v3m") || lowerName.endsWith(".3dclip")) {
            return CadFileType.ASPIRE_3D
        }
        if (lowerName.endsWith(".dxf")) return CadFileType.DXF
        if (lowerName.endsWith(".dwg")) return CadFileType.DWG
        if (lowerName.endsWith(".tap") || lowerName.endsWith(".nc") ||
            lowerName.endsWith(".gcode") || lowerName.endsWith(".cnc") ||
            lowerName.endsWith(".ngc") || lowerName.endsWith(".din") ||
            lowerName.endsWith(".apt")) {
            return CadFileType.TOOLPATH_GCODE
        }

        // 2. MIME type inspection
        mimeType?.lowercase()?.let { mime ->
            when {
                mime == "application/pdf" || mime.contains("pdf") -> return CadFileType.PDF
                mime.contains("spreadsheet") || mime.contains("excel") || mime.contains("sheet") ||
                        mime.contains("csv") || mime.contains("comma-separated") -> return CadFileType.EXCEL
                mime.contains("model/stl") || mime == "application/sla" -> return CadFileType.STL
                mime.contains("model/obj") || mime.contains("wavefront") -> return CadFileType.OBJ
                mime.contains("3dxml") -> return CadFileType.XML3D
                mime.contains("artcam") || mime.contains("rlf") -> return CadFileType.RLF
                mime.contains("dxf") -> return CadFileType.DXF
                mime.contains("dwg") || mime.contains("autocad") -> return CadFileType.DWG
                mime.contains("gcode") -> return CadFileType.TOOLPATH_GCODE
            }
        }

        // 3. Deep Content Header Inspection (Magic bytes)
        // Especially crucial for WhatsApp content URIs like content://.../1928374 without extensions
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(2048)
                val bytesRead = stream.read(buffer)
                if (bytesRead > 0) {
                    val fileSize = queryFileSize(context, uri)
                    val inspectedType = inspectHeaderBytes(buffer.copyOf(bytesRead), fileSize)
                    if (inspectedType != null) {
                        return inspectedType
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. Default fallback: G-Code toolpath
        return CadFileType.TOOLPATH_GCODE
    }

    /**
     * Pure header byte inspector for testing and deep file classification.
     */
    fun inspectHeaderBytes(headerBytes: ByteArray, fileSize: Long = -1L): CadFileType? {
        if (headerBytes.isEmpty()) return null

        // 1. Check PDF header (%PDF-)
        if (headerBytes.size >= 5) {
            val pdfMagic = String(headerBytes, 0, 5, Charsets.US_ASCII)
            if (pdfMagic == "%PDF-" || pdfMagic.startsWith("%PDF")) {
                return CadFileType.PDF
            }
        }

        // 2. Check Excel ZIP (PK\u0003\u0004 with [Content_Types] or xl/ or workbook)
        if (headerBytes.size >= 4 && headerBytes[0] == 0x50.toByte() && headerBytes[1] == 0x4B.toByte()) {
            val sampleAscii = String(headerBytes, 0, minOf(headerBytes.size, 1500), Charsets.US_ASCII)
            if (sampleAscii.contains("xl/", ignoreCase = true) ||
                sampleAscii.contains("workbook", ignoreCase = true) ||
                sampleAscii.contains("spreadsheet", ignoreCase = true)
            ) {
                return CadFileType.EXCEL
            }
            if (sampleAscii.contains("3dxml", ignoreCase = true) || sampleAscii.contains("3DRep", ignoreCase = true)) {
                return CadFileType.XML3D
            }
        }

        // 3. Check Excel OLE2 Compound Binary (.xls): D0 CF 11 E0 A1 B1 1A E1
        if (headerBytes.size >= 8 &&
            headerBytes[0] == 0xD0.toByte() && headerBytes[1] == 0xCF.toByte() &&
            headerBytes[2] == 0x11.toByte() && headerBytes[3] == 0xE0.toByte()
        ) {
            return CadFileType.EXCEL
        }

        // 4. Check AutoCAD DWG magic header (AC10xx or AC)
        if (headerBytes.size >= 6) {
            val tag = String(headerBytes, 0, 6, Charsets.US_ASCII)
            if (tag.startsWith("AC10") || tag.startsWith("AC")) {
                return CadFileType.DWG
            }
        }

        // 5. Check AutoCAD DXF text header (0\nSECTION or contains "SECTION")
        val sampleAscii = String(headerBytes, 0, minOf(headerBytes.size, 1500), Charsets.US_ASCII)
        if (sampleAscii.contains("SECTION") &&
            (sampleAscii.contains("ENTITIES") || sampleAscii.contains("HEADER") || sampleAscii.contains("TABLES") || sampleAscii.contains("BLOCKS") || sampleAscii.contains("EOF"))
        ) {
            return CadFileType.DXF
        }

        // 6. Check 3DXML (XML <Model_3dxml> / <PolygonalRep>)
        if (sampleAscii.contains("<Model_3dxml", ignoreCase = true) ||
            sampleAscii.contains("<PolygonalRep", ignoreCase = true) ||
            (sampleAscii.contains("<Positions>") && sampleAscii.contains("</Positions>"))
        ) {
            return CadFileType.XML3D
        }

        // 7. Check Wavefront OBJ format
        if (isObjContent(sampleAscii)) {
            return CadFileType.OBJ
        }

        // 8. Check STL (Stereolithography 3D Mesh)
        val trimmedSample = sampleAscii.trimStart()
        if (trimmedSample.startsWith("solid", ignoreCase = true) &&
            (sampleAscii.contains("facet", ignoreCase = true) || sampleAscii.contains("endsolid", ignoreCase = true) || sampleAscii.contains("normal", ignoreCase = true))
        ) {
            return CadFileType.STL
        }

        if (headerBytes.size >= 84 && fileSize >= 84L) {
            val byteBuffer = ByteBuffer.wrap(headerBytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN)
            val numTriangles = byteBuffer.int.toLong() and 0xFFFFFFFFL
            val expectedSize = 84L + (numTriangles * 50L)
            if (numTriangles > 0L && (fileSize == expectedSize || (fileSize - 84L) % 50L == 0L)) {
                return CadFileType.STL
            }
        }

        // 9. Check ArtCAM Relief (RLF) or Model (ART)
        if (sampleAscii.contains("Delcam", ignoreCase = true) ||
            sampleAscii.contains("ArtCAM", ignoreCase = true) ||
            sampleAscii.contains("RLF", ignoreCase = true)
        ) {
            return CadFileType.RLF
        }

        // 10. Check Vectric Aspire
        if (sampleAscii.contains("Vectric", ignoreCase = true) ||
            sampleAscii.contains("Aspire", ignoreCase = true) ||
            (sampleAscii.contains("3D Finish", ignoreCase = true) && sampleAscii.contains("Ball Nose", ignoreCase = true))
        ) {
            return CadFileType.ASPIRE_3D
        }

        // 11. Check CSV spreadsheet indicators
        val firstLine = sampleAscii.lines().firstOrNull()?.trim() ?: ""
        if (firstLine.contains(",") && (firstLine.contains("Item") || firstLine.contains("Qty") ||
                    firstLine.contains("Description") || firstLine.contains("Material") || firstLine.contains("Part"))) {
            return CadFileType.EXCEL
        }

        // 12. Check G-Code Toolpath patterns
        val gCodeKeywords = listOf("G0", "G1", "G2", "G3", "G00", "G01", "G02", "G03", "G90", "G91", "M03", "M3", "M05", "M5", "M30", "G17", "G20", "G21")
        val lines = sampleAscii.lines().take(25)
        var gCodeMatchCount = 0
        for (line in lines) {
            val upper = line.trim().uppercase()
            if (upper.startsWith("%") || upper.startsWith("(") || upper.startsWith("O") || upper.startsWith("N")) {
                gCodeMatchCount++
            }
            for (kw in gCodeKeywords) {
                if (upper.contains(kw)) {
                    gCodeMatchCount++
                    break
                }
            }
        }
        if (gCodeMatchCount >= 2) {
            return CadFileType.TOOLPATH_GCODE
        }

        return null
    }

    private fun isObjContent(sample: String): Boolean {
        var vCount = 0
        var fCount = 0
        for (line in sample.lines().take(30)) {
            val t = line.trim()
            if (t.startsWith("v ") || t.startsWith("v\t")) vCount++
            if (t.startsWith("f ") || t.startsWith("f\t")) fCount++
            if (t.startsWith("vn ") || t.startsWith("vt ")) vCount++
        }
        return (vCount >= 2 && fCount >= 1) || vCount >= 4
    }

    private fun ensureProperExtension(fileName: String, type: CadFileType): String {
        val lower = fileName.lowercase()
        return when (type) {
            CadFileType.PDF -> if (lower.endsWith(".pdf")) fileName else "$fileName.pdf"
            CadFileType.EXCEL -> {
                if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv") || lower.endsWith(".tsv")) {
                    fileName
                } else {
                    "$fileName.xlsx"
                }
            }
            CadFileType.STL -> if (lower.endsWith(".stl")) fileName else "$fileName.stl"
            CadFileType.OBJ -> if (lower.endsWith(".obj")) fileName else "$fileName.obj"
            CadFileType.RLF -> if (lower.endsWith(".rlf")) fileName else "$fileName.rlf"
            CadFileType.ART -> if (lower.endsWith(".art")) fileName else "$fileName.art"
            CadFileType.XML3D -> if (lower.endsWith(".3dxml")) fileName else "$fileName.3dxml"
            CadFileType.ASPIRE_3D -> {
                if (lower.endsWith(".crv") || lower.endsWith(".crv3d") || lower.endsWith(".v3m") || lower.endsWith(".3dclip")) {
                    fileName
                } else {
                    "$fileName.crv3d"
                }
            }
            CadFileType.DXF -> if (lower.endsWith(".dxf")) fileName else "$fileName.dxf"
            CadFileType.DWG -> if (lower.endsWith(".dwg")) fileName else "$fileName.dwg"
            CadFileType.TOOLPATH_GCODE -> {
                if (lower.endsWith(".tap") || lower.endsWith(".nc") || lower.endsWith(".gcode") ||
                    lower.endsWith(".cnc") || lower.endsWith(".din") || lower.endsWith(".ngc") || lower.endsWith(".txt")) {
                    fileName
                } else {
                    "$fileName.nc"
                }
            }
        }
    }
}
