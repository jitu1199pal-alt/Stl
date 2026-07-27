package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val uriString: String,
    val fileType: String, // "GCODE", "STL", "DXF"
    val sizeBytes: Long,
    val lineOrFaceCount: Int,
    val lastOpenedTimestamp: Long = System.currentTimeMillis()
)
