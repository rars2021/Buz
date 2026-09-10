package com.buz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.buz.core.Rose
import kotlin.math.*

data class RoseStyle(
    val background: Color = Color.White,
    val frame: Color = Color(0xFF222222),
    val grid: Color = Color(0xFFCCCCCC),
    val petal: Color = Color(0xFF4477CC),
    val label: Color = Color(0xFF333333),
    val stroke: Float = 1.5f,
)

@Composable
fun RoseView(
    bins: List<Rose.Bin>,
    modifier: Modifier = Modifier,
    style: RoseStyle = RoseStyle(),
    rings: Int = 4,
    axialInput: Boolean = true,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(size.width, size.height) / 2f * 0.82f
        val maxCount = (bins.maxOfOrNull { it.count } ?: 1.0).coerceAtLeast(1.0)
        val totalCount = bins.sumOf { it.count } / (if (axialInput) 2.0 else 1.0)
        val dominantBin = bins.maxByOrNull { it.count }

        // Grid rings + ring labels
        val labelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(255, 68, 68, 68)
            textSize = 22f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.LEFT
        }
        val cardinalPaint = android.graphics.Paint(labelPaint).apply {
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val infoPaint = android.graphics.Paint(labelPaint).apply {
            textSize = 24f
            textAlign = android.graphics.Paint.Align.LEFT
        }

        for (i in 1..rings) {
            val rr = r * i / rings
            drawCircle(style.grid, radius = rr, center = Offset(cx, cy), style = Stroke(1f))
            val n = (maxCount * i / rings)
            drawContext.canvas.nativeCanvas.drawText(
                fmtCount(n), cx + 4f, cy - rr - 2f, labelPaint
            )
        }

        // Cardinal spokes
        for ((deg, label) in listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")) {
            val a = Math.toRadians((90 - deg).toDouble())
            drawLine(
                style.grid,
                Offset(cx, cy),
                Offset(cx + (r * cos(a)).toFloat(), cy - (r * sin(a)).toFloat()),
                strokeWidth = 1f
            )
            val lx = cx + ((r + 18f) * cos(a)).toFloat()
            val ly = cy - ((r + 18f) * sin(a)).toFloat() + cardinalPaint.textSize * 0.35f
            drawContext.canvas.nativeCanvas.drawText(label, lx, ly, cardinalPaint)
        }

        // Petals
        for (b in bins) {
            if (b.count <= 0) continue
            val len = (r * b.count / maxCount).toFloat()
            val a1 = Math.toRadians(90.0 - b.startDeg)
            val a2 = Math.toRadians(90.0 - b.endDeg)
            val path = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + (len * cos(a1)).toFloat(), cy - (len * sin(a1)).toFloat())
                val steps = 6
                for (k in 1..steps) {
                    val t = k.toDouble() / steps
                    val a = a1 + (a2 - a1) * t
                    lineTo(cx + (len * cos(a)).toFloat(), cy - (len * sin(a)).toFloat())
                }
                close()
            }
            drawPath(path, color = style.petal.copy(alpha = 0.55f))
            drawPath(path, color = style.petal, style = Stroke(style.stroke))
        }

        drawCircle(style.frame, radius = r, center = Offset(cx, cy), style = Stroke(style.stroke))

        // Info block bottom-left
        val native = drawContext.canvas.nativeCanvas
        val lineH = infoPaint.textSize + 4f
        var y0 = size.height - 4f - lineH * 3
        native.drawText("N=${fmtCount(totalCount)}", 8f, y0, infoPaint); y0 += lineH
        native.drawText("bins=${bins.size}  max=${fmtCount(maxCount)}", 8f, y0, infoPaint); y0 += lineH
        if (dominantBin != null) {
            val mid = 0.5 * (dominantBin.startDeg + dominantBin.endDeg)
            val pct = if (totalCount > 0) 100.0 * dominantBin.count / (totalCount * if (axialInput) 2 else 1) else 0.0
            native.drawText("dominante ≈ ${"%.0f".format(mid)}°  (${"%.1f".format(pct)}%)", 8f, y0, infoPaint)
        }
    }
}

private fun fmtCount(v: Double): String =
    if (v >= 10 || v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
