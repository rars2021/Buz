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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.buz.core.*
import kotlin.math.min
import kotlin.math.sin

data class StereonetStyle(
    val background: Color = Color.White,
    val frame: Color = Color(0xFF222222),
    val gridMinor: Color = Color(0xFFCCCCCC),
    val gridMajor: Color = Color(0xFF888888),
    val label: Color = Color(0xFF444444),
    val pole: Color = Color(0xFF0055AA),
    val plane: Color = Color(0xFFAA2222),
    val poleRadiusPx: Float = 4f,
    val strokeWidthPx: Float = 1.5f,
)

// Sets — warm/varied palette (greens/oranges/purples/teals).
private val setPalette = listOf(
    Color(0xFF1B7F3B), Color(0xFFB05E00), Color(0xFF7A1F9E),
    Color(0xFF0E6E8C), Color(0xFF9E1B4E), Color(0xFF556B00),
)

// Auto-families — cool/blue palette so they never look the same as manual sets.
private val familyPalette = listOf(
    Color(0xFF1F4EA8), Color(0xFF00A3B4), Color(0xFF4A2AA8),
    Color(0xFF006A6A), Color(0xFF3F7DF7), Color(0xFF2E4A6E),
)

data class StereonetPlot(
    val poles: List<Pole> = emptyList(),
    val planes: List<Pole> = emptyList(),
    val densityGrid: Density.Grid? = null,
    val densityLevels: List<Double> = listOf(2.0, 4.0, 6.0, 8.0, 10.0),
    val projection: ProjectionType = ProjectionType.EQUAL_AREA,
    val showGrid: Boolean = true,
    val showPoles: Boolean = true,
    val showLabels: Boolean = true,
    val filledDensity: Boolean = false,
    val windows: List<SetWindow> = emptyList(),
    val poleSetIndex: IntArray = IntArray(0),
    /** When true, poles are coloured with the family palette (cool tones)
     *  and cluster diamonds match; when false, they use the sets palette. */
    val useFamilyPalette: Boolean = false,
    val clusterCentres: List<Pole> = emptyList(),
    val clusterAngleDeg: Double? = null,
    val showClusterRings: Boolean = true,
    val familyMeanPlanes: List<Pole> = emptyList(),
    /** Optional watershed basins per grid cell (flat gridSize², -1 = none).
     *  When present, drawn as one contour per basin (in each family's colour)
     *  instead of the circular cone rings. */
    val familyBasins: IntArray = IntArray(0),
    val familyBasinsGridSize: Int = 0,
    val familyBasinCount: Int = 0,
    val scanlineAxis: Pole? = null,
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
        val r = min(size.width, size.height) / 2f * 0.90f
        fun px(x: Double) = cx + (x * r).toFloat()
        fun py(y: Double) = cy - (y * r).toFloat()

        drawRect(color = style.background, topLeft = Offset(0f, 0f), size = Size(size.width, size.height))

        if (plot.filledDensity && plot.densityGrid != null) {
            drawHeatmap(plot.densityGrid, cx, cy, r)
        }

        if (plot.showGrid) drawGrid(cx, cy, r, plot.projection, style)

        plot.densityGrid?.let { grid ->
            val levels = adaptiveLevels(grid, plot.densityLevels)
            for ((li, level) in levels.withIndex()) {
                val path = marchingSquaresPath(grid, level, cx, cy, r)
                drawPath(
                    path,
                    color = Color(0xFF1F3A6E).copy(alpha = (0.35f + 0.10f * li).coerceAtMost(0.9f)),
                    style = Stroke(width = style.strokeWidthPx)
                )
            }
        }

        // Set windows
        for ((i, w) in plot.windows.withIndex()) {
            val col = setPalette[i % setPalette.size]
            val fx0 = px(w.xMin); val fy0 = py(w.yMax)
            val fx1 = px(w.xMax); val fy1 = py(w.yMin)
            val topLeft = Offset(fx0, fy0)
            val sizeRect = Size(fx1 - fx0, fy1 - fy0)
            when (w.shape) {
                WindowShape.RECT -> {
                    drawRect(col.copy(alpha = 0.12f), topLeft = topLeft, size = sizeRect)
                    drawRect(col, topLeft = topLeft, size = sizeRect, style = Stroke(style.strokeWidthPx))
                }
                WindowShape.ELLIPSE -> {
                    drawOval(col.copy(alpha = 0.12f), topLeft = topLeft, size = sizeRect)
                    drawOval(col, topLeft = topLeft, size = sizeRect, style = Stroke(style.strokeWidthPx))
                }
            }
        }

        for (pole in plot.planes) {
            val pts = Projection.greatCircle(pole, plot.projection)
            drawPolyline(pts, style.plane, style.strokeWidthPx, ::px, ::py)
        }

        drawCircle(color = style.frame, radius = r, center = Offset(cx, cy), style = Stroke(width = style.strokeWidthPx))
        drawLine(style.frame, Offset(cx, cy - r), Offset(cx, cy - r - 12f), strokeWidth = style.strokeWidthPx)

        if (plot.showLabels) drawLabels(cx, cy, r, plot.projection, style)

        if (plot.clusterCentres.isNotEmpty()) {
            // Prefer watershed basin outlines when available; otherwise fall back
            // to the circular cone ring around each centre.
            val useBasins = plot.familyBasins.isNotEmpty() &&
                plot.familyBasinsGridSize > 1 && plot.familyBasinCount > 0
            if (plot.showClusterRings) {
                if (useBasins) {
                    drawBasinContours(
                        plot.familyBasins, plot.familyBasinsGridSize, plot.familyBasinCount,
                        cx, cy, r,
                    )
                } else if (plot.clusterAngleDeg != null && plot.clusterAngleDeg > 0.0) {
                    for ((i, c) in plot.clusterCentres.withIndex()) {
                        val col = familyPalette[i % familyPalette.size]
                        val pts = Projection.smallCircle(c, Math.toRadians(plot.clusterAngleDeg), plot.projection)
                        drawPolyline(pts, col, style.strokeWidthPx, ::px, ::py)
                    }
                }
            }
            for ((i, c) in plot.clusterCentres.withIndex()) {
                val col = familyPalette[i % familyPalette.size]
                val (x, y) = Projection.project(c, plot.projection)
                val fx = px(x); val fy = py(y)
                drawDiamond(fx, fy, 8f, col)
            }
        }
        if (plot.familyMeanPlanes.isNotEmpty()) {
            for ((i, meanPole) in plot.familyMeanPlanes.withIndex()) {
                val col = familyPalette[i % familyPalette.size]
                val pts = Projection.greatCircle(meanPole, plot.projection)
                drawPolyline(pts, col, style.strokeWidthPx + 0.5f, ::px, ::py)
            }
        }

        plot.scanlineAxis?.let { s ->
            val (x, y) = Projection.project(s, plot.projection)
            val fx = px(x); val fy = py(y)
            val col = Color(0xFF008B00)
            drawCircle(col, radius = 8f, center = Offset(fx, fy), style = Stroke(2.5f))
            drawLine(col, Offset(fx - 12f, fy), Offset(fx + 12f, fy), strokeWidth = 2.5f)
            drawLine(col, Offset(fx, fy - 12f), Offset(fx, fy + 12f), strokeWidth = 2.5f)
        }

        if (plot.showPoles) {
            val palette = if (plot.useFamilyPalette) familyPalette else setPalette
            for ((i, pole) in plot.poles.withIndex()) {
                val (x, y) = Projection.project(pole, plot.projection)
                val setIdx = plot.poleSetIndex.getOrNull(i) ?: -1
                val col = if (setIdx >= 0) palette[setIdx % palette.size] else style.pole
                if (plot.useFamilyPalette && setIdx >= 0) {
                    // Family poles: filled circle with a small ring so they read as
                    // distinctly from manual-set poles even at a glance.
                    drawCircle(color = col, radius = style.poleRadiusPx, center = Offset(px(x), py(y)))
                    drawCircle(color = col, radius = style.poleRadiusPx + 2f, center = Offset(px(x), py(y)),
                        style = Stroke(1f))
                } else {
                    drawCircle(color = col, radius = style.poleRadiusPx, center = Offset(px(x), py(y)))
                }
            }
        }

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
    val px: (Double) -> Float = { cx + (it * r).toFloat() }
    val py: (Double) -> Float = { cy - (it * r).toFloat() }

    // Latitudes — small circles around the horizontal N-S axis.
    val axisNS = Pole.fromTrendPlunge(0.0, 0.0)
    for (dip in step..80 step step) {
        val alpha = Math.toRadians(dip.toDouble())
        val pts = Projection.smallCircle(axisNS, alpha, projection)
        drawPolyline(pts, if (dip % 30 == 0) style.gridMajor else style.gridMinor, 1f, px, py)
    }

    // Meridians — great circles of planes striking N-S dipping east and west.
    // Their arcs converge at N and S. The polyline jump-detector prevents the
    // hemisphere-flip artefact for steep dips.
    for (dip in step..80 step step) {
        val poleE = Pole.poleOfPlane(dip.toDouble(), 90.0)
        val poleW = Pole.poleOfPlane(dip.toDouble(), 270.0)
        val col = if (dip % 30 == 0) style.gridMajor else style.gridMinor
        drawPolyline(Projection.greatCircle(poleE, projection), col, 1f, px, py)
        drawPolyline(Projection.greatCircle(poleW, projection), col, 1f, px, py)
    }

    // The two central axes: horizontal E–W diameter and vertical N–S diameter.
    drawLine(style.gridMajor, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = 1f)
    drawLine(style.gridMajor, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = 1f)
}

