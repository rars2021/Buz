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
    fun kamb(poles: List<Pole>, gridSize: Int = 101, projection: ProjectionType = ProjectionType.EQUAL_AREA, k: Double = 2.0): Grid {
        val n = poles.size.coerceAtLeast(1)
        val cosAlpha = 1.0 - (k * k) / (n + k * k)
        val values = Array(gridSize) { DoubleArray(gridSize) { Double.NaN } }
        val vs = poles.map { it.v }
        val step = 2.0 / (gridSize - 1)
        for (iy in 0 until gridSize) {
            val py = -1.0 + iy * step
            for (ix in 0 until gridSize) {
                val px = -1.0 + ix * step
                val r2 = px * px + py * py
                if (r2 > 1.0) continue
                val v = unproject(px, py, projection) ?: continue
                var count = 0
                for (u in vs) {
                    val dot = u.x * v.x + u.y * v.y + u.z * v.z
                    // Non-axial: only the actual pole location contributes.
                    // Poles are already forced to the lower hemisphere so the
                    // antipode would be off-plot; using |dot| there would light
                    // up mirror regions where no pole actually is.
                    if (dot >= cosAlpha) count++
                }
                values[iy][ix] = count.toDouble()
            }
        }
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

    /**
     * Gaussian-kernel density on the projection disc. For each query point,
     * sums exp(-θ²/2σ²) over all poles, where θ is the angular distance from
     * the pole to the query. Result is scaled so 1σ Kamb-equivalent value ≈ 1.
     *
     * Unlike Kamb's hard counting circle (which spreads density evenly over a
     * large disc for small N), a Gaussian bump is centred on each pole and
     * falls off smoothly, so the heat visibly clings to the actual points and
     * intensifies where they cluster.
     */
    fun gaussian(
        poles: List<Pole>,
        gridSize: Int = 101,
        projection: ProjectionType = ProjectionType.EQUAL_AREA,
        sigmaDeg: Double = 12.0,
    ): Grid {
        val values = Array(gridSize) { DoubleArray(gridSize) { Double.NaN } }
        val vs = poles.map { it.v }
        val step = 2.0 / (gridSize - 1)
        val sigmaRad = Math.toRadians(sigmaDeg)
        val twoSigmaSq = 2.0 * sigmaRad * sigmaRad
        for (iy in 0 until gridSize) {
            val py = -1.0 + iy * step
            for (ix in 0 until gridSize) {
                val px = -1.0 + ix * step
                val r2 = px * px + py * py
                if (r2 > 1.0) continue
                val v = unproject(px, py, projection) ?: continue
                var sum = 0.0
                for (u in vs) {
                    val dot = (u.x * v.x + u.y * v.y + u.z * v.z).coerceIn(-1.0, 1.0)
                    if (dot <= 0) continue  // more than 90° away — ignore
                    val ang = acos(dot)
                    sum += exp(-ang * ang / twoSigmaSq)
                }
                values[iy][ix] = sum
            }
        }
        var maxV = 0.0
        for (iy in 0 until gridSize) for (ix in 0 until gridSize) {
            val v = values[iy][ix]
            if (!v.isNaN() && v > maxV) maxV = v
        }
        return Grid(gridSize, values, sigmaUnit = 1.0, maxSigma = maxV)
    }

    /**
     * Find local maxima in a density grid and return them as Poles.
     * A cell is a peak when it strictly exceeds every 8-neighbour, and is
     * above `minFraction * maxSigma` of the grid's global max.
     * Returned poles are sorted by descending peak intensity.
     */
    fun peaks(grid: Grid, projection: ProjectionType, minFraction: Double): List<Pole> {
        val n = grid.size
        if (n < 3 || grid.maxSigma <= 0.0) return emptyList()
        val threshold = grid.maxSigma * minFraction.coerceIn(0.0, 1.0)
        val step = 2.0 / (n - 1)
        val out = ArrayList<Pair<Pole, Double>>()
        for (iy in 1 until n - 1) {
            for (ix in 1 until n - 1) {
                val v = grid.values[iy][ix]
                if (v.isNaN() || v < threshold) continue
                var isPeak = true
                loop@ for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nb = grid.values[iy + dy][ix + dx]
                    if (!nb.isNaN() && nb > v) { isPeak = false; break@loop }
                }
                if (isPeak) {
                    val px = -1.0 + ix * step
                    val py = -1.0 + iy * step
                    unproject(px, py, projection)?.let { vec ->
                        out += Pole(vec.lower()) to v
                    }
                }
            }
        }
        return out.sortedByDescending { it.second }.map { it.first }
    }

    /**
     * Watershed segmentation of a density grid: for each cell above `minValue`,
     * follow the steepest-ascent path to a local maximum, then map that local
     * max to the nearest `peaks` centre by angular distance. Returns a flat
     * `IntArray(size*size)` where each entry is the basin index (peak index)
     * or -1 if the cell is below threshold / off-plot.
     *
     * A walked cell is cached so the whole grid is resolved in O(n²).
     */
    fun basins(
        grid: Grid,
        peaks: List<Pole>,
        projection: ProjectionType,
        minValue: Double,
    ): IntArray {
        val n = grid.size
        val basin = IntArray(n * n) { -1 }
        if (peaks.isEmpty()) return basin
        val step = 2.0 / (n - 1)
        for (iy0 in 0 until n) for (ix0 in 0 until n) {
            val v0 = grid.values[iy0][ix0]
            if (v0.isNaN() || v0 < minValue) continue
            if (basin[iy0 * n + ix0] != -1) continue
            var cx = ix0; var cy = iy0
            val path = ArrayList<Int>()
            var found = -1
            while (true) {
                val key = cy * n + cx
                if (basin[key] != -1) { found = basin[key]; break }
                path += key
                var bestV = grid.values[cy][cx]
                var bestX = cx; var bestY = cy
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = cx + dx; val ny = cy + dy
                    if (nx !in 0 until n || ny !in 0 until n) continue
                    val nv = grid.values[ny][nx]
                    if (!nv.isNaN() && nv > bestV) {
                        bestV = nv; bestX = nx; bestY = ny
                    }
                }
                if (bestX == cx && bestY == cy) {
                    val lx = -1.0 + cx * step
                    val ly = -1.0 + cy * step
                    val vec = unproject(lx, ly, projection) ?: break
                    var bestPeak = -1
                    var bestDot = -2.0
                    for ((pi, pk) in peaks.withIndex()) {
                        val d = abs(vec.x * pk.v.x + vec.y * pk.v.y + vec.z * pk.v.z)
                        if (d > bestDot) { bestDot = d; bestPeak = pi }
                    }
                    found = bestPeak
                    break
                }
                cx = bestX; cy = bestY
            }
            for (k in path) basin[k] = found
        }
        return basin
    }

    /**
     * Wedge (plane intersection) orientations. For every pair of planes given
     * by their poles, computes the line of intersection (pole × pole), forced
     * to the lower hemisphere. Used to feed `kamb` for a wedge density plot,
     * which highlights the trend/plunge of intersections likely to fail as
     * kinematic wedges.
     */
    fun wedgeIntersections(poles: List<Pole>): List<Pole> {
        if (poles.size < 2) return emptyList()
        val out = ArrayList<Pole>(poles.size * (poles.size - 1) / 2)
        for (i in poles.indices) {
            val a = poles[i].v
            for (j in i + 1 until poles.size) {
                val b = poles[j].v
                val cx = a.y * b.z - a.z * b.y
                val cy = a.z * b.x - a.x * b.z
                val cz = a.x * b.y - a.y * b.x
                val n = sqrt(cx * cx + cy * cy + cz * cz)
                if (n < 1e-9) continue
                out += Pole(Vec3(cx / n, cy / n, cz / n).lower())
            }
        }
        return out
    }

    internal fun unproject(px: Double, py: Double, type: ProjectionType): Vec3? {
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
