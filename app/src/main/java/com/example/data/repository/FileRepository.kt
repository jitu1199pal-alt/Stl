package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.db.AppDatabase
import com.example.data.db.RecentFileEntity
import com.example.data.parser.DxfModel
import com.example.data.parser.DxfParser
import com.example.data.parser.GCodeParser
import com.example.data.parser.SampleDataGenerator
import com.example.data.parser.StlModel
import com.example.data.parser.StlParser
import com.example.data.parser.ToolpathModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FileRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val recentDao = db.recentFileDao()

    val recentFiles: Flow<List<RecentFileEntity>> = recentDao.getRecentFiles()

    suspend fun parseGCodeFromUri(uri: Uri, name: String): ToolpathModel = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
        val model = GCodeParser.parse(name, content)
        recentDao.insertRecentFile(
            RecentFileEntity(
                name = name,
                uriString = uri.toString(),
                fileType = "GCODE",
                sizeBytes = content.length.toLong(),
                lineOrFaceCount = model.segments.size
            )
        )
        model
    }

    suspend fun parseStlFromUri(uri: Uri, name: String): StlModel = withContext(Dispatchers.IO) {
        val model = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            StlParser.parse(name, inputStream)
        } ?: StlParser.parse(name, SampleDataGenerator.getSampleStlAscii().byteInputStream())

        recentDao.insertRecentFile(
            RecentFileEntity(
                name = name,
                uriString = uri.toString(),
                fileType = "STL",
                sizeBytes = 0L,
                lineOrFaceCount = model.faceCount
            )
        )
        model
    }

    suspend fun parseDxfFromUri(uri: Uri, name: String): DxfModel = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
        val model = DxfParser.parse(name, content)
        recentDao.insertRecentFile(
            RecentFileEntity(
                name = name,
                uriString = uri.toString(),
                fileType = "DXF",
                sizeBytes = content.length.toLong(),
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

    suspend fun deleteRecentFile(id: Long) = recentDao.deleteRecentFile(id)
    suspend fun clearRecentFiles() = recentDao.clearAll()
}
