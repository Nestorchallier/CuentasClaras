package com.nestor.cuentasclaras.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.ui.MainViewModel
import com.nestor.cuentasclaras.ui.components.*
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Dates
import com.nestor.cuentasclaras.util.Fmt

/** Tendencia de 12 meses de una categoría + movimientos del mes elegido. */
@Composable
fun CategoryDetailScreen(vm: MainViewModel, catId: Long, onBack: () -> Unit, onOpenTx: (Tx) -> Unit) {
    val all by vm.txs.collectAsState()
    val cats by vm.categories.collectAsState()
    val accs by vm.accounts.collectAsState()
    val accMap = remember(accs) { accs.associateBy { it.id } }
    val cat = cats.firstOrNull { it.id == catId }
    val color = Color(cat?.color ?: 0xFF94A3B8)
    val months = remember(vm.month) { (11 downTo 0).map { vm.month.minusMonths(it.toLong()) } }
    var sel by remember(vm.month) { mutableIntStateOf(11) }
    val catTxs = remember(all, catId) { all.filter { it.categoryId == catId } }
    val totals = remember(catTxs, months) { months.map { m -> Dates.inMonth(catTxs, m).sumOf { it.amount } } }
    val nonZero = totals.filter { it > 0 }
    val avg = if (nonZero.isEmpty()) 0.0 else nonZero.average()
    val list = remember(catTxs, sel, months) { Dates.inMonth(catTxs, months[sel]) }
    val groups = remember(list) { list.groupBy { Dates.localDate(it.date) } }

    GradientBg(color.copy(alpha = 0.35f)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 60.dp)
        ) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onClick = onBack)
                    Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${cat?.emoji ?: ""} ${cat?.name ?: "Categoría"}", color = C.Sub, fontSize = 15.sp)
                        Text(Fmt.money(totals[sel]), color = C.Text, fontSize = 36.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            "${Fmt.monthName(months[sel].monthValue)} ${months[sel].year} · promedio ${Fmt.money(avg)}",
                            color = C.Sub, fontSize = 13.sp
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(18.dp))
                BarChart(
                    series = listOf(totals),
                    colors = listOf(color),
                    labels = months.mapIndexed { i, m -> if (i % 2 == 1) Fmt.monthShort(m.monthValue) else "" },
                    selected = sel,
                    avg = if (avg > 0) avg else null,
                    onSelect = { sel = it },
                    modifier = Modifier.fillMaxWidth().height(230.dp)
                )
                Text("Tocá una barra para ver ese mes", color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (groups.isEmpty()) {
                item { EmptyState("Sin movimientos", "No hay movimientos de esta categoría en el mes elegido.") }
            }
            groups.entries.forEach { entry ->
                val date = entry.key
                val l = entry.value
                item(key = date.toString()) {
                    TxGroup(Dates.dayLabel(date), Fmt.money(l.sumOf { it.amount })) {
                        l.forEachIndexed { i, t ->
                            if (i > 0) RowDivider()
                            TxRow(t, cat, accMap[t.accountId]) { onOpenTx(t) }
                        }
                    }
                }
            }
        }
    }
}
