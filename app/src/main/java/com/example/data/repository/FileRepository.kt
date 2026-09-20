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
import com.example.data.parser.GCodeParser
import com.example.data.parser.ObjParser
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
        val model = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            if (isDwg) {
                DwgParser.parseStream(name, inputStream)
            } else {
                DxfParser.parseStream(name, inputStream)
            }
        } ?: if (isDwg) {
            DwgParser.parseStream(name, "".byteInputStream())
        } else {
            DxfParser.parse(name, SampleDataGenerator.getSampleDxf())
        }

        recentDao.insertRecentFile(
            RecentFileEntity(
                name = name,
                uriString = uri.toString(),
                fileType = if (isDwg) "DWG" else "DXF",
                sizeBytes = 0L,
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

    suspend fun deleteRecentFile(id: Long) = recentDao.deleteRecentFile(id)
    suspend fun clearRecentFiles() = recentDao.clearAll()
}
