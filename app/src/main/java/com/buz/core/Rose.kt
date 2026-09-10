package com.buz.core

import kotlin.math.PI
import kotlin.math.floor

/**
 * Rose diagram data: azimuth histogram (0..360) binned in equal sectors.
 * By convention we use strike (or trend) direction and treat it as axial:
 * an azimuth of 30 also counts in the 210 bin.
 */
object Rose {
    data class Bin(val startDeg: Double, val endDeg: Double, val count: Double)

    fun build(azimuths: List<Double>, weights: List<Double>? = null, bins: Int = 36, axial: Boolean = true): List<Bin> {
        require(bins > 0) { "bins must be positive" }
        val binSize = 360.0 / bins
        val counts = DoubleArray(bins)
        for ((i, azRaw) in azimuths.withIndex()) {
            val w = weights?.getOrNull(i) ?: 1.0
            val az = ((azRaw % 360.0) + 360.0) % 360.0
            val b1 = floor(az / binSize).toInt().coerceIn(0, bins - 1)
            counts[b1] += w
            if (axial) {
                val az2 = (az + 180.0) % 360.0
                val b2 = floor(az2 / binSize).toInt().coerceIn(0, bins - 1)
                counts[b2] += w
            }
        }
        return (0 until bins).map { i ->
            Bin(i * binSize, (i + 1) * binSize, counts[i])
        }
    }
}
