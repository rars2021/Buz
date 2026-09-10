package com.buz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.buz.core.*
import kotlin.math.min

data class StereonetStyle(
    val background: Color = Color.White,
    val frame: Color = Color(0xFF222222),
    val gridMinor: Color = Color(0xFFCCCCCC),
    val gridMajor: Color = Color(0xFF888888),
    val pole: Color = Color(0xFF0055AA),
    val plane: Color = Color(0xFFAA2222),
    val poleRadiusPx: Float = 4f,
    val strokeWidthPx: Float = 1.5f,
)

/** Colours cycled through when several set windows are visible. */
private val setPalette = listOf(
    Color(0xFF1B7F3B), Color(0xFFB05E00), Color(0xFF7A1F9E),
    Color(0xFF0E6E8C), Color(0xFF9E1B4E), Color(0xFF556B00),
)

data class StereonetPlot(
    val poles: List<Pole> = emptyList(),
    val planes: List<Pole> = emptyList(),
    val densityGrid: Density.Grid? = null,
    val densityLevels: List<Double> = listOf(2.0, 4.0, 6.0, 8.0, 10.0),
    val projection: ProjectionType = ProjectionType.EQUAL_AREA,
    val showGrid: Boolean = true,
    val windows: List<SetWindow> = emptyList(),
    val poleSetIndex: IntArray = IntArray(0),   // aligned with poles; -1 = unassigned
)

@Composable
fun StereonetView(
    plot: StereonetPlot,
    modifier: Modifier = Modifier,
    style: StereonetStyle = StereonetStyle(),
    drawingEnabled: Boolean = false,
    onWindowDrawn: ((x0: Double, y0: Double, x1: Double, y1: Double) -> Unit)? = null,
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val gestureMod = if (drawingEnabled && onWindowDrawn != null) {
        Modifier.pointerInput(plot.projection) {
            detectDragGestures(
                onDragStart = { off -> dragStart = off; dragEnd = off },
                onDrag = { change, _ -> dragEnd = change.position },
                onDragEnd = {
                    val s = dragStart; val e = dragEnd
                    if (s != null && e != null && canvasSize != Size.Zero) {
                        val cx = canvasSize.width / 2f
                        val cy = canvasSize.height / 2f
                        val r = min(canvasSize.width, canvasSize.height) / 2f * 0.94f
                        val x0 = (s.x - cx) / r
                        val y0 = -(s.y - cy) / r
                        val x1 = (e.x - cx) / r
                        val y1 = -(e.y - cy) / r
                        onWindowDrawn(x0.toDouble(), y0.toDouble(), x1.toDouble(), y1.toDouble())
                    }
                    dragStart = null; dragEnd = null
                },
                onDragCancel = { dragStart = null; dragEnd = null }
            )
        }
    } else Modifier

    Canvas(modifier = modifier.fillMaxSize().then(gestureMod)) {
        canvasSize = size
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(size.width, size.height) / 2f * 0.94f
        fun px(x: Double) = cx + (x * r).toFloat()
        fun py(y: Double) = cy - (y * r).toFloat()

        drawRect(color = style.background, topLeft = Offset(0f, 0f), size = Size(size.width, size.height))
        if (plot.showGrid) drawGrid(cx, cy, r, plot.projection, style)

        // Density contours
        plot.densityGrid?.let { grid ->
            for ((li, level) in plot.densityLevels.withIndex()) {
                val path = marchingSquaresPath(grid, level, cx, cy, r)
                drawPath(
                    path,
                    color = Color(0xFF4477CC).copy(alpha = (0.18f + 0.09f * li).coerceAtMost(0.75f)),
                    style = Stroke(width = style.strokeWidthPx)
                )
            }
        }

        // Set windows (drawn first so poles paint on top)
        for ((i, w) in plot.windows.withIndex()) {
            val col = setPalette[i % setPalette.size]
            val fx0 = px(w.xMin); val fy0 = py(w.yMax)
            val fx1 = px(w.xMax); val fy1 = py(w.yMin)
            drawRect(col.copy(alpha = 0.12f), topLeft = Offset(fx0, fy0), size = Size(fx1 - fx0, fy1 - fy0))
            drawRect(col, topLeft = Offset(fx0, fy0), size = Size(fx1 - fx0, fy1 - fy0), style = Stroke(style.strokeWidthPx))
        }

        // Great circles for planes
        for (pole in plot.planes) {
            val pts = Projection.greatCircle(pole, plot.projection)
            drawPolyline(pts, style.plane, style.strokeWidthPx, ::px, ::py)
        }

        // Frame
        drawCircle(color = style.frame, radius = r, center = Offset(cx, cy), style = Stroke(width = style.strokeWidthPx))
        drawLine(style.frame, Offset(cx, cy - r), Offset(cx, cy - r - 12f), strokeWidth = style.strokeWidthPx)

        // Poles — coloured by set assignment when available
        for ((i, pole) in plot.poles.withIndex()) {
            val (x, y) = Projection.project(pole, plot.projection)
            val setIdx = plot.poleSetIndex.getOrNull(i) ?: -1
            val col = if (setIdx >= 0) setPalette[setIdx % setPalette.size] else style.pole
            drawCircle(color = col, radius = style.poleRadiusPx, center = Offset(px(x), py(y)))
        }

        // In-progress drag rectangle
        val s = dragStart; val e = dragEnd
        if (drawingEnabled && s != null && e != null) {
            val left = minOf(s.x, e.x); val top = minOf(s.y, e.y)
            val w = kotlin.math.abs(e.x - s.x); val h = kotlin.math.abs(e.y - s.y)
            drawRect(Color(0xFF444444).copy(alpha = 0.15f), topLeft = Offset(left, top), size = Size(w, h))
            drawRect(Color(0xFF444444), topLeft = Offset(left, top), size = Size(w, h), style = Stroke(1.5f))
        }
    }
}

