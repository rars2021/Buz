package com.buz.core

import kotlin.math.*

/**
 * Orientation types supported by DIPS.
 * The DIP file "global orientation flag" maps as:
 *   0 -> DIP_DIPDIR      (plane: dip, dip direction)
 *   1 -> STRIKE_RHR_DIP  (plane: strike right-hand-rule, dip)
 *   2 -> STRIKE_DIPQ     (plane: strike + dip + quadrant)
 *   3 -> TREND_PLUNGE    (line: trend, plunge)
 *   4 -> PLUNGE_TREND    (line: plunge, trend)
 */
enum class OrientationType { DIP_DIPDIR, STRIKE_RHR_DIP, STRIKE_DIPQ, TREND_PLUNGE, PLUNGE_TREND }

/** Unit vector on the lower hemisphere (z >= 0 points down). */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun norm() = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 { val n = norm(); return if (n == 0.0) this else Vec3(x / n, y / n, z / n) }
    /** Force to lower hemisphere (z >= 0). */
    fun lower(): Vec3 = if (z < 0) Vec3(-x, -y, -z) else this
}

/**
 * A pole to a plane, or a lineation. Always stored as a unit vector
 * pointing into the lower hemisphere. Convention:
 *   x = East, y = North, z = Down.
 */
data class Pole(val v: Vec3) {
    val trend: Double get() {
        // trend = azimuth from North measured clockwise (0..360)
        val t = Math.toDegrees(atan2(v.x, v.y))
        return (t + 360.0) % 360.0
    }
    val plunge: Double get() = Math.toDegrees(asin(v.z.coerceIn(-1.0, 1.0)))

    companion object {
        /** Build a downward unit vector from trend+plunge (both in degrees). */
        fun fromTrendPlunge(trend: Double, plunge: Double): Pole {
            val t = Math.toRadians(trend)
            val p = Math.toRadians(plunge)
            val cp = cos(p)
            return Pole(Vec3(cp * sin(t), cp * cos(t), sin(p)).lower())
        }

        /** Pole to a plane defined by dip and dip-direction (degrees). */
        fun poleOfPlane(dip: Double, dipDir: Double): Pole {
            // pole plunges (90 - dip) in the direction opposite to dip direction
            val trend = (dipDir + 180.0) % 360.0
            val plunge = 90.0 - dip
            return fromTrendPlunge(trend, plunge)
        }
    }
}

/**
 * Raw measurement as read from a .DIP file, before we resolve the
 * global/traverse orientation flag into a Pole.
 */
data class Measurement(
    val a: Double,           // first orientation value
    val b: Double,           // second orientation value
    val quantity: Double = 1.0,
    val traverseId: Int? = null,
    val extras: List<String> = emptyList(),
    val type: OrientationType = OrientationType.DIP_DIPDIR,
    /** 1-based row index preserved from the source file. Distinguishes two
     *  identical readings taken at different positions along the scanline. */
    val rowId: Int = 0,
    /** Absolute position along the scanline from its start, in metres.
     *  First measurement is typically at distance 0. Optional. */
    val distance: Double? = null,
) {
    /** Pole to plane, or lineation, depending on the orientation type. */
    fun toPole(): Pole = when (type) {
        OrientationType.DIP_DIPDIR -> Pole.poleOfPlane(dip = a, dipDir = b)
        OrientationType.STRIKE_RHR_DIP -> Pole.poleOfPlane(dip = b, dipDir = (a + 90.0) % 360.0)
        OrientationType.STRIKE_DIPQ -> Pole.poleOfPlane(dip = b, dipDir = (a + 90.0) % 360.0)
        OrientationType.TREND_PLUNGE -> Pole.fromTrendPlunge(trend = a, plunge = b)
        OrientationType.PLUNGE_TREND -> Pole.fromTrendPlunge(trend = b, plunge = a)
    }
}
