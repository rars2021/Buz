package com.buz.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buz.core.*

/**
 * Dense, spreadsheet-style editor for the measurements list.
 * Single implicit scanline: no SL column, no per-row scanline picker.
 * The table always renders — if there are no rows, the parent should pre-seed
 * a few empties so the user can start typing right away.
 *
 * In landscape mode two pages render side-by-side and navigation steps by
 * two pages at a time, so the wider viewport is actually used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableDataTable(
    data: BuzDataset?,
    measurements: SnapshotStateList<Measurement>,
    onSave: () -> Unit,
) {
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

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // In landscape we render two consecutive pages side-by-side, so nav
    // effectively steps by 2 — the pair the user sees moves as a unit.
    val step = if (isLandscape) 2 else 1

    fun addRow() {
        val nextId = (measurements.maxOfOrNull { it.rowId } ?: 0) + 1
        measurements.add(Measurement(0.0, 0.0, 1.0, null, emptyList(), orientationType, nextId, null))
    }

    Column(Modifier.fillMaxSize()) {
        // One compact strip on top — buttons + source/format/count on the right.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                addRow()
                page = (measurements.size - 1) / pageSize
            }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("+ Fila") }
            TextButton(onClick = {
                repeat(pageSize) { addRow() }
                page = (measurements.size - 1) / pageSize
            }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("+ Página") }
            TextButton(onClick = onSave,
                contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Guardar") }
            Spacer(Modifier.weight(1f))
            Text(
                "${data?.sourceName ?: "editado"} · $orientationType · n=${measurements.size}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        HorizontalDivider()

        if (isLandscape) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                PageTable(
                    measurements, page, pageSize, aName, bName,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onAddRow = ::addRow,
                )
                VerticalDivider()
                PageTable(
                    measurements, page + 1, pageSize, aName, bName,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onAddRow = ::addRow,
                )
            }
        } else {
            PageTable(
                measurements, page, pageSize, aName, bName,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onAddRow = ::addRow,
            )
        }

        if (nPages > 1) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { page = (page - step).coerceAtLeast(0) },
                    enabled = page > 0,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text("‹") }
                val pageLabel = if (isLandscape && page + 1 < nPages)
                    "Pág. ${page + 1}-${page + 2}/$nPages"
                else "Pág. ${page + 1}/$nPages"
                Text(
                    pageLabel,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = { page = (page + step).coerceAtMost(nPages - 1) },
                    enabled = page < nPages - 1,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text("›") }
            }
        }
    }
}

/**
 * One page of the table (header row + up to `pageSize` measurement rows).
 * Extracted so landscape can render two of these side-by-side.
 */
@Composable
private fun PageTable(
    measurements: SnapshotStateList<Measurement>,
    page: Int,
    pageSize: Int,
    aName: String,
    bName: String,
    modifier: Modifier = Modifier,
    onAddRow: () -> Unit,
) {
    val fromIdx = page * pageSize
    val toIdx = ((page + 1) * pageSize).coerceAtMost(measurements.size)
    val rowsInPage = (toIdx - fromIdx).coerceAtLeast(0)

    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            HeaderCell("#", 0.6f)
            HeaderCell(aName, 1.2f)
            HeaderCell(bName, 1.2f)
            HeaderCell("Δm", 1.1f)
            HeaderCell("dist m", 1.2f)
            HeaderCell("", 0.5f)
        }
        HorizontalDivider()

        if (rowsInPage == 0) {
            Box(Modifier.fillMaxWidth().weight(1f)) {}
            return@Column
        }

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
                    CompactRow(
                        m = m,
                        relDistance = relValue,
                        onChangeA = { v -> replaceAt(measurements, i) { it.copy(a = v) } },
                        onChangeB = { v -> replaceAt(measurements, i) { it.copy(b = v) } },
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
                        isLastRow = (i == measurements.size - 1),
                        onSubmitLast = { onAddRow() },
                    )
                    HorizontalDivider(color = Color(0x14000000))
                }
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
    onChangeA: (Double) -> Unit,
    onChangeB: (Double) -> Unit,
    onChangeRel: (Double?) -> Unit,
    onChangeAbs: (Double?) -> Unit,
    onDelete: () -> Unit,
    isLastRow: Boolean,
    onSubmitLast: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(34.dp).padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${m.rowId}",
            modifier = Modifier.weight(0.6f).padding(horizontal = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
        NumCell(m.a, 1.2f, isLast = false, onSubmitLast = onSubmitLast, onValue = onChangeA)
        NumCell(m.b, 1.2f, isLast = false, onSubmitLast = onSubmitLast, onValue = onChangeB)
        NumCellNullable(relDistance, 1.1f, tint = Color(0x1F1F4EA8),
            isLast = false, onSubmitLast = onSubmitLast, onValue = onChangeRel)
        NumCellNullable(m.distance, 1.2f, tint = Color(0x1F1B7F3B),
            isLast = isLastRow, onSubmitLast = onSubmitLast, onValue = onChangeAbs)
        TextButton(
            onClick = onDelete,
            modifier = Modifier.weight(0.5f),
            contentPadding = PaddingValues(0.dp),
        ) { Text("×", style = MaterialTheme.typography.titleSmall) }
    }
}

/**
 * Numeric editable cell. Enter/Check advances focus to the next field.
 * When it is the very last cell of the very last row, Enter appends a new
 * row and moves focus into it — so typing straight through a table just
 * keeps going without touching "+ Fila".
 */
@Composable
private fun RowScope.NumCell(
    value: Double,
    weight: Float,
    isLast: Boolean,
    onSubmitLast: () -> Unit,
    onValue: (Double) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(fmt(value)) }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            it.replace(',', '.').toDoubleOrNull()?.let(onValue)
        },
        singleLine = true,
        textStyle = cellStyle,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = {
                onSubmitLast()
                focusManager.moveFocus(FocusDirection.Next)
            },
        ),
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp).background(Color(0x14000000)),
    )
}

@Composable
private fun RowScope.NumCellNullable(
    value: Double?,
    weight: Float,
    tint: Color = Color(0x14000000),
    isLast: Boolean,
    onSubmitLast: () -> Unit,
    onValue: (Double?) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(value?.let { fmt(it) } ?: "") }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onValue(if (it.isBlank()) null else it.replace(',', '.').toDoubleOrNull())
        },
        singleLine = true,
        textStyle = cellStyle,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = {
                onSubmitLast()
                focusManager.moveFocus(FocusDirection.Next)
            },
        ),
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp).background(tint),
    )
}

private val cellStyle = TextStyle(
    fontSize = 14.sp,
    fontFamily = FontFamily.Monospace,
    textAlign = TextAlign.Center,
)

private fun fmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)
