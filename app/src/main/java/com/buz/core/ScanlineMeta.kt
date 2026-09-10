package com.buz.core

/**
 * Per-scanline metadata: how to interpret the group of measurements that
 * share this scanline id (traverseId).
 *
 * `trend` and `plunge` describe the orientation of the scanline line itself
 * (used for Terzaghi correction and for placing the scanline in 3D later).
 * Name is a free-form label for the UI.
 */
data class ScanlineMeta(
    val id: Int,
    val name: String = "SL $id",
    val trend: Double = 0.0,
    val plunge: Double = 90.0,
) {
    /** Convert the (trend, plunge) into a Pole vector usable by Terzaghi. */
    fun axis(): Pole = Pole.fromTrendPlunge(trend, plunge)
}
