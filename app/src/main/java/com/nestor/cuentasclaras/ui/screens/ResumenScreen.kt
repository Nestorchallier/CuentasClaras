package com.nestor.cuentasclaras.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.data.Category
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.ui.MainViewModel
import com.nestor.cuentasclaras.ui.components.*
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Dates
import com.nestor.cuentasclaras.util.Fmt
import java.time.YearMonth
import kotlin.math.abs

private data class CatSum(val id: Long, val cat: Category?, val count: Int, val total: Double)

@Composable
fun ResumenScreen(vm: MainViewModel, onSettings: () -> Unit, onOpenCategory: (Long) -> Unit) {
    val all by vm.txsArs.collectAsState()
    val cats by vm.categories.collectAsState()
    val accs by vm.accounts.collectAsState()
    val catMap = remember(cats) { cats.associateBy { it.id } }
    val month = vm.month
    val type = vm.typeFilter
    val accF = vm.accountFilter

    fun sel(ym: YearMonth) = Dates.inMonth(all, ym).filter { it.type == type && (accF == null || it.accountId == accF) }
    val cur = remember(all, month, type, accF) { sel(month) }
    val prev = remember(all, month, type, accF) { sel(month.minusMonths(1)) }
    val total = cur.sumOf { it.amount }
    val prevTotal = prev.sumOf { it.amount }
    val rows = remember(cur, catMap) {
        cur.groupBy { it.categoryId }
            .map { e -> CatSum(e.key, catMap[e.key], e.value.size, e.value.sumOf { it.amount }) }
            .sortedByDescending { it.total }
    }

    GradientBg(C.TopNeutral) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 170.dp)
        ) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    RoundIcon(Icons.Filled.Settings, "Ajustes", onClick = onSettings)
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp)) { MonthSwitcher(month) { vm.month = it } }
                }
            }
            item {
                DonutChart(
                    rows.map { it.total to Color(it.cat?.color ?: 0xFF94A3B8) },
                    Modifier.fillMaxWidth().height(290.dp).padding(horizontal = 24.dp, vertical = 12.dp),
                    stroke = 24.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (type == TxType.GASTO) "Gastos" else "Ingresos", color = C.Sub, fontSize = 15.sp)
                        Text(Fmt.money(total), color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (prevTotal > 0) {
                            val pct = (total - prevTotal) / prevTotal * 100
                            val good = if (type == TxType.GASTO) pct <= 0 else pct >= 0
                            Text(
                                "${if (pct <= 0) "↓" else "↑"} ${Fmt.pct(abs(pct))} vs mes anterior",
                                color = if (good) C.Green else C.Red, fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (type == TxType.GASTO) "Gastos ⇄" else "Ingresos ⇄", selected = true) {
                        vm.typeFilter = if (type == TxType.GASTO) TxType.INGRESO else TxType.GASTO
                    }
                    DropPill(
                        accF?.let { id -> accs.firstOrNull { it.id == id }?.name } ?: "Todas las cuentas",
                        listOf<Pair<Long?, String>>(null to "Todas las cuentas") + accs.map { it.id to "${it.emoji} ${it.name}" }
                    ) { vm.accountFilter = it }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (rows.isEmpty()) {
                item { EmptyState("Nada para mostrar", "Cuando registres movimientos vas a ver acá la distribución por categoría.") }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Card)) {
                        rows.forEachIndexed { i, r ->
                            if (i > 0) RowDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenCategory(r.id) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EmojiBadge(r.cat?.emoji ?: "❔", Color(r.cat?.color ?: 0xFF94A3B8))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    r.cat?.name ?: "Sin categoría", color = C.Text, fontSize = 16.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier.clip(RoundedCornerShape(8.dp)).background(C.CardHi)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) { Text("${r.count}", color = C.Sub, fontSize = 12.sp) }
                                Spacer(Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Fmt.money(r.total), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text(Fmt.pct(if (total > 0) r.total / total * 100 else 0.0), color = C.Sub, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
