package com.buz.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.buz.core.*
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { BuzApp() } }
    }
}

data class BuzDataset(
    val sourceName: String,
    val header: DipHeader,
    val measurements: List<Measurement>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuzApp() {
    var dataset by remember { mutableStateOf<BuzDataset?>(null) }
    var projection by remember { mutableStateOf(ProjectionType.EQUAL_AREA) }
    var showContours by remember { mutableStateOf(true) }
    var showPlanes by remember { mutableStateOf(false) }
    var applyTerzaghi by remember { mutableStateOf(false) }
    val terzaghiAxis = remember { Pole.fromTrendPlunge(0.0, 90.0) }
    var tab by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var drawingMode by remember { mutableStateOf(false) }
    val windows = remember { mutableStateListOf<SetWindow>() }

    val context = LocalContextCompat.current
    val density = LocalDensity.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        loadError = null
        try { dataset = loadFromUri(context, uri); windows.clear() }
        catch (e: Exception) { loadError = e.message ?: e::class.simpleName }
    }

    val poles = remember(dataset) { dataset?.measurements?.map { it.toPole() } ?: emptyList() }
    val weights = remember(poles, applyTerzaghi) {
        if (applyTerzaghi && poles.isNotEmpty())
            Terzaghi.weights(poles, List(poles.size) { ScanlineAxis(terzaghiAxis) })
        else List(poles.size) { 1.0 }
    }
    val fisher = remember(poles) { Fisher.analyse(poles) }
    val densityGrid = remember(poles, projection) {
        if (poles.size >= 5) Density.kamb(poles, 81, projection) else null
    }
    val setAssignment = remember(poles, windows.toList(), projection) {
        val fixed = windows.map { it.copy(projection = projection) }
        if (fixed.isEmpty()) IntArray(poles.size) { -1 }
        else SetAssignment.assign(poles, fixed)
    }
    val setSummaries = remember(poles, windows.toList(), projection) {
        val fixed = windows.map { it.copy(projection = projection) }
        SetAssignment.summarise(poles, fixed)
    }
    val roseBins = remember(dataset, weights) {
        val ms = dataset?.measurements ?: return@remember emptyList()
        val az = ms.map { m ->
            when (m.type) {
                OrientationType.DIP_DIPDIR -> (m.b - 90.0 + 360.0) % 360.0
                OrientationType.STRIKE_RHR_DIP, OrientationType.STRIKE_DIPQ -> m.a
                OrientationType.TREND_PLUNGE -> m.a
                OrientationType.PLUNGE_TREND -> m.b
            }
        }
        Rose.build(az, weights = weights, bins = 36, axial = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dataset?.header?.title1 ?: "Buz") },
                actions = {
                    TextButton(onClick = {
                        picker.launch(arrayOf("*/*"))
                    }) { Text("Abrir") }
                    TextButton(onClick = {
                        exportPlotAsPng(context, density, poles, densityGrid, projection,
                            showContours, showPlanes, windows.toList(), setAssignment)
                    }) { Text("PNG") }
                    TextButton(onClick = {
                        exportCsvs(context, dataset, windows.toList(), setSummaries)
                    }) { Text("CSV") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = {}, label = { Text("Red") })
                NavigationBarItem(tab == 1, { tab = 1 }, icon = {}, label = { Text("Rosa") })
                NavigationBarItem(tab == 2, { tab = 2 }, icon = {}, label = { Text("Datos") })
                NavigationBarItem(tab == 3, { tab = 3 }, icon = {}, label = { Text("Estad.") })
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(8.dp)) {
            loadError?.let {
                Card { Text("Error al leer: $it", Modifier.padding(8.dp)) }
                Spacer(Modifier.height(4.dp))
            }
            when (tab) {
                0 -> {
                    Row {
                        FilterChip(projection == ProjectionType.EQUAL_AREA, { projection = ProjectionType.EQUAL_AREA }, { Text("Equiareal") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(projection == ProjectionType.EQUAL_ANGLE, { projection = ProjectionType.EQUAL_ANGLE }, { Text("Equiangular") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(showContours, { showContours = !showContours }, { Text("Contornos") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(showPlanes, { showPlanes = !showPlanes }, { Text("Planos") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(applyTerzaghi, { applyTerzaghi = !applyTerzaghi }, { Text("Terzaghi") })
                    }
                    Row {
                        FilterChip(drawingMode, { drawingMode = !drawingMode }, { Text(if (drawingMode) "Dibujando set…" else "Dibujar set") })
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = { if (windows.isNotEmpty()) windows.removeAt(windows.size - 1) }) { Text("Borrar último") }
                        TextButton(onClick = { windows.clear() }) { Text("Borrar sets") }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        StereonetView(
                            plot = StereonetPlot(
                                poles = poles,
                                planes = if (showPlanes) poles else emptyList(),
                                densityGrid = if (showContours) densityGrid else null,
                                projection = projection,
                                windows = windows.map { it.copy(projection = projection) },
                                poleSetIndex = setAssignment,
                            ),
                            modifier = Modifier.fillMaxSize(),
                            drawingEnabled = drawingMode,
                            onWindowDrawn = { x0, y0, x1, y1 ->
                                windows += SetWindow(
                                    id = windows.size,
                                    label = "Set ${windows.size + 1}",
                                    x0 = x0, y0 = y0, x1 = x1, y1 = y1,
                                    projection = projection,
                                )
                                drawingMode = false
                            }
                        )
                    }
                    Text("N=${poles.size}  |  Sets: ${windows.size}")
                }
                1 -> RoseView(roseBins, Modifier.fillMaxSize())
                2 -> DataTable(dataset)
                3 -> StatsView(fisher, weights, applyTerzaghi, windows, setSummaries)
            }
        }
    }
}

@Composable
fun DataTable(data: BuzDataset?) {
    if (data == null) { Text("Sin datos. Tocá 'Abrir' (.dip / .csv / .tsv / .xlsx)."); return }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(data.sourceName, style = MaterialTheme.typography.titleMedium)
            Text(data.header.title1, style = MaterialTheme.typography.bodyMedium)
            Text(data.header.title2, style = MaterialTheme.typography.bodySmall)
            Text("Orientación: ${data.header.orientationType}")
            Divider(Modifier.padding(vertical = 4.dp))
        }
        items(data.measurements) { m ->
            Text("${"%.1f".format(m.a)} / ${"%.1f".format(m.b)}   q=${m.quantity}   trav=${m.traverseId ?: "-"}")
        }
    }
}

@Composable
fun StatsView(
    fisher: Fisher.Result?,
    weights: List<Double>,
    applyTerzaghi: Boolean,
    windows: List<SetWindow>,
    perSet: List<Fisher.Result?>,
) {
    LazyColumn(Modifier.fillMaxSize().padding(4.dp)) {
        item {
            Text("Fisher global", style = MaterialTheme.typography.titleMedium)
            if (fisher == null) Text("Sin datos.")
            else {
                Text("N = ${fisher.n}")
                Text("Media: trend=${"%.1f".format(fisher.mean.trend)}°  plunge=${"%.1f".format(fisher.mean.plunge)}°")
                Text("R = ${"%.3f".format(fisher.R)}")
                Text("k = ${if (fisher.k.isFinite()) "%.2f".format(fisher.k) else "∞"}")
                Text("Cono 95% = ${"%.2f".format(fisher.cone95Deg)}°")
            }
            if (applyTerzaghi) {
                val sum = weights.sum(); val max = weights.maxOrNull() ?: 0.0
                Spacer(Modifier.height(6.dp))
                Text("Terzaghi: Σw = ${"%.1f".format(sum)}, wmax = ${"%.2f".format(max)}")
            }
            Spacer(Modifier.height(12.dp))
            Text("Sets dibujados", style = MaterialTheme.typography.titleMedium)
        }
        items(windows.size) { i ->
            val w = windows[i]; val r = perSet.getOrNull(i)
            Text("Set ${i + 1} (${w.label})")
            if (r == null) Text("   n=0")
            else Text("   n=${r.n}  media trend=${"%.1f".format(r.mean.trend)}° plunge=${"%.1f".format(r.mean.plunge)}°  k=${if (r.k.isFinite()) "%.1f".format(r.k) else "∞"}  cono95=${"%.1f".format(r.cone95Deg)}°")
            Divider(Modifier.padding(vertical = 4.dp))
        }
    }
}

private fun loadFromUri(context: Context, uri: Uri): BuzDataset {
    val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "archivo"
    val lower = name.lowercase()
    val cr = context.contentResolver
    return when {
        lower.endsWith(".xlsx") -> {
            val table = cr.openInputStream(uri).use { XlsxFile.parse(it!!) }
            val ms = CsvFile.toMeasurements(table)
            BuzDataset(name, defaultHeader(ms), ms)
        }
        lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt") -> {
            val table = cr.openInputStream(uri).use { CsvFile.parse(InputStreamReader(it!!)) }
            val ms = CsvFile.toMeasurements(table)
            BuzDataset(name, defaultHeader(ms), ms)
        }
        lower.endsWith(".dip") -> {
            val data = cr.openInputStream(uri).use { DipFile.parse(InputStreamReader(it!!)) }
            BuzDataset(name, data.header, data.measurements)
        }
        else -> {
            val bytes = cr.openInputStream(uri).use { it!!.readBytes() }
            if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                val table = XlsxFile.parse(bytes.inputStream())
                val ms = CsvFile.toMeasurements(table)
                BuzDataset(name, defaultHeader(ms), ms)
            } else {
                val text = String(bytes)
                if (text.contains("Traverse", ignoreCase = true) || text.lineSequence().take(4).joinToString(" ").contains("PROJECT", ignoreCase = true)) {
                    val data = DipFile.parse(text.reader())
                    BuzDataset(name, data.header, data.measurements)
                } else {
                    val table = CsvFile.parse(text.reader())
                    val ms = CsvFile.toMeasurements(table)
                    BuzDataset(name, defaultHeader(ms), ms)
                }
            }
        }
    }
}

private fun defaultHeader(ms: List<Measurement>) = DipHeader(
    "Importado", "", emptyList(),
    ms.firstOrNull()?.type ?: OrientationType.DIP_DIPDIR,
    ms.any { it.quantity != 1.0 }, emptyList()
)

private fun queryDisplayName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null) ?: return null
    return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
}

