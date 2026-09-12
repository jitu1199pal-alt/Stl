package com.example.data.parser

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

object SampleDataGenerator {

    fun getSampleGCode(): String {
        return """
; ============================================
; Toolpath Simulation Sample - 3D Relief Pocket
; Units: Metric (mm)
; ============================================
G21 G90 G17
G00 Z15.0000
T1 M06 (6mm Flat Endmill)
S18000 M03
G00 X0.0000 Y0.0000 Z10.0000
F2400

; Outer Contour Pass
G00 X10.0000 Y10.0000
G01 Z-2.0000 F600
G01 X90.0000 Y10.0000 F1800
G02 X100.0000 Y20.0000 I0.0000 J10.0000
G01 X100.0000 Y80.0000
G02 X90.0000 Y90.0000 I-10.0000 J0.0000
G01 X20.0000 Y90.0000
G02 X100.0000 Y80.0000 I0.0000 J-10.0000
G01 X10.0000 Y20.0000
G02 X20.0000 Y10.0000 I10.0000 J0.0000

; Circular Pocketing
G00 Z5.0000
G00 X50.0000 Y50.0000
G01 Z-4.0000 F400
G03 X50.0000 Y50.0000 I15.0000 J0.0000 F1200
G03 X50.0000 Y50.0000 I25.0000 J0.0000

; 3D Wave Pattern
G00 Z5.0000
T2 M06 (3mm Ballnose)
S20000 M03
G00 X25.0000 Y25.0000 Z2.0000
G01 Z-1.0000 F800
G01 X30.0000 Y30.0000 Z-3.5000 F1500
G01 X35.0000 Y25.0000 Z-1.0000
G01 X40.0000 Y35.0000 Z-4.0000
G01 X45.0000 Y25.0000 Z-1.5000
G01 X50.0000 Y40.0000 Z-5.0000
G01 X55.0000 Y25.0000 Z-1.5000
G01 X60.0000 Y35.0000 Z-4.0000
G01 X65.0000 Y25.0000 Z-1.0000
G01 X70.0000 Y30.0000 Z-3.5000
G01 X75.0000 Y25.0000 Z-1.0000

; Retract & Finish
G00 Z20.0000
M05
M30
        """.trimIndent()
    }

    fun getSampleStlAscii(): String {
        return """
solid 3D_Mounting_Bracket
  facet normal 0.000000 0.000000 -1.000000
    outer loop
      vertex 0.000000 0.000000 0.000000
      vertex 50.000000 0.000000 0.000000
      vertex 50.000000 50.000000 0.000000
    endloop
  endfacet
  facet normal 0.000000 0.000000 -1.000000
    outer loop
      vertex 0.000000 0.000000 0.000000
      vertex 50.000000 50.000000 0.000000
      vertex 0.000000 50.000000 0.000000
    endloop
  endfacet
  facet normal 0.000000 0.000000 1.000000
    outer loop
      vertex 0.000000 0.000000 25.000000
      vertex 50.000000 50.000000 25.000000
      vertex 50.000000 0.000000 25.000000
    endloop
  endfacet
  facet normal 0.000000 0.000000 1.000000
    outer loop
      vertex 0.000000 0.000000 25.000000
      vertex 0.000000 50.000000 25.000000
      vertex 50.000000 50.000000 25.000000
    endloop
  endfacet
  facet normal 0.000000 -1.000000 0.000000
    outer loop
      vertex 0.000000 0.000000 0.000000
      vertex 50.000000 0.000000 25.000000
      vertex 50.000000 0.000000 0.000000
    endloop
  endfacet
  facet normal 0.000000 -1.000000 0.000000
    outer loop
      vertex 0.000000 0.000000 0.000000
      vertex 0.000000 0.000000 25.000000
      vertex 50.000000 0.000000 25.000000
    endloop
  endfacet
  facet normal 1.000000 0.000000 0.000000
    outer loop
      vertex 50.000000 0.000000 0.000000
      vertex 50.000000 50.000000 25.000000
      vertex 50.000000 50.000000 0.000000
    endloop
  endfacet
  facet normal 1.000000 0.000000 0.000000
    outer loop
      vertex 50.000000 0.000000 0.000000
      vertex 50.000000 0.000000 25.000000
      vertex 50.000000 50.000000 25.000000
    endloop
  endfacet
  facet normal 0.000000 1.000000 0.000000
    outer loop
      vertex 50.000000 50.000000 0.000000
      vertex 0.000000 50.000000 25.000000
      vertex 0.000000 50.000000 0.000000
    endloop
  endfacet
  facet normal 0.000000 1.000000 0.000000
    outer loop
      vertex 50.000000 50.000000 0.000000
      vertex 50.000000 50.000000 25.000000
      vertex 0.000000 50.000000 25.000000
    endloop
  endfacet
  facet normal -1.000000 0.000000 0.000000
    outer loop
      vertex 0.000000 50.000000 0.000000
      vertex 0.000000 0.000000 25.000000
      vertex 0.000000 0.000000 0.000000
    endloop
  endfacet
  facet normal -1.000000 0.000000 0.000000
    outer loop
      vertex 0.000000 50.000000 0.000000
      vertex 0.000000 50.000000 25.000000
      vertex 0.000000 0.000000 25.000000
    endloop
  endfacet
endsolid 3D_Mounting_Bracket
        """.trimIndent()
    }

