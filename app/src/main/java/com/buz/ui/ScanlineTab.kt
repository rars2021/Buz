package com.buz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buz.core.*

private val tickPalette = listOf(
    Color(0xFF1B7F3B), Color(0xFFB05E00), Color(0xFF7A1F9E),
    Color(0xFF0E6E8C), Color(0xFF9E1B4E), Color(0xFF556B00),
)

/** Colour for a given scanline id. Deterministic so the same SL keeps its
 *  colour across screens. */
internal fun scanlineColour(id: Int?): Color = when (id) {
    null -> Color(0xFF666666)
    else -> tickPalette[(id % tickPalette.size + tickPalette.size) % tickPalette.size]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanlineTab(
    scanlineMetas: SnapshotStateList<ScanlineMeta>,
    measurements: List<Measurement>,
    poleColourIndex: IntArray,
    onExportDipsOnly: () -> Unit,
    onExportWithDist: () -> Unit,
    exportMsg: String?,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        // ── Sección 1 ─────────────────────────────────────────────────────
        Text("1) Scanlines", style = MaterialTheme.typography.titleSmall)
        Text("Cada scanline (SL) tiene su propia orientación de línea de barrido.",
            style = MaterialTheme.typography.bodySmall)

        val usedIds = measurements.mapNotNull { it.traverseId }.toSortedSet()
        Row {
            TextButton(onClick = {
                val nextId = ((scanlineMetas.maxOfOrNull { it.id } ?: 0)
                    .coerceAtLeast(usedIds.maxOrNull() ?: 0)) + 1
                scanlineMetas.add(ScanlineMeta(id = nextId))
            }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("+ Nueva SL") }
            TextButton(onClick = {
                // Sync with the data: remove metas whose id has no measurements,
                // and add missing ones for ids that are present in the data.
                val have = scanlineMetas.map { it.id }.toSet()
                for (id in usedIds) if (id !in have) scanlineMetas.add(ScanlineMeta(id))
                val idsToDrop = scanlineMetas.map { it.id }.filter { it !in usedIds }
                for (id in idsToDrop) {
                    val i = scanlineMetas.indexOfFirst { it.id == id }
                    if (i >= 0) scanlineMetas.removeAt(i)
                }
            }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("Sync con datos")
            }
        }

        if (scanlineMetas.isEmpty() && usedIds.isEmpty()) {
            Text("No hay scanlines. Asigná la columna SL en Datos o creá una arriba.",
                style = MaterialTheme.typography.bodySmall)
        } else {
            // Header labels so the row inputs are self-explanatory.
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(14.dp))
                Spacer(Modifier.width(4.dp))
                Spacer(Modifier.width(40.dp))
                LabelCell("Nombre", 1.4f)
                Spacer(Modifier.width(2.dp))
                LabelCell("Rumbo °", 1f)
                Spacer(Modifier.width(2.dp))
                LabelCell("Buz °", 1f)
                Spacer(Modifier.width(4.dp))
                Spacer(Modifier.width(34.dp))
                Spacer(Modifier.width(28.dp))
            }
            for ((idx, meta) in scanlineMetas.withIndex()) {
                CompactScanlineRow(
                    meta = meta,
                    nRows = measurements.count { it.traverseId == meta.id },
                    onChange = { updated ->
                        if (idx < scanlineMetas.size) scanlineMetas[idx] = updated
                    },
                    onDelete = { if (idx < scanlineMetas.size) scanlineMetas.removeAt(idx) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))

        // ── Sección 2 ─────────────────────────────────────────────────────
        Text("2) Mediciones por scanline", style = MaterialTheme.typography.titleSmall)
        val withDist = measurements.count { it.distance != null }
        val totalLen = measurements.mapNotNull { it.distance }
            .let { if (it.isEmpty()) 0.0 else it.max() - it.min() }
        Text("total n=${measurements.size}   con dist=$withDist   largo global ≈ ${"%.2f".format(totalLen)} m",
            style = MaterialTheme.typography.bodySmall)

        val byScanline = measurements.groupBy { it.traverseId }
            .toSortedMap(compareBy(nullsLast()) { it })
        for ((sl, ms) in byScanline) {
            val nDist = ms.count { it.distance != null }
            val ds = ms.mapNotNull { it.distance }
            val len = if (ds.size >= 2) ds.max() - ds.min() else 0.0
            val name = if (sl == null) "sin SL" else "SL $sl"
            Text("  · $name — n=${ms.size}   con dist=$nDist   largo=${"%.2f".format(len)} m",
                style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))

        // ── Sección 3 ─────────────────────────────────────────────────────
        Text("3) Sección transversal", style = MaterialTheme.typography.titleSmall)
        Text("Vista perpendicular al eje de la scanline. Rojo = scanline, verde = discontinuidades.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        CrossSectionView(
            measurements = measurements,
            metas = scanlineMetas,
            poleColourIndex = poleColourIndex,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))

        // ── Sección 4 ─────────────────────────────────────────────────────
        Text("4) Exportar", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Button(onClick = onExportDipsOnly, modifier = Modifier.weight(1f)) { Text("CSV solo dips") }
            Spacer(Modifier.width(6.dp))
            Button(onClick = onExportWithDist, modifier = Modifier.weight(1f)) { Text("CSV dips + dist") }
        }
        exportMsg?.let {
            Card(Modifier.padding(top = 4.dp)) { Text(it, Modifier.padding(8.dp)) }
        }
    }
}

