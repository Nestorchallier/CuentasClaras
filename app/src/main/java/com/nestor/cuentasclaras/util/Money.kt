package com.nestor.cuentasclaras.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.sync.CloudSync
import com.nestor.cuentasclaras.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Una cotización del dólar: cuánto te pagan (compra) y cuánto te cobran (venta) por US$ 1. */
data class Rate(val buy: Double, val sell: Double)

/**
 * Cotizaciones del dólar (oficial, MEP y blue) desde dolarapi.com, guardadas en el teléfono para
 * poder usarlas sin internet. Los saldos en dólares se pasan a pesos con la cotización elegida.
 */
object Money {
    /** Tipos de dólar: clave de dolarapi.com → nombre para mostrar. */
    val TYPES: LinkedHashMap<String, String> = linkedMapOf("oficial" to "Oficial", "bolsa" to "MEP", "blue" to "Blue")

    private const val URL_RATES = "https://dolarapi.com/v1/dolares"
    private var ctx: Context? = null
    private val _rates = mutableStateOf<Map<String, Rate>>(emptyMap())
    private val _type = mutableStateOf("bolsa")
    private val _updated = mutableStateOf("")

    fun init(context: Context) {
        val p = context.getSharedPreferences("rates", Context.MODE_PRIVATE)
        ctx = context.applicationContext
        _type.value = p.getString("type", "bolsa") ?: "bolsa"
        _updated.value = p.getString("updated", "") ?: ""
        _rates.value = TYPES.keys.mapNotNull { k ->
            val buy = p.getString("${k}_buy", null)?.toDoubleOrNull()
            val sell = p.getString("${k}_sell", null)?.toDoubleOrNull()
            if (buy != null && sell != null) k to Rate(buy, sell) else null
        }.toMap()
    }

    val rates: Map<String, Rate> get() = _rates.value
    val updated: String get() = _updated.value

    /** Tipo de dólar elegido para convertir ("oficial", "bolsa" = MEP, "blue"). */
    var type: String
        get() = _type.value
        set(v) {
            _type.value = v
            ctx?.getSharedPreferences("rates", Context.MODE_PRIVATE)?.edit()?.putString("type", v)?.apply()
        }

    val typeName: String get() = TYPES[type] ?: type

    /** Cotización elegida, o null si nunca se pudo bajar. */
    val current: Rate? get() = rates[type]

    /** Pesos por dólar para valuar lo que tenés en dólares (precio de compra). 0 si no hay cotización. */
    val usdToArs: Double get() = current?.buy ?: 0.0

    fun toArs(amount: Double, account: Account?): Double = if (account?.isUsd == true) amount * usdToArs else amount

    /**
     * Copia de los movimientos con los montos de cuentas en dólares pasados a pesos
     * (para resúmenes, presupuestos y gráficos). Las transferencias no se tocan.
     */
    fun inArs(txs: List<Tx>, accounts: List<Account>): List<Tx> {
        val usd = accounts.filter { it.isUsd }.map { it.id }.toSet()
        if (usd.isEmpty()) return txs
        val r = usdToArs
        return txs.map { t -> if (t.type != TxType.TRANSFER && t.accountId in usd) t.copy(amount = t.amount * r) else t }
    }

    /** Suma en pesos (lo de cuentas en dólares, convertido). */
    fun sumArs(txs: List<Tx>, accounts: Map<Long, Account>): Double = txs.sumOf { toArs(it.amount, accounts[it.accountId]) }

    /** Baja las cotizaciones de internet. Devuelve true si salió bien. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(URL_RATES).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
            }
            val body = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val arr = JSONArray(body)
            val found = HashMap<String, Rate>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val casa = o.optString("casa")
                if (casa in TYPES) {
                    val buy = o.optDouble("compra")
                    val sell = o.optDouble("venta")
                    if (!buy.isNaN() && !sell.isNaN() && buy > 0 && sell > 0) found[casa] = Rate(buy, sell)
                }
            }
            if (found.isEmpty()) return@withContext false
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
            ctx?.getSharedPreferences("rates", Context.MODE_PRIVATE)?.edit()?.apply {
                found.forEach { (k, r) -> putString("${k}_buy", r.buy.toString()); putString("${k}_sell", r.sell.toString()) }
                putString("updated", now)
                apply()
            }
            _rates.value = rates + found
            _updated.value = now
            ctx?.let { WidgetUpdater.refresh(it); CloudSync.requestPush(it) }
            true
        } catch (e: Exception) {
            false
        }
    }
}
