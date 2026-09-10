package com.buz.core

import kotlin.math.*

/**
 * Stereographic projections onto the unit circle for lower-hemisphere plotting.
 * Input pole is a downward-pointing unit vector (x=E, y=N, z=Down).
 * Output is (px, py) with px=East, py=North, px^2+py^2 <= 1.
 */
enum class ProjectionType { EQUAL_AREA, EQUAL_ANGLE }

object Projection {
    /**
     * Project a lower-hemisphere unit vector to the equatorial plane.
     * EQUAL_AREA is the Schmidt (Lambert azimuthal) net — the default in DIPS.
     * EQUAL_ANGLE is the Wulff net.
     */
    fun project(p: Pole, type: ProjectionType): Pair<Double, Double> {
        val v = p.v
        // Distance from the origin on the projection plane.
        // Both formulas below place a vertical vector at the origin
        // and a horizontal one on the unit circle.
        val r = when (type) {
            // Equal area: r = sqrt(2) * sin((90 - plunge)/2 * ... )
            // With plunge measured from horizontal (z = sin plunge),
            // r = sqrt(1 - z) / sqrt(2) * sqrt(2) = sqrt(1 - z).
            // Wait: at z=1 (vertical) r=0, at z=0 (horizontal) r=1. Good.
            ProjectionType.EQUAL_AREA -> sqrt(1.0 - v.z).let { it / sqrt(2.0) } * sqrt(2.0)
            // Equal angle: r = (1 - z) / 1 for lower hemisphere from the top focal point
            // At z=1 r=0, at z=0 r=1. Good.
            ProjectionType.EQUAL_ANGLE -> {
                val denom = 1.0 + v.z
                if (denom == 0.0) 1.0 else sqrt(v.x * v.x + v.y * v.y) / denom
            }
        }
        // Direction on the plane is the same as the (x, y) direction of the vector.
        val horiz = sqrt(v.x * v.x + v.y * v.y)
        val (dx, dy) = if (horiz == 0.0) 0.0 to 0.0 else (v.x / horiz) to (v.y / horiz)
        return dx * r to dy * r
    }

    /**
     * Great-circle arc for a plane, as a polyline of (px, py) points on the
     * projection unit circle. Uses the pole to the plane.
     */
    fun greatCircle(polePlane: Pole, type: ProjectionType, steps: Int = 90): List<Pair<Double, Double>> {
        // Build any two orthogonal unit vectors in the plane whose normal is the pole.
        val n = polePlane.v.normalized()
        // Pick a helper vector not parallel to n.
        val helper = if (abs(n.z) < 0.9) Vec3(0.0, 0.0, 1.0) else Vec3(1.0, 0.0, 0.0)
        val e1 = cross(n, helper).normalized()
        val e2 = cross(n, e1).normalized()
        val out = ArrayList<Pair<Double, Double>>(steps + 1)
        for (i in 0..steps) {
            val a = 2.0 * PI * i / steps
            val v = (e1 * cos(a)) + (e2 * sin(a))
            // Project only the lower-hemisphere part; skip points on the upper hemisphere.
            val vl = if (v.z < 0) Vec3(-v.x, -v.y, -v.z) else v
            out += project(Pole(vl), type)
        }
        return out
    }

    /** Small-circle cone around an axis at angular radius `alpha` (radians). */
    fun smallCircle(axis: Pole, alphaRad: Double, type: ProjectionType, steps: Int = 90): List<Pair<Double, Double>> {
        val n = axis.v.normalized()
        val helper = if (abs(n.z) < 0.9) Vec3(0.0, 0.0, 1.0) else Vec3(1.0, 0.0, 0.0)
        val e1 = cross(n, helper).normalized()
        val e2 = cross(n, e1).normalized()
        val out = ArrayList<Pair<Double, Double>>(steps + 1)
        val cosA = cos(alphaRad); val sinA = sin(alphaRad)
        for (i in 0..steps) {
            val t = 2.0 * PI * i / steps
            val v = (n * cosA) + (e1 * (sinA * cos(t))) + (e2 * (sinA * sin(t)))
            val vl = if (v.z < 0) Vec3(-v.x, -v.y, -v.z) else v
            out += project(Pole(vl), type)
        }
        return out
    }

    private fun cross(a: Vec3, b: Vec3) = Vec3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )
}
