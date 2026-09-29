package com.nestor.cuentasclaras.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Category
import com.nestor.cuentasclaras.data.Recurring
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.ui.MainViewModel
import com.nestor.cuentasclaras.ui.components.*
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Dates
import com.nestor.cuentasclaras.util.Fmt
import com.nestor.cuentasclaras.util.GeneralCards
import com.nestor.cuentasclaras.util.Prefs
import com.nestor.cuentasclaras.util.Projection
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

@Composable
fun GeneralScreen(vm: MainViewModel, onSettings: () -> Unit, onRecurrings: () -> Unit) {
    val all by vm.txs.collectAsState()
    val accs by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    val recs by vm.recurrings.collectAsState()
    val accF = vm.accountFilter
    val months = remember(vm.month) { (5 downTo 0).map { vm.month.minusMonths(it.toLong()) } }
    var sel by remember(vm.month) { mutableIntStateOf(5) }
    var customizing by remember { mutableStateOf(false) }
    val scoped = remember(all, accF) { if (accF == null) all else all.filter { it.accountId == accF } }
    val ing = remember(scoped, months) { months.map { m -> Dates.inMonth(scoped, m).filter { it.type == TxType.INGRESO }.sumOf { it.amount } } }
    val gas = remember(scoped, months) { months.map { m -> Dates.inMonth(scoped, m).filter { it.type == TxType.GASTO }.sumOf { it.amount } } }
    val labels = months.map { Fmt.monthShort(it.monthValue) }
    val selMonth = months[sel]
    val cards = Prefs.generalCards

    GradientBg(C.TopNeutral) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 170.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundIcon(Icons.Filled.Settings, "Ajustes", onClick = onSettings)
                        Spacer(Modifier.weight(1f))
                        DropPill(
                            accF?.let { id -> accs.firstOrNull { it.id == id }?.name } ?: "Todas las cuentas",
                            listOf<Pair<Long?, String>>(null to "Todas las cuentas") + accs.map { it.id to "${it.emoji} ${it.name}" }
                        ) { vm.accountFilter = it }
                        RoundIcon(Icons.Filled.Tune, "Personalizar") { customizing = true }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MonthSwitcher(vm.month) { vm.month = it }
                        Spacer(Modifier.weight(1f))
                        Text("Viendo: ${Fmt.monthName(selMonth.monthValue)} ${selMonth.year}", color = C.Sub, fontSize = 13.sp)
                    }
                }
            }

            if (cards.isEmpty()) {
                item {
                    EmptyState("No hay tarjetas visibles", "Tocá el botón de ajustes (arriba a la derecha) para elegir qué mostrar.")
                }
            }

            items(cards, key = { it }) { id ->
                when (id) {
                    "flujo" -> FlujoCard(ing, gas, labels, sel) { sel = it }
                    "restantes" -> RestantesCard(ing, gas, labels, sel, selMonth) { sel = it }
                    "proyeccion" -> ProyeccionCard(scoped, recs, accF, selMonth, ing[sel])
                    "presupuesto" -> PresupuestoCard(cats, gas[sel], selMonth)
                    "categorias" -> CategoriasCard(scoped, cats, selMonth)
                    "cuentas" -> CuentasCard(all, accs)
                    "recurrentes" -> RecurrentesCard(recs, onRecurrings)
                    "calendario" -> CalendarioCard(scoped, selMonth)
                }
            }
        }
    }

    if (customizing) CustomizeDialog { customizing = false }
}

// ---------------- Tarjetas ----------------

@Composable
private fun FlujoCard(ing: List<Double>, gas: List<Double>, labels: List<String>, sel: Int, onSelect: (Int) -> Unit) {
    CardBox {
        Row {
            Legend("Ingresos", C.Green, Fmt.money(ing[sel]))
            Spacer(Modifier.width(28.dp))
            Legend("Gastos", C.Blue, Fmt.money(gas[sel]))
        }
        Spacer(Modifier.height(16.dp))
        BarChart(
            series = listOf(ing, gas), colors = listOf(C.Green, C.Blue), labels = labels,
            selected = sel, onSelect = onSelect, modifier = Modifier.fillMaxWidth().height(200.dp)
        )
    }
}

@Composable
private fun RestantesCard(ing: List<Double>, gas: List<Double>, labels: List<String>, sel: Int, selMonth: YearMonth, onSelect: (Int) -> Unit) {
    var pctMode by remember { mutableStateOf(false) }
    val rest = ing.indices.map { ing[it] - gas[it] }
    val restPct = ing.indices.map { if (ing[it] > 0) rest[it] / ing[it] * 100 else 0.0 }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ingresos restantes · ${Fmt.monthName(selMonth.monthValue)}", color = C.Sub, fontSize = 14.sp)
                Text(
                    if (pctMode) Fmt.pct(restPct[sel]) else Fmt.money(rest[sel]),
                    color = if (rest[sel] < 0) C.Red else C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold
                )
            }
            Segmented(listOf("\$", "%"), if (pctMode) 1 else 0, { pctMode = it == 1 }, Modifier.width(110.dp))
        }
        Spacer(Modifier.height(14.dp))
        BarChart(
            series = listOf((if (pctMode) restPct else rest).map { max(it, 0.0) }),
            colors = listOf(C.Purple), labels = labels, selected = sel, onSelect = onSelect,
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )
    }
}

