package com.buz.core

import java.io.BufferedReader
import java.io.Reader

/**
 * Reader for CSV / TSV files. Detects delimiter (`,` `;` `\t`) from the header,
 * supports quoted fields with escaped quotes, and blank / comment (`#`) lines.
 * Returns a list of rows (each a list of trimmed cells) plus the header.
 */
data class CsvTable(val header: List<String>, val rows: List<List<String>>) {
    fun column(name: String): List<String>? {
        val idx = header.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (idx < 0) return null
        return rows.map { it.getOrNull(idx) ?: "" }
    }
    fun columnIndex(name: String): Int = header.indexOfFirst { it.equals(name, ignoreCase = true) }
}

object CsvFile {
    fun parse(reader: Reader): CsvTable {
        val br = if (reader is BufferedReader) reader else BufferedReader(reader)
        val raw = br.readLines().map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (raw.isEmpty()) return CsvTable(emptyList(), emptyList())
        val delim = detectDelimiter(raw[0])
        val header = splitLine(raw[0], delim).map { it.trim() }
        val rows = raw.drop(1).map { splitLine(it, delim).map { c -> c.trim() } }
        return CsvTable(header, rows)
    }

    /**
     * Map a CsvTable to Buz measurements. Columns are matched by name (case
     * insensitive). Supported names:
     *   dip / dipdir  |  strike / dip  |  trend / plunge  |  quantity  |  traverse
     * If nothing matches, falls back to positional: col 0 = a, col 1 = b.
     */
    fun toMeasurements(t: CsvTable, defaultType: OrientationType = OrientationType.DIP_DIPDIR): List<Measurement> {
        if (t.rows.isEmpty()) return emptyList()
        val hasDip = t.columnIndex("dip") >= 0
        val hasDipDir = t.columnIndex("dipdir") >= 0 || t.columnIndex("dip_dir") >= 0 || t.columnIndex("dip direction") >= 0
        val hasStrike = t.columnIndex("strike") >= 0
        val hasTrend = t.columnIndex("trend") >= 0
        val hasPlunge = t.columnIndex("plunge") >= 0
        val type = when {
            hasDip && hasDipDir -> OrientationType.DIP_DIPDIR
            hasStrike && hasDip -> OrientationType.STRIKE_RHR_DIP
            hasTrend && hasPlunge -> OrientationType.TREND_PLUNGE
            else -> defaultType
        }
        val (iA, iB) = when (type) {
            OrientationType.DIP_DIPDIR -> t.columnIndex("dip") to
                (listOf("dipdir","dip_dir","dip direction").map { t.columnIndex(it) }.firstOrNull { it >= 0 } ?: -1)
            OrientationType.STRIKE_RHR_DIP -> t.columnIndex("strike") to t.columnIndex("dip")
            OrientationType.STRIKE_DIPQ -> t.columnIndex("strike") to t.columnIndex("dip")
            OrientationType.TREND_PLUNGE -> t.columnIndex("trend") to t.columnIndex("plunge")
            OrientationType.PLUNGE_TREND -> t.columnIndex("plunge") to t.columnIndex("trend")
        }.let { (a, b) ->
            if (a < 0 || b < 0) 0 to 1 else a to b
        }
        val iQty = listOf("quantity","qty","weight").map { t.columnIndex(it) }.firstOrNull { it >= 0 } ?: -1
        val iTrav = listOf("traverse","trav","trav_id").map { t.columnIndex(it) }.firstOrNull { it >= 0 } ?: -1
        val iDist = listOf("dist","distance","d","dist_m").map { t.columnIndex(it) }.firstOrNull { it >= 0 } ?: -1
        val iRow = listOf("row","rowid","id","idx","index").map { t.columnIndex(it) }.firstOrNull { it >= 0 } ?: -1
        return t.rows.mapIndexedNotNull { idx, row ->
            val a = row.getOrNull(iA)?.replace(',', '.')?.toDoubleOrNull() ?: return@mapIndexedNotNull null
            val b = row.getOrNull(iB)?.replace(',', '.')?.toDoubleOrNull() ?: return@mapIndexedNotNull null
            val q = if (iQty >= 0) row.getOrNull(iQty)?.replace(',', '.')?.toDoubleOrNull() ?: 1.0 else 1.0
            val tr = if (iTrav >= 0) row.getOrNull(iTrav)?.toIntOrNull() else null
            val d = if (iDist >= 0) row.getOrNull(iDist)?.replace(',', '.')?.toDoubleOrNull() else null
            val rid = if (iRow >= 0) row.getOrNull(iRow)?.toIntOrNull() ?: (idx + 1) else (idx + 1)
            Measurement(a, b, q, tr, emptyList(), type, rid, distance = d)
        }
    }

    private fun detectDelimiter(line: String): Char {
        val candidates = listOf(';', '\t', ',')
        return candidates.maxByOrNull { c -> line.count { it == c } } ?: ','
    }

    private fun splitLine(line: String, delim: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == delim && !inQuotes -> { out += cur.toString(); cur.setLength(0) }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }
}
