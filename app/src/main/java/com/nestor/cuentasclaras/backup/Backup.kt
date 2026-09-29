package com.nestor.cuentasclaras.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nestor.cuentasclaras.repo
import com.nestor.cuentasclaras.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Backup automático: una vez por semana guarda un CSV (el mismo de "Exportar") en la carpeta que
 * eligió el usuario. Si esa carpeta es de Google Drive o se sincroniza con Drive, queda en la nube.
 * Para recuperar los datos: Ajustes → Importar datos (CSV).
 */
object Backup {
    private const val WORK = "backup_semanal"

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (Prefs.backupFolder.isNotBlank()) {
            wm.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build()
            )
        } else wm.cancelUniqueWork(WORK)
    }

    /** Guarda el backup ahora. Devuelve el nombre del archivo creado. */
    suspend fun run(context: Context): String = withContext(Dispatchers.IO) {
        val tree = Uri.parse(Prefs.backupFolder.ifBlank { error("No elegiste una carpeta para el backup") })
        val csv = context.repo.exportCsv()
        val name = "cuentas_claras_backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + ".csv"
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(context.contentResolver, parent, "text/csv", name)
            ?: error("No se pudo crear el archivo en la carpeta elegida")
        context.contentResolver.openOutputStream(doc)?.use { out -> csv.inputStream().use { it.copyTo(out) } }
            ?: error("No se pudo escribir el archivo")
        Prefs.lastBackup = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        name
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        Backup.run(applicationContext)
        Result.success()
    } catch (e: Exception) {
        // Carpeta borrada o sin permiso: se reintenta la semana que viene.
        Result.failure()
    }
}
