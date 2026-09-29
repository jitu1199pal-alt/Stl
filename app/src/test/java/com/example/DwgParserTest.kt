package com.example

import com.example.data.parser.DxfEntity
import com.example.data.parser.DxfParser
import com.example.data.parser.DwgParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DwgParserTest {

    @Test
    fun testDwgHeaderDetection() {
        val dwgHeader2018 = "AC1032\u0000\u0000\u0000\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val status2018 = DwgParser.detectHeader(dwgHeader2018)
        assertEquals("AC1032", status2018.versionTag)
        assertTrue(status2018.autocadVersion.contains("2018"))

        val dwgHeader2013 = "AC1027\u0000\u0000\u0000\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val status2013 = DwgParser.detectHeader(dwgHeader2013)
        assertEquals("AC1027", status2013.versionTag)
        assertTrue(status2013.autocadVersion.contains("2013"))

        val dwgHeader2000 = "AC1015\u0000\u0000\u0000\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val status2000 = DwgParser.detectHeader(dwgHeader2000)
        assertEquals("AC1015", status2000.versionTag)
        assertTrue(status2000.autocadVersion.contains("2000"))
    }

    @Test
    fun testIndependentDwgParsingNoHardcodedGeometry() {
        // Test parsing File A
        val fileABytes = "AC1032\u0000\u0000\u0000FLOOR_PLAN_SECTION_A\u0000ROOM_101".toByteArray(Charsets.US_ASCII)
        val modelA = DwgParser.parseStream("FloorPlan_A.dwg", fileABytes.inputStream())

        assertNotNull(modelA)
        assertEquals("FloorPlan_A.dwg", modelA.fileName)
        // Ensure no hardcoded Toran or Pillar layers exist in FloorPlan_A
        assertFalse("Should NOT contain hardcoded Toran layers", modelA.layers.contains("TORAN_CARVINGS"))
        assertFalse("Should NOT contain hardcoded Pillar layers", modelA.layers.contains("PILLAR_ELEVATION"))

        // Test parsing File B
        val fileBBytes = "AC1024\u0000\u0000\u0000GEAR_SHAFT_MECHANICAL\u0000PINION".toByteArray(Charsets.US_ASCII)
        val modelB = DwgParser.parseStream("Gear_Assembly_B.dwg", fileBBytes.inputStream())

        assertNotNull(modelB)
        assertEquals("Gear_Assembly_B.dwg", modelB.fileName)
        assertFalse("Should NOT contain hardcoded Toran layers", modelB.layers.contains("TORAN_CARVINGS"))

        // Verify debug report contains required fields
        assertNotNull(modelA.debugReport)
        assertTrue(modelA.debugReport!!.contains("Selected file:\nFloorPlan_A.dwg"))
        assertTrue(modelA.debugReport!!.contains("Entity count:"))
        assertTrue(modelA.debugReport!!.contains("LINE ="))
        assertTrue(modelA.debugReport!!.contains("ARC ="))
        assertTrue(modelA.debugReport!!.contains("CIRCLE ="))
        assertTrue(modelA.debugReport!!.contains("LWPOLYLINE ="))
        assertTrue(modelA.debugReport!!.contains("POLYLINE ="))
        assertTrue(modelA.debugReport!!.contains("SPLINE ="))
        assertTrue(modelA.debugReport!!.contains("HATCH ="))
        assertTrue(modelA.debugReport!!.contains("INSERT ="))
        assertTrue(modelA.debugReport!!.contains("TEXT ="))
        assertTrue(modelA.debugReport!!.contains("MTEXT ="))
        assertTrue(modelA.debugReport!!.contains("DIMENSION ="))
    }

    @Test
    fun testSplineInterpolation() {
        val pts = listOf(
            com.example.ui.render3d.Vector3D(0f, 0f, 0f),
            com.example.ui.render3d.Vector3D(10f, 20f, 0f),
            com.example.ui.render3d.Vector3D(20f, 0f, 0f),
            com.example.ui.render3d.Vector3D(30f, 20f, 0f)
        )
        val interpolated = DxfParser.interpolateSpline(pts, isClosed = false)
        assertTrue("Interpolated spline should have smooth density", interpolated.size > 20)
    }

    @Test
    fun testBlockExpansionWithTransformation() {
        val sampleDxfWithBlock = """
0
SECTION
2
BLOCKS
0
BLOCK
2
MY_COLUMN
10
0.0
20
0.0
30
0.0
0
LINE
8
0
10
0.0
20
0.0
11
100.0
21
0.0
0
ENDBLK
0
ENDSEC
0
SECTION
2
ENTITIES
0
INSERT
2
MY_COLUMN
8
WALLS
10
500.0
20
600.0
41
2.0
42
2.0
0
ENDSEC
0
EOF
        """.trimIndent()

        val model = DxfParser.parse("block_test.dxf", sampleDxfWithBlock)
        assertEquals(1, model.entities.size)
        val line = model.entities[0] as DxfEntity.Line
        assertEquals(500f, line.start.x, 0.01f)
        assertEquals(600f, line.start.y, 0.01f)
        // Scaled by 2.0: 100 * 2 = 200 + 500 = 700
        assertEquals(700f, line.end.x, 0.01f)
        assertEquals(600f, line.end.y, 0.01f)
    }
}
