package com.nestor.cuentasclaras.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Category
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Fmt
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun GradientBg(top: Color, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .background(Brush.verticalGradient(0f to top, 0.5f to C.Bg)),
        content = content
    )
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) C.CardHi else C.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = C.Text, fontSize = 14.sp, maxLines = 1) }
}

@Composable
fun <T> DropPill(label: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Pill("$label ▾") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = C.CardHi) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text, color = C.Text) }, onClick = { open = false; onSelect(value) })
            }
        }
    }
}

@Composable
fun RoundIcon(icon: ImageVector, desc: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(C.Card).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = C.Text, modifier = Modifier.size(22.dp)) }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Card).padding(padding),
        content = content
    )
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = C.Sub, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Card), content = content)
}

/** Círculo con emoji y borde del color de la categoría. Si hay progress, el borde es un anillo de avance. */
@Composable
fun EmojiBadge(emoji: String, color: Color, dim: Dp = 42.dp, progress: Float? = null) {
    Box(Modifier.size(dim), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 2.5.dp.toPx()
            val r = size.minDimension / 2 - sw / 2
            drawCircle(color.copy(alpha = 0.14f), radius = r)
            if (progress == null) {
                drawCircle(color, radius = r, style = Stroke(sw))
            } else {
                drawCircle(color.copy(alpha = 0.25f), radius = r, style = Stroke(sw))
                drawArc(
                    color, -90f, 360f * progress.coerceIn(0f, 1f), false,
                    topLeft = Offset(sw / 2, sw / 2),
                    size = Size(size.width - sw, size.height - sw),
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
        }
        Text(emoji, fontSize = (dim.value * 0.42f).sp)
    }
}

@Composable
fun TxRow(tx: Tx, cat: Category?, acc: Account?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmojiBadge(cat?.emoji ?: "❔", Color(cat?.color ?: 0xFF94A3B8))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tx.note.ifBlank { cat?.name ?: "Sin categoría" },
                color = C.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val sub = listOfNotNull(
                if (tx.note.isNotBlank()) cat?.name else null,
                acc?.name,
                if (tx.recurringId != null) "Recurrente" else null
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, color = C.Sub, fontSize = 12.sp, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        val income = tx.type == TxType.INGRESO
        Text(
            (if (income) "+" else "") + Fmt.money(tx.amount),
            color = if (income) C.Green else C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TxGroup(title: String, total: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
            Text(title, color = C.Sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(total, color = C.Sub, fontSize = 14.sp)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Card), content = content)
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(color = C.Bg.copy(alpha = 0.6f), thickness = 1.dp)
}

@Composable
fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Sub)
}

@Composable
fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit) {
    // Permite ir hasta 2 años hacia adelante (movimientos programados y recurrentes)
    val canNext = month < YearMonth.now().plusMonths(24)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior", tint = C.Sub,
            modifier = Modifier.clip(CircleShape).clickable { onChange(month.minusMonths(1)) }.padding(6.dp)
        )
        Text(Fmt.monthLabel(month), color = C.Sub, fontSize = 15.sp)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente",
            tint = if (canNext) C.Sub else C.Sub.copy(alpha = 0.2f),
            modifier = Modifier.clip(CircleShape).clickable(enabled = canNext) { onChange(month.plusMonths(1)) }.padding(6.dp)
        )
    }
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onClick = onBack)
            Spacer(Modifier.weight(1f))
            action?.invoke()
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(C.Card).padding(4.dp)) {
        options.forEachIndexed { i, s ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (i == selected) C.CardHi else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s, color = if (i == selected) C.Text else C.Sub, fontSize = 15.sp,
                    fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun SettingRow(
    icon: ImageVector, title: String, value: String? = null,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.CardHi), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = C.Sub, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = C.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = C.Sub, fontSize = 15.sp)
        trailing?.invoke()
    }
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, number: Boolean = false
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = C.Text, unfocusedTextColor = C.Text,
            focusedBorderColor = C.Teal, unfocusedBorderColor = C.CardHi,
            focusedLabelColor = C.Teal, unfocusedLabelColor = C.Sub, cursorColor = C.Teal
        )
    )
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🪙", fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = C.Sub, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.Text, contentColor = C.Bg)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

/** Selector de emoji: sugerencias + campo libre. */
@Composable
fun EmojiPicker(value: String, options: List<String>, onChange: (String) -> Unit) {
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { e ->
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (e == value) C.Teal.copy(alpha = 0.3f) else C.CardHi)
                        .clickable { onChange(e) },
                    contentAlignment = Alignment.Center
                ) { Text(e, fontSize = 20.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Field(value, { onChange(it.take(8)) }, "Emoji (podés escribir otro)")
    }
}

@Composable
fun ColorPicker(value: Long, options: List<Long>, onChange: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { c ->
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(if (c == value) C.Text else Color.Transparent)
                    .padding(3.dp).clip(CircleShape).background(Color(c))
                    .clickable { onChange(c) }
            )
        }
    }
}

/** Fila horizontal de opciones tipo chip. */
@Composable
fun ChoiceRow(items: List<Pair<Long, String>>, selected: Long?, onSelect: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (id, label) -> Pill(label, selected = id == selected) { onSelect(id) } }
    }
}

/** Grilla de calendario (semana empieza lunes). */
@Composable
fun MonthGrid(month: YearMonth, cell: @Composable RowScope.(LocalDate?) -> Unit) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cells: List<LocalDate?> = List<LocalDate?>(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                Text(it, color = C.Sub, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { cell(it) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
