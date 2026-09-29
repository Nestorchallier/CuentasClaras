package com.nestor.cuentasclaras.capture

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.repo
import com.nestor.cuentasclaras.util.Balances
import com.nestor.cuentasclaras.util.Dates
import com.nestor.cuentasclaras.util.Fmt
import com.nestor.cuentasclaras.util.Money
import com.nestor.cuentasclaras.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Bot de Telegram propio de Nestor: le escribe "café 2500" y el gasto se carga en la app.
 *
 * No hay servidor: la app le pregunta a Telegram si hay mensajes nuevos (getUpdates).
 * - Con la app abierta: espera mensajes en vivo (MainActivity llama a [poll] con espera larga).
 * - Con la app cerrada: cada 15 minutos con WorkManager.
 * Solo se aceptan mensajes del chat vinculado con el código que muestra la app.
 */
object TelegramBot {
    private const val WORK = "telegram_bot"
    private val lock = Mutex()

    val enabled: Boolean get() = Prefs.telegramToken.isNotBlank()

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            wm.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TelegramWorker>(15, TimeUnit.MINUTES).build()
            )
        } else wm.cancelUniqueWork(WORK)
    }

    private suspend fun call(method: String, params: Map<String, String>, readTimeoutMs: Int = 15000): JSONObject =
        withContext(Dispatchers.IO) {
            val query = params.entries.joinToString("&") { "${it.key}=" + URLEncoder.encode(it.value, "UTF-8") }
            val url = URL("https://api.telegram.org/bot${Prefs.telegramToken.trim()}/$method?$query")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = readTimeoutMs
            }
            try {
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                JSONObject(stream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }

    /** Verifica el token y devuelve el nombre del bot (ej. "@MisCuentasBot"), o null si el token es inválido. */
    suspend fun check(): String? = runCatching {
        val r = call("getMe", emptyMap())
        if (r.optBoolean("ok")) "@" + r.getJSONObject("result").optString("username") else null
    }.getOrNull()

    private suspend fun send(chat: Long, text: String) {
        runCatching { call("sendMessage", mapOf("chat_id" to chat.toString(), "text" to text)) }
    }

    /**
     * Lee los mensajes nuevos y los procesa. [waitSeconds] > 0 deja la conexión abierta esperando
     * mensajes (se usa con la app abierta para que llegue al instante).
     */
    suspend fun poll(context: Context, waitSeconds: Int = 0) {
        if (!enabled) return
        lock.withLock {
            val r = call(
                "getUpdates",
                mapOf("offset" to Prefs.telegramOffset.toString(), "timeout" to waitSeconds.toString(), "allowed_updates" to "[\"message\"]"),
                readTimeoutMs = (waitSeconds + 15) * 1000
            )
            if (!r.optBoolean("ok")) return
            val updates = r.getJSONArray("result")
            for (i in 0 until updates.length()) {
                val u = updates.getJSONObject(i)
                // Se marca como leído antes de procesar: si algo falla, no se carga dos veces.
                Prefs.telegramOffset = u.getLong("update_id") + 1
                val msg = u.optJSONObject("message") ?: continue
                val chat = msg.getJSONObject("chat").getLong("id")
                val text = msg.optString("text").trim()
                if (text.isNotEmpty()) runCatching { handle(context, chat, text) }
            }
        }
    }

    private suspend fun handle(context: Context, chat: Long, text: String) {
        // Vinculación: el primer chat que manda el código queda como el único autorizado.
        if (Prefs.telegramChat == 0L) {
            if (text.contains(Prefs.telegramCode)) {
                Prefs.telegramChat = chat
                send(chat, "✅ Listo, este chat quedó vinculado a Cuentas Claras.\n\n" + HELP)
            } else {
                send(chat, "Para vincular este chat, mandá el código de 4 números que aparece en la app (Ajustes → Bot de Telegram).")
            }
            return
        }
        if (chat != Prefs.telegramChat) return // mensajes de otras personas: se ignoran

        val repo = context.repo
        val cmd = text.lowercase().substringBefore(' ').substringBefore('@')
        when (cmd) {
            "/start", "/ayuda", "/help", "ayuda" -> send(chat, HELP)
            "/saldo", "saldo" -> {
                val accs = repo.db.accounts().all()
                val b = Balances.of(accs, repo.db.txs().all())
                val lines = accs.joinToString("\n") { "${it.emoji} ${it.name}: ${Fmt.money(b[it.id] ?: 0.0, it)}" }
                send(chat, "💰 Saldo total: ${Fmt.money(Balances.totalArs(accs, b))}\n\n$lines")
            }
            "/mes", "mes" -> {
                val accs = repo.db.accounts().all()
                val month = Dates.inMonth(Money.inArs(repo.db.txs().all(), accs), YearMonth.now())
                val g = month.filter { it.type == TxType.GASTO }.sumOf { it.amount }
                val i = month.filter { it.type == TxType.INGRESO }.sumOf { it.amount }
                send(chat, "📅 ${Fmt.monthName(YearMonth.now().monthValue)}\nGastos: ${Fmt.money(g)}\nIngresos: ${Fmt.money(i)}\nBalance: ${Fmt.money(i - g)}")
            }
            "/deshacer", "deshacer" -> {
                val t = Prefs.telegramLastTx.takeIf { it > 0 }?.let { repo.txById(it) }
                if (t == null) send(chat, "No hay nada para deshacer.")
                else {
                    repo.deleteTx(t)
                    Prefs.telegramLastTx = 0
                    send(chat, "↩️ Borrado: ${t.note.ifBlank { "movimiento" }} ${Fmt.money(t.amount)}")
                }
            }
            else -> {
                val cats = repo.db.categories().all()
                val accs = repo.db.accounts().all()
                val p = QuickText.parse(text, cats, accs)
                if (p == null || p.category == null || p.account == null) {
                    send(chat, "No entendí el monto 🤔\n\n$HELP")
                    return
                }
                val time = if (p.date == java.time.LocalDate.now()) LocalTime.now() else LocalTime.NOON
                val id = repo.saveTx(
                    Tx(
                        amount = p.amount, type = p.type, categoryId = p.category.id, accountId = p.account.id,
                        date = Dates.toMillis(p.date, time), note = p.note
                    )
                )
                Prefs.telegramLastTx = id
                val sign = if (p.type == TxType.INGRESO) "Ingreso" else "Gasto"
                val day = if (p.date == java.time.LocalDate.now()) "" else " · ${Dates.dayLabel(p.date)}"
                send(
                    chat,
                    "✅ $sign cargado: ${p.category.emoji} ${p.category.name} ${Fmt.money(p.amount, p.account)}\n" +
                        "Cuenta: ${p.account.name}$day" + (if (p.note.isNotBlank()) "\nNota: ${p.note}" else "") +
                        "\n\n(/deshacer si me equivoqué)"
                )
            }
        }
    }

    private const val HELP = "Escribime así:\n" +
        "• café 2500\n" +
        "• super 15 mil tarjeta\n" +
        "• nafta 20000 ayer\n" +
        "• + sueldo 800000  (el + es ingreso)\n\n" +
        "Comandos: /saldo · /mes · /deshacer"
}

/** Cada 15 minutos: lee los mensajes que llegaron al bot con la app cerrada. */
class TelegramWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        TelegramBot.poll(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
