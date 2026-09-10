package com.buz.core

import kotlin.math.*

/**
 * Fisher statistics for a set of unit vectors on the sphere.
 * All input poles are treated as lower-hemisphere; before averaging we
 * flip any vector whose dot product with the running mean is negative
 * (axial data — bidirectional as poles).
 */
object Fisher {
    data class Result(
        val mean: Pole,
        val n: Int,
        val R: Double,       // resultant length
        val k: Double,       // Fisher concentration parameter
        val cone95Deg: Double // 95% cone of confidence half-angle
    )

    fun analyse(poles: List<Pole>): Result? {
        if (poles.isEmpty()) return null
        // Axial mean: pick the first pole as seed, flip others onto its hemisphere.
        val seed = poles[0].v
        var sx = 0.0; var sy = 0.0; var sz = 0.0
        for (p in poles) {
            val v = p.v
            val s = if (v.x * seed.x + v.y * seed.y + v.z * seed.z < 0) -1.0 else 1.0
            sx += s * v.x; sy += s * v.y; sz += s * v.z
        }
        val R = sqrt(sx * sx + sy * sy + sz * sz)
        val n = poles.size
        val mean = Pole(Vec3(sx / R, sy / R, sz / R).lower())
        val k = if (n > 1 && R < n) (n - 1.0) / (n - R) else Double.POSITIVE_INFINITY
        // 95% cone (Fisher 1953)
        val cone = if (n > 1 && k.isFinite() && k > 0) {
            val term = (1.0 / (n - R)) * (20.0.pow(1.0 / (n - 1.0)) - 1.0)
            Math.toDegrees(acos((1.0 - term).coerceIn(-1.0, 1.0)))
        } else Double.NaN
        return Result(mean, n, R, k, cone)
    }
}

/**
 * Kamb-style pole density contouring on a regular polar grid over the
 * projection disc. Cell counts are converted to a standard deviation
 * measure so contours are labelled in "sigma" units, following the
 * convention used by DIPS and other stereonet software.
 *
 * `counts[y][x]` is a density value at grid position (x, y) in [-1, 1].
 * Points outside the unit circle are set to NaN.
 */
object Density {
    data class Grid(
        val size: Int,
        val values: Array<DoubleArray>,
        val sigmaUnit: Double,   // 1 sigma expressed in the same units as values
        val maxSigma: Double
    )

    /**
     * Kamb (1959) counting: the counting circle has area A/n * (1 + k^2 / n)
     * so that under a uniform distribution the expected count per circle
     * is k standard deviations above the noise floor. We use the common
     * k = 3.
     */
    fun kamb(poles: List<Pole>, gridSize: Int = 101, projection: ProjectionType = ProjectionType.EQUAL_AREA, k: Double = 3.0): Grid {
        val n = poles.size.coerceAtLeast(1)
        // Counting cap half-angle alpha such that (1 - cos alpha) = k^2 / (n + k^2)
        val cosAlpha = 1.0 - (k * k) / (n + k * k)
        val values = Array(gridSize) { DoubleArray(gridSize) { Double.NaN } }
        // Precompute pole vectors
        val vs = poles.map { it.v }
        val step = 2.0 / (gridSize - 1)
        for (iy in 0 until gridSize) {
            val py = -1.0 + iy * step
            for (ix in 0 until gridSize) {
                val px = -1.0 + ix * step
                val r2 = px * px + py * py
                if (r2 > 1.0) continue
                // Unproject (px, py) back to a lower-hemisphere unit vector.
                val v = unproject(px, py, projection) ?: continue
                var count = 0
                for (u in vs) {
                    val dot = u.x * v.x + u.y * v.y + u.z * v.z
                    // Axial: count both hemispheres
                    if (abs(dot) >= cosAlpha) count++
                }
                values[iy][ix] = count.toDouble()
            }
        }
        // Convert counts to sigma (standard deviations above expected).
        val expected = n * (1.0 - cosAlpha)
        val sigma = sqrt(expected * cosAlpha).coerceAtLeast(1e-9)
        var maxSig = 0.0
        for (iy in 0 until gridSize) for (ix in 0 until gridSize) {
            val v = values[iy][ix]
            if (v.isNaN()) continue
            val s = (v - expected) / sigma
            values[iy][ix] = s
            if (s > maxSig) maxSig = s
        }
        return Grid(gridSize, values, sigmaUnit = 1.0, maxSigma = maxSig)
    }

    private fun unproject(px: Double, py: Double, type: ProjectionType): Vec3? {
        val r = sqrt(px * px + py * py)
        if (r > 1.0) return null
        return when (type) {
            ProjectionType.EQUAL_AREA -> {
                // Inverse of r = sqrt(1 - z)  =>  z = 1 - r^2
                val z = 1.0 - r * r
                val horiz = sqrt((1.0 - z * z).coerceAtLeast(0.0))
                val (dx, dy) = if (r == 0.0) 0.0 to 0.0 else (px / r) to (py / r)
                Vec3(dx * horiz, dy * horiz, z)
            }
            ProjectionType.EQUAL_ANGLE -> {
                // Inverse of stereographic projection from top (0,0,1):
                // z = (1 - r^2) / (1 + r^2)
                val denom = 1.0 + r * r
                val z = (1.0 - r * r) / denom
                val scale = 2.0 / denom
                Vec3(px * scale, py * scale, z)
            }
        }
    }
}
