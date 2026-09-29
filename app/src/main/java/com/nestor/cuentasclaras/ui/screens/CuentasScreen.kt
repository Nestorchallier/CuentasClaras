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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Defaults
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.ui.MainViewModel
import com.nestor.cuentasclaras.ui.components.*
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Fmt

@Composable
fun CuentasScreen(vm: MainViewModel, onSettings: () -> Unit) {
    val all by vm.txs.collectAsState()
    val accs by vm.accounts.collectAsState()
    val recs by vm.recurrings.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    val balances = remember(all, accs) {
        accs.associate { a ->
            a.id to (a.initialBalance + all.filter { it.accountId == a.id }
                .sumOf { if (it.type == TxType.INGRESO) it.amount else -it.amount })
        }
    }
    val total = balances.values.sum()

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
                    }
                    RoundIcon(Icons.Filled.Add, "Nueva cuenta", Modifier.align(Alignment.TopEnd)) { creating = true }
                }
            }
            items(accs, key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Card)
                        .clickable { editing = a }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(C.CardHi), contentAlignment = Alignment.Center) {
                        Text(a.emoji, fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(a.name, color = C.Sub, fontSize = 15.sp)
                        val b = balances[a.id] ?: 0.0
                        Text(Fmt.money(b), color = if (b < 0) C.Red else C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Text(
                    "Saldo = saldo inicial + ingresos − gastos de esa cuenta. Tocá una cuenta para editarla.",
                    color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    if (creating || editing != null) {
        val e = editing
        AccountDialog(
            initial = e,
            usage = if (e == null) 0 else all.count { it.accountId == e.id } + recs.count { it.accountId == e.id },
            targets = accs.filter { it.id != e?.id },
            onDismiss = { creating = false; editing = null },
            onSave = { a -> vm.launch { vm.repo.saveAccount(a.copy(position = if (a.id == 0L) accs.size else a.position)) }; creating = false; editing = null },
            onDelete = { a, moveTo -> vm.launch { vm.repo.deleteAccount(a, moveTo) }; editing = null }
        )
    }
}

@Composable
fun AccountDialog(
    initial: Account?, usage: Int, targets: List<Account>,
    onDismiss: () -> Unit, onSave: (Account) -> Unit, onDelete: (account: Account, moveTo: Long?) -> Unit
) {
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
                Field(balance, { balance = it }, "Saldo inicial", number = true)
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
                    onSave(base.copy(name = name.trim(), emoji = emoji.ifBlank { "🏦" }, initialBalance = Fmt.parse(balance)))
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
