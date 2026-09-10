package com.buz.core

/**
 * Serialisation helpers. Kept in pure Kotlin so tests do not need Android.
 */
object CsvExport {
    /** All measurements as CSV — good for round-tripping a `.DIP` to spreadsheet. */
    fun measurements(ms: List<Measurement>): String {
        val sb = StringBuilder("row,a,b,quantity,traverse,dist,type\n")
        for (m in ms) {
            sb.append(
                "%d,%.4f,%.4f,%.4f,%s,%s,%s\n".format(
                    m.rowId,
                    m.a, m.b, m.quantity,
                    m.traverseId?.toString() ?: "",
                    m.distance?.let { "%.4f".format(it) } ?: "",
                    m.type.name
                )
            )
        }
        return sb.toString()
    }

    /** Dips-only variant: no `row`, no `dist`. Useful when spacing along the
     *  scanline is not needed. */
    fun measurementsDipsOnly(ms: List<Measurement>): String {
        if (ms.isEmpty()) return "a,b,quantity,traverse\n"
        val t = ms.first().type
        val (aName, bName) = when (t) {
            OrientationType.DIP_DIPDIR -> "dip" to "dipdir"
            OrientationType.STRIKE_RHR_DIP, OrientationType.STRIKE_DIPQ -> "strike" to "dip"
            OrientationType.TREND_PLUNGE -> "trend" to "plunge"
            OrientationType.PLUNGE_TREND -> "plunge" to "trend"
        }
        val sb = StringBuilder("$aName,$bName,quantity,traverse\n")
        for (m in ms) {
            sb.append(
                "%.4f,%.4f,%.4f,%s\n".format(
                    m.a, m.b, m.quantity,
                    m.traverseId?.toString() ?: ""
                )
            )
        }
        return sb.toString()
    }

    /** Write the current measurements in a form parseable back by `CsvFile`.
     *  Column names use the orientation type of the first measurement so
     *  reopening the file selects the same interpretation. */
    fun measurementsRoundtrip(ms: List<Measurement>): String {
        if (ms.isEmpty()) return "row,a,b,quantity,traverse,dist\n"
        val t = ms.first().type
        val (aName, bName) = when (t) {
            OrientationType.DIP_DIPDIR -> "dip" to "dipdir"
            OrientationType.STRIKE_RHR_DIP, OrientationType.STRIKE_DIPQ -> "strike" to "dip"
            OrientationType.TREND_PLUNGE -> "trend" to "plunge"
            OrientationType.PLUNGE_TREND -> "plunge" to "trend"
        }
        val sb = StringBuilder("row,$aName,$bName,quantity,traverse,dist\n")
        for (m in ms) {
            sb.append(
                "%d,%.4f,%.4f,%.4f,%s,%s\n".format(
                    m.rowId,
                    m.a, m.b, m.quantity,
                    m.traverseId?.toString() ?: "",
                    m.distance?.let { "%.4f".format(it) } ?: ""
                )
            )
        }
        return sb.toString()
    }

    /** Per-set Fisher summary. */
    fun setStatistics(windows: List<SetWindow>, results: List<Fisher.Result?>): String {
        val sb = StringBuilder("set,label,n,mean_trend,mean_plunge,k,cone95\n")
        for ((i, w) in windows.withIndex()) {
            val r = results.getOrNull(i)
            sb.append("${i + 1},${w.label},${r?.n ?: 0},")
            sb.append("${r?.mean?.trend?.let { "%.2f".format(it) } ?: ""},")
            sb.append("${r?.mean?.plunge?.let { "%.2f".format(it) } ?: ""},")
            sb.append("${r?.k?.let { if (it.isFinite()) "%.2f".format(it) else "inf" } ?: ""},")
            sb.append("${r?.cone95Deg?.let { "%.2f".format(it) } ?: ""}\n")
        }
        return sb.toString()
    }
}