/** Cardinal points + trend labels every 30° + one dip label on the North axis. */
private fun DrawScope.drawLabels(cx: Float, cy: Float, r: Float, projection: ProjectionType, style: StereonetStyle) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(255, 68, 68, 68)
        textSize = 26f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val small = android.graphics.Paint(paint).apply { textSize = 20f }

    val canvas = drawContext.canvas.nativeCanvas

    // Trend labels around the perimeter every 30°
    for (t in 0 until 360 step 30) {
        val ang = Math.toRadians(t.toDouble())
        val rr = r + 22f
        val tx = cx + (rr * sin(ang)).toFloat()
        val ty = cy - (rr * kotlin.math.cos(ang)).toFloat() + paint.textSize * 0.35f
        val lbl = when (t) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "${t}°" }
        val usePaint = if (t % 90 == 0) paint else small
        canvas.drawText(lbl, tx, ty, usePaint)
    }

    // Dip labels along the North axis (top). For equal-area: r = sqrt(1 - sin(dipDeg))
    for (dip in 30..60 step 30) {
        val z = sin(Math.toRadians(dip.toDouble()))
        val rr = when (projection) {
            ProjectionType.EQUAL_AREA -> kotlin.math.sqrt((1.0 - z).coerceAtLeast(0.0))
            ProjectionType.EQUAL_ANGLE -> {
                val denom = 1.0 + z; if (denom == 0.0) 0.0 else kotlin.math.sqrt(1.0 - z * z) / denom
            }
        }
        val ty = cy - (rr * r).toFloat() - 4f
        canvas.drawText("${dip}°", cx + 12f, ty, small)
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
    var prevX = 0.0; var prevY = 0.0
    val jumpSq = 0.15 * 0.15
    // Any endpoint whose normalized r > 0.9 gets pushed radially out to r = 1
    // so arcs touch the perimeter cleanly instead of stopping short. Interior
    // points are untouched — only ends of sub-segments (moveTo, close-before-break,
    // and the very last point) go through this to avoid distorting the arc's body.
    fun extendXY(x: Double, y: Double): Pair<Double, Double> {
        val d = kotlin.math.sqrt(x * x + y * y)
        return if (d > 0.9 && d > 1e-6) (x / d) to (y / d) else x to y
    }
    for ((x, y) in pts) {
        val inside = x * x + y * y <= 1.0001
        if (!inside) { started = false; continue }
        val breakHere = if (!started) true else {
            val dx = x - prevX; val dy = y - prevY
            dx * dx + dy * dy > jumpSq
        }
        if (breakHere) {
            // Close the previous open sub-segment by nudging its last point
            // to the perimeter if it was near it.
            if (started) {
                val (ex, ey) = extendXY(prevX, prevY)
                if (ex != prevX || ey != prevY) path.lineTo(px(ex), py(ey))
            }
            // Start the new sub-segment. If the start is near the perimeter,
            // begin at the perimeter and then draw into the interior.
            val (sx, sy) = extendXY(x, y)
            if (sx != x || sy != y) {
                path.moveTo(px(sx), py(sy))
                path.lineTo(px(x), py(y))
            } else {
                path.moveTo(px(x), py(y))
            }
            started = true
        } else {
            path.lineTo(px(x), py(y))
        }
        prevX = x; prevY = y
    }
    // Close the final open sub-segment.
    if (started) {
        val (ex, ey) = extendXY(prevX, prevY)
        if (ex != prevX || ey != prevY) path.lineTo(px(ex), py(ey))
    }
    drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
}

