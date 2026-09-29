package com.nestor.cuentasclaras.sync

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Category
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.repo
import com.nestor.cuentasclaras.util.Money
import com.nestor.cuentasclaras.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Sincronización con Firebase para ver y cargar movimientos desde la página web.
 *
 * - Celular → nube: después de cada cambio se suben SOLO los documentos que cambiaron
 *   (se guarda una "huella" de lo último subido en sync_state.txt).
 * - Web → celular: la página deja los movimientos nuevos en users/{uid}/inbox; la app los trae
 *   (al instante si está abierta, o cada 15 minutos con WorkManager) y los borra del inbox.
 *
 * Todo vive bajo users/{uid}/..., y las reglas de Firestore solo dejan leer/escribir al dueño.
 */
object CloudSync {
    /** Datos del proyecto de Firebase de Nestor (no son secretos: la seguridad la dan el login y las reglas). */
    private val OPTIONS = FirebaseOptions.Builder()
        .setApiKey("AIzaSyAHoD9O08ssJbYRupcN2wT6oC74cu9-IqE")
        .setApplicationId("1:754676898835:web:9fb61fa07eeea01ffd33e6")
        .setProjectId("cuentasclaras-55dd0")
        .setStorageBucket("cuentasclaras-55dd0.firebasestorage.app")
        .setGcmSenderId("754676898835")
        .build()

    private const val WORK = "sincronizar_web"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pushLock = Mutex()
    private val inboxLock = Mutex()
    private var pending: Job? = null
    private var listener: ListenerRegistration? = null
    private var ready = false

    /** Mail de la cuenta conectada (o "" si no hay). Es state de Compose para que Ajustes se actualice solo. */
    val email = mutableStateOf("")
    /** Último estado para mostrar en Ajustes ("Sincronizado 14:32", errores, etc.). */
    val status = mutableStateOf("")

