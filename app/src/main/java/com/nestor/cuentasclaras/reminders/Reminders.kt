package com.nestor.cuentasclaras.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nestor.cuentasclaras.R
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.repo
import com.nestor.cuentasclaras.ui.MainActivity
import com.nestor.cuentasclaras.util.CardCycle
import com.nestor.cuentasclaras.util.Fmt
import com.nestor.cuentasclaras.util.Prefs
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Recordatorios con WorkManager:
 * - A las 9: avisa lo que vence mañana (gastos recurrentes y tarjetas).
 * - A las 21 (opcional): "¿Cargaste tus gastos de hoy?".
 */
object Reminders {
    private const val CHANNEL = "recordatorios"
    private const val WORK_DUE = "recordatorio_vencimientos"
    private const val WORK_DAILY = "recordatorio_diario"

    /** Programa (o cancela) los recordatorios según lo elegido en Ajustes. Se llama al abrir la app. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (Prefs.remindDue) {
            wm.enqueueUniquePeriodicWork(
                WORK_DUE, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DueWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayUntil(LocalTime.of(9, 0)), TimeUnit.MILLISECONDS).build()
            )
        } else wm.cancelUniqueWork(WORK_DUE)
        if (Prefs.remindDaily) {
            wm.enqueueUniquePeriodicWork(
                WORK_DAILY, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayUntil(LocalTime.of(21, 0)), TimeUnit.MILLISECONDS).build()
            )
        } else wm.cancelUniqueWork(WORK_DAILY)
    }

    private fun delayUntil(t: LocalTime): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(t)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(context: Context, id: Int, title: String, text: String, tab: Int) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Recordatorios", NotificationManager.IMPORTANCE_DEFAULT))
        }
        // Abre la app en la pestaña indicada (mismo deep link que usan los widgets).
        val open = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("cuentasclaras://open/$tab")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: no hacer nada.
        }
    }
}

/** Lo que vence mañana: recurrentes de gasto todavía no generados y resúmenes de tarjeta. */
class DueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val db = ctx.repo.db
        val tomorrow = LocalDate.now().plusDays(1)
        val ym = YearMonth.from(tomorrow)
        val lines = mutableListOf<String>()

        db.recurrings().all()
            .filter { it.type == TxType.GASTO && it.lastGenerated != ym.toString() }
            .filter { it.dayOfMonth.coerceIn(1, ym.lengthOfMonth()) == tomorrow.dayOfMonth }
            .forEach { lines += "${it.name}: ${Fmt.money(it.amount)}" }

        val txs = db.txs().all()
        db.accounts().all().filter { it.isCard }.forEach { card ->
            val st = CardCycle.toPay(card, txs)
            if (st != null && st.due == tomorrow) lines += "Tarjeta ${card.name}: ${Fmt.money(st.total)}"
        }

        if (lines.isNotEmpty()) {
            Reminders.notify(
                ctx, 1001,
                if (lines.size == 1) "Mañana vence 1 pago" else "Mañana vencen ${lines.size} pagos",
                lines.joinToString("\n"), tab = 4
            )
        }
        return Result.success()
    }
}

/** Aviso diario opcional para no olvidarse de cargar los gastos. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Reminders.notify(applicationContext, 1002, "¿Cargaste tus gastos de hoy?", "Tocá para abrir Cuentas Claras.", tab = 0)
        return Result.success()
    }
}
