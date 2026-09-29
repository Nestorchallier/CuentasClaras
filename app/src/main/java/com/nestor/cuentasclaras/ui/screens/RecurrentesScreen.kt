package com.nestor.cuentasclaras.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Category
import com.nestor.cuentasclaras.data.Recurring
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.ui.MainViewModel
import com.nestor.cuentasclaras.ui.components.*
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Fmt
import java.time.YearMonth

@Composable
fun RecurrentesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val recs by vm.recurrings.collectAsState()
    val cats by vm.categories.collectAsState()
    val accs by vm.accounts.collectAsState()
    val catMap = remember(cats) { cats.associateBy { it.id } }
    var editing by remember { mutableStateOf<Recurring?>(null) }
    var creating by remember { mutableStateOf(false) }
    val monthly = recs.filter { it.type == TxType.GASTO }.sumOf { it.amount }

    GradientBg(Color(0xFF1E3F36)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 40.dp)
        ) {
            item {
                BackHeader("Gastos recurrentes", onBack)
                Text(Fmt.money(monthly), color = C.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("${Fmt.money(monthly * 12)} al año", color = C.Sub, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Se registran solos cada mes en el día que elijas (alquiler, abono del celu, suscripciones, cuota del auto…).",
                    color = C.Sub, fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
            }
            if (recs.isEmpty()) {
                item { EmptyState("Sin recurrentes", "Agregá tus gastos fijos para no cargarlos a mano todos los meses.") }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Card)) {
                        recs.forEachIndexed { i, r ->
                            if (i > 0) RowDivider()
                            val c = catMap[r.categoryId]
                            Row(
                                Modifier.fillMaxWidth().clickable { editing = r }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EmojiBadge(c?.emoji ?: "📅", Color(c?.color ?: 0xFF94A3B8))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, color = C.Text, fontSize = 16.sp, maxLines = 1)
                                    Text("Día ${r.dayOfMonth} · ${c?.name ?: "Sin categoría"}", color = C.Sub, fontSize = 12.sp)
                                }
                                Text(
                                    (if (r.type == TxType.INGRESO) "+" else "") + Fmt.money(r.amount),
                                    color = if (r.type == TxType.INGRESO) C.Green else C.Text, fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Nuevo recurrente") { creating = true }
            }
        }
    }

    if (creating || editing != null) {
        RecurringDialog(
            initial = editing, cats = cats, accs = accs,
            onDismiss = { creating = false; editing = null },
            onSave = { r -> vm.launch { vm.repo.saveRecurring(r) }; creating = false; editing = null },
            onDelete = { r -> vm.launch { vm.repo.deleteRecurring(r) }; editing = null }
        )
    }
}

@Composable
fun RecurringDialog(
    initial: Recurring?, cats: List<Category>, accs: List<Account>,
    onDismiss: () -> Unit, onSave: (Recurring) -> Unit, onDelete: (Recurring) -> Unit
) {
    var type by remember { mutableStateOf(initial?.type ?: TxType.GASTO) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.let { Fmt.plain(it) } ?: "") }
    var day by remember { mutableStateOf(initial?.dayOfMonth?.toString() ?: "") }
    var catId by remember { mutableStateOf(initial?.categoryId) }
    var accId by remember { mutableStateOf(initial?.accountId ?: accs.firstOrNull()?.id) }
    var paid by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val typeCats = cats.filter { it.type == type }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { Text(if (initial == null) "Nuevo recurrente" else "Editar recurrente", color = C.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(listOf("Gasto", "Ingreso"), if (type == TxType.GASTO) 0 else 1, {
                    val nt = if (it == 0) TxType.GASTO else TxType.INGRESO
                    if (nt != type) { type = nt; catId = null }
                })
                Field(name, { name = it }, "Nombre (ej: Alquiler)")
                Field(amount, { amount = it }, "Monto", number = true)
                Field(day, { day = it.filter(Char::isDigit).take(2) }, "Día del mes (1 a 31)", number = true)
                Text("Categoría", color = C.Sub, fontSize = 13.sp)
                ChoiceRow(typeCats.map { it.id to "${it.emoji} ${it.name}" }, catId) { catId = it }
                if (accs.size > 1) {
                    Text("Cuenta", color = C.Sub, fontSize = 13.sp)
                    ChoiceRow(accs.map { it.id to "${it.emoji} ${it.name}" }, accId) { accId = it }
                }
                if (initial == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(paid, { paid = it }, colors = CheckboxDefaults.colors(checkedColor = C.Teal))
                        Text("Ya lo registré este mes (empezar el mes que viene)", color = C.Text, fontSize = 14.sp)
                    }
                }
                error?.let { Text(it, color = C.Red, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = Fmt.parse(amount)
                val d = day.toIntOrNull() ?: 0
                when {
                    name.isBlank() -> error = "Poné un nombre"
                    a <= 0 -> error = "Ingresá un monto"
                    d !in 1..31 -> error = "El día tiene que ser entre 1 y 31"
                    catId == null -> error = "Elegí una categoría"
                    accId == null -> error = "Creá una cuenta primero"
                    else -> onSave(
                        Recurring(
                            id = initial?.id ?: 0, name = name.trim(), amount = a, type = type,
                            categoryId = catId!!, accountId = accId!!, dayOfMonth = d,
                            lastGenerated = initial?.lastGenerated ?: if (paid) YearMonth.now().toString() else ""
                        )
                    )
                }
            }) { Text("Guardar", color = C.Teal) }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onDelete(initial) }) { Text("Eliminar", color = C.Red) }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = C.Sub) }
            }
        }
    )
}
