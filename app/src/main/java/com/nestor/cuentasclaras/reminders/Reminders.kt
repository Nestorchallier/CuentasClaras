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
import android.provider.Settings
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
import com.nestor.cuentasclaras.util.Dates
import com.nestor.cuentasclaras.util.Fmt
import com.nestor.cuentasclaras.util.Money
import com.nestor.cuentasclaras.util.Prefs
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Recordatorios con WorkManager (todo se configura en Ajustes → Recordatorios):
 * - Vencimientos: a la hora elegida, lo que vence en 1, 2 o 3 días (recurrentes y tarjetas) y el cierre de tarjetas.
 * - Aviso diario opcional: "¿Cargaste tus gastos de hoy?".
 * - Resumen semanal opcional: el domingo a la noche, cuánto se gastó en la semana.
 * Los avisos de presupuesto, gasto grande y poca plata salen al cargar un gasto (ver [Alerts]).
 */
object Reminders {
    /** Canales separados: en los ajustes de Android se puede elegir sonido/vibración de cada uno. */
    object Channel {
        const val DUE = "vencimientos"
        const val DAILY = "recordatorio_diario"
        const val ALERTS = "alertas_gastos"
        const val WEEKLY = "resumen_semanal"
    }

    private const val WORK_DUE = "recordatorio_vencimientos"
    private const val WORK_DAILY = "recordatorio_diario"
    private const val WORK_WEEKLY = "resumen_semanal"

    /**
     * Programa (o cancela) los recordatorios según Ajustes.
     * [changed] = true cuando se cambió una hora: se reprograma desde cero para respetar la hora nueva.
     */
    fun schedule(context: Context, changed: Boolean = false) {
        val wm = WorkManager.getInstance(context)
        val policy = if (changed) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        if (Prefs.remindDue) {
            wm.enqueueUniquePeriodicWork(
                WORK_DUE, policy,
                PeriodicWorkRequestBuilder<DueWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayUntil(LocalTime.of(Prefs.dueHour, 0)), TimeUnit.MILLISECONDS).build()
            )
        } else wm.cancelUniqueWork(WORK_DUE)
        if (Prefs.remindDaily) {
            wm.enqueueUniquePeriodicWork(
                WORK_DAILY, policy,
                PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayUntil(LocalTime.of(Prefs.dailyHour, 0)), TimeUnit.MILLISECONDS).build()
            )
        } else wm.cancelUniqueWork(WORK_DAILY)
        if (Prefs.weeklySummary) {
            wm.enqueueUniquePeriodicWork(
                WORK_WEEKLY, policy,
                PeriodicWorkRequestBuilder<WeeklyWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(delayUntilSunday(LocalTime.of(20, 0)), TimeUnit.MILLISECONDS).build()
            )
        } else wm.cancelUniqueWork(WORK_WEEKLY)
    }

    private fun delayUntil(t: LocalTime): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(t)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    private fun delayUntilSunday(t: LocalTime): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(t)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        return Duration.between(now, next).toMillis()
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        listOf(
            Channel.DUE to "Vencimientos",
            Channel.DAILY to "Recordatorio diario",
            Channel.ALERTS to "Presupuesto y gastos",
            Channel.WEEKLY to "Resumen semanal"
        ).forEach { (id, name) ->
            if (nm.getNotificationChannel(id) == null) {
                nm.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
    }

    /** Abre los ajustes de notificaciones de Android para esta app (sonido, vibración, etc. de cada tipo). */
    fun openSystemSettings(context: Context) {
        ensureChannels(context)
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }

    fun notify(context: Context, id: Int, title: String, text: String, tab: Int, channel: String = Channel.DUE) {
        if (!canNotify(context)) return
        ensureChannels(context)
        // Abre la app en la pestaña indicada (mismo deep link que usan los widgets).
        val open = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("cuentasclaras://open/$tab")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, channel)
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

/**
 * Vencimientos: recurrentes de gasto y resúmenes de tarjeta que vencen dentro de [Prefs.dueDaysBefore] días,
 * y tarjetas que cierran mañana.
 */
class DueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val db = ctx.repo.db
        val days = Prefs.dueDaysBefore.coerceIn(1, 3).toLong()
        val target = LocalDate.now().plusDays(days)
        val ym = YearMonth.from(target)
        val lines = mutableListOf<String>()

        db.recurrings().all()
            .filter { it.type == TxType.GASTO && it.lastGenerated != ym.toString() }
            .filter { it.dayOfMonth.coerceIn(1, ym.lengthOfMonth()) == target.dayOfMonth }
            .forEach { lines += "${it.name}: ${Fmt.money(it.amount)}" }

        val txs = db.txs().all()
        val tomorrow = LocalDate.now().plusDays(1)
        db.accounts().all().filter { it.isCard }.forEach { card ->
            val st = CardCycle.toPay(card, txs)
            if (st != null && st.due == target) lines += "Tarjeta ${card.name}: ${Fmt.money(st.total, card)}"
            // Aviso extra: la tarjeta cierra mañana (lo que compres después entra en el resumen siguiente).
            if (card.closingDay > 0 && CardCycle.closingOf(card, YearMonth.from(tomorrow)) == tomorrow) {
                Reminders.notify(
                    ctx, 1100 + card.id.toInt(), "Mañana cierra la tarjeta ${card.name}",
                    "Lo que compres desde pasado mañana entra en el resumen siguiente.", tab = 4
                )
            }
        }

        if (lines.isNotEmpty()) {
            val cuando = when (days) { 1L -> "Mañana"; 2L -> "En 2 días"; else -> "En 3 días" }
            Reminders.notify(
                ctx, 1001,
                if (lines.size == 1) "$cuando vence 1 pago" else "$cuando vencen ${lines.size} pagos",
                lines.joinToString("\n"), tab = 4
            )
        }
        return Result.success()
    }
}

/** Aviso diario opcional para no olvidarse de cargar los gastos. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Reminders.notify(
            applicationContext, 1002, "¿Cargaste tus gastos de hoy?", "Tocá para abrir Cuentas Claras.",
            tab = 0, channel = Reminders.Channel.DAILY
        )
        return Result.success()
    }
}

/** Resumen semanal: cuánto se gastó en los últimos 7 días y en qué categoría más. */
class WeeklyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val db = ctx.repo.db
        val accs = db.accounts().all()
        val cats = db.categories().all().associateBy { it.id }
        val from = Dates.toMillis(LocalDate.now().minusDays(6), LocalTime.MIN)
        val now = System.currentTimeMillis()
        val week = Money.inArs(db.txs().all(), accs).filter { it.type == TxType.GASTO && it.date in from..now }
        val total = week.sumOf { it.amount }
        val top = week.groupBy { it.categoryId }.maxByOrNull { e -> e.value.sumOf { it.amount } }
        val text = if (week.isEmpty()) "No cargaste gastos esta semana." else buildString {
            append("Gastaste ${Fmt.money(total)} en ${week.size} movimientos.")
            top?.let { e -> cats[e.key]?.let { c -> append("\nDonde más: ${c.emoji} ${c.name} (${Fmt.money(e.value.sumOf { it.amount })})") } }
        }
        Reminders.notify(ctx, 1003, "Tu semana", text, tab = 1, channel = Reminders.Channel.WEEKLY)
        return Result.success()
    }
}
