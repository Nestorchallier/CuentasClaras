package com.nestor.cuentasclaras.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.ui.MainViewModel
import com.nestor.cuentasclaras.ui.components.*
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Dates
import com.nestor.cuentasclaras.util.Fmt
import com.nestor.cuentasclaras.util.Money
import com.nestor.cuentasclaras.util.Projection
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ActividadScreen(
    vm: MainViewModel,
    onOpenTx: (Tx) -> Unit,
    onSettings: () -> Unit,
    onOpenRecurrings: () -> Unit
) {
    val all by vm.txs.collectAsState()
    val cats by vm.categories.collectAsState()
    val accs by vm.accounts.collectAsState()
    val recs by vm.recurrings.collectAsState()
    val catMap = remember(cats) { cats.associateBy { it.id } }
    val accMap = remember(accs) { accs.associateBy { it.id } }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var catFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    val month = vm.month
    val type = vm.typeFilter
    val accF = vm.accountFilter

    fun matches(t: Tx) = t.type == type &&
        (accF == null || t.accountId == accF) &&
        (catFilter == null || t.categoryId == catFilter) &&
        (query.isBlank() || t.note.contains(query, true) ||
            catMap[t.categoryId]?.name?.contains(query, true) == true)

    val filtered = remember(all, month, type, accF, catFilter, query, catMap) {
        Dates.inMonth(all, month).filter { matches(it) }
    }
    // Recurrentes que todavía no se registraron este mes (o meses futuros)
    val projected = remember(recs, month, type, accF, catFilter, query, catMap) {
        Projection.recurrings(month, recs).filter { matches(it) }.sortedBy { it.date }
    }
    // Totales en pesos: lo cargado en cuentas en dólares se convierte con la cotización elegida.
    val total = Money.sumArs(filtered, accMap)
    val projTotal = Money.sumArs(projected, accMap)
    val days = month.lengthOfMonth()
    val daily = remember(filtered, month) {
        val arr = DoubleArray(days)
        filtered.forEach { arr[Dates.day(it.date) - 1] += it.amount }
        arr.toList()
    }
    val now = YearMonth.now()
    val elapsed = when {
        month == now -> LocalDate.now().dayOfMonth
        month > now -> days
        else -> days
    }
    val groups = remember(filtered) { filtered.groupBy { Dates.localDate(it.date) } }

    GradientBg(if (type == TxType.GASTO) C.TopGastos else C.TopIngresos) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 170.dp)
        ) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    RoundIcon(Icons.Filled.Settings, "Ajustes", onClick = onSettings)
                    Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                        MonthSwitcher(month) { vm.month = it }
                        Text(Fmt.money(total), color = C.Text, fontSize = 38.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (projTotal > 0) {
                            Text(
                                "+ ${Fmt.money(projTotal)} programado · total ${Fmt.money(total + projTotal)}",
                                color = C.Sub, fontSize = 13.sp, maxLines = 1
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                BarChart(
                    series = listOf(daily),
                    colors = listOf(C.Text),
                    labels = (1..days).map { if (it == 1 || it == 8 || it == 15 || it == 22 || it == days) "$it" else "" },
                    avg = if (total > 0) total / elapsed else null,
                    modifier = Modifier.fillMaxWidth().height(210.dp)
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundIcon(Icons.Filled.Search, "Buscar") { searching = !searching; if (!searching) query = "" }
                    Pill(if (type == TxType.GASTO) "Gastos ⇄" else "Ingresos ⇄", selected = true) {
                        vm.typeFilter = if (type == TxType.GASTO) TxType.INGRESO else TxType.GASTO
                        catFilter = null
                    }
                    DropPill(
                        accF?.let { accMap[it]?.name } ?: "Todas las cuentas",
                        listOf<Pair<Long?, String>>(null to "Todas las cuentas") + accs.map { it.id to "${it.emoji} ${it.name}" }
                    ) { vm.accountFilter = it }
                    DropPill(
                        catFilter?.let { catMap[it]?.name } ?: "Todas las categorías",
                        listOf<Pair<Long?, String>>(null to "Todas las categorías") +
                            cats.filter { it.type == type }.map { it.id to "${it.emoji} ${it.name}" }
                    ) { catFilter = it }
                }
                if (searching) {
                    Spacer(Modifier.height(10.dp))
                    Field(query, { query = it }, "Buscar por nota o categoría")
                }
            }

            if (projected.isNotEmpty()) {
                item(key = "programados") {
                    TxGroup("Programados (recurrentes)", Fmt.money(projTotal)) {
                        projected.forEachIndexed { i, t ->
                            if (i > 0) RowDivider()
                            Box(Modifier.alpha(0.6f)) {
                                TxRow(t.copy(note = "${t.note} · día ${Dates.day(t.date)}"), catMap[t.categoryId], accMap[t.accountId]) {
                                    onOpenRecurrings()
                                }
                            }
                        }
                    }
                }
            }

            if (groups.isEmpty() && projected.isEmpty()) {
                item {
                    EmptyState(
                        if (type == TxType.GASTO) "Sin gastos en este período" else "Sin ingresos en este período",
                        "Tocá el botón + o usá el widget para registrar un movimiento."
                    )
                }
            }
            groups.entries.forEach { entry ->
                val date = entry.key
                val list = entry.value
                item(key = date.toString()) {
                    TxGroup(Dates.dayLabel(date), Fmt.money(Money.sumArs(list, accMap))) {
                        list.forEachIndexed { i, t ->
                            if (i > 0) RowDivider()
                            TxRow(t, catMap[t.categoryId], accMap[t.accountId]) { onOpenTx(t) }
                        }
                    }
                }
            }
        }
    }
}
