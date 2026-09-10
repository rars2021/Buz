package com.buz.core

/** Shape of a set window on the projection disc. */
enum class WindowShape { RECT, ELLIPSE }

/**
 * User-drawn set window on the stereographic projection disc.
 * Coordinates are in the projection plane, x=East, y=North, unit circle at r=1.
 * A pole is a member of the window when its projected (px, py) lies inside
 * the shape (rectangle or ellipse) AND inside the unit disc.
 */
data class SetWindow(
    val id: Int,
    val label: String,
    val x0: Double, val y0: Double,
    val x1: Double, val y1: Double,
    val projection: ProjectionType,
    val shape: WindowShape = WindowShape.RECT,
) {
    val xMin get() = minOf(x0, x1)
    val xMax get() = maxOf(x0, x1)
    val yMin get() = minOf(y0, y1)
    val yMax get() = maxOf(y0, y1)
    val cxBox get() = 0.5 * (xMin + xMax)
    val cyBox get() = 0.5 * (yMin + yMax)
    val rxBox get() = 0.5 * (xMax - xMin)
    val ryBox get() = 0.5 * (yMax - yMin)

    fun contains(pole: Pole): Boolean {
        val (px, py) = Projection.project(pole, projection)
        if (px * px + py * py > 1.0) return false
        return when (shape) {
            WindowShape.RECT -> px in xMin..xMax && py in yMin..yMax
            WindowShape.ELLIPSE -> {
                val rx = rxBox; val ry = ryBox
                if (rx <= 1e-9 || ry <= 1e-9) return false
                val dx = (px - cxBox) / rx
                val dy = (py - cyBox) / ry
                dx * dx + dy * dy <= 1.0
            }
        }
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
