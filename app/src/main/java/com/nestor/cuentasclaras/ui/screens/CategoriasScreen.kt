package com.nestor.cuentasclaras.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.data.Category
import com.nestor.cuentasclaras.data.Defaults
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.ui.MainViewModel
import com.nestor.cuentasclaras.ui.components.*
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Fmt

@Composable
fun CategoriasScreen(vm: MainViewModel, onBack: () -> Unit) {
    val cats by vm.categories.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val type = if (tab == 0) TxType.GASTO else TxType.INGRESO
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    val list = cats.filter { it.type == type }
    val suggestions = Defaults.suggestions.filter { s -> s.type == type && list.none { it.name.equals(s.name, true) } }

    GradientBg(C.TopNeutral) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 40.dp)
        ) {
            item {
                BackHeader("Categorías", onBack)
                Segmented(listOf("Gasto", "Ingreso"), tab, { tab = it })
                Spacer(Modifier.height(10.dp))
                if (type == TxType.GASTO) {
                    Text(
                        "Tocá una categoría para cambiarle el nombre, el color o asignarle un presupuesto mensual.",
                        color = C.Sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (list.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Card)) {
                        list.forEachIndexed { i, c ->
                            if (i > 0) RowDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable { editing = c }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EmojiBadge(c.emoji, Color(c.color))
                                Spacer(Modifier.width(12.dp))
                                Text(c.name, color = C.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                if (c.type == TxType.GASTO && c.budget > 0) {
                                    Text(Fmt.money(c.budget), color = C.Sub, fontSize = 13.sp)
                                    Spacer(Modifier.width(4.dp))
                                }
                                Chevron()
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Nueva categoría") { creating = true }
            }
            if (suggestions.isNotEmpty()) {
                item {
                    Text("Adiciones rápidas", color = C.Sub, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Card)) {
                        suggestions.forEachIndexed { i, s ->
                            if (i > 0) RowDivider()
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { vm.launch { vm.repo.saveCategory(s.copy(id = 0, position = list.size)) } }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(C.CardHi), contentAlignment = Alignment.Center) {
                                    Text(s.emoji, fontSize = 20.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(s.name, color = C.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Box(Modifier.size(30.dp).clip(CircleShape).background(C.CardHi), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Add, "Agregar", tint = C.Text, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        CategoryDialog(
            initial = editing, type = type,
            onDismiss = { creating = false; editing = null },
            onSave = { c ->
                vm.launch { vm.repo.saveCategory(if (c.id == 0L) c.copy(position = list.size) else c) }
                creating = false; editing = null
            },
            onDelete = { c -> vm.launch { vm.repo.deleteCategory(c) }; editing = null }
        )
    }
}

@Composable
fun CategoryDialog(initial: Category?, type: String, onDismiss: () -> Unit, onSave: (Category) -> Unit, onDelete: (Category) -> Unit) {
    val t = initial?.type ?: type
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: if (t == TxType.GASTO) "📦" else "💵") }
    var color by remember { mutableStateOf(initial?.color ?: Defaults.palette[0]) }
    var budget by remember { mutableStateOf(initial?.budget?.takeIf { it > 0 }?.let { Fmt.plain(it) } ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { Text(if (initial == null) "Nueva categoría" else "Editar categoría", color = C.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EmojiPicker(emoji, Defaults.emojis) { emoji = it }
                Field(name, { name = it }, "Nombre")
                Text("Color", color = C.Sub, fontSize = 13.sp)
                ColorPicker(color, Defaults.palette) { color = it }
                if (t == TxType.GASTO) {
                    Field(budget, { budget = it }, "Presupuesto mensual (opcional)", number = true)
                }
                if (confirmDelete) {
                    Text("Los movimientos de esta categoría quedarán como \"Sin categoría\". Tocá Eliminar otra vez para confirmar.", color = C.Red, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val base = initial ?: Category(name = "", emoji = "", color = color, type = t)
                    onSave(base.copy(name = name.trim(), emoji = emoji.ifBlank { "📦" }, color = color, budget = if (t == TxType.GASTO) Fmt.parse(budget) else 0.0))
                }
            }) { Text("Guardar", color = C.Teal) }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { if (confirmDelete) onDelete(initial) else confirmDelete = true }) { Text("Eliminar", color = C.Red) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = C.Sub) }
            }
        }
    )
}
