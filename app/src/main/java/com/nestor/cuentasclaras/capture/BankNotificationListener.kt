package com.nestor.cuentasclaras.capture

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nestor.cuentasclaras.R
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.reminders.Reminders
import com.nestor.cuentasclaras.util.Fmt
import com.nestor.cuentasclaras.util.Prefs
import com.nestor.cuentasclaras.widget.QuickAddActivity

/**
 * Lee las notificaciones de pagos (bancos, Mercado Pago, billeteras) y propone cargarlas:
 * "Pagaste $ 4.500 en Farmacia" → aviso "¿Cargar gasto de $ 4.500?" que abre la hoja de carga completada.
 * No guarda nada solo: siempre se confirma tocando Guardar.
 * Android pide activar el permiso "Acceso a notificaciones" (Ajustes → Avisos del banco).
 */
class BankNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Prefs.captureBank) return
        val pkg = sbn.packageName
        if (pkg == packageName || pkg in IGNORED) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        val found = BankText.parse("$title. $body") ?: return

        // La misma notificación se actualiza varias veces: avisar una sola vez.
        val key = "${found.type}|${found.amount}|${found.merchant}"
        synchronized(recent) {
            val now = System.currentTimeMillis()
            recent.entries.removeAll { now - it.value > 10 * 60_000 }
            if (recent.containsKey(key)) return
            recent[key] = now
        }
        suggest(this, found, appName(pkg))
    }

    private fun appName(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        /** Apps de mensajes y redes: no son avisos de pagos. */
        private val IGNORED = setOf(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thunderdog.challegram",
            "com.instagram.android", "com.facebook.orca", "com.facebook.katana", "com.google.android.gm",
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.twitter.android"
        )
        private val recent = HashMap<String, Long>()

        /** Muestra "¿Cargar gasto de $X?"; al tocarlo abre la hoja de carga con todo completado. */
        fun suggest(context: Context, f: BankText.Found, source: String) {
            val type = f.type
            val uri = Uri.parse("cuentasclaras://quickadd/$type").buildUpon()
                .appendQueryParameter("amount", f.amount.toString())
                .appendQueryParameter("note", f.merchant)
                .appendQueryParameter("t", System.currentTimeMillis().toString()) // hace única la dirección
                .build()
            val open = Intent(context, QuickAddActivity::class.java).apply {
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val id = 3000 + (uri.hashCode() and 0xFFF)
            val pi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val what = if (type == TxType.INGRESO) "ingreso" else "gasto"
            val text = listOf(f.merchant, "desde $source").filter { it.isNotBlank() }.joinToString(" · ")
            val notif = NotificationCompat.Builder(context, Reminders.Channel.ALERTS)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("¿Cargar $what de ${Fmt.money(f.amount)}?")
                .setContentText(text)
                .setContentIntent(pi)
                .addAction(0, "Cargar", pi)
                .setAutoCancel(true)
                .build()
            if (!Reminders.canNotify(context)) return
            Reminders.ensureChannels(context)
            try {
                NotificationManagerCompat.from(context).notify(id, notif)
            } catch (_: SecurityException) {
            }
        }
    }
}

/** Reconoce textos de avisos de pago y saca monto, tipo y comercio. */
object BankText {
    data class Found(val type: String, val amount: Double, val merchant: String)

    private val SPEND = listOf(
        "pagaste", "compraste", "compra", "consumo", "debitamos", "debito", "transferiste", "enviaste",
        "pago aprobado", "pagamos", "extraccion", "retiraste"
    )
    private val INCOME = listOf(
        "recibiste", "te transfirieron", "te enviaron", "cobraste", "acreditamos", "acreditado",
        "ingreso de dinero", "te pagaron", "deposito"
    )

    fun parse(raw: String): Found? {
        val n = QuickText.norm(raw)
        // Posición de la primera palabra de gasto y de ingreso (-1 si no hay): gana la que aparece primero.
        val spendAt = SPEND.map { n.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
        val incomeAt = INCOME.map { n.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
        if (spendAt < 0 && incomeAt < 0) return null
        val type = if (incomeAt >= 0 && (spendAt < 0 || incomeAt < spendAt)) TxType.INGRESO else TxType.GASTO
        // Monto con signo de pesos: "$ 4.500", "$4.500,50", "ARS 4500".
        val m = Regex("(?:\\$|ars)\\s?(\\d[\\d.]*(?:,\\d{1,2})?)").find(n) ?: return null
        val amount = Fmt.parseOrNull(m.groupValues[1]) ?: return null
        if (amount <= 0) return null
        // Comercio o persona: "en Farmacia Del Sol", "a Juan Perez".
        val merchant = Regex("\\b(?:en|a|de)\\s+([A-Za-zÁÉÍÓÚÑáéíóúñ0-9&'. -]{2,40}?)(?=[,.!:\\n]|\\s+(?:con|por|el|desde)\\b|$)", RegexOption.IGNORE_CASE)
            .find(raw.substring(minOf(raw.length, raw.indexOf(m.groupValues[1]).coerceAtLeast(0))))
            ?.groupValues?.get(1)?.trim().orEmpty()
        return Found(type, amount, merchant)
    }
}
