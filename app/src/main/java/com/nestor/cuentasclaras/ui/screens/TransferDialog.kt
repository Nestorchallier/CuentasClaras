package com.nestor.cuentasclaras.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.ui.components.*
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Dates
import com.nestor.cuentasclaras.util.Fmt
import com.nestor.cuentasclaras.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Transferencia entre cuentas (ej. retiro del banco a efectivo).
 * Resta de "Desde" y suma en "Hacia": no cuenta como gasto ni como ingreso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDialog(
    initial: Tx?,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (Tx) -> Unit,
    onDelete: ((Tx) -> Unit)?,
    /** Para "Pagar tarjeta": cuenta destino y monto sugeridos. */
    presetTo: Long? = null,
    presetAmount: Double? = null
) {
    var to by remember { mutableStateOf(initial?.toAccountId ?: presetTo ?: accounts.getOrNull(1)?.id) }
    var from by remember {
        mutableStateOf(initial?.accountId ?: accounts.firstOrNull { it.id != to && !it.isCard }?.id ?: accounts.getOrNull(0)?.id)
    }
    var amount by remember { mutableStateOf(initial?.let { Fmt.plain(it.amount) } ?: presetAmount?.let { Fmt.plain(it) } ?: "") }
    var received by remember { mutableStateOf(initial?.toAmount?.let { Fmt.plain(it) } ?: "") }
    var date by remember { mutableStateOf(initial?.let { Dates.localDate(it.date) } ?: LocalDate.now()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val options = accounts.map { it.id to "${it.emoji} ${it.name}" + if (it.isUsd) " (US$)" else "" }
    val fromAcc = accounts.firstOrNull { it.id == from }
    val toAcc = accounts.firstOrNull { it.id == to }
    // Compra o venta de dólares: sale en una moneda y llega en otra.
    val exchange = fromAcc != null && toAcc != null && fromAcc.currency != toAcc.currency

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { Text(if (initial == null) "Transferir entre cuentas" else "Editar transferencia", color = C.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Desde", color = C.Sub, fontSize = 13.sp)
                ChoiceRow(options, from) { from = it; error = null }
                Text("Hacia", color = C.Sub, fontSize = 13.sp)
                ChoiceRow(options, to) { to = it; error = null }
                Field(amount, { amount = it; error = null }, if (fromAcc?.isUsd == true) "Monto que sale (US$)" else "Monto que sale", number = true)
                if (exchange) {
                    Field(received, { received = it; error = null }, if (toAcc?.isUsd == true) "Monto que llega (US$)" else "Monto que llega ($)", number = true)
                    val r = Money.current
                    val sale = Fmt.parse(amount)
                    if (r != null && sale > 0) {
                        // Sugerencia con la cotización elegida: comprar dólares usa la venta, venderlos usa la compra.
                        val suggested = if (toAcc?.isUsd == true) sale / r.sell else sale * r.buy
                        Text(
                            "Con el dólar ${Money.typeName} serían ${Fmt.plain(Math.round(suggested * 100) / 100.0)} · tocá para usarlo",
                            color = C.Teal, fontSize = 12.sp,
                            modifier = Modifier.clickable { received = Fmt.plain(Math.round(suggested * 100) / 100.0) }
                        )
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val yesterday = today.minusDays(1)
                    val other = date != today && date != yesterday
                    Pill("Hoy", selected = date == today) { date = today }
                    Pill("Ayer", selected = date == yesterday) { date = yesterday }
                    Pill(if (other) "📅 ${Fmt.shortDate(date)}" else "📅 Otra fecha", selected = other) { pickDate = true }
                }
                Field(note, { note = it }, "Nota (opcional)")
                Text("No cuenta como gasto ni como ingreso: solo mueve plata de una cuenta a otra.", color = C.Sub, fontSize = 12.sp)
                error?.let { Text(it, color = C.Red, fontSize = 13.sp) }
                if (confirmDelete) Text("¿Seguro? Tocá Eliminar otra vez para borrar la transferencia.", color = C.Red, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = Fmt.parse(amount)
                val f = from
                val t = to
                when {
                    accounts.size < 2 -> error = "Necesitás al menos dos cuentas"
                    f == null || t == null -> error = "Elegí las dos cuentas"
                    f == t -> error = "Elegí dos cuentas distintas"
                    v <= 0 -> error = "Ingresá un monto"
                    exchange && Fmt.parse(received) <= 0 -> error = "Ingresá cuánto llega a la otra cuenta"
                    else -> {
                        val time = when {
                            initial != null && Dates.localDate(initial.date) == date -> Dates.localTime(initial.date)
                            date == today -> LocalTime.now()
                            else -> LocalTime.NOON
                        }
                        onSave(
                            Tx(
                                id = initial?.id ?: 0, amount = v, type = TxType.TRANSFER, categoryId = 0,
                                accountId = f!!, toAccountId = t, date = Dates.toMillis(date, time), note = note.trim(),
                                toAmount = if (exchange) Fmt.parse(received) else null
                            )
                        )
                    }
                }
            }) { Text("Guardar", color = C.Teal) }
        },
        dismissButton = {
            Row {
                if (initial != null && onDelete != null) {
                    TextButton(onClick = { if (confirmDelete) onDelete(initial) else confirmDelete = true }) { Text("Eliminar", color = C.Red) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = C.Sub) }
            }
        }
    )

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
