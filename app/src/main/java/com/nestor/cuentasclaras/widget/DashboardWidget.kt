package com.nestor.cuentasclaras.widget

import android.content.Context
import android.content.Intent
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
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nestor.cuentasclaras.R
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.ui.MainActivity
import com.nestor.cuentasclaras.util.Fmt

class DashboardWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetData.load(context)
        provideContent {
            val snap by remember { WidgetData.observe(context) }.collectAsState(initial)
            DashboardContent(context, snap)
        }
    }
}

class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardWidget()
}

// Colores con versión de día y de noche (ver WColors.kt).
private val White = W.Text
private val Sub = W.Sub
private val Green = W.Green
private val Dark = W.OnButton

@Composable
private fun DashboardContent(context: Context, s: Snapshot) {
    val size = LocalSize.current
    val openApp = actionStartActivity(openAppIntent(context, 0))
    Column(
        GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg))
            .padding(14.dp).clickable(openApp)
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text("Gastos de ${s.monthName}", style = TextStyle(color = Sub, fontSize = 12.sp), maxLines = 1)
                Text(
                    Fmt.money(s.gastos),
                    style = TextStyle(color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 1
                )
            }
            Box(
                GlanceModifier.size(40.dp).background(ImageProvider(R.drawable.widget_btn_white))
                    .clickable(actionStartActivity(quickAddIntent(context, TxType.GASTO))),
                contentAlignment = Alignment.Center
            ) {
                Text("+", style = TextStyle(color = Dark, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            }
        }

        if (size.height >= 140.dp) {
            Spacer(GlanceModifier.height(8.dp))
            val bmp = remember(s) { WidgetData.barsBitmap(s.daily, s.todayIndex, night = W.isNight(context)) }
            Image(
                provider = ImageProvider(bmp),
                contentDescription = "Gastos por día",
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentScale = ContentScale.FillBounds
            )
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }

        Spacer(GlanceModifier.height(8.dp))
        if (s.budget > 0) {
            LinearProgressIndicator(
                progress = (s.gastos / s.budget).toFloat().coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = if (s.gastos > s.budget) W.Red else W.Teal,
                backgroundColor = W.Track
            )
            Spacer(GlanceModifier.height(6.dp))
        }
        Row(GlanceModifier.fillMaxWidth()) {
            Text(
                "Ingresos ${Fmt.money(s.ingresos)}",
                style = TextStyle(color = Green, fontSize = 12.sp), maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            val right = if (s.budget > 0) "Disponible ${Fmt.money(s.budget - s.gastos)}"
            else "Saldo ${Fmt.money(s.ingresos - s.gastos)}"
            Text(right, style = TextStyle(color = White, fontSize = 12.sp), maxLines = 1)
        }
    }
}
