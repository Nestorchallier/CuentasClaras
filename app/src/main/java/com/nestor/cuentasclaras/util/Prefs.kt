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
    private val _mainAccount = mutableStateOf(-1L)
    private val _remindDue = mutableStateOf(true)
    private val _theme = mutableStateOf("dark")
    private val _remindDaily = mutableStateOf(false)
    private val _dueHour = mutableStateOf(9)
    private val _dueDays = mutableStateOf(1)
    private val _dailyHour = mutableStateOf(21)
    private val _weekly = mutableStateOf(false)
    private val _budgetAlerts = mutableStateOf(true)
    private val _bigExpense = mutableStateOf(0.0)
    private val _lowBalance = mutableStateOf(0.0)
    private val _backupFolder = mutableStateOf("")
    private val _lastBackup = mutableStateOf("")

    fun init(context: Context) {
        val p = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        sp = p
        _currency.value = p.getString("currency", "$") ?: "$"
        _cents.value = p.getBoolean("cents", false)
        _hide.value = p.getBoolean("hide", false)
        _mainAccount.value = p.getLong("main_account", -1L)
        _remindDue.value = p.getBoolean("remind_due", true)
        _theme.value = p.getString("theme", "dark") ?: "dark"
        _remindDaily.value = p.getBoolean("remind_daily", false)
        _dueHour.value = p.getInt("due_hour", 9)
        _dueDays.value = p.getInt("due_days", 1)
        _dailyHour.value = p.getInt("daily_hour", 21)
        _weekly.value = p.getBoolean("weekly_summary", false)
        _budgetAlerts.value = p.getBoolean("budget_alerts", true)
        _bigExpense.value = p.getString("big_expense", null)?.toDoubleOrNull() ?: 0.0
        _lowBalance.value = p.getString("low_balance", null)?.toDoubleOrNull() ?: 0.0
        _backupFolder.value = p.getString("backup_folder", "") ?: ""
        _lastBackup.value = p.getString("last_backup", "") ?: ""
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

    /** Cuenta donde entra el sueldo y de donde salen los gastos por defecto (-1 = ninguna). */
    var mainAccountId: Long
        get() = _mainAccount.value
        set(v) { _mainAccount.value = v; sp?.edit()?.putLong("main_account", v)?.apply() }

    /** Tema: "dark" (oscuro), "light" (claro) o "system" (como el teléfono). */
    var theme: String
        get() = _theme.value
        set(v) { _theme.value = v; sp?.edit()?.putString("theme", v)?.apply() }

    /** Avisar el día antes de cada vencimiento (recurrentes y tarjetas). */
    var remindDue: Boolean
        get() = _remindDue.value
        set(v) { _remindDue.value = v; sp?.edit()?.putBoolean("remind_due", v)?.apply() }

    /** Aviso diario "¿Cargaste tus gastos de hoy?". */
    var remindDaily: Boolean
        get() = _remindDaily.value
        set(v) { _remindDaily.value = v; sp?.edit()?.putBoolean("remind_daily", v)?.apply() }

    /** Carpeta (URI de Storage Access Framework) donde va el backup semanal. Vacío = sin backup. */
    var backupFolder: String
        get() = _backupFolder.value
        set(v) { _backupFolder.value = v; sp?.edit()?.putString("backup_folder", v)?.apply() }

    /** Fecha y hora del último backup, para mostrar en Ajustes. */
    var lastBackup: String
        get() = _lastBackup.value
        set(v) { _lastBackup.value = v; sp?.edit()?.putString("last_backup", v)?.apply() }

    /** Hora del aviso de vencimientos (0-23). */
    var dueHour: Int
        get() = _dueHour.value
        set(v) { _dueHour.value = v; sp?.edit()?.putInt("due_hour", v)?.apply() }

    /** Con cuántos días de anticipación avisar los vencimientos (1 a 3). */
    var dueDaysBefore: Int
        get() = _dueDays.value
        set(v) { _dueDays.value = v; sp?.edit()?.putInt("due_days", v)?.apply() }

    /** Hora del aviso diario (0-23). */
    var dailyHour: Int
        get() = _dailyHour.value
        set(v) { _dailyHour.value = v; sp?.edit()?.putInt("daily_hour", v)?.apply() }

    /** Resumen semanal el domingo a la noche. */
    var weeklySummary: Boolean
        get() = _weekly.value
        set(v) { _weekly.value = v; sp?.edit()?.putBoolean("weekly_summary", v)?.apply() }

    /** Aviso al llegar al 80% y al 100% del presupuesto de una categoría. */
    var budgetAlerts: Boolean
        get() = _budgetAlerts.value
        set(v) { _budgetAlerts.value = v; sp?.edit()?.putBoolean("budget_alerts", v)?.apply() }

    /** Avisar cuando se carga un gasto mayor o igual a este monto en pesos (0 = no avisar). */
    var bigExpense: Double
        get() = _bigExpense.value
        set(v) { _bigExpense.value = v; sp?.edit()?.putString("big_expense", v.toString())?.apply() }

    /** Avisar cuando lo que queda del mes en la cuenta del sueldo baja de este monto (0 = no avisar). */
    var lowBalance: Double
        get() = _lowBalance.value
        set(v) { _lowBalance.value = v; sp?.edit()?.putString("low_balance", v.toString())?.apply() }

    /** Si ya se pidió una vez el permiso de notificaciones al abrir la app. */
    var askedNotifications: Boolean
        get() = sp?.getBoolean("asked_notif", false) ?: false
        set(v) { sp?.edit()?.putBoolean("asked_notif", v)?.apply() }

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
