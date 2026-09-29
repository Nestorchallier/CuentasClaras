package com.nestor.cuentasclaras.data

import android.content.Context
import androidx.room.withTransaction
import com.nestor.cuentasclaras.util.Dates
import com.nestor.cuentasclaras.util.Fmt
import com.nestor.cuentasclaras.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class Repository(private val context: Context, val db: AppDatabase) {

    private suspend fun changed() = WidgetUpdater.refresh(context)

    suspend fun seedIfEmpty() {
        if (db.categories().count() == 0) db.categories().insertAll(Defaults.categories)
        if (db.accounts().count() == 0) db.accounts().insertAll(Defaults.accounts)
    }

    // ---------- Movimientos ----------
    suspend fun saveTx(t: Tx) { db.txs().upsert(t); changed() }
    suspend fun deleteTx(t: Tx) { db.txs().delete(t); changed() }
    suspend fun clearTransactions() { db.txs().clear(); changed() }

    // ---------- Categorías y cuentas ----------
    suspend fun saveCategory(c: Category) { db.categories().upsert(c); changed() }
    suspend fun deleteCategory(c: Category) { db.categories().delete(c); changed() }
    suspend fun saveAccount(a: Account) { db.accounts().upsert(a); changed() }
    suspend fun deleteAccount(a: Account) { db.accounts().delete(a); changed() }

    // ---------- Recurrentes ----------
    suspend fun saveRecurring(r: Recurring) {
        recurringLock.withLock {
            // Si mientras se editaba ya se generó el mes, no perder esa marca (evita duplicar el movimiento).
            val stored = if (r.id != 0L) db.recurrings().get(r.id)?.lastGenerated.orEmpty() else ""
            db.recurrings().upsert(if (stored > r.lastGenerated) r.copy(lastGenerated = stored) else r)
        }
        processRecurrings()
        changed()
    }
    suspend fun deleteRecurring(r: Recurring) { db.recurrings().delete(r); changed() }

    /** Evita que dos llamadas simultáneas (inicio de la app, onResume, guardar recurrente) generen duplicados. */
    private val recurringLock = Mutex()

    /** Genera los movimientos recurrentes del mes actual cuyo día ya llegó. */
    suspend fun processRecurrings() {
        val created = recurringLock.withLock {
            db.withTransaction {
                val today = LocalDate.now()
                val ym = YearMonth.from(today)
                val key = ym.toString()
                var n = 0
                for (r in db.recurrings().all()) {
                    if (r.lastGenerated == key) continue
                    val day = r.dayOfMonth.coerceIn(1, ym.lengthOfMonth())
                    if (today.dayOfMonth >= day) {
                        db.txs().upsert(
                            Tx(
                                amount = r.amount, type = r.type, categoryId = r.categoryId, accountId = r.accountId,
                                date = Dates.toMillis(ym.atDay(day), LocalTime.of(9, 0)), note = r.name, recurringId = r.id
                            )
                        )
                        db.recurrings().upsert(r.copy(lastGenerated = key))
                        n++
                    }
                }
                n
            }
        }
        if (created > 0) changed()
    }

    // ---------- Exportar / importar CSV ----------
    suspend fun exportCsv(): File = withContext(Dispatchers.IO) {
        val cats = db.categories().all().associateBy { it.id }
        val accs = db.accounts().all().associateBy { it.id }
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val sb = StringBuilder("fecha,tipo,monto,categoria,cuenta,nota\n")
        db.txs().all().forEach { t ->
            val dt = Instant.ofEpochMilli(t.date).atZone(Dates.zone).toLocalDateTime()
            val row = listOf(
                fmt.format(dt),
                if (t.type == TxType.INGRESO) "Ingreso" else "Gasto",
                BigDecimal.valueOf(t.amount).toPlainString(),
                cats[t.categoryId]?.name ?: "",
                accs[t.accountId]?.name ?: "",
                t.note
            )
            sb.append(row.joinToString(",") { csv(it) }).append('\n')
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        File(dir, "cuentas_claras_${LocalDate.now()}.csv").apply { writeText("\uFEFF" + sb) }
    }

    /** Importa un CSV con el mismo formato que exporta. Devuelve la cantidad importada. */
    suspend fun importCsv(text: String): Int = withContext(Dispatchers.IO) {
        val lines = text.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
        if (lines.size < 2) return@withContext 0
        val cats = db.categories().all().toMutableList()
        val accs = db.accounts().all().toMutableList()
        var n = 0
        for (line in lines.drop(1)) {
            val f = parseCsvLine(line)
            if (f.size < 3) continue
            val date = parseDate(f[0].trim()) ?: continue
            val type = if (f[1].trim().lowercase().startsWith("ing")) TxType.INGRESO else TxType.GASTO
            val amount = Fmt.parseOrNull(f[2]) ?: continue

            val catName = f.getOrElse(3) { "" }.trim().ifBlank { "Otros" }
            var cat = cats.firstOrNull { it.name.equals(catName, true) && it.type == type }
            if (cat == null) {
                val c = Category(
                    name = catName, emoji = if (type == TxType.GASTO) "📦" else "💵",
                    color = Defaults.palette[cats.size % Defaults.palette.size], type = type, position = cats.size
                )
                cat = c.copy(id = db.categories().upsert(c))
                cats.add(cat)
            }
            val accName = f.getOrElse(4) { "" }.trim().ifBlank { "Efectivo" }
            var acc = accs.firstOrNull { it.name.equals(accName, true) }
            if (acc == null) {
                val a = Account(name = accName, emoji = "🏦", position = accs.size)
                acc = a.copy(id = db.accounts().upsert(a))
                accs.add(acc)
            }
            db.txs().upsert(
                Tx(amount = abs(amount), type = type, categoryId = cat.id, accountId = acc.id,
                    date = date, note = f.getOrElse(5) { "" }.trim())
            )
            n++
        }
        changed()
        n
    }

    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        val sep = if (!line.contains(',') && line.contains(';')) ';' else ','
        while (i < line.length) {
            val c = line[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ } else quoted = false
                } else sb.append(c)
            } else {
                when (c) {
                    '"' -> quoted = true
                    sep -> { out.add(sb.toString()); sb.clear() }
                    else -> sb.append(c)
                }
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    private fun parseDate(s: String): Long? {
        val patterns = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd/MM/yyyy HH:mm", "dd/MM/yyyy")
        for (p in patterns) {
            try {
                val f = DateTimeFormatter.ofPattern(p)
                return if (p.contains("HH")) {
                    LocalDateTime.parse(s, f).atZone(Dates.zone).toInstant().toEpochMilli()
                } else {
                    Dates.toMillis(LocalDate.parse(s, f), LocalTime.NOON)
                }
            } catch (_: Exception) { }
        }
        return null
    }
}