private fun DrawScope.drawGrid(cx: Float, cy: Float, r: Float, projection: ProjectionType, style: StereonetStyle) {
    val step = 10
    for (dip in step..80 step step) {
        val axis = Pole.fromTrendPlunge(0.0, 0.0)
        val alpha = Math.toRadians(dip.toDouble())
        val pts = Projection.smallCircle(axis, alpha, projection)
        drawPolyline(pts, if (dip % 30 == 0) style.gridMajor else style.gridMinor, 1f, { cx + (it * r).toFloat() }, { cy - (it * r).toFloat() })
    }
    for (trend in step..170 step step) {
        val poleOfPlane = Pole.fromTrendPlunge((trend + 90.0) % 360.0, 0.0)
        val pts = Projection.greatCircle(poleOfPlane, projection)
        drawPolyline(pts, if (trend % 30 == 0) style.gridMajor else style.gridMinor, 1f, { cx + (it * r).toFloat() }, { cy - (it * r).toFloat() })
    }
}

private fun DrawScope.drawPolyline(
    pts: List<Pair<Double, Double>>,
    color: Color,
    stroke: Float,
    px: (Double) -> Float,
    py: (Double) -> Float,
) {
    if (pts.size < 2) return
    val path = Path()
    var started = false
    for ((x, y) in pts) {
        val inside = x * x + y * y <= 1.0001
        if (!inside) { started = false; continue }
        val fx = px(x); val fy = py(y)
        if (!started) { path.moveTo(fx, fy); started = true } else path.lineTo(fx, fy)
    }
    drawPath(path, color = color, style = Stroke(width = stroke))
}

private fun marchingSquaresPath(grid: Density.Grid, level: Double, cx: Float, cy: Float, r: Float): Path {
    val path = Path()
    val n = grid.size
    val step = 2.0 / (n - 1)
    fun toScreen(fx: Double, fy: Double) = Offset(cx + (fx * r).toFloat(), cy - (fy * r).toFloat())
    for (iy in 0 until n - 1) {
        for (ix in 0 until n - 1) {
            val v00 = grid.values[iy][ix]
            val v10 = grid.values[iy][ix + 1]
            val v01 = grid.values[iy + 1][ix]
            val v11 = grid.values[iy + 1][ix + 1]
            if (v00.isNaN() || v10.isNaN() || v01.isNaN() || v11.isNaN()) continue
            val b00 = v00 >= level; val b10 = v10 >= level
            val b01 = v01 >= level; val b11 = v11 >= level
            val idx = (if (b00) 1 else 0) or (if (b10) 2 else 0) or (if (b11) 4 else 0) or (if (b01) 8 else 0)
            if (idx == 0 || idx == 15) continue
            val x0 = -1.0 + ix * step; val y0 = -1.0 + iy * step
            val x1 = x0 + step; val y1 = y0 + step
            val eBot = { val t = (level - v00) / (v10 - v00); toScreen(x0 + t * step, y0) }
            val eRight = { val t = (level - v10) / (v11 - v10); toScreen(x1, y0 + t * step) }
            val eTop = { val t = (level - v01) / (v11 - v01); toScreen(x0 + t * step, y1) }
            val eLeft = { val t = (level - v00) / (v01 - v00); toScreen(x0, y0 + t * step) }
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
    }
    return path
}
