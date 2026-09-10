package com.buz.core

import java.io.BufferedReader
import java.io.Reader

/**
 * Parser for the classic DIPS 4.x/5.x text data file (.DIP).
 * Format summary (extracted from the format's own header comments):
 *   - Lines starting with `*` are comments.
 *   - Blank lines are ignored.
 *   - First two non-comment/non-blank lines are the two project title strings.
 *   - Next line: number of traverses (integer).
 *   - If traverses > 0, one line per traverse describing it.
 *   - Next line: global orientation flag (0..4).
 *   - Next line: quantity column flag (0 or 1).
 *   - Next line: number of extra data columns and their labels.
 *   - Remaining lines: data rows. Columns:
 *       orient1  orient2  [quantity]  [traverse#]  extra1  extra2  ...
 */
data class DipHeader(
    val title1: String,
    val title2: String,
    val traverses: List<String>,
    val orientationType: OrientationType,
    val hasQuantity: Boolean,
    val extraLabels: List<String>,
)

data class DipData(val header: DipHeader, val measurements: List<Measurement>)

class DipParseException(msg: String) : RuntimeException(msg)

object DipFile {
    fun parse(reader: Reader): DipData {
        val br = if (reader is BufferedReader) reader else BufferedReader(reader)
        val lines = br.useLines { seq ->
            seq.map { it.trimEnd('\r') }
               .filter { line ->
                   val t = line.trimStart()
                   t.isNotEmpty() && !t.startsWith("*")
               }
               .toList()
        }
        val it = lines.iterator()
        fun next(what: String): String {
            if (!it.hasNext()) throw DipParseException("Missing $what")
            return it.next()
        }

        val title1 = next("project title 1")
        val title2 = next("project title 2")

        val nTrav = parseLeadingInt(next("number of traverses"))
            ?: throw DipParseException("Cannot read number of traverses")
        val traverses = (0 until nTrav).map { next("traverse #${it + 1}") }

        val orientFlag = parseLeadingInt(next("global orientation flag"))
            ?: throw DipParseException("Cannot read orientation flag")
        val orientType = when (orientFlag) {
            0 -> OrientationType.DIP_DIPDIR
            1 -> OrientationType.STRIKE_RHR_DIP
            2 -> OrientationType.STRIKE_DIPQ
            3 -> OrientationType.TREND_PLUNGE
            4 -> OrientationType.PLUNGE_TREND
            else -> throw DipParseException("Unknown orientation flag $orientFlag")
        }

        val qFlag = parseLeadingInt(next("quantity flag"))
            ?: throw DipParseException("Cannot read quantity flag")
        val hasQuantity = qFlag != 0

        val extraCountLine = next("extra columns count")
        val nExtras = parseLeadingInt(extraCountLine)
            ?: throw DipParseException("Cannot read number of extra columns")
        val extraLabels = (0 until nExtras).map { next("extra column label #${it + 1}") }

        val header = DipHeader(title1, title2, traverses, orientType, hasQuantity, extraLabels)

        val hasTraverses = traverses.isNotEmpty()
        val measurements = ArrayList<Measurement>()
        while (it.hasNext()) {
            val row = it.next()
            val parts = row.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
            if (parts.size < 2) continue
            val a = parts[0].toDoubleOrNull() ?: continue
            val b = parts[1].toDoubleOrNull() ?: continue
            var idx = 2
            val quantity = if (hasQuantity) {
                val q = parts.getOrNull(idx)?.toDoubleOrNull() ?: 1.0
                idx++
                q
            } else 1.0
            val travId = if (hasTraverses) {
                val t = parts.getOrNull(idx)?.toIntOrNull()
                idx++
                t
            } else null
            val extras = if (idx < parts.size) parts.subList(idx, parts.size).toList() else emptyList()
            measurements += Measurement(a, b, quantity, travId, extras, orientType)
        }
        return DipData(header, measurements)
    }

    /** Read a leading integer token from a line (allows trailing comments). */
    private fun parseLeadingInt(line: String): Int? {
        val token = line.trim().split(Regex("\\s+")).firstOrNull() ?: return null
        return token.toIntOrNull()
    }
}