private fun DrawScope.drawDiamond(cx: Float, cy: Float, half: Float, color: Color) {
    val path = Path().apply {
        moveTo(cx, cy - half)
        lineTo(cx + half, cy)
        lineTo(cx, cy + half)
        lineTo(cx - half, cy)
        close()
    }
    drawPath(path, color = color.copy(alpha = 0.3f))
    drawPath(path, color = color, style = Stroke(width = 2.5f))
}

/** Draw the marching-squares boundary of each basin, in that family's colour.
 *  Segment endpoints in normalized space near the perimeter (r > 0.88) are
 *  pushed radially out to r = 1.0 so contours join the frame cleanly instead
 *  of terminating one grid cell short. */
private fun DrawScope.drawBasinContours(
    basins: IntArray, n: Int, famCount: Int,
    cx: Float, cy: Float, r: Float,
) {
    if (n < 2 || basins.size < n * n) return
    val step = 2.0 / (n - 1)
    fun scr(fx: Double, fy: Double): Offset {
        val d = kotlin.math.sqrt(fx * fx + fy * fy)
        val (nx, ny) = if (d > 0.88 && d > 1e-6) {
            val s = 1.0 / d
            fx * s to fy * s
        } else fx to fy
        return Offset(cx + (nx * r).toFloat(), cy - (ny * r).toFloat())
    }
    for (fam in 0 until famCount) {
        val col = familyPalette[fam % familyPalette.size]
        val path = Path()
        for (iy in 0 until n - 1) for (ix in 0 until n - 1) {
            val b00 = basins[iy * n + ix] == fam
            val b10 = basins[iy * n + ix + 1] == fam
            val b01 = basins[(iy + 1) * n + ix] == fam
            val b11 = basins[(iy + 1) * n + ix + 1] == fam
            val idx = (if (b00) 1 else 0) or (if (b10) 2 else 0) or
                      (if (b11) 4 else 0) or (if (b01) 8 else 0)
            if (idx == 0 || idx == 15) continue
            val x0 = -1.0 + ix * step; val y0 = -1.0 + iy * step
            val x1 = x0 + step; val y1 = y0 + step
            val eBot = scr(x0 + step * 0.5, y0)
            val eRight = scr(x1, y0 + step * 0.5)
            val eTop = scr(x0 + step * 0.5, y1)
            val eLeft = scr(x0, y0 + step * 0.5)
            when (idx) {
                1, 14 -> { path.moveTo(eBot.x, eBot.y); path.lineTo(eLeft.x, eLeft.y) }
                2, 13 -> { path.moveTo(eBot.x, eBot.y); path.lineTo(eRight.x, eRight.y) }
                3, 12 -> { path.moveTo(eLeft.x, eLeft.y); path.lineTo(eRight.x, eRight.y) }
                4, 11 -> { path.moveTo(eTop.x, eTop.y); path.lineTo(eRight.x, eRight.y) }
                6, 9  -> { path.moveTo(eBot.x, eBot.y); path.lineTo(eTop.x, eTop.y) }
                7, 8  -> { path.moveTo(eLeft.x, eLeft.y); path.lineTo(eTop.x, eTop.y) }
                5 -> { path.moveTo(eLeft.x, eLeft.y); path.lineTo(eBot.x, eBot.y)
                       path.moveTo(eTop.x, eTop.y); path.lineTo(eRight.x, eRight.y) }
                10 -> { path.moveTo(eLeft.x, eLeft.y); path.lineTo(eTop.x, eTop.y)
                        path.moveTo(eBot.x, eBot.y); path.lineTo(eRight.x, eRight.y) }
            }
        }
        drawPath(path, color = col, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawHeatmap(grid: Density.Grid, cx: Float, cy: Float, r: Float) {
    val n = grid.size
    if (n < 2) return
    val cell = (2f * r) / (n - 1)
    val maxSig = grid.maxSigma.coerceAtLeast(1.0)
    // Only paint cells strictly above 25% of the peak sigma. The Kamb counting
    // circle can be large (up to ~55° half-angle for small N) so lower cells
    // are just background noise from the counting circle's tail, not real peaks.
    val threshold = maxSig * 0.25
    for (iy in 0 until n) {
        for (ix in 0 until n) {
            val v = grid.values[iy][ix]
            if (v.isNaN() || v <= threshold) continue
            val t = ((v - threshold) / (maxSig - threshold)).toFloat().coerceIn(0f, 1f)
            val col = heatColor(t)
            val gx = -1.0 + ix * 2.0 / (n - 1)
            val gy = -1.0 + iy * 2.0 / (n - 1)
            val sx = cx + (gx * r).toFloat() - cell / 2f
            val sy = cy - (gy * r).toFloat() - cell / 2f
            drawRect(col, topLeft = Offset(sx, sy), size = Size(cell, cell))
        }
    }
}

private fun heatColor(t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    val r: Float; val g: Float; val b: Float
    when {
        tt < 0.33f -> { val u = tt / 0.33f; r = 0.0f;              g = 0.4f * u;          b = 0.9f }
        tt < 0.66f -> { val u = (tt - 0.33f) / 0.33f; r = u;       g = 0.4f + 0.5f * u;   b = 0.9f - 0.9f * u }
        else       -> { val u = (tt - 0.66f) / 0.34f; r = 1.0f;    g = 0.9f - 0.7f * u;   b = 0.0f }
    }
    val a = (0.35f + 0.55f * tt).coerceIn(0f, 0.95f)
    return Color(r, g, b, a)
}

private fun adaptiveLevels(grid: Density.Grid, requested: List<Double>): List<Double> {
    val maxSig = grid.maxSigma
    if (maxSig <= 0.0) return emptyList()
    val fitting = requested.filter { it <= maxSig }
    if (fitting.size >= 3) return fitting
    return listOf(0.15, 0.35, 0.6, 0.85).map { it * maxSig }
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
