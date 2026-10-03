package com.example.cad.native

import android.util.Log

/**
 * High-performance Native NDK JNI Bridge for CAD View Pro.
 * Provides safe opaque handle access to native C++ CAD engines (ODA / LibreDWG / libdxfrw).
 */
object CadNativeBridge {
    private const val TAG = "CadNativeBridge"

    @Volatile
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("cadconverter")
            isLoaded = true
            Log.i(TAG, "Native library 'cadconverter' loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native CAD engine library not present in APK. Pure Kotlin vector engine active: ${e.message}")
            isLoaded = false
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing native CAD bridge: ${e.message}")
            isLoaded = false
        }
    }

    fun isAvailable(): Boolean = isLoaded

    // -------------------------------------------------------------
    // Native JNI Methods with Opaque Handles (Long documentHandle)
    // -------------------------------------------------------------

    @JvmStatic
    external fun nativeOpenDocument(filePath: String, isDwg: Boolean): Long

    @JvmStatic
    external fun nativeCloseDocument(documentHandle: Long)

    @JvmStatic
    external fun nativeGetBounds(documentHandle: Long): FloatArray?

    @JvmStatic
    external fun nativeRender(documentHandle: Long, width: Int, height: Int, cameraX: Float, cameraY: Float, zoom: Float): Boolean

    @JvmStatic
    external fun nativeGetLayers(documentHandle: Long): Array<String>

    @JvmStatic
    external fun nativeSetLayerVisibility(documentHandle: Long, layerName: String, isVisible: Boolean)

    @JvmStatic
    external fun nativeGetEntities(documentHandle: Long): Int

    @JvmStatic
    external fun nativeFindText(documentHandle: Long, query: String): Array<String>

    @JvmStatic
    external fun nativeMeasureDistance(documentHandle: Long, x1: Double, y1: Double, x2: Double, y2: Double): DoubleArray?

    @JvmStatic
    external fun nativeMeasureArea(documentHandle: Long, pointsX: DoubleArray, pointsY: DoubleArray): DoubleArray?

    @JvmStatic
    external fun nativeGetDrawingInfo(documentHandle: Long): String
}
