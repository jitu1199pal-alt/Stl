package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.db.AppDatabase
import com.example.data.db.RecentFileEntity
import com.example.data.parser.ArtcamReliefParser
import com.example.data.parser.AspireReliefParser
import com.example.data.parser.DwgParser
import com.example.data.parser.DxfModel
import com.example.data.parser.DxfParser
import com.example.data.parser.ExcelModel
import com.example.data.parser.ExcelParser
import com.example.data.parser.GCodeParser
import com.example.data.parser.ObjParser
import com.example.data.parser.PdfDocumentInfo
import com.example.data.parser.PdfHelper
import com.example.data.parser.SampleDataGenerator
import com.example.data.parser.StlModel
import com.example.data.parser.StlParser
import com.example.data.parser.ThreeDXmlParser
import com.example.data.parser.ToolpathModel
import com.example.util.CadFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FileRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val recentDao = db.recentFileDao()

    val recentFiles: Flow<List<RecentFileEntity>> = recentDao.getRecentFiles()

    suspend fun parseGCodeFromUri(uri: Uri, name: String): ToolpathModel = withContext(Dispatchers.IO) {
        val model = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            GCodeParser.parseStream(name, inputStream)
        } ?: GCodeParser.parse(name, SampleDataGenerator.getSampleGCode())

        recentDao.insertRecentFile(
            RecentFileEntity(
                name = name,
                uriString = uri.toString(),
                fileType = "GCODE",
                sizeBytes = 0L,
                lineOrFaceCount = model.segments.size
            )
        )
        model
    }

    suspend fun parseStlFromUri(uri: Uri, name: String): StlModel =
        parse3DModelFromUri(uri, name, CadFileType.STL)

    suspend fun parse3DModelFromUri(uri: Uri, name: String, fileType: CadFileType = CadFileType.STL): StlModel = withContext(Dispatchers.IO) {
        val model = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            when (fileType) {
                CadFileType.OBJ -> ObjParser.parse(name, inputStream)
                CadFileType.RLF, CadFileType.ART -> ArtcamReliefParser.parse(name, inputStream)
                CadFileType.XML3D -> ThreeDXmlParser.parse(name, inputStream)
                CadFileType.ASPIRE_3D -> AspireReliefParser.parse(name, inputStream)
                CadFileType.STL -> StlParser.parse(name, inputStream)
                else -> {
                    val lower = name.lowercase()
                    when {
                        lower.endsWith(".obj") -> ObjParser.parse(name, inputStream)
                        lower.endsWith(".rlf") || lower.endsWith(".art") -> ArtcamReliefParser.parse(name, inputStream)
                        lower.endsWith(".3dxml") -> ThreeDXmlParser.parse(name, inputStream)
                        lower.endsWith(".crv") || lower.endsWith(".crv3d") || lower.endsWith(".v3m") || lower.endsWith(".3dclip") -> AspireReliefParser.parse(name, inputStream)
                        else -> StlParser.parse(name, inputStream)
                    }
                }
            }
        } ?: StlParser.parse(name, SampleDataGenerator.getSampleStlAscii().byteInputStream())

        recentDao.insertRecentFile(
            RecentFileEntity(
                name = name,
                uriString = uri.toString(),
                fileType = fileType.badge,
                sizeBytes = 0L,
                lineOrFaceCount = model.faceCount
            )
        )
        model
    }

    suspend fun parseDxfFromUri(uri: Uri, name: String): DxfModel = withContext(Dispatchers.IO) {
        val lower = name.lowercase()
        val isDwg = lower.endsWith(".dwg")
        val mimeType = context.contentResolver.getType(uri)

        var querySize = 0L
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIdx != -1) {
                        querySize = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (_: Exception) {}

        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        } catch (_: Exception) {
            ByteArray(0)
        }

        val actualSize = if (querySize > 0L) querySize else bytes.size.toLong()

        val model = if (isDwg) {
            DwgParser.parseBytes(
                fileName = name,
                uriString = uri.toString(),
                mimeType = mimeType,
                fileSize = actualSize,
                bytes = bytes
            )
        } else {
            DxfParser.parseBytes(
                name = name,
                bytes = bytes,
                uriString = uri.toString(),
                mimeType = mimeType,
                fileSize = actualSize
            )
        }

        recentDao.insertRecentFile(
            RecentFileEntity(
                name = name,
                uriString = uri.toString(),
                fileType = if (isDwg) "DWG" else "DXF",
                sizeBytes = actualSize,
                lineOrFaceCount = model.entities.size
            )
        )
        model
    }

    suspend fun loadSampleGCode(): ToolpathModel = withContext(Dispatchers.IO) {
        val sampleText = SampleDataGenerator.getSampleGCode()
        GCodeParser.parse("sample_3d_relief.tap", sampleText)
    }

    suspend fun loadSampleStl(): StlModel = withContext(Dispatchers.IO) {
        val sampleText = SampleDataGenerator.getSampleStlAscii()
        StlParser.parse("sample_mounting_bracket.stl", sampleText.byteInputStream())
    }

    suspend fun loadSampleCrv3d(): StlModel = withContext(Dispatchers.IO) {
        val model = AspireReliefParser.parse("sample_decorative_rosette.crv3d", java.io.ByteArrayInputStream(ByteArray(0)))
        recentDao.insertRecentFile(
            RecentFileEntity(
                name = "sample_decorative_rosette.crv3d",
                uriString = "sample://sample_decorative_rosette.crv3d",
                fileType = "CRV3D",
                sizeBytes = 148520L,
                lineOrFaceCount = model.faceCount
            )
        )
        model
    }

    suspend fun loadSampleDxf(): DxfModel = withContext(Dispatchers.IO) {
        val sampleText = SampleDataGenerator.getSampleDxf()
        DxfParser.parse("sample_flange.dxf", sampleText)
    }

    suspend fun loadArchitecturalDxf(): DxfModel = withContext(Dispatchers.IO) {
        val sampleText = SampleDataGenerator.getSampleArchitecturalDxf()
        DxfParser.parse("architectural_floor_plan.dxf", sampleText)
    }

    suspend fun loadCncBracketDxf(): DxfModel = withContext(Dispatchers.IO) {
        val sampleText = SampleDataGenerator.getSampleCncBracketDxf()
        DxfParser.parse("cnc_bracket_plate.dxf", sampleText)
    }

    suspend fun parseExcelFromUri(uri: Uri, name: String): ExcelModel = withContext(Dispatchers.IO) {
        val model = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ExcelParser.parse(name, inputStream)
        } ?: ExcelParser.parse(name, "".byteInputStream())

        recentDao.insertRecentFile(
            RecentFileEntity(
                name = name,
                uriString = uri.toString(),
                fileType = "EXCEL",
                sizeBytes = 0L,
                lineOrFaceCount = model.totalRows
            )
        )
        model
    }

    suspend fun preparePdfFromUri(uri: Uri, name: String): PdfDocumentInfo = withContext(Dispatchers.IO) {
        val info = PdfHelper.preparePdf(context, uri, name)
        recentDao.insertRecentFile(
            RecentFileEntity(
                name = name,
                uriString = uri.toString(),
                fileType = "PDF",
                sizeBytes = 0L,
                lineOrFaceCount = info.pageCount
            )
        )
        info
    }

    suspend fun loadSampleExcel(): ExcelModel = withContext(Dispatchers.IO) {
        ExcelModel(
            fileName = "CNC_Parts_Cutting_List.xlsx",
            sheets = listOf(
                ExcelParser.createSampleSheet("Cutting List"),
                com.example.data.parser.ExcelSheet(
                    "Materials & Rates",
                    listOf(
                        com.example.data.parser.ExcelRow(1, listOf("Material Code", "Description", "Density (g/cm³)", "Stock Size", "Rate / sq.ft")),
                        com.example.data.parser.ExcelRow(2, listOf("MAT-01", "Teak Wood Grade A", "0.65", "8x4 ft x 25mm", "₹ 450")),
                        com.example.data.parser.ExcelRow(3, listOf("MAT-02", "Rosewood (Sheesham)", "0.80", "6x3 ft x 30mm", "₹ 620")),
                        com.example.data.parser.ExcelRow(4, listOf("MAT-03", "Aluminum Plate 6061", "2.70", "4x2 ft x 12mm", "₹ 850")),
                        com.example.data.parser.ExcelRow(5, listOf("MAT-04", "Brass Plate C360", "8.50", "2x2 ft x 15mm", "₹ 1400")),
                        com.example.data.parser.ExcelRow(6, listOf("MAT-05", "High Density MDF", "0.75", "8x4 ft x 18mm", "₹ 110"))
                    ),
                    5
                )
            )
        )
    }

    suspend fun loadSamplePdf(): PdfDocumentInfo = withContext(Dispatchers.IO) {
        val sampleFile = java.io.File(context.cacheDir, "CNC_Engineering_Drawing.pdf")
        if (!sampleFile.exists()) {
            PdfHelper.generateSampleEngineeringPdf(sampleFile, "CNC_Engineering_Drawing.pdf")
        }
        PdfDocumentInfo(
            fileName = "CNC_Engineering_Drawing.pdf",
            pageCount = 1,
            localFilePath = sampleFile.absolutePath
        )
    }

    suspend fun deleteRecentFile(id: Long) = recentDao.deleteRecentFile(id)
    suspend fun clearRecentFiles() = recentDao.clearAll()
}