    fun getSampleDxf(): String {
        return """
0
SECTION
2
ENTITIES
0
LINE
8
OUTLINE
10
10.0
20
10.0
11
90.0
21
10.0
0
LINE
8
OUTLINE
10
90.0
20
10.0
11
90.0
21
90.0
0
LINE
8
OUTLINE
10
90.0
20
90.0
11
10.0
21
90.0
0
LINE
8
OUTLINE
10
10.0
20
90.0
11
10.0
21
10.0
0
CIRCLE
8
CENTER_BORE
10
50.0
20
50.0
40
20.0
0
CIRCLE
8
BOLT_HOLES
10
25.0
20
25.0
40
5.0
0
CIRCLE
8
BOLT_HOLES
10
75.0
20
25.0
40
5.0
0
CIRCLE
8
BOLT_HOLES
10
75.0
20
75.0
40
5.0
0
CIRCLE
8
BOLT_HOLES
10
25.0
20
75.0
40
5.0
0
TEXT
8
ANNOTATIONS
10
32.0
20
6.0
40
4.0
1
CNC FLANGE 100x100mm
0
ENDSEC
0
EOF
        """.trimIndent()
    }

    fun getSampleArchitecturalDxf(): String {
        return """
0
SECTION
2
ENTITIES
0
LINE
8
WALLS
10
0.0
20
0.0
11
120.0
21
0.0
0
LINE
8
WALLS
10
120.0
20
0.0
11
120.0
21
80.0
0
LINE
8
WALLS
10
120.0
20
80.0
11
0.0
21
80.0
0
LINE
8
WALLS
10
0.0
20
80.0
11
0.0
21
0.0
0
LINE
8
PARTITIONS
10
60.0
20
0.0
11
60.0
21
80.0
0
LINE
8
PARTITIONS
10
0.0
20
40.0
11
60.0
21
40.0
0
ARC
8
DOORS
10
60.0
20
25.0
40
15.0
50
0.0
51
90.0
0
ARC
8
DOORS
10
15.0
20
40.0
40
12.0
50
90.0
51
180.0
0
TEXT
8
ROOM_NAMES
10
15.0
20
55.0
40
5.0
1
BEDROOM 1
0
TEXT
8
ROOM_NAMES
10
15.0
20
18.0
40
5.0
1
KITCHEN
0
TEXT
8
ROOM_NAMES
10
75.0
20
40.0
40
6.0
1
LIVING ROOM
0
ENDSEC
0
EOF
        """.trimIndent()
    }

    fun getSampleCncBracketDxf(): String {
        return """
0
SECTION
2
ENTITIES
0
LWPOLYLINE
8
OUTER_CONTOUR
70
1
10
15.0
20
0.0
10
105.0
20
0.0
10
120.0
20
15.0
10
120.0
20
65.0
10
105.0
20
80.0
10
15.0
20
80.0
10
0.0
20
65.0
10
0.0
20
15.0
0
CIRCLE
8
MOUNT_PINS
10
20.0
20
20.0
40
6.0
0
CIRCLE
8
MOUNT_PINS
10
100.0
20
20.0
40
6.0
0
CIRCLE
8
MOUNT_PINS
10
100.0
20
60.0
40
6.0
0
CIRCLE
8
MOUNT_PINS
10
20.0
20
60.0
40
6.0
0
CIRCLE
8
CENTER_BEARING
10
60.0
20
40.0
40
18.0
0
TEXT
8
SPECS
10
30.0
20
5.0
40
4.0
1
CNC BRACKET AL-6061 T6
0
ENDSEC
0
EOF
        """.trimIndent()
    }

    fun getSampleRlfInputStream(): InputStream {
        // Generates an authentic 3D Carved Box Relief (Workpiece block with raised rectangular border, flat recessed plateau, and carved crest)
        val gridW = 90
        val gridH = 90
        val header = ByteArray(64)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x524C4620) // Magic 'RLF '
        buf.putInt(gridW) // explicit Int gridWidth
        buf.putInt(gridH) // explicit Int gridHeight
        buf.putFloat(150f) // physical sizeX in mm
        buf.putFloat(150f) // physical sizeY in mm

        val payloadSize = gridW * gridH * 4
        val dataBytes = ByteArray(64 + payloadSize)
        System.arraycopy(header, 0, dataBytes, 0, 64)

        val dataBuf = ByteBuffer.wrap(dataBytes, 64, payloadSize).order(ByteOrder.LITTLE_ENDIAN)

        for (x in 0 until gridW) {
            for (y in 0 until gridH) {
                // Construct a 3D Box Relief:
                // Border margin: 0 to 6 and 84 to 90
                val edgeDistX = minOf(x, gridW - 1 - x)
                val edgeDistY = minOf(y, gridH - 1 - y)
                val minEdge = minOf(edgeDistX, edgeDistY)

                val zVal = when {
                    minEdge < 4 -> 0f // Base workpiece stock
                    minEdge in 4..10 -> (minEdge - 4) * 2f // Beveled outer slope of box rim
                    minEdge in 11..16 -> 12f // Raised box top rim
                    minEdge in 17..20 -> 12f - (minEdge - 16) * 1.5f // Step drop into box cavity
                    else -> {
                        // Center plateau with raised rectangular emblem
                        val cx = abs(x - 45)
                        val cy = abs(y - 45)
                        val boxDist = max(cx, cy)
                        if (boxDist < 12) {
                            8f + (12 - boxDist) * 0.4f + cos(cx * 0.4f) * 0.8f
                        } else {
                            6.0f // Flat box floor
                        }
                    }
                }
                dataBuf.putFloat(zVal)
            }
        }
        return ByteArrayInputStream(dataBytes)
    }
}
