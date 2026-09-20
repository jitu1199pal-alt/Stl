package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class CadFileType {
    STL,
    DXF,
    DWG,
    TOOLPATH_GCODE
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
     * Resolves display name and accurate CAD / 3D / Toolpath type for a given Content or File URI.
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
        if (lowerName.endsWith(".stl")) return CadFileType.STL
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
                mime.contains("stl") || mime == "application/sla" -> return CadFileType.STL
                mime.contains("dxf") -> return CadFileType.DXF
                mime.contains("dwg") || mime.contains("autocad") -> return CadFileType.DWG
                mime.contains("gcode") -> return CadFileType.TOOLPATH_GCODE
            }
        }

        // 3. Deep Content Header Inspection (Magic bytes)
        // Especially crucial for WhatsApp content URIs like content://.../1928374 without extensions
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(1024)
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

        // 4. Default fallback: If it ends in .txt or unknown, default to G-Code toolpath
        return CadFileType.TOOLPATH_GCODE
    }

    /**
     * Pure header byte inspector for testing and deep file classification.
     */
    fun inspectHeaderBytes(headerBytes: ByteArray, fileSize: Long = -1L): CadFileType? {
        if (headerBytes.isEmpty()) return null

        // A. Check AutoCAD DWG magic header (AC10xx)
        if (headerBytes.size >= 6) {
            val tag = String(headerBytes, 0, 6, Charsets.US_ASCII)
            if (tag.startsWith("AC10") || tag.startsWith("AC")) {
                return CadFileType.DWG
            }
        }

        // B. Check AutoCAD DXF text header (0\nSECTION or contains "SECTION")
        val sampleAscii = String(headerBytes, 0, minOf(headerBytes.size, 1024), Charsets.US_ASCII)
        if (sampleAscii.contains("SECTION") &&
            (sampleAscii.contains("ENTITIES") || sampleAscii.contains("HEADER") || sampleAscii.contains("TABLES") || sampleAscii.contains("BLOCKS") || sampleAscii.contains("EOF"))
        ) {
            return CadFileType.DXF
        }

        // C. Check STL (Stereolithography 3D Mesh)
        // C1. ASCII STL: starts with "solid"
        val trimmedSample = sampleAscii.trimStart()
        if (trimmedSample.startsWith("solid", ignoreCase = true) &&
            (sampleAscii.contains("facet", ignoreCase = true) || sampleAscii.contains("endsolid", ignoreCase = true) || sampleAscii.contains("normal", ignoreCase = true))
        ) {
            return CadFileType.STL
        }

        // C2. Binary STL: 80 bytes header + 4 bytes uint32 triangle count (N).
        // Exact mathematical equation: FileSize == 84 + (N * 50).
        if (headerBytes.size >= 84 && fileSize >= 84L) {
            val byteBuffer = ByteBuffer.wrap(headerBytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN)
            val numTriangles = byteBuffer.int.toLong() and 0xFFFFFFFFL
            val expectedSize = 84L + (numTriangles * 50L)
            if (numTriangles > 0L && (fileSize == expectedSize || (fileSize - 84L) % 50L == 0L)) {
                return CadFileType.STL
            }
        }

        // D. Check G-Code Toolpath patterns
        val gCodeKeywords = listOf("G0", "G1", "G2", "G3", "G00", "G01", "G02", "G03", "G90", "G91", "M03", "M3", "M05", "M5", "M30", "G17", "G20", "G21")
        val lines = sampleAscii.lines().take(20)
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

    private fun ensureProperExtension(fileName: String, type: CadFileType): String {
        val lower = fileName.lowercase()
        return when (type) {
            CadFileType.STL -> if (lower.endsWith(".stl")) fileName else "$fileName.stl"
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
