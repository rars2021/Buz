package com.buz.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.buz.core.*
import kotlin.math.min

/**
 * Off-screen stereonet renderer sharing StereonetView's math but callable
 * from a plain DrawScope (no @Composable). Used by the PNG exporter.
 */
object PngRender {
    private val palette = listOf(
        Color(0xFF1B7F3B), Color(0xFFB05E00), Color(0xFF7A1F9E),
        Color(0xFF0E6E8C), Color(0xFF9E1B4E), Color(0xFF556B00),
    )

    fun render(scope: DrawScope, size: Size, plot: StereonetPlot) {
        with(scope) {
            drawRect(Color.White, topLeft = Offset(0f, 0f), size = size)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = min(size.width, size.height) / 2f * 0.92f
            fun px(x: Double) = cx + (x * r).toFloat()
            fun py(y: Double) = cy - (y * r).toFloat()

            // Grid
            val gridMinor = Color(0xFFCCCCCC); val gridMajor = Color(0xFF888888)
            for (dip in 10..80 step 10) {
                val alpha = Math.toRadians(dip.toDouble())
                val pts = Projection.smallCircle(Pole.fromTrendPlunge(0.0, 0.0), alpha, plot.projection)
                polyline(pts, if (dip % 30 == 0) gridMajor else gridMinor, 1f, ::px, ::py)
            }
            for (t in 10..170 step 10) {
                val p = Pole.fromTrendPlunge((t + 90.0) % 360.0, 0.0)
                val pts = Projection.greatCircle(p, plot.projection)
                polyline(pts, if (t % 30 == 0) gridMajor else gridMinor, 1f, ::px, ::py)
            }

            // Contours
            plot.densityGrid?.let { g ->
                for ((li, lvl) in plot.densityLevels.withIndex()) {
                    val col = Color(0xFF4477CC).copy(alpha = (0.18f + 0.09f * li).coerceAtMost(0.75f))
                    marchingSquares(g, lvl, cx, cy, r, col)
                }
            }

            // Windows
            for ((i, w) in plot.windows.withIndex()) {
                val c = palette[i % palette.size]
                val fx0 = px(w.xMin); val fy0 = py(w.yMax)
                val fx1 = px(w.xMax); val fy1 = py(w.yMin)
                drawRect(c.copy(alpha = 0.12f), Offset(fx0, fy0), Size(fx1 - fx0, fy1 - fy0))
                drawRect(c, Offset(fx0, fy0), Size(fx1 - fx0, fy1 - fy0), style = Stroke(1.5f))
            }

            // Planes
            for (p in plot.planes) {
                val pts = Projection.greatCircle(p, plot.projection)
                polyline(pts, Color(0xFFAA2222), 1.5f, ::px, ::py)
            }

            // Frame
            drawCircle(Color(0xFF222222), r, Offset(cx, cy), style = Stroke(1.5f))

            // Poles
            for ((i, p) in plot.poles.withIndex()) {
                val (x, y) = Projection.project(p, plot.projection)
                val setIdx = plot.poleSetIndex.getOrNull(i) ?: -1
                val col = if (setIdx >= 0) palette[setIdx % palette.size] else Color(0xFF0055AA)
                drawCircle(col, radius = 4f, center = Offset(px(x), py(y)))
            }
        }
    }

    private fun DrawScope.polyline(pts: List<Pair<Double, Double>>, color: Color, stroke: Float, px: (Double) -> Float, py: (Double) -> Float) {
        if (pts.size < 2) return
        val path = Path(); var started = false
        for ((x, y) in pts) {
            if (x * x + y * y > 1.0001) { started = false; continue }
            val fx = px(x); val fy = py(y)
            if (!started) { path.moveTo(fx, fy); started = true } else path.lineTo(fx, fy)
        }
        drawPath(path, color = color, style = Stroke(stroke))
    }

    private fun DrawScope.marchingSquares(grid: Density.Grid, level: Double, cx: Float, cy: Float, r: Float, col: Color) {
        val n = grid.size; val step = 2.0 / (n - 1)
        val path = Path()
        fun scr(fx: Double, fy: Double) = Offset(cx + (fx * r).toFloat(), cy - (fy * r).toFloat())
        for (iy in 0 until n - 1) for (ix in 0 until n - 1) {
            val v00 = grid.values[iy][ix]; val v10 = grid.values[iy][ix + 1]
            val v01 = grid.values[iy + 1][ix]; val v11 = grid.values[iy + 1][ix + 1]
            if (v00.isNaN() || v10.isNaN() || v01.isNaN() || v11.isNaN()) continue
            val idx = (if (v00 >= level) 1 else 0) or (if (v10 >= level) 2 else 0) or
                      (if (v11 >= level) 4 else 0) or (if (v01 >= level) 8 else 0)
            if (idx == 0 || idx == 15) continue
            val x0 = -1.0 + ix * step; val y0 = -1.0 + iy * step
            val x1 = x0 + step; val y1 = y0 + step
            val eBot = { val t = (level - v00) / (v10 - v00); scr(x0 + t * step, y0) }
            val eRight = { val t = (level - v10) / (v11 - v10); scr(x1, y0 + t * step) }
            val eTop = { val t = (level - v01) / (v11 - v01); scr(x0 + t * step, y1) }
            val eLeft = { val t = (level - v00) / (v01 - v00); scr(x0, y0 + t * step) }
            val edges = mutableListOf<Pair<Offset, Offset>>()
            when (idx) {
                1, 14 -> edges += eBot() to eLeft()
                2, 13 -> edges += eBot() to eRight()
                3, 12 -> edges += eLeft() to eRight()
                4, 11 -> edges += eTop() to eRight()
                6, 9 -> edges += eBot() to eTop()
                7, 8 -> edges += eLeft() to eTop()
                5 -> { edges += eLeft() to eBot(); edges += eTop() to eRight() }
                10 -> { edges += eLeft() to eTop(); edges += eBot() to eRight() }
            }
            for ((a, b) in edges) { path.moveTo(a.x, a.y); path.lineTo(b.x, b.y) }
        }
        drawPath(path, col, style = Stroke(1.5f))
    }
}
