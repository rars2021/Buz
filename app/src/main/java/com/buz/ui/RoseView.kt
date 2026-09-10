package com.buz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.buz.core.Rose
import kotlin.math.*

data class RoseStyle(
    val background: Color = Color.White,
    val frame: Color = Color(0xFF222222),
    val grid: Color = Color(0xFFCCCCCC),
    val petal: Color = Color(0xFF4477CC),
    val stroke: Float = 1.5f,
)

@Composable
fun RoseView(
    bins: List<Rose.Bin>,
    modifier: Modifier = Modifier,
    style: RoseStyle = RoseStyle(),
    rings: Int = 4,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(size.width, size.height) / 2f * 0.9f
        val maxCount = (bins.maxOfOrNull { it.count } ?: 1.0).coerceAtLeast(1.0)

        // Concentric grid rings
        for (i in 1..rings) {
            drawCircle(style.grid, radius = r * i / rings, center = Offset(cx, cy), style = Stroke(1f))
        }
        // Cardinal ticks
        for (deg in listOf(0, 90, 180, 270)) {
            val a = Math.toRadians((90 - deg).toDouble())
            drawLine(
                style.grid,
                Offset(cx, cy),
                Offset(cx + (r * cos(a)).toFloat(), cy - (r * sin(a)).toFloat()),
                strokeWidth = 1f
            )
        }

        // Petals
        for (b in bins) {
            if (b.count <= 0) continue
            val len = (r * b.count / maxCount).toFloat()
            // Rose convention: 0° = North, growing clockwise.
            val a1 = Math.toRadians(90.0 - b.startDeg)
            val a2 = Math.toRadians(90.0 - b.endDeg)
            val path = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + (len * cos(a1)).toFloat(), cy - (len * sin(a1)).toFloat())
                // Sweep a small arc along the outer edge for a smoother petal.
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
    }
}
