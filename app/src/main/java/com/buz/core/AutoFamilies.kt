package com.buz.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Automatic family detection built on top of ClusterSets.kmeans:
 * runs k-means to find `k` cluster centres, then assigns each pole to the
 * nearest centre only if the angular distance is <= `angleDeg`.
 * Poles outside every cone remain unassigned (-1).
 */
object AutoFamilies {
    data class Family(
        val id: Int,
        val centre: Pole,
        val members: List<Int>,   // indices into the input poles list
    )

    fun detect(
        poles: List<Pole>,
        k: Int,
        angleDeg: Double,
        seed: Long = 42L,
    ): List<Family> {
        if (poles.isEmpty() || k <= 0) return emptyList()
        val kk = k.coerceAtMost(poles.size)
        val clusters = ClusterSets.kmeans(poles, kk, seed = seed)
        val cosThr = cos(Math.toRadians(angleDeg.coerceIn(0.0, 90.0)))
        return clusters.map { c ->
            val centreV = c.centre.v
            val members = poles.indices.filter { i ->
                abs(poles[i].v.dot(centreV)) >= cosThr
            }
            Family(c.id, c.centre, members)
        }
    }

    /**
     * Density-peak detection: compute a Gaussian density grid, find its local
     * maxima (peaks above `minFraction * maxSigma`), and use each peak as a
     * family centre. Members are poles within `angleDeg` of the centre.
     *
     * More faithful to the visual heat than K-means when the data has natural
     * clusters of different sizes.
     */
    fun detectByDensityPeaks(
        poles: List<Pole>,
        sigmaDeg: Double,
        angleDeg: Double,
        minPeakFraction: Double,
        mergeDeg: Double = 0.0,
        projection: ProjectionType = ProjectionType.EQUAL_AREA,
    ): List<Family> = detectByDensityPeaksFull(
        poles, sigmaDeg, minPeakFraction, mergeDeg, projection
    ).families

    /** Watershed-based density detection. Each grid cell above `basinMinFraction`
     *  of the peak flows uphill to a local max; members follow their basin. */
    data class DensityDetection(
        val families: List<Family>,
        val basins: IntArray,       // flat gridSize² array, -1 = no basin
        val gridSize: Int,
    )

    fun detectByDensityPeaksFull(
        poles: List<Pole>,
        sigmaDeg: Double,
        minPeakFraction: Double,
        mergeDeg: Double = 0.0,
        projection: ProjectionType = ProjectionType.EQUAL_AREA,
        basinMinFraction: Double = 0.15,
    ): DensityDetection {
        if (poles.isEmpty()) return DensityDetection(emptyList(), IntArray(0), 0)
        val grid = Density.gaussian(poles, 81, projection, sigmaDeg)
        val raw = Density.peaks(grid, projection, minPeakFraction)
        val centres = mergeClosePeaks(raw, mergeDeg)
        val n = grid.size
        if (centres.isEmpty()) return DensityDetection(emptyList(), IntArray(0), n)
        val basins = Density.basins(grid, centres, projection, grid.maxSigma * basinMinFraction)
        val members = List(centres.size) { mutableListOf<Int>() }
        for ((i, p) in poles.withIndex()) {
            val (px, py) = Projection.project(p, projection)
            val ix = ((px + 1.0) / 2.0 * (n - 1)).toInt().coerceIn(0, n - 1)
            val iy = ((py + 1.0) / 2.0 * (n - 1)).toInt().coerceIn(0, n - 1)
            val fam = basins[iy * n + ix]
            if (fam in 0 until centres.size) members[fam].add(i)
        }
        // Recompute each family's centre as the density-weighted centroid of
        // its watershed basin.  Every grid cell assigned to this basin
        // contributes its 3-D unit vector scaled by the local density value.
        // The result tracks the heat-map continuously: as σ grows and a
        // saddle rises, the centroid drifts smoothly instead of jumping to a
        // Fisher mean that snaps when two sub-clusters merge.
        val families = centres.mapIndexed { i, initial ->
            val mean = densityWeightedCentre(grid, basins, i, n, projection) ?: initial
            Family(i, mean, members[i])
        }
        return DensityDetection(families, basins, n)
    }

    /** Greedy merge: keep the first (most intense) centre; drop any later one
     *  whose angular distance to a kept centre is less than `mergeDeg`. */
    private fun mergeClosePeaks(centres: List<Pole>, mergeDeg: Double): List<Pole> {
        if (mergeDeg <= 0.0 || centres.size < 2) return centres
        val cosMerge = cos(Math.toRadians(mergeDeg.coerceIn(0.0, 90.0)))
        val kept = mutableListOf<Pole>()
        for (c in centres) {
            val tooClose = kept.any { abs(it.v.dot(c.v)) >= cosMerge }
            if (!tooClose) kept += c
        }
        return kept
    }

    /**
     * Density-weighted centroid of a watershed basin.  For every grid cell
     * tagged as belonging to basin [basinIdx], its 3-D unit vector (obtained
     * by un-projecting the cell back to the sphere) is added to a running
     * sum, scaled by the cell's density value.  The normalised resultant is
     * the centroid.
     *
     * Because each cell contributes proportionally to the local heat, the
     * centroid follows the density landscape continuously.  As σ increases
     * and a saddle between two peaks rises, the boundary cells gradually
     * shift basin membership and the centroid drifts smoothly — no jump.
     */
    private fun densityWeightedCentre(
        grid: Density.Grid,
        basins: IntArray,
        basinIdx: Int,
        n: Int,
        projection: ProjectionType,
    ): Pole? {
        val step = 2.0 / (n - 1)
        var wx = 0.0; var wy = 0.0; var wz = 0.0
        for (iy in 0 until n) {
            for (ix in 0 until n) {
                if (basins[iy * n + ix] != basinIdx) continue
                val v = grid.values[iy][ix]
                if (v.isNaN() || v <= 0.0) continue
                val px = -1.0 + ix * step
                val py = -1.0 + iy * step
                val vec = Density.unproject(px, py, projection) ?: continue
                wx += vec.x * v; wy += vec.y * v; wz += vec.z * v
            }
        }
        val norm = sqrt(wx * wx + wy * wy + wz * wz)
        if (norm < 1e-12) return null
        return Pole(Vec3(wx / norm, wy / norm, wz / norm).lower())
    }

    /** Percent of the total N per family; unassigned poles contribute to the remainder. */
    fun percentages(families: List<Family>, total: Int): List<Double> {
        if (total <= 0) return List(families.size) { 0.0 }
        return families.map { 100.0 * it.members.size / total }
    }

    /** Per-pole family index (-1 if unassigned). If a pole falls into several cones,
     *  it is assigned to the one whose centre is closest. */
    fun assign(poles: List<Pole>, families: List<Family>, angleDeg: Double): IntArray {
        val out = IntArray(poles.size) { -1 }
        if (families.isEmpty()) return out
        val cosThr = cos(Math.toRadians(angleDeg.coerceIn(0.0, 90.0)))
        for (i in poles.indices) {
            var bestF = -1
            var bestDot = cosThr
            for ((fi, f) in families.withIndex()) {
                val d = abs(poles[i].v.dot(f.centre.v))
                if (d >= bestDot) { bestDot = d; bestF = fi }
            }
            out[i] = bestF
        }
        return out
    }
}
