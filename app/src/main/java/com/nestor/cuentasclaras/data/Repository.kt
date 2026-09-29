package com.nestor.cuentasclaras.data

import android.content.Context
import androidx.room.withTransaction
import com.nestor.cuentasclaras.reminders.Alerts
import com.nestor.cuentasclaras.sync.CloudSync
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

    private suspend fun changed() {
        WidgetUpdater.refresh(context)
        CloudSync.requestPush(context) // página web (si hay cuenta conectada)
    }

    suspend fun seedIfEmpty() {
        if (db.categories().count() == 0) db.categories().insertAll(Defaults.categories)
        if (db.accounts().count() == 0) db.accounts().insertAll(Defaults.accounts)
    }

    // ---------- Movimientos ----------
    suspend fun saveTx(t: Tx) {
        // Para los avisos (presupuesto, gasto grande, poca plata) se compara antes y después de guardar.
        val before = if (t.type == TxType.GASTO) db.txs().all() else emptyList()
        if (t.id == 0L && t.installments > 1 && t.installment == 0) saveInstallments(t)
        else db.txs().upsert(t)
        changed()
        if (t.type == TxType.GASTO) runCatching { Alerts.afterSave(context, db, t, before) }
    }
    suspend fun deleteTx(t: Tx) { db.txs().delete(t); changed() }

    /** Borra todas las cuotas de una compra. */
    suspend fun deleteInstallments(groupId: Long) { db.txs().deleteGroup(groupId); changed() }

    /**
     * Compra en cuotas: [t].amount es el total. Se crea una cuota por mes desde la fecha de compra,
     * cada una con su parte (la última ajusta los centavos para que la suma dé exacto).
     */
    private suspend fun saveInstallments(t: Tx) {
        val n = t.installments
        val cents = Math.round(t.amount * 100)
        val each = cents / n
        val groupId = System.currentTimeMillis()
        val start = Dates.localDate(t.date)
        val time = Dates.localTime(t.date)
        val base = t.note.ifBlank { "Compra" }
        db.withTransaction {
            for (k in 1..n) {
                val part = if (k == n) cents - each * (n - 1) else each
                db.txs().upsert(
                    t.copy(
                        id = 0, amount = part / 100.0, installment = k, installments = n, groupId = groupId,
                        date = Dates.toMillis(start.plusMonths((k - 1).toLong()), time),
                        note = "$base (cuota $k/$n)"
                    )
                )
            }
        }
    }
    suspend fun clearTransactions() { db.txs().clear(); changed() }

    // ---------- Categorías y cuentas ----------
    suspend fun saveCategory(c: Category) { db.categories().upsert(c); changed() }
    /** Guarda la cuenta y devuelve su id (el nuevo, si se acaba de crear). */
    suspend fun saveAccount(a: Account): Long {
        val r = db.accounts().upsert(a)
        changed()
        return if (a.id == 0L) r else a.id
    }

    /** Borra la categoría. Si tiene movimientos o recurrentes, se pasan antes a [moveTo] para no dejarlos huérfanos. */
    suspend fun deleteCategory(c: Category, moveTo: Long?) {
        db.withTransaction {
            if (moveTo != null && moveTo != c.id) {
                db.txs().moveCategory(c.id, moveTo)
                db.recurrings().moveCategory(c.id, moveTo)
            }
            db.categories().delete(c)
        }
        changed()
    }

    /** Borra la cuenta. Si tiene movimientos o recurrentes, se pasan antes a [moveTo] para no dejarlos huérfanos. */
    suspend fun deleteAccount(a: Account, moveTo: Long?) {
        db.withTransaction {
            if (moveTo != null && moveTo != a.id) {
                db.txs().moveAccount(a.id, moveTo)
                db.txs().moveTransferTarget(a.id, moveTo)
                db.recurrings().moveAccount(a.id, moveTo)
            }
            db.accounts().delete(a)
        }
        changed()
    }

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
        val sb = StringBuilder("fecha,tipo,monto,categoria,cuenta,nota,cuenta_destino,moneda,monto_destino\n")
        db.txs().all().forEach { t ->
            val dt = Instant.ofEpochMilli(t.date).atZone(Dates.zone).toLocalDateTime()
            val row = listOf(
                fmt.format(dt),
                when (t.type) {
                    TxType.INGRESO -> "Ingreso"
                    TxType.TRANSFER -> "Transferencia"
                    else -> "Gasto"
                },
                BigDecimal.valueOf(t.amount).toPlainString(),
                if (t.isTransfer) "" else cats[t.categoryId]?.name ?: "",
                accs[t.accountId]?.name ?: "",
                t.note,
                t.toAccountId?.let { accs[it]?.name } ?: "",
                accs[t.accountId]?.currency ?: Currency.ARS,
                t.toAmount?.let { BigDecimal.valueOf(it).toPlainString() } ?: ""
            )
            sb.append(row.joinToString(",") { csv(it) }).append('\n')
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        File(dir, "cuentas_claras_${LocalDate.now()}.csv").apply { writeText("\uFEFF" + sb) }
    }

    /** Resultado de importar: cuántos se agregaron, cuántos ya estaban y cuántas filas no se pudieron leer. */
    data class ImportResult(val imported: Int, val duplicates: Int, val failed: Int)

    /**
     * Importa un CSV con el mismo formato que exporta (también el de Excel en español, con ';' y coma decimal).
     * Si un movimiento ya existe (misma fecha y hora, monto, tipo, categoría y nota) se saltea,
     * así reimportar el mismo archivo no duplica nada.
     */
    suspend fun importCsv(text: String): ImportResult = withContext(Dispatchers.IO) {
        val lines = text.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
        if (lines.size < 2) return@withContext ImportResult(0, 0, 0)
        val sep = detectSeparator(lines.first())
        val cats = db.categories().all().toMutableList()
        val accs = db.accounts().all().toMutableList()
        // Cuántas veces está cada movimiento en la base (así dos cafés iguales del mismo archivo no se pierden).
        val existing = db.txs().all().groupingBy { dupKey(it.date, it.amount, it.type, it.categoryId, it.note) }
            .eachCount().toMutableMap()
        var n = 0
        var dup = 0
        var failed = 0
        db.withTransaction {
            /** Busca la cuenta por nombre o la crea. */
            suspend fun account(name: String, currency: String = Currency.ARS): Long {
                accs.firstOrNull { it.name.equals(name, true) }?.let { return it.id }
                val a = Account(name = name, emoji = if (currency == Currency.USD) "💵" else "🏦", position = accs.size, currency = currency)
                val created = a.copy(id = db.accounts().upsert(a))
                accs.add(created)
                return created.id
            }
            for (line in lines.drop(1)) {
                val f = parseCsvLine(line, sep)
                val date = f.getOrNull(0)?.let { parseDate(it.trim()) }
                val amount = f.getOrNull(2)?.let { Fmt.parseOrNull(it) }
                if (f.size < 3 || date == null || amount == null) { failed++; continue }
                val typeText = f[1].trim().lowercase()
                val type = when {
                    typeText.startsWith("ing") -> TxType.INGRESO
                    typeText.startsWith("trans") -> TxType.TRANSFER
                    else -> TxType.GASTO
                }
                val toName = f.getOrElse(6) { "" }.trim()
                if (type == TxType.TRANSFER && toName.isBlank()) { failed++; continue }

                // Las transferencias no tienen categoría (categoryId = 0).
                val catId = if (type == TxType.TRANSFER) 0L else {
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
                    cat.id
                }
                val note = f.getOrElse(5) { "" }.trim()
                val key = dupKey(date, abs(amount), type, catId, note)
                val left = existing[key] ?: 0
                if (left > 0) { existing[key] = left - 1; dup++; continue }

                val currency = if (f.getOrElse(7) { "" }.trim().equals(Currency.USD, true)) Currency.USD else Currency.ARS
                val accId = account(f.getOrElse(4) { "" }.trim().ifBlank { "Efectivo" }, currency)
                val toId = if (type == TxType.TRANSFER) account(toName) else null
                db.txs().upsert(
                    Tx(amount = abs(amount), type = type, categoryId = catId, accountId = accId, date = date,
                        note = note, toAccountId = toId,
                        toAmount = if (type == TxType.TRANSFER) f.getOrNull(8)?.let { Fmt.parseOrNull(it) }?.let { abs(it) } else null)
                )
                n++
            }
        }
        changed()
        ImportResult(n, dup, failed)
    }

    /** El CSV exporta fecha al minuto y montos con centavos: se compara con esa misma precisión. */
    private fun dupKey(date: Long, amount: Double, type: String, categoryId: Long, note: String) =
        "${date / 60_000}|${Math.round(abs(amount) * 100)}|$type|$categoryId|${note.trim()}"

    /** Decide el separador una sola vez mirando el encabezado (Excel en español usa ';'). */
    private fun detectSeparator(header: String): Char {
        val counts = listOf(',', ';', '\t').associateWith { c -> header.count { it == c } }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun parseCsvLine(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
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
