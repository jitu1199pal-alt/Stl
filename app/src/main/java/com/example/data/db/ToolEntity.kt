package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tools")
data class ToolEntity(
    @PrimaryKey val toolNumber: Int,
    val name: String,
    val type: String, // "ENDMILL", "BALLNOSE", "VBIT"
    val diameterMm: Double,
    val lengthMm: Double,
    val maxRpm: Int,
    val colorHex: String = "#FFC107"
)
