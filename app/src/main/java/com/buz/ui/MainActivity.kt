package com.buz.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BuzApp() {
    val context = LocalContextCompat.current
    val density = LocalDensity.current

    val initial = remember { BuzPrefs.load(context) }

    var dataset by remember { mutableStateOf<BuzDataset?>(null) }
    var lastUri by remember { mutableStateOf(initial.lastUri) }
    var projection by remember { mutableStateOf(initial.projection) }
    var polesDensity by remember { mutableStateOf(initial.showContours) }
    var wedgesDensity by remember { mutableStateOf(false) }
    var showPlanes by remember { mutableStateOf(initial.showPlanes) }
    var showPoles by remember { mutableStateOf(initial.showPoles) }
    var showGrid by remember { mutableStateOf(initial.showGrid) }
    var showLabels by remember { mutableStateOf(initial.showLabels) }
    var polesSigmaDeg by remember { mutableStateOf(initial.polesSigmaDeg) }
    var wedgesKamb by remember { mutableStateOf(initial.wedgesKamb) }
    // Terzaghi is disabled by default now (no UI toggle). Kept off; the weights
    // computation below returns 1.0 for every pole, so nothing else changes.
    val applyTerzaghi = false
    var autoOn by remember { mutableStateOf(initial.autoOn) }
    var autoK by remember { mutableStateOf(initial.autoK) }
    var coneAngleDeg by remember { mutableStateOf(initial.coneAngleDeg) }
    var familyMethod by remember { mutableStateOf(initial.familyMethod) }
    // Sigma is unified: same value drives Q polos heatmap AND density-peak families.
    // peakMinFraction stays as a small internal default (no dedicated slider).
    val peakMinFraction = 0.2
    var peakMergeDeg by remember { mutableStateOf(initial.peakMergeDeg) }
    var showFamilyRings by remember { mutableStateOf(initial.showFamilyRings) }
    var showFamilyPlanes by remember { mutableStateOf(initial.showFamilyPlanes) }
    var scanlineTrend by remember { mutableStateOf(initial.scanlineTrend) }
    var scanlinePlunge by remember { mutableStateOf(initial.scanlinePlunge) }
    val measurements = remember { mutableStateListOf<Measurement>() }
    val scanlineMetas = remember { mutableStateListOf<ScanlineMeta>().apply { addAll(initial.scanlineMetas) } }
    val hiddenSLs = remember { mutableStateListOf<Int>().apply { addAll(initial.hiddenScanlineIds) } }

    var tab by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saveMsg by remember { mutableStateOf<String?>(null) }
    var scanlineExportMsg by remember { mutableStateOf<String?>(null) }

    val scanlineAxis = remember(scanlineTrend, scanlinePlunge) {
        Pole.fromTrendPlunge(scanlineTrend, scanlinePlunge)
    }

    LaunchedEffect(Unit) {
        val u = initial.lastUri
        if (u != null && dataset == null) {
            try {
                val d = loadFromUri(context, Uri.parse(u))
                dataset = d
                measurements.clear()
                measurements.addAll(d.measurements)
            } catch (_: Exception) { }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        loadError = null
        try {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val d = loadFromUri(context, uri)
            dataset = d
            lastUri = uri.toString()
            measurements.clear()
            measurements.addAll(d.measurements)
        } catch (e: Exception) { loadError = e.message ?: e::class.simpleName }
    }

    val poles = remember(measurements.toList()) { measurements.map { it.toPole() } }

    // Auto-create metas for any SL id present in data but missing from state.
    LaunchedEffect(measurements.toList()) {
        val usedIds = measurements.mapNotNull { it.traverseId }.toSet()
        val existing = scanlineMetas.map { it.id }.toSet()
        for (id in usedIds - existing) {
            scanlineMetas.add(ScanlineMeta(id))
        }
    }

    val weights = remember(poles, applyTerzaghi, scanlineAxis, scanlineMetas.toList(), measurements.toList()) {
        if (!applyTerzaghi || poles.isEmpty()) List(poles.size) { 1.0 }
        else {
            val defaultAxis = scanlineAxis
            val axes = measurements.map { m ->
                val meta = m.traverseId?.let { id -> scanlineMetas.firstOrNull { it.id == id } }
                ScanlineAxis(meta?.axis() ?: defaultAxis)
            }
            Terzaghi.weights(poles, axes)
        }
    }
    val fisher = remember(poles) { Fisher.analyse(poles) }
    val polesGrid = remember(poles, projection, polesSigmaDeg) {
        if (poles.size >= 3) Density.gaussian(poles, 81, projection, polesSigmaDeg) else null
    }
    val wedges = remember(poles) { Density.wedgeIntersections(poles) }
    val wedgesGrid = remember(wedges, projection, wedgesKamb) {
        if (wedges.size >= 5) Density.kamb(wedges, 81, projection, wedgesKamb) else null
    }
    val activeGrid = when {
        wedgesDensity -> wedgesGrid
        polesDensity -> polesGrid
        else -> null
    }

    val densityDetection = remember(poles, autoOn, familyMethod,
        polesSigmaDeg, peakMinFraction, peakMergeDeg, projection) {
        if (!autoOn || familyMethod != "DENSITY" || poles.isEmpty()) null
        else runCatching {
            AutoFamilies.detectByDensityPeaksFull(
                poles, polesSigmaDeg, peakMinFraction, peakMergeDeg, projection
            )
        }.getOrNull()
    }
    val families = remember(poles, autoK, coneAngleDeg, autoOn, familyMethod, densityDetection) {
        if (!autoOn || poles.isEmpty()) emptyList()
        else if (familyMethod == "DENSITY") densityDetection?.families ?: emptyList()
        else runCatching { AutoFamilies.detect(poles, autoK, coneAngleDeg) }.getOrDefault(emptyList())
    }
    val familyPct = remember(families, poles) { AutoFamilies.percentages(families, poles.size) }
    val familyAssignment = remember(poles, families, coneAngleDeg) {
        if (families.isEmpty()) IntArray(poles.size) { -1 }
        else AutoFamilies.assign(poles, families, coneAngleDeg)
    }
    val familyFisher = remember(poles, families) {
        families.map { fam ->
            val members = fam.members.mapNotNull { poles.getOrNull(it) }
            Fisher.analyse(members)
        }
    }
    val poleColourIndex = remember(familyAssignment, autoOn, poles.size) {
        if (autoOn) familyAssignment else IntArray(poles.size) { -1 }
    }
    val useFamilyPalette = autoOn

    // Visible poles/colours after SL filter (used only in the Red tab display).
    val visibleIdx = remember(measurements.toList(), hiddenSLs.toList()) {
        measurements.mapIndexedNotNull { i, m ->
            if (m.traverseId != null && m.traverseId in hiddenSLs) null else i
        }
    }
    val visiblePoles = remember(poles, visibleIdx) { visibleIdx.map { poles[it] } }
    val visibleColourIndex = remember(poleColourIndex, visibleIdx) {
        IntArray(visibleIdx.size) { j -> poleColourIndex.getOrNull(visibleIdx[j]) ?: -1 }
    }
    val presentSLIds = remember(measurements.toList()) {
        measurements.mapNotNull { it.traverseId }.toSortedSet().toList()
    }
    val roseBins = remember(measurements.toList(), weights) {
        if (measurements.isEmpty()) return@remember emptyList()
        val az = measurements.map { m ->
            when (m.type) {
                OrientationType.DIP_DIPDIR -> (m.b - 90.0 + 360.0) % 360.0
                OrientationType.STRIKE_RHR_DIP, OrientationType.STRIKE_DIPQ -> m.a
                OrientationType.TREND_PLUNGE -> m.a
                OrientationType.PLUNGE_TREND -> m.b
            }
        }
        Rose.build(az, weights = weights, bins = 36, axial = true)
    }

    LaunchedEffect(
        lastUri, projection, polesDensity, showPlanes, showPoles, showGrid, showLabels,
        wedgesDensity, applyTerzaghi, autoOn, autoK, coneAngleDeg,
        scanlineTrend, scanlinePlunge, scanlineMetas.toList(), hiddenSLs.toList(),
        polesSigmaDeg, wedgesKamb,
        familyMethod, peakMergeDeg,
        showFamilyRings, showFamilyPlanes,
    ) {
        BuzPrefs.save(context, BuzState(
            lastUri = lastUri, projection = projection,
            showContours = polesDensity, showPlanes = showPlanes,
            showPoles = showPoles, showGrid = showGrid, showLabels = showLabels,
            filledDensity = true, applyTerzaghi = false,
            autoOn = autoOn, autoK = autoK, coneAngleDeg = coneAngleDeg,
            scanlineTrend = scanlineTrend, scanlinePlunge = scanlinePlunge,
            drawShape = WindowShape.RECT, windows = emptyList(),
            scanlineMetas = scanlineMetas.toList(),
            hiddenScanlineIds = hiddenSLs.toSet(),
            polesSigmaDeg = polesSigmaDeg,
            wedgesKamb = wedgesKamb,
            familyMethod = familyMethod,
            peakSigmaDeg = polesSigmaDeg,
            peakMinFraction = peakMinFraction,
            peakMergeDeg = peakMergeDeg,
            showFamilyRings = showFamilyRings,
            showFamilyPlanes = showFamilyPlanes,
        ))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dataset?.header?.title1 ?: "Buz") },
                actions = {
                    TextButton(onClick = { picker.launch(arrayOf("*/*")) },
                        contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Abrir") }
                    TextButton(onClick = {
                        exportPlotAsPng(context, density, poles, activeGrid, projection,
                            polesDensity || wedgesDensity, showPlanes, emptyList(), poleColourIndex)
                    }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("PNG") }
                    TextButton(onClick = {
                        exportCsvs(context, dataset, emptyList(), emptyList())
                    }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("CSV") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = {}, label = { Text("Red") })
                NavigationBarItem(tab == 1, { tab = 1 }, icon = {}, label = { Text("Datos") })
                NavigationBarItem(tab == 2, { tab = 2 }, icon = {}, label = { Text("Estad.") })
                NavigationBarItem(tab == 3, { tab = 3 }, icon = {}, label = { Text("Scanline") })
                NavigationBarItem(tab == 4, { tab = 4 }, icon = {}, label = { Text("Familias") })
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(6.dp)) {
            loadError?.let {
                Card { Text("Error al leer: $it", Modifier.padding(8.dp)) }
                Spacer(Modifier.height(4.dp))
            }
            when (tab) {
                0 -> RedTab(
                    projection = projection, onProjection = { projection = it },
                    polesDensity = polesDensity, onPolesDensity = {
                        polesDensity = it; if (it) wedgesDensity = false
                    },
                    wedgesDensity = wedgesDensity, onWedgesDensity = {
                        wedgesDensity = it; if (it) polesDensity = false
                    },
                    showPlanes = showPlanes, onPlanes = { showPlanes = it },
                    showPoles = showPoles, onPoles = { showPoles = it },
                    showGrid = showGrid, onGrid = { showGrid = it },
                    showLabels = showLabels, onLabels = { showLabels = it },
                    polesSigmaDeg = polesSigmaDeg, onPolesSigma = { polesSigmaDeg = it },
                    wedgesKamb = wedgesKamb, onWedgesKamb = { wedgesKamb = it },
                    poles = visiblePoles,
                    activeGrid = activeGrid,
                    poleColourIndex = visibleColourIndex,
                    useFamilyPalette = useFamilyPalette,
                    clusterCentres = if (autoOn) families.map { it.centre } else emptyList(),
                    clusterAngleDeg = if (autoOn) coneAngleDeg else null,
                    scanlineAxis = if (applyTerzaghi) scanlineAxis else null,
                    presentSLIds = presentSLIds,
                    hiddenSLs = hiddenSLs,
                    familiesActive = autoOn && families.isNotEmpty(),
                    showFamilyRings = showFamilyRings, onFamilyRings = { showFamilyRings = it },
                    showFamilyPlanes = showFamilyPlanes, onFamilyPlanes = { showFamilyPlanes = it },
                    familyMeanPlanes = if (autoOn && showFamilyPlanes)
                        families.map { it.centre } else emptyList(),
                    familyBasins = densityDetection?.basins ?: IntArray(0),
                    familyBasinsGridSize = densityDetection?.gridSize ?: 0,
                    familyBasinCount = densityDetection?.families?.size ?: 0,
                )
                1 -> {
                    saveMsg?.let {
                        Card { Text(it, Modifier.padding(6.dp)) }
                        Spacer(Modifier.height(2.dp))
                    }
                    EditableDataTable(
                        data = dataset,
                        measurements = measurements,
                        familyAssignment = familyAssignment,
                        onSave = {
                            saveMsg = saveMeasurements(context, lastUri, measurements.toList())
                        },
                    )
                }
                2 -> RoseAndStats(roseBins, fisher, weights, applyTerzaghi,
                    families, familyFisher, familyPct)
                3 -> ScanlineTab(
                    scanlineMetas = scanlineMetas,
                    measurements = measurements.toList(),
                    poleColourIndex = poleColourIndex,
                    onExportDipsOnly = {
                        scanlineExportMsg = exportScanlineCsv(context, measurements.toList(), withDist = false)
                    },
                    onExportWithDist = {
                        scanlineExportMsg = exportScanlineCsv(context, measurements.toList(), withDist = true)
                    },
                    exportMsg = scanlineExportMsg,
                )
                4 -> FamiliesTab(
                    autoOn = autoOn, onAutoOn = { autoOn = it },
                    method = familyMethod, onMethod = { familyMethod = it },
                    k = autoK, onK = { autoK = it },
                    angleDeg = coneAngleDeg, onAngle = { coneAngleDeg = it },
                    sigmaDeg = polesSigmaDeg, onSigma = { polesSigmaDeg = it },
                    peakMergeDeg = peakMergeDeg, onPeakMerge = { peakMergeDeg = it },
                    families = families, percents = familyPct,
                    fisherPerFamily = familyFisher, total = poles.size,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RedTab(
    projection: ProjectionType, onProjection: (ProjectionType) -> Unit,
    polesDensity: Boolean, onPolesDensity: (Boolean) -> Unit,
    wedgesDensity: Boolean, onWedgesDensity: (Boolean) -> Unit,
    showPlanes: Boolean, onPlanes: (Boolean) -> Unit,
    showPoles: Boolean, onPoles: (Boolean) -> Unit,
    showGrid: Boolean, onGrid: (Boolean) -> Unit,
    showLabels: Boolean, onLabels: (Boolean) -> Unit,
    polesSigmaDeg: Double, onPolesSigma: (Double) -> Unit,
    wedgesKamb: Double, onWedgesKamb: (Double) -> Unit,
    poles: List<Pole>,
    activeGrid: Density.Grid?,
    poleColourIndex: IntArray,
    useFamilyPalette: Boolean,
    clusterCentres: List<Pole>,
    clusterAngleDeg: Double?,
    scanlineAxis: Pole?,
    presentSLIds: List<Int>,
    hiddenSLs: androidx.compose.runtime.snapshots.SnapshotStateList<Int>,
    familiesActive: Boolean,
    showFamilyRings: Boolean, onFamilyRings: (Boolean) -> Unit,
    showFamilyPlanes: Boolean, onFamilyPlanes: (Boolean) -> Unit,
    familyMeanPlanes: List<Pole>,
    familyBasins: IntArray,
    familyBasinsGridSize: Int,
    familyBasinCount: Int,
) {
    Column(Modifier.fillMaxSize()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CompactChip(projection == ProjectionType.EQUAL_AREA, { onProjection(ProjectionType.EQUAL_AREA) }, "Equiar.")
            CompactChip(projection == ProjectionType.EQUAL_ANGLE, { onProjection(ProjectionType.EQUAL_ANGLE) }, "Equiang.")
            CompactChip(polesDensity, { onPolesDensity(!polesDensity) }, "Q polos")
            CompactChip(wedgesDensity, { onWedgesDensity(!wedgesDensity) }, "Q cuñas")
            CompactChip(showPoles, { onPoles(!showPoles) }, "Polos")
            CompactChip(showPlanes, { onPlanes(!showPlanes) }, "Planos")
            CompactChip(showGrid, { onGrid(!showGrid) }, "Grid")
            CompactChip(showLabels, { onLabels(!showLabels) }, "Métricas")
        }
        // Parameter slider — only when a density is active.
        if (polesDensity) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("σ polos ${"%.0f".format(polesSigmaDeg)}°", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(end = 6.dp))
                Slider(
                    value = polesSigmaDeg.toFloat(),
                    onValueChange = { onPolesSigma(it.toDouble()) },
                    valueRange = 3f..30f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (wedgesDensity) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("k cuñas ${"%.1f".format(wedgesKamb)}", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(end = 6.dp))
                Slider(
                    value = wedgesKamb.toFloat(),
                    onValueChange = { onWedgesKamb(it.toDouble()) },
                    valueRange = 1f..5f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // SL filter chips — only visible when there is more than one scanline.
        if (presentSLIds.size >= 2) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Filtro SL:", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp, end = 4.dp))
                for (id in presentSLIds) {
                    val visible = id !in hiddenSLs
                    CompactChip(visible, {
                        if (visible) hiddenSLs.add(id) else hiddenSLs.remove(id)
                    }, "SL $id")
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            StereonetView(
                plot = StereonetPlot(
                    poles = poles,
                    planes = if (showPlanes) poles else emptyList(),
                    densityGrid = activeGrid,
                    projection = projection,
                    showGrid = showGrid,
                    showPoles = showPoles,
                    showLabels = showLabels,
                    filledDensity = true,
                    windows = emptyList(),
                    poleSetIndex = poleColourIndex,
                    useFamilyPalette = useFamilyPalette,
                    clusterCentres = clusterCentres,
                    clusterAngleDeg = clusterAngleDeg,
                    showClusterRings = showFamilyRings,
                    familyMeanPlanes = familyMeanPlanes,
                    familyBasins = familyBasins,
                    familyBasinsGridSize = familyBasinsGridSize,
                    familyBasinCount = familyBasinCount,
                    scanlineAxis = scanlineAxis,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }
        val densityLabel = when {
            wedgesDensity -> "Q cuñas (intersecciones de planos, no zonas de deslizamiento)"
            polesDensity -> "Q polos (densidad Kamb, calor sobre las orientaciones)"
            else -> null
        }
        val fams = if (clusterCentres.isNotEmpty()) "  |  Familias: ${clusterCentres.size}" else ""
        val dens = if (densityLabel != null && activeGrid != null)
            "  |  $densityLabel — pico ${"%.1f".format(activeGrid.maxSigma)} σ" else ""
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("N=${poles.size}$fams$dens",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f))
            if (familiesActive) {
                CompactChip(showFamilyRings, { onFamilyRings(!showFamilyRings) }, "Anillos")
                Spacer(Modifier.width(4.dp))
                CompactChip(showFamilyPlanes, { onFamilyPlanes(!showFamilyPlanes) }, "Planos F")
            }
        }
    }
}

@Composable
private fun RoseAndStats(
    roseBins: List<Rose.Bin>,
    fisher: Fisher.Result?,
    weights: List<Double>,
    applyTerzaghi: Boolean,
    families: List<AutoFamilies.Family>,
    fisherPerFamily: List<Fisher.Result?>,
    percents: List<Double>,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(310.dp)) {
            RoseView(roseBins, Modifier.fillMaxSize())
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(4.dp)) {
            item {
                Text("Fisher global", style = MaterialTheme.typography.titleSmall)
                if (fisher == null) Text("Sin datos.", style = MaterialTheme.typography.bodySmall)
                else {
                    Text("N=${fisher.n}   R=${"%.3f".format(fisher.R)}", style = MaterialTheme.typography.bodySmall)
                    Text("Media trend=${"%.1f".format(fisher.mean.trend)}°  plunge=${"%.1f".format(fisher.mean.plunge)}°",
                        style = MaterialTheme.typography.bodySmall)
                    Text("k=${if (fisher.k.isFinite()) "%.2f".format(fisher.k) else "∞"}   cono95=${"%.2f".format(fisher.cone95Deg)}°",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (applyTerzaghi && weights.isNotEmpty()) {
                    Text("Terzaghi: Σw=${"%.1f".format(weights.sum())}  wmax=${"%.2f".format(weights.max())}",
                        style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                Text("Familias detectadas", style = MaterialTheme.typography.titleSmall)
                if (families.isEmpty()) {
                    Text("Sin familias activas (activá desde la pestaña Familias).",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            items(families.size) { i ->
                val f = families[i]; val r = fisherPerFamily.getOrNull(i)
                val pct = percents.getOrElse(i) { 0.0 }
                Text("F${i + 1}  n=${f.members.size}  (${"%.1f".format(pct)}%)  " +
                     "centro=${"%.0f".format(f.centre.trend)}°/${"%.0f".format(f.centre.plunge)}°" +
                     (if (r != null) "  k=${if (r.k.isFinite()) "%.1f".format(r.k) else "∞"}" else ""),
                    style = MaterialTheme.typography.bodySmall)
            }
            item {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Desarrollado por R. Sagastegui",
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(androidx.compose.material.icons.Icons.Default.Email, contentDescription = "Email", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("andre.ramirez.@uni.pe", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(androidx.compose.material.icons.Icons.Default.Phone, contentDescription = "Teléfono", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("+51 912103589", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(androidx.compose.material.icons.Icons.Default.Info, contentDescription = "GitHub", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("github.com/rars2021", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FamiliesTab(
    autoOn: Boolean, onAutoOn: (Boolean) -> Unit,
    method: String, onMethod: (String) -> Unit,
    k: Int, onK: (Int) -> Unit,
    angleDeg: Double, onAngle: (Double) -> Unit,
    sigmaDeg: Double, onSigma: (Double) -> Unit,
    peakMergeDeg: Double, onPeakMerge: (Double) -> Unit,
    families: List<AutoFamilies.Family>,
    percents: List<Double>,
    fisherPerFamily: List<Fisher.Result?>,
    total: Int,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Text("Familias automáticas", style = MaterialTheme.typography.titleMedium)
        FilterChip(autoOn, { onAutoOn(!autoOn) },
            { Text(if (autoOn) "Activas — se ven en la Red" else "Activar familias") })

        Spacer(Modifier.height(10.dp))
        Text("Método de detección", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(method == "KMEANS", { onMethod("KMEANS") },
                { Text("K-means (k fijo)") })
            FilterChip(method == "DENSITY", { onMethod("DENSITY") },
                { Text("Picos de densidad") })
        }

        Spacer(Modifier.height(10.dp))
        if (method == "KMEANS") {
            Text("K-means axial: elige un número fijo de familias y cada polo va a la más cercana dentro del cono.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("Número de familias (k): $k")
            Slider(
                value = k.toFloat(),
                onValueChange = { onK(it.toInt().coerceIn(1, 8)) },
                valueRange = 1f..8f, steps = 6,
            )
        } else {
            Text("Picos de densidad: cada máximo local del mapa de calor Gaussiano se vuelve una familia. σ chico = picos afilados, más familias. Fusión colapsa picos cercanos entre sí.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("σ campana: ${"%.0f".format(sigmaDeg)}°   (mismo σ que Q polos)")
            Slider(
                value = sigmaDeg.toFloat().coerceIn(3f, 30f),
                onValueChange = { onSigma(it.toDouble()) },
                valueRange = 3f..30f,
            )
            Spacer(Modifier.height(6.dp))
            Text("Fusionar picos separados por menos de: ${"%.0f".format(peakMergeDeg)}°")
            Slider(
                value = peakMergeDeg.toFloat().coerceIn(0f, 45f),
                onValueChange = { onPeakMerge(it.toDouble()) },
                valueRange = 0f..45f,
            )
            Text("0° = no fusiona nada. Subilo si dos picos cercanos deberían ser una sola familia.",
                style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(6.dp))
        Text("Ángulo de ventana (cono para asignar polos a cada familia): ${"%.0f".format(angleDeg)}°")
        Slider(
            value = angleDeg.toFloat().coerceIn(5f, 60f),
            onValueChange = { onAngle(it.toDouble()) },
            valueRange = 5f..60f,
        )
        Text("El % se recalcula en vivo al mover los sliders.",
            style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(Modifier.height(6.dp))

        if (families.isEmpty()) {
            Text(if (!autoOn) "Activa el chip para ver las familias." else "Sin datos.")
        } else {
            val assigned = families.sumOf { it.members.size }
            val unassigned = (total - assigned).coerceAtLeast(0)
            Text("N total = $total   |   asignados = $assigned   |   sueltos = $unassigned",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            families.forEachIndexed { i, fam ->
                val fisher = fisherPerFamily.getOrNull(i)
                val pct = percents.getOrElse(i) { 0.0 }
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("Familia ${i + 1}  —  ${"%.1f".format(pct)}%  (n=${fam.members.size})",
                            style = MaterialTheme.typography.titleSmall)
                        Text("Centro: trend=${"%.1f".format(fam.centre.trend)}°  plunge=${"%.1f".format(fam.centre.plunge)}°",
                            style = MaterialTheme.typography.bodySmall)
                        if (fisher != null) {
                            Text("Fisher n=${fisher.n}  k=${if (fisher.k.isFinite()) "%.1f".format(fisher.k) else "∞"}  cono95=${"%.1f".format(fisher.cone95Deg)}°",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        val progress = (pct / 100.0).toFloat().coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
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
        val plot = StereonetPlot(
            poles = poles,
            planes = if (planes) poles else emptyList(),
            densityGrid = if (contours) grid else null,
            projection = projection,
            windows = windows,
            poleSetIndex = setAssignment,
        )
        PngRender.render(this, size, plot)
    }
    val ts = System.currentTimeMillis()
    val uri = PngExport.savePng(context, bmp, "buz_$ts.png")
    Toast.makeText(context, if (uri != null) "Guardado en Pictures/Buz" else "No se pudo guardar el PNG", Toast.LENGTH_SHORT).show()
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

private fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri, "wt")?.use {
        it.write(text.toByteArray(Charsets.UTF_8))
        true
    } ?: false
}.getOrDefault(false)

/** Save the current edited measurements. Overwrites the source file if the
 *  URI still allows writing, otherwise falls back to Documents/Buz. */
fun saveMeasurements(context: Context, lastUri: String?, ms: List<Measurement>): String {
    val text = CsvExport.measurementsRoundtrip(ms)
    if (lastUri != null) {
        val u = runCatching { Uri.parse(lastUri) }.getOrNull()
        if (u != null && writeTextToUri(context, u, text)) {
            return "Guardado sobre el archivo original."
        }
    }
    val ts = System.currentTimeMillis()
    val saved = PngExport.saveTextAsFile(context, text, "buz_edit_$ts.csv")
    return if (saved != null) "Guardado en Documents/Buz (nuevo archivo)."
           else "No se pudo guardar el archivo."
}

/** Export CSV for the scanline. `withDist` controls whether the position
 *  along the scanline is included. Always writes to Documents/Buz. */
private fun exportScanlineCsv(context: Context, ms: List<Measurement>, withDist: Boolean): String {
    if (ms.isEmpty()) return "Sin datos para exportar."
    val text = if (withDist) CsvExport.measurementsRoundtrip(ms)
               else CsvExport.measurementsDipsOnly(ms)
    val ts = System.currentTimeMillis()
    val suffix = if (withDist) "dips_dist" else "dips"
    val saved = PngExport.saveTextAsFile(context, text, "buz_scan_${suffix}_$ts.csv")
    return if (saved != null) "Guardado en Documents/Buz/buz_scan_${suffix}_$ts.csv"
           else "No se pudo guardar el archivo."
}

object LocalContextCompat {
    val current: Context
        @Composable get() = androidx.compose.ui.platform.LocalContext.current
}
