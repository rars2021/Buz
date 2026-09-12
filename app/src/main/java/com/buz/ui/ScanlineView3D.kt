package com.buz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.buz.core.Measurement
import kotlin.math.*

/**
 * 3D cross-section of the (single implicit) scanline and its discontinuities.
 *
 * Each measurement with a non-null distance becomes a small disk oriented by
 * its pole (dip / dip direction), placed along the scanline at its distance
 * from the origin. Camera is orthographic with painter's-algorithm depth.
 *
 * Gestures
 *   • 1 finger drag  → pan  (translate camera parallel to view plane)
 *   • 2 finger pinch → zoom (0.5×–4×, defaults to 2×)
 *   • 2 finger drag  → orbit (yaw + pitch)
 *
 * Coordinate system: x = East, y = North, z = Up. Trend is azimuth from
 * North (clockwise), plunge is angle below horizontal.
 */
@Composable
fun ScanlineView3D(
    measurements: List<Measurement>,
    scanlineTrendDeg: Double,
    scanlinePlungeDeg: Double,
    modifier: Modifier = Modifier,
) {
    var yaw by remember { mutableStateOf(0.6) }
    var pitch by remember { mutableStateOf(-0.35) }
    var zoomLevel by remember { mutableStateOf(2f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    Canvas(
        modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val activePointers = event.changes.count { it.pressed }
                    val panChange = event.calculatePan()
                    val zoomChange = event.calculateZoom()
                    when {
                        activePointers >= 2 -> {
                            if (zoomChange != 1f) {
                                zoomLevel = (zoomLevel * zoomChange).coerceIn(0.5f, 4f)
                            }
                            // With both fingers down, translational motion of the
                            // centroid rotates the camera; pinch handles zoom
                            // independently of that motion.
                            yaw += panChange.x * 0.01
                            pitch = (pitch - panChange.y * 0.01).coerceIn(-PI / 2 + 0.05, PI / 2 - 0.05)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        activePointers == 1 -> {
                            // Single-finger drag pans the view without changing angle.
                            panX += panChange.x
                            panY += panChange.y
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    ) {
        drawStereoScene(measurements, scanlineTrendDeg, scanlinePlungeDeg,
            yaw, pitch, zoomLevel, panX, panY)
    }
}

private fun DrawScope.drawStereoScene(
    measurements: List<Measurement>,
    scanlineTrendDeg: Double,
    scanlinePlungeDeg: Double,
    yaw: Double,
    pitch: Double,
    zoomLevel: Float,
    panX: Float,
    panY: Float,
) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFFAFAFA), topLeft = Offset(0f, 0f), size = size)

    val scanlineDir = trendPlungeToXYZ(scanlineTrendDeg, scanlinePlungeDeg)

    val hits = measurements.mapNotNull { m ->
        val d = m.distance ?: return@mapNotNull null
        if (!m.a.isFinite() || !m.b.isFinite()) return@mapNotNull null
        val pos = Triple(scanlineDir.first * d, scanlineDir.second * d, scanlineDir.third * d)
        val pole = m.toPole()
        val nvec = trendPlungeToXYZ(pole.trend, pole.plunge)
        Hit(pos, nvec, d)
    }

    val maxDist = hits.maxOfOrNull { abs(it.distanceAlong) } ?: 5.0
    val extent = (maxDist * 2.0).coerceAtLeast(3.0)
    val screenSize = min(w, h)
    val baseScale = (screenSize / extent).toFloat() * 0.75f
    val scale = baseScale * zoomLevel
    val cx = w / 2f + panX
    val cy = h / 2f + panY
    val discRadius = extent * 0.06

    val cyaw = cos(yaw); val syaw = sin(yaw)
    val cpit = cos(pitch); val spit = sin(pitch)

    fun rotated(x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
        val x1 = x * cyaw + y * syaw
        val y1 = -x * syaw + y * cyaw
        val z1 = z
        val y2 = y1 * cpit - z1 * spit
        val z2 = y1 * spit + z1 * cpit
        return Triple(x1, y2, z2)
    }
    fun toScreen(x: Double, y: Double, z: Double): Offset {
        val (rx, ry, _) = rotated(x, y, z)
        return Offset(cx + (rx * scale).toFloat(), cy - (ry * scale).toFloat())
    }
    fun depth(x: Double, y: Double, z: Double): Double = rotated(x, y, z).third

    // 1. Ground grid at z = 0
    val gridCount = 6
    val gridSpan = extent * 0.5
    val gridStep = gridSpan / gridCount
    val gridCol = Color(0xFFD6D6D6)
    for (i in -gridCount..gridCount) {
        val v = i * gridStep
        drawLine(gridCol, toScreen(-gridSpan, v, 0.0), toScreen(gridSpan, v, 0.0), strokeWidth = 1f)
        drawLine(gridCol, toScreen(v, -gridSpan, 0.0), toScreen(v, gridSpan, 0.0), strokeWidth = 1f)
    }

    // 2. Axes E (red), N (green), Up (blue)
    val axLen = gridSpan * 0.6
    drawLine(Color(0xFFB22222), toScreen(0.0, 0.0, 0.0), toScreen(axLen, 0.0, 0.0), strokeWidth = 2.5f)
    drawLine(Color(0xFF1B7F3B), toScreen(0.0, 0.0, 0.0), toScreen(0.0, axLen, 0.0), strokeWidth = 2.5f)
    drawLine(Color(0xFF1F4EA8), toScreen(0.0, 0.0, 0.0), toScreen(0.0, 0.0, axLen), strokeWidth = 2.5f)

    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 26f
        isAntiAlias = true
    }
    val canvas = drawContext.canvas.nativeCanvas
    val labelOff = axLen * 1.08
    val eLab = toScreen(labelOff, 0.0, 0.0)
    val nLab = toScreen(0.0, labelOff, 0.0)
    val zLab = toScreen(0.0, 0.0, labelOff)
    canvas.drawText("E", eLab.x, eLab.y, paint)
    canvas.drawText("N", nLab.x, nLab.y, paint)
    canvas.drawText("Z", zLab.x, zLab.y, paint)

    // 3. Scanline
    val slFwd = (hits.maxOfOrNull { it.distanceAlong } ?: (extent * 0.35)).coerceAtLeast(extent * 0.15)
    val slBack = -slFwd * 0.15
    val slA = Triple(scanlineDir.first * slBack, scanlineDir.second * slBack, scanlineDir.third * slBack)
    val slB = Triple(scanlineDir.first * slFwd * 1.1, scanlineDir.second * slFwd * 1.1, scanlineDir.third * slFwd * 1.1)
    drawLine(Color(0xFFCC1111),
        toScreen(slA.first, slA.second, slA.third),
        toScreen(slB.first, slB.second, slB.third),
        strokeWidth = 3.5f)

    // 4. Discontinuity discs, painter's algorithm (far → near)
    val samples = 28
    val sorted = hits.sortedByDescending { depth(it.pos.first, it.pos.second, it.pos.third) }
    for (hit in sorted) {
        val (u, v) = perpBasis(hit.normal)
        val pts = (0 until samples).map { k ->
            val a = 2 * PI * k / samples
            val px = hit.pos.first + discRadius * (cos(a) * u.first + sin(a) * v.first)
            val py = hit.pos.second + discRadius * (cos(a) * u.second + sin(a) * v.second)
            val pz = hit.pos.third + discRadius * (cos(a) * u.third + sin(a) * v.third)
            toScreen(px, py, pz)
        }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }
        drawPath(path, Color(0x333366CC))
        drawPath(path, Color(0xFF1F3A6E), style = Stroke(1.5f))
        val c = toScreen(hit.pos.first, hit.pos.second, hit.pos.third)
        drawCircle(Color(0xFF1B7F3B), radius = 3.5f, center = c)
    }

    // Zoom badge, top-right
    val zoomPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(160, 40, 40, 40)
        textSize = 24f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.RIGHT
    }
    canvas.drawText("${(zoomLevel * 100).toInt()}%", w - 8f, 22f, zoomPaint)
}

