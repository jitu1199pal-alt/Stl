package com.example.data.parser

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
G02 X10.0000 Y80.0000 I0.0000 J-10.0000
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
0.0
20
0.0
11
100.0
21
0.0
0
LINE
8
OUTLINE
10
100.0
20
0.0
11
100.0
21
80.0
0
LINE
8
OUTLINE
10
100.0
20
80.0
11
0.0
21
80.0
0
LINE
8
OUTLINE
10
0.0
20
80.0
11
0.0
21
0.0
0
CIRCLE
8
CENTER_HOLE
10
50.0
20
40.0
40
20.0
0
CIRCLE
8
BOLT_HOLES
10
20.0
20
20.0
40
6.0
0
CIRCLE
8
BOLT_HOLES
10
80.0
20
20.0
40
6.0
0
CIRCLE
8
BOLT_HOLES
10
20.0
20
60.0
40
6.0
0
CIRCLE
8
BOLT_HOLES
10
80.0
20
60.0
40
6.0
0
ENDSEC
0
EOF
        """.trimIndent()
    }

    fun getSampleRlfInputStream(): java.io.InputStream {
        val gridW = 80
        val gridH = 80
        val header = ByteArray(64)
        val buf = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x524C4620) // Magic 'RLF '
        buf.putShort(gridW.toShort())
        buf.putShort(gridH.toShort())
        buf.putFloat(120f) // sizeX
        buf.putFloat(120f) // sizeY

        val dataBytes = ByteArray(64 + gridW * gridH * 4)
        System.arraycopy(header, 0, dataBytes, 0, 64)

        val dataBuf = java.nio.ByteBuffer.wrap(dataBytes, 64, gridW * gridH * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val cx = gridW / 2f
        val cy = gridH / 2f

        for (x in 0 until gridW) {
            for (y in 0 until gridH) {
                val dx = (x - cx) / cx
                val dy = (y - cy) / cy
                val r = kotlin.math.sqrt(dx * dx + dy * dy)
                val zVal = if (r < 0.95f) {
                    val petal = kotlin.math.cos(r * 3.14159f * 6f) * 3f + kotlin.math.sin(r * 3.14159f * 10f) * 2f
                    (8f * (1f - r) + petal).coerceIn(0f, 15f)
                } else {
                    0f
                }
                dataBuf.putFloat(zVal)
            }
        }
        return java.io.ByteArrayInputStream(dataBytes)
    }
}
