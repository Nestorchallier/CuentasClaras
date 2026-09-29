package com.nestor.cuentasclaras.util

import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Tx
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Fmt {
    private val AR = Locale("es", "AR")
    private val SYM = DecimalFormatSymbols(AR)
    private val MONTHS = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    fun monthName(m: Int) = MONTHS[m - 1]
    fun monthShort(m: Int) = MONTHS[m - 1].take(3)

    fun monthLabel(ym: YearMonth): String {
        val now = YearMonth.now()
        return when (ym) {
            now -> "Este mes"
            now.minusMonths(1) -> "Mes pasado"
            now.plusMonths(1) -> "Mes que viene"
            else -> "${monthName(ym.monthValue)} ${ym.year}"
        }
    }

    /** $ 1.093.500 (formato argentino) */
    fun money(v: Double): String = format(v, Prefs.currency)

    /** Monto de una cuenta: US$ si la cuenta es en dólares (siempre con centavos). */
    fun money(v: Double, account: Account?): String =
        if (account?.isUsd == true) format(v, "US$", cents = true) else money(v)

    private fun format(v: Double, symbol: String, cents: Boolean = Prefs.showCents): String {
        if (Prefs.hideBalances) return "$symbol •••"
        val df = DecimalFormat(if (cents) "#,##0.00" else "#,##0", SYM)
        val s = df.format(abs(v))
        val neg = v < 0 && s.any { it in '1'..'9' }
        return (if (neg) "-" else "") + symbol + " " + s
    }

    /** 39,1k / 1,3M para ejes de gráficos */
    fun compact(v: Double): String {
        val a = abs(v)
        return when {
            a >= 1_000_000 -> one(v / 1_000_000) + "M"
            a >= 1_000 -> one(v / 1_000) + "k"
            else -> v.roundToLong().toString()
        }
    }

    private fun one(d: Double): String = String.format(AR, "%.1f", d).removeSuffix(",0")

    fun pct(v: Double): String = String.format(AR, "%.1f%%", v)

    fun groupInt(s: String): String = s.toLongOrNull()?.let { DecimalFormat("#,##0", SYM).format(it) } ?: s

    /** Número editable: 12500 / 12,5 */
    fun plain(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString()
        else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.').replace('.', ',')

    /** Acepta "12.500,50", "12500.5", "12500", "250.000", "$ 1.500" (0 si no se entiende). */
    fun parse(s: String): Double = parseOrNull(s) ?: 0.0

    /**
     * Única función para leer montos escritos por el usuario o importados de un CSV.
     * - La coma es decimal: "1.500,5" = 1500,5.
     * - Un punto seguido de exactamente 3 dígitos es separador de miles: "250.000" = 250000.
     * - Cualquier otro punto es decimal: "1500.5" (así exporta la app) = 1500,5.
     * - Si hay punto y coma juntos, el último que aparece es el decimal: "1,500.50" = 1500,5.
     */
    fun parseOrNull(raw: String): Double? {
        var t = raw.trim().filter { !it.isWhitespace() && it != '\u00A0' && it != '$' }
            .removePrefix("US").removePrefix("ARS")
        if (t.isEmpty()) return null
        val neg = t.startsWith("-")
        t = t.removePrefix("-").removePrefix("+")
        val lastDot = t.lastIndexOf('.')
        val lastComma = t.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 -> {
                val dec = if (lastComma > lastDot) ',' else '.'
                val thousands = if (dec == ',') '.' else ','
                t.replace(thousands.toString(), "").replace(dec, '.')
            }
            lastComma >= 0 ->
                if (t.count { it == ',' } > 1) t.replace(",", "") else t.replace(',', '.')
            lastDot >= 0 -> {
                val dots = t.count { it == '.' }
                val afterLast = t.length - lastDot - 1
                if (dots > 1 || afterLast == 3) t.replace(".", "") else t
            }
            else -> t
        }
        if (normalized.isEmpty() || !normalized.all { it.isDigit() || it == '.' }) return null
        val v = normalized.toDoubleOrNull() ?: return null
        return if (neg) -v else v
    }

    fun shortDate(d: LocalDate) = "${d.dayOfMonth}/${d.monthValue}"
}

/** Recurrentes que todavía no se registraron en un mes (para mostrarlos como "programados"). */
object Projection {
    fun recurrings(month: YearMonth, recs: List<com.nestor.cuentasclaras.data.Recurring>): List<Tx> {
        val now = YearMonth.now()
        if (month < now) return emptyList()
        val key = month.toString()
        return recs.filter { month > now || it.lastGenerated != key }.map { r ->
            val day = r.dayOfMonth.coerceIn(1, month.lengthOfMonth())
            Tx(
                id = -r.id, amount = r.amount, type = r.type, categoryId = r.categoryId, accountId = r.accountId,
                date = Dates.toMillis(month.atDay(day), LocalTime.of(9, 0)), note = r.name, recurringId = r.id
            )
        }
    }
}

object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()
    private val DAYS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

    fun range(ym: YearMonth): Pair<Long, Long> =
        ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun inMonth(list: List<Tx>, ym: YearMonth): List<Tx> {
        val (a, b) = range(ym)
        return list.filter { it.date in a until b }
    }

    fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun localTime(ms: Long): LocalTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()
    fun day(ms: Long): Int = localDate(ms).dayOfMonth
    fun toMillis(d: LocalDate, t: LocalTime): Long = d.atTime(t).atZone(zone).toInstant().toEpochMilli()

    fun dayLabel(d: LocalDate): String {
        val t = LocalDate.now()
        return when (d) {
            t -> "Hoy"
            t.minusDays(1) -> "Ayer"
            t.plusDays(1) -> "Mañana"
            else -> "${DAYS[d.dayOfWeek.value - 1]} ${d.dayOfMonth} de ${Fmt.monthName(d.monthValue).lowercase()}"
        }
    }
}