@Composable
private fun CrossSectionView(
    measurements: List<Measurement>,
    metas: List<ScanlineMeta>,
    poleColourIndex: IntArray,
    modifier: Modifier,
) {
    val withDist = measurements.mapIndexedNotNull { idx, m ->
        m.distance?.let { Triple(idx, m, it) }
    }
    if (withDist.isEmpty()) {
        Box(modifier) {
            Text("Sin distancias todavía. Asigná 'Δm' o 'dist m' en la pestaña Datos.",
                style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val totalDMin = withDist.minOf { it.third }
    val totalDMax = withDist.maxOf { it.third }
    val globalSpan = (totalDMax - totalDMin).coerceAtLeast(1e-6)

    val totalTicks = withDist.size
    val canvasWidthDp = maxOf(360, totalTicks * 80).dp
    
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val trackHDp = (conf.screenHeightDp * 0.28f).coerceAtLeast(260f)

    Box(modifier.horizontalScroll(rememberScrollState())) {
        Canvas(
            Modifier.width(canvasWidthDp)
                .height(trackHDp.dp)
                .padding(vertical = 10.dp)
        ) {
            val trackH = size.height
            val marginX = 40f
            val axisY = trackH * 0.65f

            // 1. Rock section boundary (Black "L" shape)
            val boxColor = Color.Black
            val boxStroke = 4f
            val floorY = axisY + (trackH * 0.2f)
            val wallTopY = axisY - (trackH * 0.5f)
            drawLine(boxColor, Offset(marginX, wallTopY), Offset(marginX, floorY), strokeWidth = boxStroke) // Left wall
            drawLine(boxColor, Offset(marginX, floorY), Offset(size.width - 20f, floorY), strokeWidth = boxStroke) // Floor

            // 2. Scanline (Red)
            val scanlineColor = Color.Red
            drawLine(scanlineColor, Offset(marginX - 10f, axisY), Offset(size.width - 20f, axisY), strokeWidth = 4f)

            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(30, 30, 30)
                textSize = 24f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.MONOSPACE
            }
            val distPaint = android.graphics.Paint(labelPaint).apply {
                textSize = 20f
                color = android.graphics.Color.GRAY
            }
            val scalePaint = android.graphics.Paint(labelPaint).apply {
                textSize = 18f
                color = android.graphics.Color.BLACK
            }

            // Draw Metric Scale on the floor
            val meterStep = when {
                globalSpan > 50 -> 10.0
                globalSpan > 20 -> 5.0
                globalSpan > 10 -> 2.0
                else -> 1.0
            }
            val startMeter = kotlin.math.ceil(totalDMin / meterStep) * meterStep
            var currentMeter = startMeter
            while (currentMeter <= totalDMax + 1e-6) {
                val t = ((currentMeter - totalDMin) / globalSpan).toFloat()
                val x = marginX + 20f + t * (size.width - marginX - 60f)
                drawLine(Color.Black, Offset(x, floorY), Offset(x, floorY - 10f), strokeWidth = 3f)
                val lbl = if (currentMeter % 1.0 == 0.0) "${currentMeter.toInt()}m" else "${"%.1f".format(currentMeter)}m"
                drawContext.canvas.nativeCanvas.drawText(lbl, x, floorY + 24f, scalePaint)
                currentMeter += meterStep
            }

            // Reference axis for angle calculations (use first scanline or default)
            val slAxis = metas.firstOrNull()?.axis() ?: Pole.fromTrendPlunge(0.0, 90.0)
            val axv = slAxis.v
            val slHmag = kotlin.math.sqrt(axv.x * axv.x + axv.y * axv.y)
            val slHx: Double; val slHy: Double
            if (slHmag > 1e-6) { slHx = axv.x / slHmag; slHy = axv.y / slHmag }
            else { slHx = 0.0; slHy = 1.0 }

            val ticks = withDist.sortedBy { it.third }
            for ((k, triple) in ticks.withIndex()) {
                val (origIdx, m, d) = triple
                val t = ((d - totalDMin) / globalSpan).toFloat()
                val x = marginX + 20f + t * (size.width - marginX - 60f)

                val polev = m.toPole().v
                val cosAng = kotlin.math.abs(polev.x * axv.x + polev.y * axv.y + polev.z * axv.z).coerceIn(0.0, 1.0)
                val angBetween = kotlin.math.acos(cosAng)
                val traceRad = (kotlin.math.PI / 2 - angBetween)

                val crossZ = slHx * polev.y - slHy * polev.x
                val tiltSign = if (crossZ >= 0.0) 1.0 else -1.0

                val len = trackH * 0.45f // Scale line lengths proportionally
                val dx = (len * kotlin.math.cos(traceRad) * tiltSign).toFloat()
                val dy = (len * kotlin.math.sin(traceRad)).toFloat()

                // 3. Discontinuity trace (Green)
                drawLine(Color(0xFF22AA44), Offset(x - dx, axisY + dy), Offset(x + dx, axisY - dy), strokeWidth = 3.5f)

                // 4. Measurement tick (Blue)
                drawLine(Color.Blue, Offset(x, axisY - 14f), Offset(x, axisY + 14f), strokeWidth = 3.5f)

                // Labels
                val above = k % 2 == 0
                val ly = if (above) axisY - (trackH * 0.35f) else axisY + (trackH * 0.15f)
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0f/%.0f".format(m.a, m.b), x, ly, labelPaint
                )
                val dyLabel = if (above) axisY + (trackH * 0.18f) else axisY - (trackH * 0.32f)
                drawContext.canvas.nativeCanvas.drawText(
                    "%.2f".format(d), x, dyLabel, distPaint
                )
            }
        }
    }
}

@Composable
private fun RowScope.LabelCell(text: String, weight: Float) {
    Text(
        text,
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun CompactScanlineRow(
    meta: ScanlineMeta,
    nRows: Int,
    onChange: (ScanlineMeta) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp)) {
            Canvas(Modifier.fillMaxSize()) { drawCircle(scanlineColour(meta.id)) }
        }
        Spacer(Modifier.width(4.dp))
        Text("SL ${meta.id}", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(40.dp))
        MiniTextField(meta.name, "nombre", Modifier.weight(1.4f)) {
            onChange(meta.copy(name = it))
        }
        Spacer(Modifier.width(2.dp))
        MiniNumField(meta.trend, "trend", Modifier.weight(1f)) {
            onChange(meta.copy(trend = it.coerceIn(0.0, 360.0)))
        }
        Spacer(Modifier.width(2.dp))
        MiniNumField(meta.plunge, "plun", Modifier.weight(1f)) {
            onChange(meta.copy(plunge = it.coerceIn(0.0, 90.0)))
        }
        Spacer(Modifier.width(4.dp))
        Text("n=$nRows", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(34.dp))
        TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp),
            modifier = Modifier.width(28.dp)) { Text("×") }
    }
}

@Composable
private fun MiniTextField(value: String, hint: String, modifier: Modifier, onValue: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    androidx.compose.foundation.text.BasicTextField(
        value = text,
        onValueChange = { text = it; onValue(it) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        decorationBox = { inner ->
            Box(Modifier.background(androidx.compose.ui.graphics.Color(0x14000000)).padding(4.dp)) {
                if (text.isEmpty()) Text(hint, style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color(0x88000000))
                inner()
            }
        },
        modifier = modifier.height(28.dp),
    )
}

@Composable
private fun MiniNumField(value: Double, hint: String, modifier: Modifier, onValue: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf("%.1f".format(value)) }
    androidx.compose.foundation.text.BasicTextField(
        value = text,
        onValueChange = {
            text = it
            it.replace(',', '.').toDoubleOrNull()?.let(onValue)
        },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        decorationBox = { inner ->
            Box(Modifier.background(androidx.compose.ui.graphics.Color(0x14000000)).padding(4.dp)) {
                inner()
            }
        },
        modifier = modifier.height(28.dp),
    )
}