private data class Hit(
    val pos: Triple<Double, Double, Double>,
    val normal: Triple<Double, Double, Double>,
    val distanceAlong: Double,
)

private fun trendPlungeToXYZ(trendDeg: Double, plungeDeg: Double): Triple<Double, Double, Double> {
    val t = Math.toRadians(trendDeg); val p = Math.toRadians(plungeDeg)
    val h = cos(p)
    return Triple(sin(t) * h, cos(t) * h, -sin(p))
}

private fun cross(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>) =
    Triple(
        a.second * b.third - a.third * b.second,
        a.third * b.first - a.first * b.third,
        a.first * b.second - a.second * b.first,
    )

private fun norm(a: Triple<Double, Double, Double>): Triple<Double, Double, Double> {
    val n = sqrt(a.first * a.first + a.second * a.second + a.third * a.third)
    return if (n < 1e-9) a else Triple(a.first / n, a.second / n, a.third / n)
}

private fun perpBasis(
    n: Triple<Double, Double, Double>,
): Pair<Triple<Double, Double, Double>, Triple<Double, Double, Double>> {
    val ref = if (abs(n.first) < 0.9) Triple(1.0, 0.0, 0.0) else Triple(0.0, 1.0, 0.0)
    val u = norm(cross(n, ref))
    val v = cross(n, u)
    return u to v
}
