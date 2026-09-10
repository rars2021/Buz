package com.buz.core

import kotlin.math.abs
import kotlin.math.sin

/**
 * Terzaghi bias correction for a linear scanline / borehole.
 *
 * Discontinuities whose poles lie close to the scanline axis are systematically
 * over-sampled; those whose poles are perpendicular are under-sampled. The
 * standard correction weights each measurement by 1 / sin(theta), where theta
 * is the angle between the pole to the plane and the scanline axis.
 *
 * A minimum-angle threshold caps the weight so that near-parallel poles do
 * not blow up. Rocscience DIPS defaults to 15°.
 */
data class ScanlineAxis(val axis: Pole)

object Terzaghi {
    /**
     * Return weights aligned with `poles`. `axes[i]` is the scanline direction
     * used to correct pole `i`; if the caller only has one global axis, pass
     * a list of the same axis repeated.
     */
    fun weights(poles: List<Pole>, axes: List<ScanlineAxis>, minAngleDeg: Double = 15.0): List<Double> {
        require(axes.size == poles.size) { "axes and poles must have equal size" }
        val minSin = sin(Math.toRadians(minAngleDeg))
        return poles.mapIndexed { i, p ->
            val dot = abs(p.v.normalized().dot(axes[i].axis.v.normalized())).coerceIn(0.0, 1.0)
            // angle between pole and axis; the "bias" angle from the plane is 90 - that.
            // Under-sampling factor = sin(bias) = cos(angle between pole and axis) = dot.
            // Correction weight = 1 / sin(bias) = 1 / dot, capped.
            val s = maxOf(dot, minSin)
            1.0 / s
        }
    }
}
