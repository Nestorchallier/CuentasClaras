package com.nestor.cuentasclaras.util

import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType
import java.time.LocalDate
import java.time.YearMonth

/** Resumen de tarjeta: lo comprado entre el cierre anterior (excluido) y [closing] (incluido). */
data class Statement(val closing: LocalDate, val due: LocalDate, val txs: List<Tx>) {
    /** Gastos menos devoluciones (los pagos a la tarjeta son transferencias y no cuentan acá). */
    val total: Double get() = txs.sumOf { if (it.type == TxType.INGRESO) -it.amount else it.amount }
}

/** Cálculo de resúmenes de tarjeta según el día de cierre y de vencimiento. */
object CardCycle {
    private fun day(ym: YearMonth, d: Int) = ym.atDay(d.coerceIn(1, ym.lengthOfMonth()))

    fun closingOf(card: Account, ym: YearMonth): LocalDate = day(ym, card.closingDay.takeIf { it > 0 } ?: 31)

    /** Vence el mes siguiente al cierre, salvo que el día de vencimiento sea posterior al de cierre. */
    fun dueOf(card: Account, closingMonth: YearMonth): LocalDate {
        val c = card.closingDay.takeIf { it > 0 } ?: 31
        val d = card.dueDay.takeIf { it > 0 } ?: 10
        return day(if (d > c) closingMonth else closingMonth.plusMonths(1), d)
    }

    /** Mes de cierre del resumen en el que entra una compra de fecha [date]. */
    fun closingMonthFor(card: Account, date: LocalDate): YearMonth {
        val ym = YearMonth.from(date)
        return if (!date.isAfter(closingOf(card, ym))) ym else ym.plusMonths(1)
    }

    fun statement(card: Account, closingMonth: YearMonth, txs: List<Tx>): Statement {
        val close = closingOf(card, closingMonth)
        val prevClose = closingOf(card, closingMonth.minusMonths(1))
        val list = txs.filter { t ->
            t.accountId == card.id && t.type != TxType.TRANSFER &&
                Dates.localDate(t.date).let { it.isAfter(prevClose) && !it.isAfter(close) }
        }.sortedBy { it.date }
        return Statement(close, dueOf(card, closingMonth), list)
    }

    /** Resumen ya cerrado que todavía no venció (el que hay que pagar ahora), o null. */
    fun toPay(card: Account, txs: List<Tx>): Statement? {
        val today = LocalDate.now()
        val last = statement(card, closingMonthFor(card, today).minusMonths(1), txs)
        return last.takeIf { !today.isAfter(it.due) && it.total > 0 }
    }

    /** El resumen que todavía no cerró (o cierra hoy) y los [count] - 1 siguientes. */
    fun upcoming(card: Account, txs: List<Tx>, count: Int = 6): List<Statement> {
        val first = closingMonthFor(card, LocalDate.now())
        return (0 until count).map { statement(card, first.plusMonths(it.toLong()), txs) }
    }
}
