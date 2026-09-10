package com.buz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buz.core.*

/** Dense, spreadsheet-style editor for the measurements list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableDataTable(
    data: BuzDataset?,
    measurements: SnapshotStateList<Measurement>,
    familyAssignment: IntArray,
    onSave: () -> Unit,
) {
    if (data == null && measurements.isEmpty()) {
        Text("Sin datos. Pulsa 'Abrir' (.dip / .csv / .tsv / .xlsx).")
        return
    }
    val orientationType = measurements.firstOrNull()?.type
        ?: data?.header?.orientationType ?: OrientationType.DIP_DIPDIR
    val (aName, bName) = when (orientationType) {
        OrientationType.DIP_DIPDIR -> "dip" to "ddir"
        OrientationType.STRIKE_RHR_DIP, OrientationType.STRIKE_DIPQ -> "strike" to "dip"
        OrientationType.TREND_PLUNGE -> "trend" to "plun"
        OrientationType.PLUNGE_TREND -> "plun" to "trend"
    }

    val pageSize = 20
    var page by remember { mutableIntStateOf(0) }
    val nPages = ((measurements.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    if (page >= nPages) page = nPages - 1

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                val nextId = (measurements.maxOfOrNull { it.rowId } ?: 0) + 1
                measurements.add(Measurement(0.0, 0.0, 1.0, null, emptyList(), orientationType, nextId, null))
                page = (measurements.size - 1) / pageSize
            }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("+ Fila") }
            TextButton(onClick = onSave,
                contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Guardar") }
            Spacer(Modifier.weight(1f))
            Text("n=${measurements.size}", style = MaterialTheme.typography.bodySmall)
        }
        Text("${data?.sourceName ?: "editado"} · $orientationType", style = MaterialTheme.typography.labelSmall)
        HorizontalDivider(Modifier.padding(vertical = 2.dp))

        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            HeaderCell("#", 0.5f)
            HeaderCell("SL", 0.6f)
            HeaderCell("Fam", 0.6f)
            HeaderCell(aName, 1.0f)
            HeaderCell(bName, 1.0f)
            HeaderCell("Δm", 0.9f)
            HeaderCell("dist m", 1.0f)
            HeaderCell("", 0.5f)
        }
        HorizontalDivider()

        val fromIdx = page * pageSize
        val toIdx = ((page + 1) * pageSize).coerceAtMost(measurements.size)
        val rowsInPage = (toIdx - fromIdx).coerceAtLeast(0)

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(rowsInPage) { offset ->
                val i = fromIdx + offset
                if (i < measurements.size) {
                    val m = measurements[i]
                    val prevAbs = if (i > 0) measurements.getOrNull(i - 1)?.distance else null
                    val relValue: Double? = when {
                        m.distance == null -> null
                        i == 0 -> m.distance
                        prevAbs == null -> null
                        else -> m.distance - prevAbs
                    }
                    val famIdx = familyAssignment.getOrElse(i) { -1 }
                    CompactRow(
                        m = m,
                        relDistance = relValue,
                        familyIndex = famIdx,
                        onChangeA = { v -> replaceAt(measurements, i) { it.copy(a = v) } },
                        onChangeB = { v -> replaceAt(measurements, i) { it.copy(b = v) } },
                        onChangeSL = { newSl ->
                            replaceAt(measurements, i) { it.copy(traverseId = newSl) }
                        },
                        onChangeRel = { newRel ->
                            replaceAt(measurements, i) { row ->
                                val base = if (i == 0) 0.0
                                           else measurements.getOrNull(i - 1)?.distance ?: 0.0
                                row.copy(distance = if (newRel == null) null else base + newRel)
                            }
                        },
                        onChangeAbs = { newAbs ->
                            replaceAt(measurements, i) { it.copy(distance = newAbs) }
                        },
                        onDelete = { if (i < measurements.size) measurements.removeAt(i) },
                    )
                    HorizontalDivider(color = Color(0x14000000))
                }
            }
        }

        if (nPages > 1) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (page > 0) page-- }, enabled = page > 0,
                    contentPadding = PaddingValues(horizontal = 6.dp)) { Text("‹") }
                Text("Pag. ${page + 1}/$nPages", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                TextButton(onClick = { if (page < nPages - 1) page++ }, enabled = page < nPages - 1,
                    contentPadding = PaddingValues(horizontal = 6.dp)) { Text("›") }
            }
        }
    }
}

private fun replaceAt(list: SnapshotStateList<Measurement>, i: Int, f: (Measurement) -> Measurement) {
    if (i in list.indices) list[i] = f(list[i])
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CompactRow(
    m: Measurement,
    relDistance: Double?,
    familyIndex: Int,
    onChangeA: (Double) -> Unit,
    onChangeB: (Double) -> Unit,
    onChangeSL: (Int?) -> Unit,
    onChangeRel: (Double?) -> Unit,
    onChangeAbs: (Double?) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(32.dp).padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${m.rowId}",
            modifier = Modifier.weight(0.5f).padding(horizontal = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
        CellIntOrNull(m.traverseId, 0.6f, tint = Color(0x1FB05E00), onValue = onChangeSL)
        val famText = if (familyIndex >= 0) "F${familyIndex + 1}" else "SF"
        Text(famText, modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        Cell(m.a, 1.0f, onChangeA)
        Cell(m.b, 1.0f, onChangeB)
        CellDoubleOrNull(relDistance, 0.9f, tint = Color(0x1F1F4EA8), onValue = onChangeRel)
        CellDoubleOrNull(m.distance, 1.0f, tint = Color(0x1F1B7F3B), onValue = onChangeAbs)
        TextButton(
            onClick = onDelete,
            modifier = Modifier.weight(0.5f),
            contentPadding = PaddingValues(0.dp),
        ) { Text("×", style = MaterialTheme.typography.titleSmall) }
    }
}

@Composable
private fun RowScope.CellIntOrNull(
    value: Int?, weight: Float,
    tint: Color = Color(0x14000000),
    onValue: (Int?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onValue(if (it.isBlank()) null else it.toIntOrNull())
        },
        singleLine = true,
        textStyle = cellStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp).background(tint),
    )
}

@Composable
private fun RowScope.Cell(value: Double, weight: Float, onValue: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(fmt(value)) }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            it.replace(',', '.').toDoubleOrNull()?.let(onValue)
        },
        singleLine = true,
        textStyle = cellStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp).background(Color(0x14000000)),
    )
}

@Composable
private fun RowScope.CellDoubleOrNull(
    value: Double?, weight: Float,
    tint: Color = Color(0x14000000),
    onValue: (Double?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value?.let { fmt(it) } ?: "") }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onValue(if (it.isBlank()) null else it.replace(',', '.').toDoubleOrNull())
        },
        singleLine = true,
        textStyle = cellStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp).background(tint),
    )
}

private val cellStyle = TextStyle(
    fontSize = 13.sp,
    fontFamily = FontFamily.Monospace,
    textAlign = TextAlign.Center,
)

private fun fmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)
