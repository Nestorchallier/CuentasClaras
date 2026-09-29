package com.nestor.cuentasclaras.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nestor.cuentasclaras.R
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.util.Fmt
import kotlin.math.max

private val WText = ColorProvider(Color(0xFFF1F5F7))
private val WSub = ColorProvider(Color(0xFF8C979F))
private val WGreen = ColorProvider(Color(0xFF34D399))
private val WRed = ColorProvider(Color(0xFFF87171))
private val WDark = ColorProvider(Color(0xFF12171B))

/** Estructura común: carga inicial + se actualiza sola cuando cambian los datos. */
abstract class SnapshotWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetData.load(context)
        provideContent {
            val snap by remember { WidgetData.observe(context) }.collectAsState(initial)
            Content(context, snap)
        }
    }

    @Composable
    abstract fun Content(context: Context, s: Snapshot)
}

@Composable
private fun Header(context: Context, label: String, value: String, valueColor: ColorProvider = WText, showAdd: Boolean = true) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(label, style = TextStyle(color = WSub, fontSize = 12.sp), maxLines = 1)
            Text(value, style = TextStyle(color = valueColor, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        if (showAdd) {
            Box(
                GlanceModifier.size(36.dp).background(ImageProvider(R.drawable.widget_btn_white))
                    .clickable(actionStartActivity(quickAddIntent(context, TxType.GASTO))),
                contentAlignment = Alignment.Center
            ) { Text("+", style = TextStyle(color = WDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)) }
        }
    }
}

// ---------------- Actividad: últimos movimientos ----------------

class ActividadWidget : SnapshotWidget() {
    @Composable
    override fun Content(context: Context, s: Snapshot) {
        val size = LocalSize.current
        val rows = max(1, ((size.height.value - 70f) / 40f).toInt()).coerceAtMost(s.recent.size.coerceAtLeast(1))
        Column(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(14.dp)
                .clickable(actionStartActivity(openAppIntent(context, 0)))
        ) {
            Header(context, "Gastos de ${s.monthName}", Fmt.money(s.gastos))
            Spacer(GlanceModifier.height(8.dp))
            if (s.recent.isEmpty()) {
                Text("Todavía no hay movimientos", style = TextStyle(color = WSub, fontSize = 13.sp))
            }
            s.recent.take(rows).forEach { r ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.emoji, style = TextStyle(fontSize = 18.sp))
                    Spacer(GlanceModifier.width(8.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(r.title, style = TextStyle(color = WText, fontSize = 14.sp), maxLines = 1)
                        Text(r.subtitle, style = TextStyle(color = WSub, fontSize = 11.sp), maxLines = 1)
                    }
                    Text(
                        (if (r.income) "+" else "") + Fmt.money(r.amount, r.account),
                        style = TextStyle(color = if (r.income) WGreen else WText, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

class ActividadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ActividadWidget()
}

// ---------------- Resumen: dona por categoría ----------------

class ResumenWidget : SnapshotWidget() {
    @Composable
    override fun Content(context: Context, s: Snapshot) {
        val size = LocalSize.current
        val donutSize = (size.height.value - 28f).coerceIn(70f, 150f).dp
        val showLegend = size.width.value >= 220f
        val legendRows = max(1, ((size.height.value - 30f) / 26f).toInt()).coerceAtMost(5)
        Row(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(14.dp)
                .clickable(actionStartActivity(openAppIntent(context, 1))),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(GlanceModifier.size(donutSize), contentAlignment = Alignment.Center) {
                val bmp = remember(s.slices) { WidgetData.donutBitmap(s.slices) }
                Image(ImageProvider(bmp), contentDescription = "Gastos por categoría", modifier = GlanceModifier.size(donutSize))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.monthName.take(3), style = TextStyle(color = WSub, fontSize = 11.sp))
                    Text(Fmt.compact(s.gastos), style = TextStyle(color = WText, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
            }
            if (showLegend) {
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    if (s.slices.isEmpty()) Text("Sin gastos este mes", style = TextStyle(color = WSub, fontSize = 13.sp))
                    s.slices.take(legendRows).forEach { c ->
                        val pct = if (s.gastos > 0) c.amount / s.gastos * 100 else 0.0
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(GlanceModifier.size(8.dp).background(Color(c.color))) {}
                            Spacer(GlanceModifier.width(6.dp))
                            Text("${c.emoji} ${c.name}", style = TextStyle(color = WText, fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                            Text(Fmt.pct(pct), style = TextStyle(color = WSub, fontSize = 12.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

class ResumenWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ResumenWidget()
}

// ---------------- Cuentas: saldos ----------------

class CuentasWidget : SnapshotWidget() {
    @Composable
    override fun Content(context: Context, s: Snapshot) {
        val size = LocalSize.current
        val rows = max(0, ((size.height.value - 70f) / 30f).toInt())
        val total = s.totalBalance
        Column(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(14.dp)
                .clickable(actionStartActivity(openAppIntent(context, 4)))
        ) {
            Header(context, "Saldo total", Fmt.money(total), if (total < 0) WRed else WText, showAdd = false)
            if (rows > 0) Spacer(GlanceModifier.height(6.dp))
            s.accounts.take(rows).forEach { a ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.emoji, style = TextStyle(fontSize = 16.sp))
                    Spacer(GlanceModifier.width(8.dp))
                    Text(a.name, style = TextStyle(color = WText, fontSize = 14.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Text(
                        Fmt.money(a.balance, a.account),
                        style = TextStyle(color = if (a.balance < 0) WRed else WText, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

class CuentasWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CuentasWidget()
}
