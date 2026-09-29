package com.nestor.cuentasclaras.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/** Preferencias simples. Son "state" de Compose para que la UI se actualice sola. */
object Prefs {
    private var sp: SharedPreferences? = null
    private val _currency = mutableStateOf("$")
    private val _cents = mutableStateOf(false)
    private val _hide = mutableStateOf(false)
    private val _general = mutableStateOf(GeneralCards.DEFAULT)

    fun init(context: Context) {
        val p = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        sp = p
        _currency.value = p.getString("currency", "$") ?: "$"
        _cents.value = p.getBoolean("cents", false)
        _hide.value = p.getBoolean("hide", false)
        _general.value = p.getString("general_cards", null)
            ?.split(",")?.filter { it in GeneralCards.ALL.keys }
            ?: GeneralCards.DEFAULT
    }

    var currency: String
        get() = _currency.value
        set(v) { _currency.value = v; sp?.edit()?.putString("currency", v)?.apply() }

    var showCents: Boolean
        get() = _cents.value
        set(v) { _cents.value = v; sp?.edit()?.putBoolean("cents", v)?.apply() }

    var hideBalances: Boolean
        get() = _hide.value
        set(v) { _hide.value = v; sp?.edit()?.putBoolean("hide", v)?.apply() }

    /** Tarjetas visibles en "General", en orden. */
    var generalCards: List<String>
        get() = _general.value
        set(v) { _general.value = v; sp?.edit()?.putString("general_cards", v.joinToString(","))?.apply() }
}

/** Catálogo de tarjetas disponibles para la pantalla General. */
object GeneralCards {
    val ALL: LinkedHashMap<String, String> = linkedMapOf(
        "flujo" to "Ingresos vs gastos (6 meses)",
        "restantes" to "Ingresos restantes",
        "proyeccion" to "Ritmo de gasto y proyección del mes",
        "presupuesto" to "Presupuesto del mes",
        "categorias" to "Top categorías de gasto",
        "cuentas" to "Saldos por cuenta",
        "recurrentes" to "Gastos recurrentes (calendario)",
        "calendario" to "Calendario de gastos"
    )
    val DEFAULT = listOf("flujo", "restantes", "proyeccion", "recurrentes", "calendario")
}
