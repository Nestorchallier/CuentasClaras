package com.nestor.cuentasclaras.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Currency
import com.nestor.cuentasclaras.data.Defaults
import com.nestor.cuentasclaras.data.Recurring
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.ui.MainViewModel
import com.nestor.cuentasclaras.ui.components.*
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Balances
import com.nestor.cuentasclaras.util.CardCycle
import com.nestor.cuentasclaras.util.Dates
import com.nestor.cuentasclaras.util.Fmt
import com.nestor.cuentasclaras.util.Money
import com.nestor.cuentasclaras.util.Prefs
import com.nestor.cuentasclaras.util.Projection
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CuentasScreen(vm: MainViewModel, onSettings: () -> Unit, onOpenCard: (Long) -> Unit) {
    val all by vm.txs.collectAsState()
    val accs by vm.accounts.collectAsState()
    val recs by vm.recurrings.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    var transferring by remember { mutableStateOf(false) }
    var editingTransfer by remember { mutableStateOf<Tx?>(null) }
    val transfers = remember(all) { all.filter { it.isTransfer }.take(10) }
    val accMap = remember(accs) { accs.associateBy { it.id } }
    val balances = remember(all, accs) { Balances.of(accs, all) }
    val total = Balances.totalArs(accs, balances)
    val hasUsd = accs.any { it.isUsd }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var refreshing by remember { mutableStateOf(false) }
    // Al entrar, actualizar la cotización si hay cuentas en dólares.
    LaunchedEffect(hasUsd) { if (hasUsd) { refreshing = true; Money.refresh(); refreshing = false } }

    GradientBg(C.TopNeutral) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 170.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    RoundIcon(Icons.Filled.Settings, "Ajustes", onClick = onSettings)
                    Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Saldo total", color = C.Sub, fontSize = 15.sp)
                        Text(Fmt.money(total), color = if (total < 0) C.Red else C.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (hasUsd) {
                            Text("en pesos, con el dólar ${Money.typeName}", color = C.Sub, fontSize = 12.sp)
                        }
                    }
                    RoundIcon(Icons.Filled.Add, "Nueva cuenta", Modifier.align(Alignment.TopEnd)) { creating = true }
                }
            }
            if (hasUsd) {
                item {
                    RateCard(refreshing) {
                        scope.launch {
                            refreshing = true
                            val ok = Money.refresh()
                            refreshing = false
                            if (!ok) Toast.makeText(ctx, "No se pudo actualizar la cotización (¿sin internet?)", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            item {
                SueldoCard(all, recs, accs.firstOrNull { it.id == Prefs.mainAccountId }, accs, balances)
            }
            items(accs, key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Card)
                        .clickable { if (a.isCard) onOpenCard(a.id) else editing = a }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(C.CardHi), contentAlignment = Alignment.Center) {
                        Text(a.emoji, fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            if (a.id == Prefs.mainAccountId) "${a.name} · cuenta del sueldo" else a.name,
                            color = C.Sub, fontSize = 15.sp
                        )
                        val b = balances[a.id] ?: 0.0
                        Text(Fmt.money(b, a), color = if (b < 0) C.Red else C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (a.isUsd && Money.usdToArs > 0) {
                            Text("≈ ${Fmt.money(Money.toArs(b, a))}", color = C.Sub, fontSize = 12.sp)
                        }
                        if (a.isCard) {
                            val next = remember(all, a) { CardCycle.upcoming(a, all, 1).first() }
                            val pay = remember(all, a) { CardCycle.toPay(a, all) }
                            if (pay != null) {
                                Text("A pagar ${Fmt.money(pay.total, a)} · vence ${Fmt.shortDate(pay.due)}", color = C.Red, fontSize = 12.sp)
                            }
                            Text(
                                "Próximo resumen ${Fmt.money(next.total, a)} · cierra ${Fmt.shortDate(next.closing)}",
                                color = C.Sub, fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "Saldo = saldo inicial + ingresos − gastos ± transferencias de esa cuenta. Tocá una cuenta para editarla.",
                    color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (accs.size >= 2) {
                item {
                    PrimaryButton("⇄  Transferir entre cuentas") { transferring = true }
                }
            }
            if (transfers.isNotEmpty()) {
                item {
                    TxGroup("Últimas transferencias", "") {
                        transfers.forEachIndexed { i, t ->
                            if (i > 0) RowDivider()
                            TransferRow(t, accMap[t.accountId], t.toAccountId?.let { accMap[it] }) { editingTransfer = t }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val e = editing
        AccountDialog(
            initial = e,
            usage = if (e == null) 0 else all.count { it.accountId == e.id || it.toAccountId == e.id } + recs.count { it.accountId == e.id },
            targets = accs.filter { it.id != e?.id },
            onDismiss = { creating = false; editing = null },
            isMain = e != null && e.id == Prefs.mainAccountId,
            onSave = { a, main ->
                vm.launch {
                    val id = vm.repo.saveAccount(a.copy(position = if (a.id == 0L) accs.size else a.position))
                    if (main) Prefs.mainAccountId = id
                    else if (id == Prefs.mainAccountId) Prefs.mainAccountId = -1L
                }
                creating = false; editing = null
            },
            onDelete = { a, moveTo ->
                if (a.id == Prefs.mainAccountId) Prefs.mainAccountId = moveTo ?: -1L
                vm.launch { vm.repo.deleteAccount(a, moveTo) }
                editing = null
            }
        )
    }

    if (transferring || editingTransfer != null) {
        TransferDialog(
            initial = editingTransfer,
            accounts = accs,
            onDismiss = { transferring = false; editingTransfer = null },
            onSave = { t -> vm.launch { vm.repo.saveTx(t) }; transferring = false; editingTransfer = null },
            onDelete = { t -> vm.launch { vm.repo.deleteTx(t) }; editingTransfer = null }
        )
    }
}

@Composable
private fun TransferRow(t: Tx, from: Account?, to: Account?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(C.CardHi), contentAlignment = Alignment.Center) {
            Text("⇄", color = C.Blue, fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${from?.emoji ?: ""} ${from?.name ?: "?"} → ${to?.emoji ?: ""} ${to?.name ?: "?"}",
                color = C.Text, fontSize = 15.sp, maxLines = 1
            )
            val sub = listOf(Dates.dayLabel(Dates.localDate(t.date)), t.note).filter { it.isNotBlank() }.joinToString(" · ")
            Text(sub, color = C.Sub, fontSize = 12.sp, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(t.amount, from), color = C.Blue, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            // Compra/venta de dólares: mostrar también lo que llegó.
            if (t.toAmount != null && from?.currency != to?.currency) {
                Text("→ ${Fmt.money(t.toAmount, to)}", color = C.Sub, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AccountDialog(
    initial: Account?, usage: Int, targets: List<Account>, isMain: Boolean,
    onDismiss: () -> Unit, onSave: (account: Account, isMain: Boolean) -> Unit,
    onDelete: (account: Account, moveTo: Long?) -> Unit
) {
    var main by remember { mutableStateOf(isMain) }
    var isCard by remember { mutableStateOf(initial?.isCard ?: false) }
    var usd by remember { mutableStateOf(initial?.isUsd ?: false) }
    var closing by remember { mutableStateOf(initial?.closingDay?.takeIf { it > 0 }?.toString() ?: "") }
    var due by remember { mutableStateOf(initial?.dueDay?.takeIf { it > 0 }?.toString() ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🏦") }
    var balance by remember { mutableStateOf(initial?.initialBalance?.let { Fmt.plain(it) } ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var moveTo by remember { mutableStateOf<Long?>(null) }
    // Si la cuenta tiene movimientos o recurrentes, hay que elegir a qué cuenta pasarlos antes de borrarla.
    val canDelete = usage == 0 || moveTo != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { Text(if (initial == null) "Nueva cuenta" else "Editar cuenta", color = C.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EmojiPicker(emoji, Defaults.accountEmojis) { emoji = it }
                Field(name, { name = it }, "Nombre (ej: Sueldo Cash Market)")
                ChoiceRow(listOf(0L to "$ Pesos", 1L to "US$ Dólares"), if (usd) 1L else 0L) { usd = it == 1L }
                if (initial != null && usd != initial.isUsd && usage > 0) {
                    Text("Ojo: los movimientos que ya tiene la cuenta no se convierten, solo cambia la moneda en que se leen.", color = C.Red, fontSize = 12.sp)
                }
                Field(balance, { balance = it }, if (usd) "Saldo inicial (US$)" else "Saldo inicial", number = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tarjeta de crédito", color = C.Text, fontSize = 15.sp)
                        Text("Permite compras en cuotas y muestra cada resumen.", color = C.Sub, fontSize = 12.sp)
                    }
                    Switch(isCard, { isCard = it; if (it) main = false }, colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = C.Text))
                }
                if (isCard) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(closing, { closing = it.filter(Char::isDigit).take(2) }, "Día de cierre", Modifier.weight(1f), number = true)
                        Field(due, { due = it.filter(Char::isDigit).take(2) }, "Día de vencimiento", Modifier.weight(1f), number = true)
                    }
                    Text("Para pagarla, hacé una transferencia desde tu banco a la tarjeta.", color = C.Sub, fontSize = 12.sp)
                }
                if (!isCard) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cuenta del sueldo", color = C.Text, fontSize = 15.sp)
                        Text("Acá entra el sueldo y de acá salen los gastos por defecto.", color = C.Sub, fontSize = 12.sp)
                    }
                    Switch(main, { main = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = C.Text))
                }
                if (confirmDelete) {
                    when {
                        usage == 0 ->
                            Text("¿Seguro? La cuenta no tiene movimientos. Tocá Eliminar otra vez.", color = C.Red, fontSize = 13.sp)
                        targets.isEmpty() ->
                            Text("No se puede eliminar: es tu única cuenta y tiene $usage movimientos o recurrentes. Creá otra cuenta primero.", color = C.Red, fontSize = 13.sp)
                        else -> {
                            Text("Esta cuenta tiene $usage movimientos o recurrentes. ¿A qué cuenta los pasamos?", color = C.Red, fontSize = 13.sp)
                            ChoiceRow(targets.map { it.id to "${it.emoji} ${it.name}" }, moveTo) { moveTo = it }
                            if (moveTo != null) Text("Tocá Eliminar otra vez para confirmar.", color = C.Sub, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val base = initial ?: Account(name = "", emoji = "")
                    onSave(
                        base.copy(
                            name = name.trim(), emoji = emoji.ifBlank { "🏦" }, initialBalance = Fmt.parse(balance),
                            isCard = isCard,
                            currency = if (usd) Currency.USD else Currency.ARS,
                            closingDay = if (isCard) (closing.toIntOrNull() ?: 0).coerceIn(0, 31) else 0,
                            dueDay = if (isCard) (due.toIntOrNull() ?: 0).coerceIn(0, 31) else 0
                        ),
                        main && !isCard
                    )
                }
            }) { Text("Guardar", color = C.Teal) }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(
                        onClick = { if (!confirmDelete) confirmDelete = true else if (canDelete) onDelete(initial, moveTo) },
                        enabled = !confirmDelete || canDelete
                    ) { Text("Eliminar", color = if (!confirmDelete || canDelete) C.Red else C.Sub) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = C.Sub) }
            }
        }
    )
}

/**
 * "Este mes" de la cuenta del sueldo: cuánto entró, cuánto se gastó, cuánto falta de gastos fijos
 * y cuánto queda libre por día hasta fin de mes. Sin cuenta del sueldo marcada, suma todas las cuentas.
 */
@Composable
private fun SueldoCard(all: List<Tx>, recs: List<Recurring>, main: Account?, accounts: List<Account>, balances: Map<Long, Double>) {
    val ym = YearMonth.now()
    fun mine(accountId: Long) = main == null || accountId == main.id
    // Si se suman todas las cuentas, lo que está en dólares se pasa a pesos.
    val month = Dates.inMonth(if (main == null) Money.inArs(all, accounts) else all, ym)
    val cobrado = month.filter { it.type == TxType.INGRESO && mine(it.accountId) }.sumOf { it.amount }
    val gastado = month.filter { it.type == TxType.GASTO && mine(it.accountId) }.sumOf { it.amount }
    // Recurrentes de gasto que todavía no se generaron este mes (alquiler, servicios, etc.)
    val proj = Projection.recurrings(ym, recs)
    val fijos = (if (main == null) Money.inArs(proj, accounts) else proj)
        .filter { it.type == TxType.GASTO && mine(it.accountId) }.sumOf { it.amount }
    val libre = cobrado - gastado - fijos
    val diasRestantes = ym.lengthOfMonth() - LocalDate.now().dayOfMonth + 1
    CardBox {
        Text(if (main != null) "${main.emoji} ${main.name} · este mes" else "Este mes · todas las cuentas", color = C.Sub, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text("Te queda", color = C.Sub, fontSize = 13.sp)
        Text(Fmt.money(libre, main), color = if (libre < 0) C.Red else C.Green, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (cobrado > 0 && libre > 0) {
            Text("${Fmt.money(libre / diasRestantes, main)} por día hasta fin de mes", color = C.Sub, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(if (cobrado > 0) ((gastado + fijos) / cobrado).toFloat() else 0f, if (libre < 0) C.Red else C.Teal)
        Spacer(Modifier.height(10.dp))
        SueldoLine("Cobrado (sueldo e ingresos)", Fmt.money(cobrado, main), C.Green)
        SueldoLine("Gastado", "− " + Fmt.money(gastado, main), C.Text)
        if (fijos > 0) SueldoLine("Gastos fijos por pagar", "− " + Fmt.money(fijos, main), C.Text)
        if (main != null) SueldoLine("Saldo actual de la cuenta", Fmt.money(balances[main.id] ?: 0.0, main), C.Text)
        if (main == null) {
            Spacer(Modifier.height(6.dp))
            Text("Tocá la cuenta donde cobrás y marcala como \"Cuenta del sueldo\" para ver solo esa.", color = C.Sub, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SueldoLine(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = C.Sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Cotización del dólar elegida y botón para actualizarla. Tocar un tipo lo elige para convertir. */
@Composable
private fun RateCard(refreshing: Boolean, onRefresh: () -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dólar ${Money.typeName}", color = C.Sub, fontSize = 14.sp)
                val r = Money.current
                Text(
                    if (r != null) "Compra ${Fmt.money(r.buy)} · Venta ${Fmt.money(r.sell)}" else "Sin cotización todavía",
                    color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    if (refreshing) "Actualizando…" else if (Money.updated.isNotBlank()) "Actualizado ${Money.updated} · dolarapi.com" else "Tocá actualizar (necesita internet)",
                    color = C.Sub, fontSize = 12.sp
                )
            }
            TextButton(onClick = onRefresh, enabled = !refreshing) { Text("Actualizar", color = C.Teal) }
        }
        Spacer(Modifier.height(8.dp))
        ChoiceRow(Money.TYPES.keys.mapIndexed { i, k -> i.toLong() to (Money.TYPES[k] ?: k) }, Money.TYPES.keys.indexOf(Money.type).toLong()) { i ->
            Money.type = Money.TYPES.keys.elementAt(i.toInt())
        }
    }
}
