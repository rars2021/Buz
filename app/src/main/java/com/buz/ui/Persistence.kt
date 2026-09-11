package com.buz.ui

import android.content.Context
import android.content.SharedPreferences
import com.buz.core.ProjectionType
import com.buz.core.SetWindow
import com.buz.core.WindowShape

/** Snapshot of everything the UI needs to restore between launches. */
data class BuzState(
    val lastUri: String? = null,
    val projection: ProjectionType = ProjectionType.EQUAL_AREA,
    val showContours: Boolean = true,
    val showPlanes: Boolean = false,
    val showPoles: Boolean = true,
    val showGrid: Boolean = true,
    val showLabels: Boolean = true,
    val filledDensity: Boolean = false,
    val applyTerzaghi: Boolean = false,
    val autoOn: Boolean = false,
    val autoK: Int = 3,
    val coneAngleDeg: Double = 20.0,
    val drawShape: WindowShape = WindowShape.RECT,
    val windows: List<SetWindow> = emptyList(),
    val polesSigmaDeg: Double = 12.0,
    val wedgesKamb: Double = 2.0,
    val familyMethod: String = "KMEANS",     // "KMEANS" or "DENSITY"
    val peakSigmaDeg: Double = 12.0,
    val peakMinFraction: Double = 0.4,
    val peakMergeDeg: Double = 15.0,
    val showFamilyRings: Boolean = true,
    val showFamilyPlanes: Boolean = false,
)

object BuzPrefs {
    private const val PREFS = "buz_prefs"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): BuzState {
        val p = prefs(ctx)
        return runCatching {
            BuzState(
                lastUri = p.getString("lastUri", null),
                projection = if (p.getString("projection", "EQUAL_AREA") == "EQUAL_ANGLE")
                    ProjectionType.EQUAL_ANGLE else ProjectionType.EQUAL_AREA,
                showContours = p.getBoolean("showContours", true),
                showPlanes = p.getBoolean("showPlanes", false),
                showPoles = p.getBoolean("showPoles", true),
                showGrid = p.getBoolean("showGrid", true),
                showLabels = p.getBoolean("showLabels", true),
                filledDensity = p.getBoolean("filledDensity", false),
                applyTerzaghi = p.getBoolean("applyTerzaghi", false),
                autoOn = p.getBoolean("autoOn", false),
                autoK = p.getInt("autoK", 3),
                coneAngleDeg = p.getFloat("coneAngle", 20f).toDouble(),
                drawShape = runCatching { WindowShape.valueOf(p.getString("drawShape", "RECT") ?: "RECT") }
                    .getOrDefault(WindowShape.RECT),
                windows = decodeWindows(p.getString("windows", "") ?: ""),
                polesSigmaDeg = p.getFloat("polesSigma", 12f).toDouble(),
                wedgesKamb = p.getFloat("wedgesKamb", 2f).toDouble(),
                familyMethod = p.getString("familyMethod", "KMEANS") ?: "KMEANS",
                peakSigmaDeg = p.getFloat("peakSigma", 12f).toDouble(),
                peakMinFraction = p.getFloat("peakMinFrac", 0.4f).toDouble(),
                peakMergeDeg = p.getFloat("peakMergeDeg", 15f).toDouble(),
                showFamilyRings = p.getBoolean("showFamilyRings", true),
                showFamilyPlanes = p.getBoolean("showFamilyPlanes", false),
            )
        }.getOrDefault(BuzState())
    }

    fun save(ctx: Context, s: BuzState) {
        prefs(ctx).edit().apply {
            putString("lastUri", s.lastUri)
            putString("projection", s.projection.name)
            putBoolean("showContours", s.showContours)
            putBoolean("showPlanes", s.showPlanes)
            putBoolean("showPoles", s.showPoles)
            putBoolean("showGrid", s.showGrid)
            putBoolean("showLabels", s.showLabels)
            putBoolean("filledDensity", s.filledDensity)
            putBoolean("applyTerzaghi", s.applyTerzaghi)
            putBoolean("autoOn", s.autoOn)
            putInt("autoK", s.autoK)
            putFloat("coneAngle", s.coneAngleDeg.toFloat())
            putString("drawShape", s.drawShape.name)
            putString("windows", encodeWindows(s.windows))
            putFloat("polesSigma", s.polesSigmaDeg.toFloat())
            putFloat("wedgesKamb", s.wedgesKamb.toFloat())
            putString("familyMethod", s.familyMethod)
            putFloat("peakSigma", s.peakSigmaDeg.toFloat())
            putFloat("peakMinFrac", s.peakMinFraction.toFloat())
            putFloat("peakMergeDeg", s.peakMergeDeg.toFloat())
            putBoolean("showFamilyRings", s.showFamilyRings)
            putBoolean("showFamilyPlanes", s.showFamilyPlanes)
            apply()
        }
    }

    private fun encodeWindows(ws: List<SetWindow>): String =
        ws.joinToString(";") { w ->
            val safeLabel = w.label.replace(";", ",").replace("|", "/")
            listOf(
                w.id.toString(), safeLabel,
                w.x0.toString(), w.y0.toString(),
                w.x1.toString(), w.y1.toString(),
                w.projection.name, w.shape.name
            ).joinToString("|")
        }

    private fun decodeWindows(s: String): List<SetWindow> {
        if (s.isBlank()) return emptyList()
        return s.split(";").mapNotNull { chunk ->
            val f = chunk.split("|")
            if (f.size < 8) return@mapNotNull null
            runCatching {
                SetWindow(
                    id = f[0].toInt(),
                    label = f[1],
                    x0 = f[2].toDouble(), y0 = f[3].toDouble(),
                    x1 = f[4].toDouble(), y1 = f[5].toDouble(),
                    projection = ProjectionType.valueOf(f[6]),
                    shape = WindowShape.valueOf(f[7]),
                )
            }.getOrNull()
        }
    }
}
