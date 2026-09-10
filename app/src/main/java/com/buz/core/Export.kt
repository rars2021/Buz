package com.buz.core

/**
 * Serialisation helpers. Kept in pure Kotlin so tests do not need Android.
 */
object CsvExport {
    /** All measurements as CSV — good for round-tripping a `.DIP` to spreadsheet. */
    fun measurements(ms: List<Measurement>): String {
        val sb = StringBuilder("a,b,quantity,traverse,type\n")
        for (m in ms) {
            sb.append("%.4f,%.4f,%.4f,%s,%s\n".format(m.a, m.b, m.quantity, m.traverseId?.toString() ?: "", m.type.name))
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
