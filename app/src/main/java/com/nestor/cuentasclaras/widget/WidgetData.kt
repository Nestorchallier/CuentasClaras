package com.nestor.cuentasclaras.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Category
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.repo
import com.nestor.cuentasclaras.ui.MainActivity
import com.nestor.cuentasclaras.util.Balances
import com.nestor.cuentasclaras.util.Dates
import com.nestor.cuentasclaras.util.Fmt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

data class RecentItem(val emoji: String, val title: String, val subtitle: String, val amount: Double, val income: Boolean)
data class CatSlice(val emoji: String, val name: String, val color: Long, val amount: Double)
data class AccItem(val emoji: String, val name: String, val balance: Double)

data class Snapshot(
    val monthName: String,
    val gastos: Double,
    val ingresos: Double,
    val hoy: Double,
    val budget: Double,
    val daily: List<Double>,
    val todayIndex: Int,
    val recent: List<RecentItem> = emptyList(),
    val slices: List<CatSlice> = emptyList(),
    val accounts: List<AccItem> = emptyList()
) {
    val totalBalance: Double get() = accounts.sumOf { it.balance }
}

object WidgetData {
    fun compute(txs: List<Tx>, cats: List<Category>, accs: List<Account>): Snapshot {
        val ym = YearMonth.now()
        val today = LocalDate.now()
        val catMap = cats.associateBy { it.id }
        val accMap = accs.associateBy { it.id }
        val month = Dates.inMonth(txs, ym)
        val gastos = month.filter { it.type == TxType.GASTO }
        val daily = DoubleArray(ym.lengthOfMonth())
        gastos.forEach { daily[Dates.day(it.date) - 1] += it.amount }

        val nowMs = System.currentTimeMillis()
        val recent = txs.asSequence().filter { it.date <= nowMs }.take(12).map { t ->
            val c = catMap[t.categoryId]
            val accText = if (t.isTransfer) "${accMap[t.accountId]?.name ?: ""} → ${accMap[t.toAccountId]?.name ?: ""}"
                else accMap[t.accountId]?.name ?: ""
            RecentItem(
                emoji = if (t.isTransfer) "⇄" else c?.emoji ?: "❔",
                title = t.note.ifBlank { if (t.isTransfer) "Transferencia" else c?.name ?: "Sin categoría" },
                subtitle = "${Dates.dayLabel(Dates.localDate(t.date))} · $accText",
                amount = t.amount,
                income = t.type == TxType.INGRESO
            )
        }.toList()

        val slices = gastos.groupBy { it.categoryId }.map { e ->
            val c = catMap[e.key]
            CatSlice(c?.emoji ?: "❔", c?.name ?: "Sin categoría", c?.color ?: 0xFF94A3B8, e.value.sumOf { it.amount })
        }.sortedByDescending { it.amount }

        val byId = Balances.of(accs, txs)
        val accounts = accs.map { a -> AccItem(a.emoji, a.name, byId[a.id] ?: 0.0) }

        return Snapshot(
            monthName = Fmt.monthName(ym.monthValue),
            gastos = gastos.sumOf { it.amount },
            ingresos = month.filter { it.type == TxType.INGRESO }.sumOf { it.amount },
            hoy = daily[today.dayOfMonth - 1],
            budget = cats.filter { it.type == TxType.GASTO }.sumOf { it.budget },
            daily = daily.toList(),
            todayIndex = today.dayOfMonth - 1,
            recent = recent,
            slices = slices,
            accounts = accounts
        )
    }

    suspend fun load(context: Context): Snapshot {
        val db = context.repo.db
        return compute(db.txs().all(), db.categories().all(), db.accounts().all())
    }

    fun observe(context: Context): Flow<Snapshot> {
        val db = context.repo.db
        return combine(db.txs().observe(), db.categories().observe(), db.accounts().observe()) { t, c, a -> compute(t, c, a) }
    }

    /** Gráfico de barras diario dibujado como imagen (Glance no tiene Canvas). */
    fun barsBitmap(values: List<Double>, highlight: Int, w: Int = 720, h: Int = 200): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val n = values.size.coerceAtLeast(1)
        val maxV = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val slot = w.toFloat() / n
        val bw = slot * 0.6f
        val r = bw / 2
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF }
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF1F5F7.toInt() }
        val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2DD4BF.toInt() }
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - bw) / 2
            c.drawRoundRect(RectF(x, 0f, x + bw, h.toFloat()), r, r, track)
            if (v > 0) {
                val bh = max(bw, (v / maxV * h).toFloat())
                c.drawRoundRect(RectF(x, h - bh, x + bw, h.toFloat()), r, r, if (i == highlight) hl else bar)
            }
        }
        return bmp
    }

    /** Dona por categoría como imagen. */
    fun donutBitmap(slices: List<CatSlice>, size: Int = 360): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val sw = size * 0.13f
        val rect = RectF(sw / 2, sw / 2, size - sw / 2, size - sw / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sw }
        val total = slices.sumOf { it.amount }
        if (total <= 0) {
            p.color = 0xFF323B42.toInt()
            c.drawArc(rect, 0f, 360f, false, p)
            return bmp
        }
        val gap = if (slices.size > 1) 4f else 0f
        var start = -90f
        slices.forEach { s ->
            val sweep = (360.0 * s.amount / total).toFloat()
            p.color = s.color.toInt()
            c.drawArc(rect, start + gap / 2, max(0.8f, sweep - gap), false, p)
            start += sweep
        }
        return bmp
    }
}

/** Intent que abre la hoja de carga rápida con el tipo preseleccionado. */
fun quickAddIntent(context: Context, type: String): Intent =
    Intent(context, QuickAddActivity::class.java).apply {
        data = Uri.parse("cuentasclaras://quickadd/$type")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

/** Intent que abre la app en una pestaña (0 Actividad, 1 Resumen, 2 Presupuesto, 3 General, 4 Cuentas). */
fun openAppIntent(context: Context, tab: Int): Intent =
    Intent(context, MainActivity::class.java).apply {
        data = Uri.parse("cuentasclaras://open/$tab")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
