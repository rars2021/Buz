package com.buz.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Off-screen stereonet renderer. Delegates entirely to the shared
 * [drawStereonetPlot] used by [StereonetView], so the exported PNG matches
 * the on-screen view exactly (heatmap, labels, family basins, cluster
 * rings, scanline axis and all).
 */
object PngRender {
    fun render(scope: DrawScope, size: Size, plot: StereonetPlot) {
        with(scope) { drawStereonetPlot(size, plot) }
    }
}
