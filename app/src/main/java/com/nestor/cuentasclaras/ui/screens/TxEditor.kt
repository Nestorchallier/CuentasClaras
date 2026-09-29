package com.nestor.cuentasclaras.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Category
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.ui.components.*
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Dates
import com.nestor.cuentasclaras.util.Fmt
import com.nestor.cuentasclaras.util.Prefs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Editor de movimiento con teclado numérico propio.
 * Se usa a pantalla completa en la app y en modo compacto en la hoja de los widgets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxEditor(
    initial: Tx?,
    initialType: String,
    categories: List<Category>,
    accounts: List<Account>,
    compact: Boolean,
    onSave: (Tx) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Si el movimiento es una cuota: borra todas las cuotas de esa compra. */
    onDeleteAll: (() -> Unit)? = null,
    /** Valores sugeridos para un movimiento NUEVO (ej. desde un aviso del banco). Id 0 / 0 = sin dato. */
    prefill: Tx? = null
) {
    val seed = initial ?: prefill
    var type by remember { mutableStateOf(seed?.type ?: initialType) }
    var amount by remember { mutableStateOf(seed?.amount?.takeIf { it > 0 }?.let { Fmt.plain(it) } ?: "") }
    var catId by remember { mutableStateOf(seed?.categoryId?.takeIf { it > 0 }) }
    var accId by remember { mutableStateOf(seed?.accountId?.takeIf { it > 0 }) }
    var date by remember { mutableStateOf(seed?.let { Dates.localDate(it.date) } ?: LocalDate.now()) }
    var note by remember { mutableStateOf(seed?.note ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    var cuotas by remember { mutableIntStateOf(1) }
    var askDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val today = LocalDate.now()
    val typeCats = categories.filter { it.type == type }
    // Cuotas: solo al cargar un gasto nuevo con una tarjeta de crédito.
    val canInstallments = initial == null && type == TxType.GASTO && accounts.firstOrNull { it.id == accId }?.isCard == true

    LaunchedEffect(accounts) {
        // Por defecto, la cuenta del sueldo (si hay una marcada); si no, la primera.
        if (accId == null || accounts.none { it.id == accId }) {
            accId = accounts.firstOrNull { it.id == Prefs.mainAccountId }?.id ?: accounts.firstOrNull()?.id
        }
    }

    fun press(k: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        error = null
        amount = when (k) {
            "⌫" -> amount.dropLast(1)
            "," -> when {
                amount.contains(',') -> amount
                amount.isEmpty() -> "0,"
                else -> "$amount,"
            }
            else -> when {
                amount.contains(',') && amount.substringAfter(',').length >= 2 -> amount
                amount == "0" -> k
                amount.length >= 12 -> amount
                else -> amount + k
            }
        }
    }

    fun save() {
        val v = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
        when {
            v <= 0 -> error = "Ingresá un monto"
            catId == null -> error = "Elegí una categoría"
            accId == null -> error = "Primero creá una cuenta"
            else -> {
                val time = when {
                    initial != null && Dates.localDate(initial.date) == date -> Dates.localTime(initial.date)
                    date == today -> LocalTime.now()
                    else -> LocalTime.NOON
                }
                onSave(
                    Tx(
                        id = initial?.id ?: 0, amount = v, type = type,
                        categoryId = catId!!, accountId = accId!!,
                        date = Dates.toMillis(date, time), note = note.trim(),
                        recurringId = initial?.recurringId,
                        toAccountId = initial?.toAccountId,
                        installment = initial?.installment ?: 0,
                        installments = initial?.installments ?: if (canInstallments && cuotas > 1) cuotas else 0,
                        groupId = initial?.groupId
                    )
                )
            }
        }
    }

    val display = run {
        val intPart = amount.substringBefore(',').ifEmpty { "0" }
        val dec = if (amount.contains(',')) "," + amount.substringAfter(',') else ""
        val symbol = if (accounts.firstOrNull { it.id == accId }?.isUsd == true) "US$" else Prefs.currency
        "$symbol ${Fmt.groupInt(intPart)}$dec"
    }

    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Filled.Close, "Cerrar", onClick = onClose)
            Spacer(Modifier.width(12.dp))
            Segmented(
                listOf("Gasto", "Ingreso"), if (type == TxType.GASTO) 0 else 1,
                { i ->
                    val nt = if (i == 0) TxType.GASTO else TxType.INGRESO
                    if (nt != type) { type = nt; catId = null }
                },
                Modifier.weight(1f)
            )
            if (onDelete != null) {
                Spacer(Modifier.width(12.dp))
                RoundIcon(Icons.Filled.Delete, "Eliminar") { if (onDeleteAll != null) askDelete = true else onDelete() }
            }
        }

        Spacer(Modifier.height(if (compact) 14.dp else 28.dp))
        Text(
            display, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            fontSize = if (compact) 40.sp else 48.sp, fontWeight = FontWeight.Bold,
            color = if (type == TxType.INGRESO) C.Green else C.Text, maxLines = 1
        )
        Spacer(Modifier.height(if (compact) 12.dp else 22.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(typeCats, key = { it.id }) { c ->
                val sel = c.id == catId
                Column(
                    Modifier.width(70.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (sel) C.CardHi else Color.Transparent)
                        .clickable { catId = c.id; error = null }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmojiBadge(c.emoji, Color(c.color), 40.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        c.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (sel) C.Text else C.Sub
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val yesterday = today.minusDays(1)
            val other = date != today && date != yesterday
            Pill("Hoy", selected = date == today) { date = today }
            Pill("Ayer", selected = date == yesterday) { date = yesterday }
            Pill(if (other) "📅 ${Fmt.shortDate(date)}" else "📅 Otra fecha", selected = other) { pickDate = true }
            if (accounts.size > 1) {
                accounts.forEach { a -> Pill("${a.emoji} ${a.name}" + if (a.isUsd) " (US$)" else "", selected = a.id == accId) { accId = a.id } }
            }
        }

        if (canInstallments) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 6, 9, 12, 18, 24).forEach { n ->
                    Pill(if (n == 1) "1 pago" else "$n cuotas", selected = cuotas == n) { cuotas = n }
                }
            }
            val total = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
            if (cuotas > 1 && total > 0) {
                Spacer(Modifier.height(4.dp))
                Text("$cuotas cuotas de ${Fmt.money(total / cuotas, accounts.firstOrNull { it.id == accId })} (una por mes)", color = C.Sub, fontSize = 13.sp)
            }
        }
        if (initial != null && initial.installments > 1) {
            Spacer(Modifier.height(6.dp))
            Text("Cuota ${initial.installment} de ${initial.installments}", color = C.Sub, fontSize = 13.sp)
        }

        Spacer(Modifier.height(10.dp))
        Field(note, { note = it }, "Nota (opcional)")
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = C.Red, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))
        Keypad(compact) { press(it) }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(if (initial == null) "Guardar" else "Guardar cambios") { save() }
    }

    if (askDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            containerColor = C.Card,
            title = { Text("Borrar cuota", color = C.Text) },
            text = { Text("¿Querés borrar solo esta cuota o todas las cuotas de la compra?", color = C.Sub) },
            confirmButton = {
                TextButton(onClick = { askDelete = false; onDeleteAll?.invoke() }) { Text("Todas", color = C.Red) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { askDelete = false; onDelete() }) { Text("Solo esta", color = C.Red) }
                    TextButton(onClick = { askDelete = false }) { Text("Cancelar", color = C.Sub) }
                }
            }
        )
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("Listo", color = C.Teal) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar", color = C.Sub) } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun Keypad(compact: Boolean, onKey: (String) -> Unit) {
    val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(",", "0", "⌫"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(if (compact) 50.dp else 58.dp)
                            .clip(RoundedCornerShape(16.dp)).background(C.Card)
                            .clickable { onKey(k) },
                        contentAlignment = Alignment.Center
                    ) { Text(k, fontSize = 24.sp, color = C.Text) }
                }
            }
        }
    }
}
