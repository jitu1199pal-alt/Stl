package com.example

import com.example.data.parser.ArtcamReliefParser
import com.example.data.parser.AspireReliefParser
import com.example.data.parser.ObjParser
import com.example.data.parser.ThreeDXmlParser
import com.example.util.CadFileType
import com.example.util.FileTypeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FileTypeResolverTest {

    @Test
    fun testStlIdentification() {
        // 1. By ASCII STL content
        val asciiStl = """
            solid test_part
              facet normal 0 0 1
                outer loop
                  vertex 0 0 0
                  vertex 10 0 0
                  vertex 0 10 0
                endloop
              endfacet
            endsolid test_part
        """.trimIndent()
        val asciiStlType = FileTypeResolver.inspectHeaderBytes(asciiStl.toByteArray(Charsets.US_ASCII))
        assertEquals(CadFileType.STL, asciiStlType)

        // 2. By Binary STL formula
        val numTriangles = 50
        val binaryHeader = ByteArray(84)
        val buf = ByteBuffer.wrap(binaryHeader, 80, 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(numTriangles)
        val totalFileSize = 84L + (numTriangles * 50L) // 2584 bytes

        val binaryStlType = FileTypeResolver.inspectHeaderBytes(binaryHeader, fileSize = totalFileSize)
        assertEquals(CadFileType.STL, binaryStlType)
    }

    @Test
    fun testObjIdentificationAndParsing() {
        val objContent = """
            # Wavefront OBJ 3D Model
            v 0.0 0.0 0.0
            v 10.0 0.0 0.0
            v 10.0 10.0 0.0
            v 0.0 10.0 0.0
            vn 0.0 0.0 1.0
            f 1//1 2//1 3//1
            f 1//1 3//1 4//1
        """.trimIndent()

        val type = FileTypeResolver.inspectHeaderBytes(objContent.toByteArray(Charsets.US_ASCII))
        assertEquals(CadFileType.OBJ, type)
        assertTrue(type!!.is3DModel)

        // Test parser
        val model = ObjParser.parse("box.obj", ByteArrayInputStream(objContent.toByteArray(Charsets.UTF_8)))
        assertEquals(2, model.faceCount)
        assertEquals(10f, model.bounds.sizeX, 0.01f)
        assertEquals(10f, model.bounds.sizeY, 0.01f)
    }

    @Test
    fun test3DXmlIdentificationAndParsing() {
        val xmlContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <Model_3dxml>
              <PolygonalRep>
                <Positions>0.0 0.0 0.0 10.0 0.0 0.0 0.0 10.0 0.0</Positions>
                <Triangles>0 1 2</Triangles>
              </PolygonalRep>
            </Model_3dxml>
        """.trimIndent()

        val type = FileTypeResolver.inspectHeaderBytes(xmlContent.toByteArray(Charsets.UTF_8))
        assertEquals(CadFileType.XML3D, type)
        assertTrue(type!!.is3DModel)

        val model = ThreeDXmlParser.parse("sample.3dxml", ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
        assertEquals(1, model.faceCount)
    }

    @Test
    fun testArtcamRlfIdentificationAndParsing() {
        val rlfSample = "Delcam ArtCAM RLF 3D Relief File Header".toByteArray(Charsets.US_ASCII)
        val type = FileTypeResolver.inspectHeaderBytes(rlfSample)
        assertEquals(CadFileType.RLF, type)
        assertTrue(type!!.is3DModel)

        val model = ArtcamReliefParser.parse("flower.rlf", ByteArrayInputStream(rlfSample))
        assertNotNull(model)
        assertTrue(model.faceCount > 0)
    }

    @Test
    fun testAspireCodeIdentificationAndParsing() {
        val aspireToolpath = """
            ( Post: Vectric Aspire 3D Finish Carving )
            ( Tool: Ball Nose 3mm )
            G90 G21
            G00 Z5.0
            G01 X0.0 Y0.0 Z-1.2 F1500
            G01 X10.0 Y0.0 Z-2.5
            G01 X10.0 Y10.0 Z-1.0
            G01 X0.0 Y10.0 Z-0.5
            M30
        """.trimIndent()

        val type = FileTypeResolver.inspectHeaderBytes(aspireToolpath.toByteArray(Charsets.US_ASCII))
        assertEquals(CadFileType.ASPIRE_3D, type)
        assertTrue(type!!.is3DModel)

        val model = AspireReliefParser.parse("carve.crv3d", ByteArrayInputStream(aspireToolpath.toByteArray(Charsets.UTF_8)))
        assertNotNull(model)
        assertTrue(model.faceCount > 0)
    }

    @Test
    fun testAutoCadDwgIdentification() {
        val dwgBytes = "AC1032\u0000\u0000\u0000\u0000SomeBinaryContentHere".toByteArray(Charsets.US_ASCII)
        val dwgType = FileTypeResolver.inspectHeaderBytes(dwgBytes)
        assertEquals(CadFileType.DWG, dwgType)

        val dwg2013 = "AC1027\u0000\u0000".toByteArray(Charsets.US_ASCII)
        assertEquals(CadFileType.DWG, FileTypeResolver.inspectHeaderBytes(dwg2013))
    }

    @Test
    fun testAutoCadDxfIdentification() {
        val dxfHeader = """
            0
            SECTION
            2
            ENTITIES
            0
            LINE
        """.trimIndent().toByteArray(Charsets.US_ASCII)
        val dxfType = FileTypeResolver.inspectHeaderBytes(dxfHeader)
        assertEquals(CadFileType.DXF, dxfType)
    }

    @Test
    fun testToolpathGCodeIdentification() {
        val gcode = """
            %
            O1001 (PART 1)
            G90 G21
            G00 Z5.0
            G01 X10.0 Y20.0 F1200
            M30
            %
        """.trimIndent().toByteArray(Charsets.US_ASCII)
        val gcodeType = FileTypeResolver.inspectHeaderBytes(gcode)
        assertEquals(CadFileType.TOOLPATH_GCODE, gcodeType)
    }
}
