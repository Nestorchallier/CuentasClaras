package com.nestor.cuentasclaras.reminders

import android.content.Context
import com.nestor.cuentasclaras.data.AppDatabase
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.util.Dates
import com.nestor.cuentasclaras.util.Fmt
import com.nestor.cuentasclaras.util.Money
import com.nestor.cuentasclaras.util.Prefs
import com.nestor.cuentasclaras.util.Projection
import java.time.YearMonth

/**
 * Avisos que salen en el momento de cargar un gasto (desde la app o desde el widget):
 * - Presupuesto de la categoría al 80% y al 100%.
 * - Gasto grande (mayor o igual al monto elegido).
 * - Poca plata: lo que queda del mes en la cuenta del sueldo baja del monto elegido.
 * Cada aviso sale solo cuando se cruza el límite (no en cada gasto posterior).
 */
object Alerts {
    suspend fun afterSave(context: Context, db: AppDatabase, saved: Tx, before: List<Tx>) {
        if (saved.type != TxType.GASTO) return
        val ym = YearMonth.now()
        if (YearMonth.from(Dates.localDate(saved.date)) != ym) return
        val accs = db.accounts().all()
        val accMap = accs.associateBy { it.id }
        val after = db.txs().all()

        // Gasto grande
        val amountArs = Money.toArs(saved.amount, accMap[saved.accountId])
        if (Prefs.bigExpense > 0 && amountArs >= Prefs.bigExpense) {
            Reminders.notify(
                context, 2001, "Gasto grande: ${Fmt.money(saved.amount, accMap[saved.accountId])}",
                saved.note.ifBlank { "Cargaste un gasto mayor a ${Fmt.money(Prefs.bigExpense)}." },
                tab = 0, channel = Reminders.Channel.ALERTS
            )
        }

        // Presupuesto de la categoría
        if (Prefs.budgetAlerts) {
            val cat = db.categories().all().firstOrNull { it.id == saved.categoryId }
            if (cat != null && cat.budget > 0) {
                fun spent(list: List<Tx>) = Money.sumArs(
                    Dates.inMonth(list, ym).filter { it.type == TxType.GASTO && it.categoryId == cat.id }, accMap
                )
                val pctBefore = spent(before) / cat.budget
                val pctAfter = spent(after) / cat.budget
                val msg = when {
                    pctBefore < 1.0 && pctAfter >= 1.0 -> "Te pasaste del presupuesto de ${cat.emoji} ${cat.name}"
                    pctBefore < 0.8 && pctAfter >= 0.8 -> "Ya usaste el 80% del presupuesto de ${cat.emoji} ${cat.name}"
                    else -> null
                }
                if (msg != null) {
                    Reminders.notify(
                        context, 2100 + cat.id.toInt(), msg,
                        "Llevás ${Fmt.money(spent(after))} de ${Fmt.money(cat.budget)} este mes.",
                        tab = 2, channel = Reminders.Channel.ALERTS
                    )
                }
            }
        }

        // Poca plata en la cuenta del sueldo
        val main = accs.firstOrNull { it.id == Prefs.mainAccountId }
        if (Prefs.lowBalance > 0 && main != null && saved.accountId == main.id) {
            val recs = db.recurrings().all()
            fun left(list: List<Tx>): Double {
                val month = Dates.inMonth(list, ym).filter { it.accountId == main.id }
                val cobrado = month.filter { it.type == TxType.INGRESO }.sumOf { it.amount }
                val gastado = month.filter { it.type == TxType.GASTO }.sumOf { it.amount }
                val fijos = Projection.recurrings(ym, recs).filter { it.type == TxType.GASTO && it.accountId == main.id }.sumOf { it.amount }
                return Money.toArs(cobrado - gastado - fijos, main)
            }
            val leftBefore = left(before)
            val leftAfter = left(after)
            if (leftBefore >= Prefs.lowBalance && leftAfter < Prefs.lowBalance) {
                Reminders.notify(
                    context, 2002, "Te queda poca plata este mes",
                    "En ${main.emoji} ${main.name} te quedan ${Fmt.money(leftAfter)} hasta fin de mes.",
                    tab = 4, channel = Reminders.Channel.ALERTS
                )
            }
        }
    }
}
