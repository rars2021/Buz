package com.buz.core

import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Minimal .xlsx reader — no Apache POI dependency, keeps the APK slim.
 * Reads the first worksheet as a CsvTable (rows of strings).
 *
 * An .xlsx file is a ZIP with two entries we care about:
 *   xl/sharedStrings.xml  (optional string pool)
 *   xl/worksheets/sheet1.xml  (the first sheet)
 *
 * Handles: inline strings, shared strings, numeric cells, dates as numbers.
 * Ignores: formulas (uses cached value), styles, formatting.
 */
object XlsxFile {
    fun parse(input: InputStream): CsvTable {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                if (!e.isDirectory) entries[e.name] = zis.readBytes()
                zis.closeEntry()
            }
        }
        val shared = entries["xl/sharedStrings.xml"]?.let { readSharedStrings(it) } ?: emptyList()
        val sheetKey = entries.keys.firstOrNull { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            ?: return CsvTable(emptyList(), emptyList())
        val rows = readSheet(entries[sheetKey]!!, shared)
        if (rows.isEmpty()) return CsvTable(emptyList(), emptyList())
        val header = rows[0]
        val data = rows.drop(1).filter { row -> row.any { it.isNotBlank() } }
        return CsvTable(header, data)
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        val handler = object : DefaultHandler() {
            val text = StringBuilder()
            var inSi = false
            var inT = false
            override fun startElement(uri: String?, localName: String?, qName: String, atts: Attributes?) {
                when (qName) {
                    "si" -> { inSi = true; text.setLength(0) }
                    "t" -> inT = true
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "t" -> inT = false
                    "si" -> { inSi = false; out += text.toString() }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inSi && inT) text.appendRange(ch, start, start + length)
            }
        }
        SAXParserFactory.newInstance().newSAXParser().parse(bytes.inputStream(), handler)
        return out
    }

    private fun readSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = ArrayList<ArrayList<String>>()
        val handler = object : DefaultHandler() {
            var currentRow: ArrayList<String>? = null
            var cellRef = ""
            var cellType = ""      // "s" string, "inlineStr", "b" bool, "" number
            val value = StringBuilder()
            var inValue = false
            var inInline = false

            override fun startElement(uri: String?, localName: String?, qName: String, atts: Attributes?) {
                when (qName) {
                    "row" -> currentRow = ArrayList()
                    "c" -> {
                        cellRef = atts?.getValue("r") ?: ""
                        cellType = atts?.getValue("t") ?: ""
                        value.setLength(0)
                    }
                    "v", "t" -> inValue = true
                    "is" -> inInline = true
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "v", "t" -> inValue = false
                    "is" -> inInline = false
                    "c" -> {
                        val raw = value.toString()
                        val resolved = when (cellType) {
                            "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw
                        }
                        placeCell(currentRow!!, cellRef, resolved)
                    }
                    "row" -> {
                        currentRow?.let { rows += it }
                        currentRow = null
                    }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue) value.appendRange(ch, start, start + length)
            }
        }
        SAXParserFactory.newInstance().newSAXParser().parse(bytes.inputStream(), handler)
        // Pad rows to the max width so headers/cells line up.
        val width = rows.maxOfOrNull { it.size } ?: 0
        return rows.map { r -> if (r.size < width) r + List(width - r.size) { "" } else r }
    }

    /** Place a value at the column index derived from a cell reference like "B3". */
    private fun placeCell(row: ArrayList<String>, ref: String, value: String) {
        val letters = ref.takeWhile { it.isLetter() }
        var col = 0
        for (c in letters.uppercase()) col = col * 26 + (c.code - 'A'.code + 1)
        val idx = (col - 1).coerceAtLeast(row.size)
        while (row.size <= idx) row.add("")
        row[idx] = value
    }
}
