package com.nestor.cuentasclaras.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Refresca todos los widgets después de cualquier cambio en los datos. */
object WidgetUpdater {
    suspend fun refresh(context: Context) {
        runCatching { DashboardWidget().updateAll(context) }
        runCatching { QuickAddWidget().updateAll(context) }
        runCatching { ActividadWidget().updateAll(context) }
        runCatching { ResumenWidget().updateAll(context) }
        runCatching { CuentasWidget().updateAll(context) }
    }
}
