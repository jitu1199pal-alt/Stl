package com.example.cad.font

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import java.io.File

/**
 * CAD Font Manager for SHX, TTF, and Unicode fonts.
 * Ensures robust text rendering without fatal errors when CAD-specific fonts are missing.
 */
object FontManager {
    private const val TAG = "FontManager"

    private val fontCache = mutableMapOf<String, Typeface>()
    private val availableShxFiles = mutableSetOf<String>()

    /**
     * Initializes font directories and scans for bundled or user-provided CAD fonts.
     */
    fun init(context: Context) {
        val searchDirs = listOf(
            File(context.filesDir, "fonts"),
            File(context.getExternalFilesDir(null), "fonts"),
            File("/system/fonts")
        )

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    val ext = file.extension.lowercase()
                    if (ext == "shx" || ext == "ttf" || ext == "otf") {
                        availableShxFiles.add(file.name.lowercase())
                    }
                }
            }
        }
        Log.d(TAG, "Discovered ${availableShxFiles.size} CAD/system fonts.")
    }

    /**
     * Resolves a typeface for a given CAD style or font file name.
     * Gracefully falls back to standard monospaced/sans-serif fonts if missing.
     */
    fun resolveTypeface(fontName: String?): Typeface {
        if (fontName.isNullOrBlank()) {
            return Typeface.MONOSPACE
        }

        val key = fontName.lowercase()
        fontCache[key]?.let { return it }

        // Attempt system or bundled font load
        val typeface = try {
            when {
                key.contains("txt") || key.contains("romans") || key.contains("simplex") -> Typeface.MONOSPACE
                key.contains("italic") -> Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                key.contains("bold") -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                else -> Typeface.SANS_SERIF
            }
        } catch (_: Exception) {
            Typeface.DEFAULT
        }

        fontCache[key] = typeface
        return typeface
    }
}
