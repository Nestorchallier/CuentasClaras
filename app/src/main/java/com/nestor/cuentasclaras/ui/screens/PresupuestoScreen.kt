package com.nestor.cuentasclaras.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.ui.MainViewModel
import com.nestor.cuentasclaras.ui.components.*
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Dates
import com.nestor.cuentasclaras.util.Fmt

@Composable
fun PresupuestoScreen(vm: MainViewModel, onEditBudgets: () -> Unit, onSettings: () -> Unit) {
    val all by vm.txsArs.collectAsState()
    val cats by vm.categories.collectAsState()
    val month = vm.month
    val gastos = remember(all, month) { Dates.inMonth(all, month).filter { it.type == TxType.GASTO } }
    val budgeted = cats.filter { it.type == TxType.GASTO && it.budget > 0 }
    val totalBudget = budgeted.sumOf { it.budget }
    val spent = gastos.sumOf { it.amount }
    val byCat = remember(gastos) { gastos.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amount } } }
    val available = totalBudget - spent
    val unbudgeted = gastos.filter { t -> budgeted.none { it.id == t.categoryId } }.sumOf { it.amount }

    GradientBg(C.TopNeutral) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 170.dp)
        ) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    RoundIcon(Icons.Filled.Settings, "Ajustes", onClick = onSettings)
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp)) { MonthSwitcher(month) { vm.month = it } }
                    RoundIcon(Icons.Filled.Edit, "Editar presupuestos", Modifier.align(Alignment.TopEnd), onClick = onEditBudgets)
                }
            }
            if (budgeted.isEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    CardBox {
                        Text("Todavía no definiste presupuestos", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Asigná un monto mensual a cada categoría de gasto y seguí cuánto te queda disponible en el mes.",
                            color = C.Sub, fontSize = 14.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton("Definir presupuestos", onClick = onEditBudgets)
                    }
                }
            } else {
                item {
                    RingProgress(
                        progress = (available / totalBudget).toFloat(),
                        color = if (available >= 0) C.Text else C.Red,
                        modifier = Modifier.fillMaxWidth().height(310.dp).padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (available >= 0) "Disponible" else "Te pasaste", color = C.Sub, fontSize = 15.sp)
                            Text(
                                Fmt.money(kotlin.math.abs(available)), fontSize = 34.sp, fontWeight = FontWeight.Bold,
                                color = if (available >= 0) C.Text else C.Red, maxLines = 1
                            )
                            Text("de ${Fmt.money(totalBudget)}", color = C.Sub, fontSize = 15.sp)
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Card)) {
                        budgeted.forEachIndexed { i, c ->
                            if (i > 0) RowDivider()
                            val s = byCat[c.id] ?: 0.0
                            val rem = c.budget - s
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                EmojiBadge(c.emoji, Color(c.color), 46.dp, progress = (s / c.budget).toFloat())
                                Spacer(Modifier.width(12.dp))
                                Text(c.name, color = C.Text, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (rem >= 0) "${Fmt.money(rem)} restantes" else "${Fmt.money(-rem)} excedido",
                                        color = if (rem >= 0) C.Text else C.Red, fontSize = 15.sp
                                    )
                                    Text("de ${Fmt.money(c.budget)}", color = C.Sub, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                if (unbudgeted > 0) {
                    item {
                        Text(
                            "Además gastaste ${Fmt.money(unbudgeted)} en categorías sin presupuesto (ya descontado del disponible).",
                            color = C.Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