    fun init(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context, OPTIONS)
            ready = true
            email.value = FirebaseAuth.getInstance().currentUser?.email ?: ""
            if (email.value.isNotBlank()) {
                startListening(context)
                schedule(context)
                requestPush(context)
            }
        } catch (e: Exception) {
            status.value = "No se pudo iniciar la sincronización: ${e.message}"
        }
    }

    private fun uid(): String? = if (ready) FirebaseAuth.getInstance().currentUser?.uid else null
    private fun db() = FirebaseFirestore.getInstance()

    suspend fun signIn(context: Context, mail: String, password: String) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(mail.trim(), password).await()
        email.value = FirebaseAuth.getInstance().currentUser?.email ?: mail.trim()
        stateFile(context).delete() // en una cuenta nueva se sube todo de cero
        startListening(context)
        schedule(context)
        push(context)
        pullInbox(context)
    }

    fun signOut(context: Context) {
        listener?.remove()
        listener = null
        if (ready) FirebaseAuth.getInstance().signOut()
        email.value = ""
        status.value = ""
        stateFile(context).delete()
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    private fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    /** Pide subir los cambios. Espera un momento para juntar varios cambios seguidos en una sola subida. */
    fun requestPush(context: Context) {
        if (uid() == null) return
        pending?.cancel()
        pending = scope.launch {
            delay(1500)
            try {
                push(context)
            } catch (e: Exception) {
                status.value = "Error al sincronizar: ${e.message}"
            }
        }
    }

    // ---------- Celular → nube ----------

    private fun stateFile(context: Context) = File(context.filesDir, "sync_state.txt")

    private fun loadState(context: Context): MutableMap<String, Int> {
        val f = stateFile(context)
        if (!f.exists()) return mutableMapOf()
        return f.readLines().mapNotNull { line ->
            val i = line.lastIndexOf('=')
            if (i <= 0) null else line.substring(0, i) to (line.substring(i + 1).toIntOrNull() ?: return@mapNotNull null)
        }.toMap().toMutableMap()
    }

    private fun saveState(context: Context, state: Map<String, Int>) {
        stateFile(context).writeText(state.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }

    private fun txMap(t: Tx): Map<String, Any?> = mapOf(
        "id" to t.id, "amount" to t.amount, "type" to t.type, "categoryId" to t.categoryId,
        "accountId" to t.accountId, "date" to t.date, "note" to t.note, "recurringId" to t.recurringId,
        "toAccountId" to t.toAccountId, "toAmount" to t.toAmount,
        "installment" to t.installment, "installments" to t.installments
    )

    private fun accMap(a: Account): Map<String, Any?> = mapOf(
        "id" to a.id, "name" to a.name, "emoji" to a.emoji, "initialBalance" to a.initialBalance,
        "position" to a.position, "isCard" to a.isCard, "currency" to a.currency
    )

    private fun catMap(c: Category): Map<String, Any?> = mapOf(
        "id" to c.id, "name" to c.name, "emoji" to c.emoji, "color" to c.color, "type" to c.type,
        "budget" to c.budget, "position" to c.position
    )

    /** Sube lo que cambió desde la última vez y borra en la nube lo que se borró en el celular. */
    suspend fun push(context: Context) {
        val uid = uid() ?: return
        pushLock.withLock {
            val repo = context.repo
            val docs = HashMap<String, Map<String, Any?>>()
            repo.db.accounts().all().forEach { docs["accounts/${it.id}"] = accMap(it) }
            repo.db.categories().all().forEach { docs["categories/${it.id}"] = catMap(it) }
            repo.db.txs().all().forEach { docs["txs/${it.id}"] = txMap(it) }
            docs["meta/info"] = mapOf(
                "usdToArs" to Money.usdToArs, "rateName" to Money.typeName, "currency" to Prefs.currency,
                "mainAccountId" to Prefs.mainAccountId
            )

            val old = loadState(context)
            val changed = docs.filter { (k, v) -> old[k] != v.hashCode() }
            val deleted = old.keys - docs.keys
            if (changed.isEmpty() && deleted.isEmpty()) return

            val root = db().collection("users").document(uid)
            // Firestore acepta hasta 500 operaciones por lote.
            val ops = changed.entries.map { it.key to it.value } + deleted.map { it to null }
            ops.chunked(450).forEach { chunk ->
                val batch = db().batch()
                chunk.forEach { (key, data) ->
                    val (col, id) = key.split("/", limit = 2).let { it[0] to it[1] }
                    val ref = root.collection(col).document(id)
                    if (data == null) batch.delete(ref) else batch.set(ref, data)
                }
                batch.commit().await()
            }
            root.set(mapOf("updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()

            val newState = old.toMutableMap()
            changed.forEach { (k, v) -> newState[k] = v.hashCode() }
            deleted.forEach { newState.remove(it) }
            saveState(context, newState)
            status.value = "Sincronizado " + java.time.LocalTime.now().withNano(0).withSecond(0)
        }
    }

    // ---------- Web → celular ----------

    private fun startListening(context: Context) {
        val uid = uid() ?: return
        listener?.remove()
        listener = db().collection("users").document(uid).collection("inbox")
            .addSnapshotListener { snap, _ ->
                if (snap != null && !snap.isEmpty) scope.launch { runCatching { process(context, snap.documents) } }
            }
    }

    /** Trae los movimientos cargados desde la web (lo usa el trabajo de cada 15 minutos). */
    suspend fun pullInbox(context: Context) {
        val uid = uid() ?: return
        val snap = db().collection("users").document(uid).collection("inbox").get().await()
        process(context, snap.documents)
    }

    private suspend fun process(context: Context, docs: List<DocumentSnapshot>) = inboxLock.withLock {
        val repo = context.repo
        val accs = repo.db.accounts().all().map { it.id }.toSet()
        val cats = repo.db.categories().all().map { it.id }.toSet()
        for (d in docs) {
            val amount = d.getDouble("amount") ?: 0.0
            val type = d.getString("type")
            val accountId = d.getLong("accountId") ?: -1L
            val categoryId = d.getLong("categoryId") ?: -1L
            val date = d.getLong("date") ?: System.currentTimeMillis()
            val ok = amount > 0 && (type == TxType.GASTO || type == TxType.INGRESO) && accountId in accs && categoryId in cats
            if (ok) {
                // Si ya se procesó (el aviso de cambios puede llegar repetido), se saltea.
                val stillThere = runCatching { d.reference.get().await().exists() }.getOrDefault(false)
                if (!stillThere) continue
                // Sin esperar la confirmación: si no hay internet, Firestore lo borra cuando vuelve.
                d.reference.delete()
                repo.saveTx(
                    Tx(amount = amount, type = type!!, categoryId = categoryId, accountId = accountId, date = date,
                        note = d.getString("note")?.trim().orEmpty())
                )
            } else {
                // Datos inválidos (ej. cuenta borrada): se descarta para no trabarse.
                d.reference.delete()
            }
        }
    }
}

/** Cada 15 minutos: trae lo cargado en la web y sube lo pendiente. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        CloudSync.pullInbox(applicationContext)
        CloudSync.push(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