@Composable
private fun ProyeccionCard(scoped: List<Tx>, recs: List<Recurring>, accF: Long?, month: YearMonth, ingresos: Double) {
    val now = YearMonth.now()
    val days = month.lengthOfMonth()
    val gastos = Dates.inMonth(scoped, month).filter { it.type == TxType.GASTO }
    val spent = gastos.sumOf { it.amount }
    val variable = gastos.filter { it.recurringId == null }.sumOf { it.amount }
    val elapsed = when {
        month == now -> LocalDate.now().dayOfMonth
        month < now -> days
        else -> 0
    }
    val pending = Projection.recurrings(month, recs)
        .filter { it.type == TxType.GASTO && (accF == null || it.accountId == accF) }.sumOf { it.amount }
    val dailyAvg = if (elapsed > 0) variable / elapsed else 0.0
    val projection = spent + pending + dailyAvg * (days - elapsed)

    CardBox {
        Text(
            when {
                month < now -> "Ritmo de gasto · ${Fmt.monthName(month.monthValue)}"
                else -> "Proyección a fin de ${Fmt.monthName(month.monthValue).lowercase()}"
            },
            color = C.Sub, fontSize = 14.sp
        )
        Text(
            Fmt.money(if (month < now) spent else projection),
            color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold
        )
        if (month >= now && ingresos > 0) {
            val left = ingresos - projection
            Text(
                if (left >= 0) "Si seguís a este ritmo te quedarían ${Fmt.money(left)}" else "A este ritmo te faltarían ${Fmt.money(-left)}",
                color = if (left >= 0) C.Green else C.Red, fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        StatRow("Gastado hasta ahora", Fmt.money(spent))
        StatRow("Promedio diario (sin recurrentes)", Fmt.money(dailyAvg))
        if (month >= now) StatRow("Recurrentes pendientes", Fmt.money(pending))
        if (month == now) StatRow("Días restantes", "${days - elapsed}")
    }
}

@Composable
private fun PresupuestoCard(cats: List<Category>, spent: Double, month: YearMonth) {
    val budget = cats.filter { it.type == TxType.GASTO }.sumOf { it.budget }
    CardBox {
        Text("Presupuesto · ${Fmt.monthName(month.monthValue)}", color = C.Sub, fontSize = 14.sp)
        if (budget <= 0) {
            Spacer(Modifier.height(6.dp))
            Text("Todavía no definiste presupuestos. Hacelo desde la pestaña Presupuesto.", color = C.Sub, fontSize = 13.sp)
        } else {
            val available = budget - spent
            Text(
                if (available >= 0) "${Fmt.money(available)} disponibles" else "${Fmt.money(-available)} excedido",
                color = if (available >= 0) C.Text else C.Red, fontSize = 24.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            ProgressBar((spent / budget).toFloat(), if (available >= 0) C.Teal else C.Red)
            Spacer(Modifier.height(6.dp))
            Text("Gastado ${Fmt.money(spent)} de ${Fmt.money(budget)}", color = C.Sub, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CategoriasCard(scoped: List<Tx>, cats: List<Category>, month: YearMonth) {
    val catMap = cats.associateBy { it.id }
    val gastos = Dates.inMonth(scoped, month).filter { it.type == TxType.GASTO }
    val total = gastos.sumOf { it.amount }
    val top = gastos.groupBy { it.categoryId }
        .map { e -> e.key to e.value.sumOf { it.amount } }
        .sortedByDescending { it.second }.take(5)
    CardBox {
        Text("Top categorías · ${Fmt.monthName(month.monthValue)}", color = C.Sub, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        if (top.isEmpty()) Text("Sin gastos en este mes.", color = C.Sub, fontSize = 13.sp)
        top.forEach { (id, v) ->
            val c = catMap[id]
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(c?.emoji ?: "❔", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(c?.name ?: "Sin categoría", color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Fmt.money(v), color = C.Text, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    ProgressBar(if (total > 0) (v / total).toFloat() else 0f, Color(c?.color ?: 0xFF94A3B8))
                }
            }
        }
    }
}

@Composable
private fun CuentasCard(all: List<Tx>, accs: List<Account>) {
    val balances = accs.map { a ->
        a to (a.initialBalance + all.filter { it.accountId == a.id }.sumOf { if (it.type == TxType.INGRESO) it.amount else -it.amount })
    }
    CardBox {
        Text("Saldos por cuenta", color = C.Sub, fontSize = 14.sp)
        Text(Fmt.money(balances.sumOf { it.second }), color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        balances.forEach { (a, b) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.emoji, fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text(a.name, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(Fmt.money(b), color = if (b < 0) C.Red else C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun RecurrentesCard(recs: List<Recurring>, onRecurrings: () -> Unit) {
    val now = YearMonth.now()
    val recGastos = recs.filter { it.type == TxType.GASTO }
    val recDays = recs.map { it.dayOfMonth.coerceAtMost(now.lengthOfMonth()) }.toSet()
    CardBox {
        Row(Modifier.fillMaxWidth().clickable(onClick = onRecurrings), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Gastos recurrentes este mes", color = C.Sub, fontSize = 14.sp)
                Text(Fmt.money(recGastos.sumOf { it.amount }), color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("${Fmt.money(recGastos.sumOf { it.amount } * 12)} al año", color = C.Sub, fontSize = 13.sp)
            }
            Chevron()
        }
        Spacer(Modifier.height(12.dp))
        MonthGrid(now) { d ->
            val has = d != null && d.dayOfMonth in recDays
            val isToday = d == LocalDate.now()
            Box(
                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (d == null) Color.Transparent else C.CardHi)
                    .then(if (isToday) Modifier.border(1.5.dp, C.Text, RoundedCornerShape(10.dp)) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (d != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${d.dayOfMonth}", color = if (has) C.Text else C.Sub, fontSize = 13.sp)
                        if (has) Box(Modifier.padding(top = 2.dp).size(width = 10.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(C.Teal))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarioCard(scoped: List<Tx>, month: YearMonth) {
    val dailyMap = remember(scoped, month) {
        Dates.inMonth(scoped, month).filter { it.type == TxType.GASTO }
            .groupBy { Dates.day(it.date) }.mapValues { e -> e.value.sumOf { it.amount } }
    }
    val maxDay = dailyMap.values.maxOrNull() ?: 0.0
    CardBox {
        Text("Calendario de gastos · ${Fmt.monthName(month.monthValue)}", color = C.Sub, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        MonthGrid(month) { d ->
            val v = d?.let { dailyMap[it.dayOfMonth] } ?: 0.0
            val intensity = if (maxDay > 0) (v / maxDay).toFloat() else 0f
            Box(
                Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            d == null -> Color.Transparent
                            v > 0 -> C.Blue.copy(alpha = 0.15f + 0.55f * intensity)
                            else -> C.CardHi
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (d != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${d.dayOfMonth}", color = C.Text, fontSize = 12.sp)
                        Text(if (v > 0) Fmt.compact(v) else "·", color = if (v > 0) C.Text else C.Sub, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------- Piezas chicas ----------------

@Composable
private fun Legend(title: String, color: Color, value: String) {
    Column {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.height(4.dp))
        Text(title, color = C.Sub, fontSize = 14.sp)
        Text(value, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = C.Sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProgressBar(progress: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.CardHi)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
    }
}

/** Elegir qué tarjetas se ven y en qué orden. */
@Composable
private fun CustomizeDialog(onDismiss: () -> Unit) {
    val initialVisible = Prefs.generalCards
    val order = remember { mutableStateListOf<String>().apply { addAll(initialVisible + GeneralCards.ALL.keys.filter { it !in initialVisible }) } }
    val visible = remember { mutableStateListOf<String>().apply { addAll(initialVisible) } }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in order.indices) return
        val tmp = order[i]; order[i] = order[j]; order[j] = tmp
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { Text("Personalizar General", color = C.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Activá las tarjetas que querés ver y ordenalas con las flechas.", color = C.Sub, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                order.forEachIndexed { i, id ->
                    val on = id in visible
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = on,
                            onCheckedChange = { if (it) visible.add(id) else visible.remove(id) },
                            colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = C.Text)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            GeneralCards.ALL[id] ?: id, color = if (on) C.Text else C.Sub, fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowUp, "Subir", tint = if (i > 0) C.Text else C.Sub.copy(alpha = 0.3f),
                            modifier = Modifier.clip(CircleShape).clickable(enabled = i > 0) { move(i, -1) }.padding(4.dp)
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowDown, "Bajar", tint = if (i < order.lastIndex) C.Text else C.Sub.copy(alpha = 0.3f),
                            modifier = Modifier.clip(CircleShape).clickable(enabled = i < order.lastIndex) { move(i, 1) }.padding(4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Prefs.generalCards = order.filter { it in visible }
                onDismiss()
            }) { Text("Guardar", color = C.Teal) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { Prefs.generalCards = GeneralCards.DEFAULT; onDismiss() }) { Text("Restablecer", color = C.Sub) }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = C.Sub) }
            }
        }
    )
}
