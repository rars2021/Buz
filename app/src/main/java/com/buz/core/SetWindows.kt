package com.buz.core

/**
 * User-drawn set window on the stereographic projection disc.
 * Coordinates are in the projection plane, x=East, y=North, unit circle at r=1.
 * A pole is a member of the window when its projected (px, py) lies inside
 * the rectangle AND inside the unit disc.
 */
data class SetWindow(
    val id: Int,
    val label: String,
    val x0: Double, val y0: Double,
    val x1: Double, val y1: Double,
    val projection: ProjectionType,
) {
    val xMin get() = minOf(x0, x1)
    val xMax get() = maxOf(x0, x1)
    val yMin get() = minOf(y0, y1)
    val yMax get() = maxOf(y0, y1)

    fun contains(pole: Pole): Boolean {
        val (px, py) = Projection.project(pole, projection)
        if (px * px + py * py > 1.0) return false
        return px in xMin..xMax && py in yMin..yMax
    }
}

/** Assign each pole to the first window that contains it, or -1 if none. */
object SetAssignment {
    fun assign(poles: List<Pole>, windows: List<SetWindow>): IntArray {
        val out = IntArray(poles.size) { -1 }
        for ((i, p) in poles.withIndex()) {
            for ((w, win) in windows.withIndex()) {
                if (win.contains(p)) { out[i] = w; break }
            }
        }
        return out
    }

    /** Return one Fisher summary per window, over the poles inside it. */
    fun summarise(poles: List<Pole>, windows: List<SetWindow>): List<Fisher.Result?> {
        val labels = assign(poles, windows)
        return windows.mapIndexed { w, _ ->
            val members = poles.filterIndexed { i, _ -> labels[i] == w }
            Fisher.analyse(members)
        }
    }
}