private fun exportPlotAsPng(
    context: Context,
    density: androidx.compose.ui.unit.Density,
    poles: List<Pole>,
    grid: Density.Grid?,
    projection: ProjectionType,
    contours: Boolean,
    planes: Boolean,
    windows: List<SetWindow>,
    setAssignment: IntArray,
) {
    val bmp = PngExport.renderToBitmap(1024, 1024, density) { size ->
        // Reuse StereonetView drawing by invoking its DrawScope on an off-screen picture.
        // For simplicity we draw a minimal representation here; the on-screen widget
        // remains authoritative for interactive exploration.
        val plot = StereonetPlot(
            poles = poles,
            planes = if (planes) poles else emptyList(),
            densityGrid = if (contours) grid else null,
            projection = projection,
            windows = windows,
            poleSetIndex = setAssignment,
        )
        // We can't call the @Composable here; the export uses the same math
        // via a lightweight replica implemented in PngRender.
        PngRender.render(this, size, plot)
    }
    val ts = System.currentTimeMillis()
    val uri = PngExport.savePng(context, bmp, "buz_$ts.png")
    Toast.makeText(context, if (uri != null) "Guardado en Pictures/Buz" else "No pude guardar el PNG", Toast.LENGTH_SHORT).show()
}

private fun exportCsvs(
    context: Context,
    dataset: BuzDataset?,
    windows: List<SetWindow>,
    setSummaries: List<Fisher.Result?>,
) {
    if (dataset == null) { Toast.makeText(context, "Sin datos.", Toast.LENGTH_SHORT).show(); return }
    val ts = System.currentTimeMillis()
    val a = PngExport.saveTextAsFile(context, CsvExport.measurements(dataset.measurements), "buz_data_$ts.csv")
    val b = if (windows.isNotEmpty())
        PngExport.saveTextAsFile(context, CsvExport.setStatistics(windows, setSummaries), "buz_sets_$ts.csv")
    else null
    Toast.makeText(context,
        "Guardado en Documents/Buz (${listOfNotNull(a, b).size} archivo/s)",
        Toast.LENGTH_SHORT).show()
}

object LocalContextCompat {
    val current: Context
        @Composable get() = androidx.compose.ui.platform.LocalContext.current
}
