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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nestor.cuentasclaras.R
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.ui.MainActivity
import com.nestor.cuentasclaras.util.Fmt

class QuickAddWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetData.load(context)
        provideContent {
            val snap by remember { WidgetData.observe(context) }.collectAsState(initial)
            QuickContent(context, snap)
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

@Composable
private fun QuickContent(context: Context, s: Snapshot) {
    val wide = LocalSize.current.width >= 220.dp
    val dark = ColorProvider(Color(0xFF12171B))
    val expense = actionStartActivity(quickAddIntent(context, TxType.GASTO))
    val income = actionStartActivity(quickAddIntent(context, TxType.INGRESO))
    val openApp = actionStartActivity(openAppIntent(context, 0))

    Row(
        GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (wide) {
            Column(GlanceModifier.defaultWeight().padding(start = 8.dp).clickable(openApp)) {
                Text("Hoy gastaste", style = TextStyle(color = ColorProvider(Color(0xFF8C979F)), fontSize = 12.sp), maxLines = 1)
                Text(
                    Fmt.money(s.hoy),
                    style = TextStyle(color = ColorProvider(Color(0xFFF1F5F7)), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        }
        Box(
            (if (wide) GlanceModifier else GlanceModifier.defaultWeight())
                .fillMaxHeight().background(ImageProvider(R.drawable.widget_btn_white))
                .padding(horizontal = 16.dp).clickable(expense),
            contentAlignment = Alignment.Center
        ) { Text("− Gasto", style = TextStyle(color = dark, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1) }
        Spacer(GlanceModifier.width(8.dp))
        Box(
            (if (wide) GlanceModifier else GlanceModifier.defaultWeight())
                .fillMaxHeight().background(ImageProvider(R.drawable.widget_btn_green))
                .padding(horizontal = 16.dp).clickable(income),
            contentAlignment = Alignment.Center
        ) { Text("+ Ingreso", style = TextStyle(color = dark, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1) }
    }
}
