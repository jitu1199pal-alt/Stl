package com.example.cad.native

import java.nio.ByteBuffer

object CadNativeEngine {
    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("cad_native_engine")
            isLibraryLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            isLibraryLoaded = false
        }
    }

    fun isAvailable(): Boolean = isLibraryLoaded

    external fun loadCadFileFromFd(fileDescriptor: Int, isDwg: Boolean): Boolean
    external fun getDirectGeometryBuffer(): ByteBuffer?
    external fun getPrimitiveCount(): Int
    external fun getModelExtents(): FloatArray?
    external fun destroyModel()

    data class ModelMetadata(
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float,
        val insUnits: Int,
        val scaleFactor: Float,
        val width: Float = maxX - minX,
        val height: Float = maxY - minY
    )

    fun fetchMetadata(): ModelMetadata? {
        if (!isLibraryLoaded) return null
        val extents = getModelExtents() ?: return null
        return ModelMetadata(
            minX = extents[0],
            maxX = extents[1],
            minY = extents[2],
            maxY = extents[3],
            insUnits = extents[4].toInt(),
            scaleFactor = extents[5]
        )
    }
}
