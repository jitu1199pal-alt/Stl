package com.example

import com.example.util.CadFileType
import com.example.util.FileTypeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
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

        // 2. By Binary STL formula: 80 bytes header + 4 bytes count (100 triangles) + exact file size
        val numTriangles = 50
        val binaryHeader = ByteArray(84)
        val buf = ByteBuffer.wrap(binaryHeader, 80, 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(numTriangles)
        val totalFileSize = 84L + (numTriangles * 50L) // 2584 bytes

        val binaryStlType = FileTypeResolver.inspectHeaderBytes(binaryHeader, fileSize = totalFileSize)
        assertEquals(CadFileType.STL, binaryStlType)
    }

    @Test
    fun testAutoCadDwgIdentification() {
        // AutoCAD 2018 DWG header starts with AC1032
        val dwgBytes = "AC1032\u0000\u0000\u0000\u0000SomeBinaryContentHere".toByteArray(Charsets.US_ASCII)
        val dwgType = FileTypeResolver.inspectHeaderBytes(dwgBytes)
        assertEquals(CadFileType.DWG, dwgType)

        // AutoCAD 2013 DWG header starts with AC1027
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
