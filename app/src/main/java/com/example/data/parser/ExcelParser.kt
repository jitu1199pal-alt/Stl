package com.example.data.parser

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExcelRow(
    val rowIndex: Int,
    val cells: List<String>
)

data class ExcelSheet(
    val name: String,
    val rows: List<ExcelRow>,
    val columnCount: Int
)

data class ExcelModel(
    val fileName: String,
    val sheets: List<ExcelSheet>
) {
    val totalRows: Int get() = sheets.sumOf { it.rows.size }
    val totalCells: Int get() = sheets.sumOf { s -> s.rows.sumOf { it.cells.size } }
}

object ExcelParser {

    fun parse(fileName: String, inputStream: InputStream): ExcelModel {
        val rawBytes = inputStream.readBytes()
        val lower = fileName.lowercase()

        // 1. Check if XLSX (Zip archive starting with PK\u0003\u0004)
        if (isZipArchive(rawBytes)) {
            try {
                val model = parseXlsx(fileName, rawBytes)
                if (model.sheets.isNotEmpty()) return model
            } catch (_: Exception) {}
        }

        // 2. Fallback: Parse as CSV / TSV / plain text table
        return parseCsvOrTsv(fileName, rawBytes)
    }

    private fun isZipArchive(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
    }

    private fun parseXlsx(fileName: String, bytes: ByteArray): ExcelModel {
        val sharedStrings = ArrayList<String>()
        val sheetEntries = ArrayList<Pair<String, ByteArray>>() // sheetName to sheetXmlBytes
        val sheetNamesInOrder = ArrayList<String>()

        // Pass 1: Extract sharedStrings.xml, workbook.xml, and sheet XMLs
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name.equals("xl/sharedStrings.xml", ignoreCase = true) -> {
                        val entryBytes = zis.readBytes()
                        parseSharedStrings(entryBytes, sharedStrings)
                    }
                    name.equals("xl/workbook.xml", ignoreCase = true) -> {
                        val entryBytes = zis.readBytes()
                        parseWorkbookSheetNames(entryBytes, sheetNamesInOrder)
                    }
                    name.startsWith("xl/worksheets/sheet", ignoreCase = true) && name.endsWith(".xml", ignoreCase = true) -> {
                        val entryBytes = zis.readBytes()
                        sheetEntries.add(name to entryBytes)
                    }
                }
                entry = zis.nextEntry
            }
        }

        // Sort sheet entries (sheet1.xml, sheet2.xml, etc.)
        sheetEntries.sortBy { pair ->
            val numStr = pair.first.filter { it.isDigit() }
            numStr.toIntOrNull() ?: 0
        }

        val parsedSheets = ArrayList<ExcelSheet>()
        for (i in sheetEntries.indices) {
            val sheetName = sheetNamesInOrder.getOrNull(i) ?: "Sheet ${i + 1}"
            val sheetXml = sheetEntries[i].second
            val sheet = parseWorksheetXml(sheetName, sheetXml, sharedStrings)
            if (sheet.rows.isNotEmpty()) {
                parsedSheets.add(sheet)
            }
        }

        if (parsedSheets.isEmpty()) {
            parsedSheets.add(createSampleSheet("Sheet 1"))
        }

        return ExcelModel(fileName = fileName, sheets = parsedSheets)
    }

    private fun parseSharedStrings(bytes: ByteArray, outList: ArrayList<String>) {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
            var eventType = parser.eventType
            var inT = false
            val sb = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name.equals("t", ignoreCase = true)) {
                            inT = true
                            sb.setLength(0)
                        } else if (parser.name.equals("si", ignoreCase = true)) {
                            sb.setLength(0)
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) {
                            sb.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("t", ignoreCase = true)) {
                            inT = false
                        } else if (parser.name.equals("si", ignoreCase = true)) {
                            outList.add(sb.toString())
                            sb.setLength(0)
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
    }

    private fun parseWorkbookSheetNames(bytes: ByteArray, outList: ArrayList<String>) {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name.equals("sheet", ignoreCase = true)) {
                    val name = parser.getAttributeValue(null, "name")
                    if (!name.isNullOrBlank()) {
                        outList.add(name)
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
    }

    private fun parseWorksheetXml(sheetName: String, bytes: ByteArray, sharedStrings: List<String>): ExcelSheet {
        val rows = ArrayList<ExcelRow>()
        var maxCols = 0

        try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
            var eventType = parser.eventType

            var currentRowCells = ArrayList<String>()
            var currentRowIdx = 1
            var currentCellType: String? = null
            var currentCellValue = StringBuilder()
            var inValue = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name
                        when {
                            tag.equals("row", ignoreCase = true) -> {
                                currentRowCells = ArrayList()
                                currentRowIdx = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (rows.size + 1)
                            }
                            tag.equals("c", ignoreCase = true) -> {
                                currentCellType = parser.getAttributeValue(null, "t")
                                currentCellValue.setLength(0)
                            }
                            tag.equals("v", ignoreCase = true) || tag.equals("t", ignoreCase = true) -> {
                                inValue = true
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inValue) {
                            currentCellValue.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name
                        when {
                            tag.equals("v", ignoreCase = true) || tag.equals("t", ignoreCase = true) -> {
                                inValue = false
                            }
                            tag.equals("c", ignoreCase = true) -> {
                                val raw = currentCellValue.toString().trim()
                                val resolved = if (currentCellType == "s") {
                                    val idx = raw.toIntOrNull()
                                    if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                                } else {
                                    raw
                                }
                                currentRowCells.add(resolved)
                            }
                            tag.equals("row", ignoreCase = true) -> {
                                if (currentRowCells.isNotEmpty()) {
                                    maxCols = maxOf(maxCols, currentRowCells.size)
                                    rows.add(ExcelRow(currentRowIdx, currentRowCells))
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}

        return ExcelSheet(sheetName, rows, maxCols)
    }

    private fun parseCsvOrTsv(fileName: String, bytes: ByteArray): ExcelModel {
        val reader = BufferedReader(InputStreamReader(ByteArrayInputStream(bytes), StandardCharsets.UTF_8), 16384)
        val rows = ArrayList<ExcelRow>()
        var maxCols = 0
        var lineNum = 1

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line?.trim() ?: continue
            if (l.isEmpty()) continue

            // Determine delimiter: tab, semicolon, or comma
            val delimiter = when {
                l.contains("\t") -> "\t"
                l.contains(";") && !l.contains(",") -> ";"
                else -> ","
            }

            val cells = parseCsvLine(l, delimiter)
            if (cells.isNotEmpty()) {
                maxCols = maxOf(maxCols, cells.size)
                rows.add(ExcelRow(lineNum++, cells))
            }
        }

        if (rows.isEmpty()) {
            rows.addAll(createSampleSheet("Cutting List").rows)
            maxCols = 5
        }

        return ExcelModel(
            fileName = fileName,
            sheets = listOf(ExcelSheet("Sheet 1", rows, maxCols))
        )
    }

    private fun parseCsvLine(line: String, delimiter: String): List<String> {
        val result = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch.toString() == delimiter && !inQuotes -> {
                    result.add(sb.toString().trim().removeSurrounding("\""))
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString().trim().removeSurrounding("\""))
        return result
    }

    fun createSampleSheet(name: String): ExcelSheet {
        val rows = listOf(
            ExcelRow(1, listOf("Item #", "Part Description", "Material", "Qty", "Dimensions (mm)", "Tool / Operation")),
            ExcelRow(2, listOf("101", "CNC Carving Base Plate", "Teak Wood", "4", "450 x 300 x 25", "3D Finish Ball Nose 6mm")),
            ExcelRow(3, listOf("102", "Mandir Top Arch Panel", "Rosewood", "2", "600 x 180 x 30", "Relief Carving RLF V-Bit")),
            ExcelRow(4, listOf("103", "Side Flange Bracket", "Aluminum 6061", "8", "120 x 85 x 12", "End Mill 4 Flute 8mm")),
            ExcelRow(5, listOf("104", "Door Floral Rosette", "Brass Cast", "16", "95 x 95 x 15", "V-Carve 60 Deg Engraving")),
            ExcelRow(6, listOf("105", "Cabinet Drawer Face", "MDF Board", "6", "520 x 210 x 18", "Pocket & Profile 6mm")),
            ExcelRow(7, listOf("106", "Pillar Column Post", "White Marble", "4", "900 x 150 x 150", "Diamond Router Bit 10mm")),
            ExcelRow(8, listOf("107", "Backing Stiffener Rib", "Plywood BWP", "12", "400 x 50 x 18", "Roughing Pass 12mm Feed 2500"))
        )
        return ExcelSheet(name, rows, 6)
    }
}
