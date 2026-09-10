package com.buz.core

import kotlin.math.*

/**
 * Very small K-means clusterer for orientation poles, treating data
 * axially (each vector and its antipode belong to the same class).
 * Not a substitute for user-drawn set windows — a first pass.
 */
object ClusterSets {
    data class Set(val id: Int, val centre: Pole, val members: List<Int>)

    fun kmeans(poles: List<Pole>, k: Int, iterations: Int = 25, seed: Long = 42L): List<Set> {
        if (poles.isEmpty() || k <= 0) return emptyList()
        val rng = java.util.Random(seed)
        val centres = ArrayList<Vec3>(k)
        // Seed centres from k evenly spaced poles.
        val step = (poles.size / k).coerceAtLeast(1)
        for (i in 0 until k) centres += poles[(i * step) % poles.size].v
        val labels = IntArray(poles.size)
        repeat(iterations) {
            // Assign
            for (i in poles.indices) {
                var best = 0
                var bestDot = -1.0
                for (c in 0 until k) {
                    val d = abs(poles[i].v.dot(centres[c]))
                    if (d > bestDot) { bestDot = d; best = c }
                }
                labels[i] = best
            }
            // Update
            for (c in 0 until k) {
                val members = poles.indices.filter { labels[it] == c }
                if (members.isEmpty()) {
                    centres[c] = poles[rng.nextInt(poles.size)].v
                    continue
                }
                val seed0 = centres[c]
                var sx = 0.0; var sy = 0.0; var sz = 0.0
                for (idx in members) {
                    val v = poles[idx].v
                    val s = if (v.dot(seed0) < 0) -1.0 else 1.0
                    sx += s * v.x; sy += s * v.y; sz += s * v.z
                }
                val n = sqrt(sx * sx + sy * sy + sz * sz).coerceAtLeast(1e-12)
                centres[c] = Vec3(sx / n, sy / n, sz / n)
            }
        }
        return (0 until k).map { c ->
            val members = poles.indices.filter { labels[it] == c }
            Set(id = c, centre = Pole(centres[c].lower()), members = members)
        }
    }
}
