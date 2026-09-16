package com.example

import com.example.data.parser.DxfEntity
import com.example.data.parser.DxfParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DxfParserTest {

    @Test
    fun testAutoCadDxfParsingAndBounds() {
        val sampleDxf = """
0
SECTION
2
HEADER
9
${'$'}EXTMIN
10
-1.000000000000000E+20
20
-1.000000000000000E+20
0
ENDSEC
0
SECTION
2
ENTITIES
0
LINE
8
WALLS
10
100.0
20
200.0
11
300.0
21
200.0
0
POLYLINE
8
OUTLINE
70
1
0
VERTEX
10
100.0
20
200.0
0
VERTEX
10
300.0
20
400.0
0
SEQEND
0
CIRCLE
8
HOLES
10
200.0
20
300.0
40
25.0
0
ENDSEC
0
EOF
        """.trimIndent()

        val model = DxfParser.parse("test.dxf", sampleDxf)

        assertTrue("Entities should not be empty", model.entities.isNotEmpty())
        assertEquals(3, model.entities.size)
        assertTrue("Contains line", model.entities[0] is DxfEntity.Line)
        assertTrue("Contains polyline from VERTEX", model.entities[1] is DxfEntity.Polyline)
        assertTrue("Contains circle", model.entities[2] is DxfEntity.Circle)

        // Verify bounds are sane and not corrupted by HEADER
        assertTrue("minX should be >= 100", model.bounds.minX >= 90f)
        assertTrue("maxX should be <= 310", model.bounds.maxX <= 310f)
        assertTrue("sizeX should be positive and reasonable", model.bounds.sizeX in 100f..300f)
    }

    @Test
    fun testLwPolylineBulgeExpandsToSmoothCurve() {
        // A semicircle from (0,0) to (2,0) with bulge = 1.0 (semi-circular arch)
        val dxfWithBulge = """
0
SECTION
2
ENTITIES
0
LWPOLYLINE
8
PETALS
70
0
90
2
10
0.0
20
0.0
42
1.0
10
2.0
20
0.0
0
ENDSEC
0
EOF
        """.trimIndent()

        val model = DxfParser.parse("bulge_test.dxf", dxfWithBulge)
        assertEquals(1, model.entities.size)
        val poly = model.entities[0] as DxfEntity.Polyline

        // Because of bulge 1.0, it should interpolate multiple points along the smooth circular arc
        assertTrue("Polyline points should be smoothly expanded (at least 10 points)", poly.points.size >= 10)
        // Check that apex is around (1.0, 1.0)
        val maxY = poly.points.maxOf { it.y }
        assertTrue("Apex of arc should reach near 1.0 (radius)", maxY in 0.95f..1.05f)
    }
}
