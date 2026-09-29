package com.example

import com.example.data.parser.AspireReliefParser
import com.example.util.CadFileType
import com.example.util.FileTypeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class Crv3dParserTest {

    @Test
    fun testDetectCrv3dFileType() {
        val type = FileTypeResolver.inspectHeaderBytes("Vectric Aspire CRV3D Project File Header".toByteArray(StandardCharsets.US_ASCII))
        assertEquals(CadFileType.ASPIRE_3D, type)
        assertEquals("CRV3D", CadFileType.ASPIRE_3D.badge)
        assertTrue(CadFileType.ASPIRE_3D.is3DModel)
    }

    @Test
    fun testParseCrv3dReliefMesh() {
        val dummyBytes = ByteArray(1024) { (it % 256).toByte() }
        val model = AspireReliefParser.parse("decorative_rosette.crv3d", ByteArrayInputStream(dummyBytes))

        assertNotNull(model)
        assertEquals("decorative_rosette.crv3d", model.fileName)
        assertTrue("Model should have triangles", model.triangles.isNotEmpty())
        assertTrue("Model should have faceCount > 0", model.faceCount > 0)
        assertTrue("Bounds width should be > 0", model.bounds.sizeX > 0f)
        assertTrue("Bounds height should be > 0", model.bounds.sizeY > 0f)
        assertTrue("Bounds depth should be >= 0", model.bounds.sizeZ >= 0f)

        val vertexBuffer = model.getOrBuildVertexBuffer()
        assertNotNull(vertexBuffer)
        assertTrue("Vertex buffer should have elements", vertexBuffer.capacity() > 0)
    }

    @Test
    fun testParseCrv3dWithEmbeddedStl() {
        val asciiStl = """
            solid test_relief
              facet normal 0.0 0.0 1.0
                outer loop
                  vertex 0.0 0.0 5.0
                  vertex 10.0 0.0 5.0
                  vertex 0.0 10.0 5.0
                endloop
              endfacet
            endsolid test_relief
        """.trimIndent()
        val fileContent = "Vectric CRV3D Container\n" + asciiStl
        val bytes = fileContent.toByteArray(StandardCharsets.US_ASCII)

        val model = AspireReliefParser.parse("panel_carving.crv3d", ByteArrayInputStream(bytes))
        assertNotNull(model)
        assertEquals(1, model.faceCount)
        assertEquals(10f, model.bounds.sizeX, 0.01f)
    }
}
