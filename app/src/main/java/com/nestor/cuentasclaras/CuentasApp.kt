package com.nestor.cuentasclaras

import android.app.Application
import android.content.Context
import com.nestor.cuentasclaras.backup.Backup
import com.nestor.cuentasclaras.data.AppDatabase
import com.nestor.cuentasclaras.data.Repository
import com.nestor.cuentasclaras.reminders.Reminders
import com.nestor.cuentasclaras.sync.CloudSync
import com.nestor.cuentasclaras.util.Money
import com.nestor.cuentasclaras.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CuentasApp : Application() {
    lateinit var repo: Repository
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Money.init(this)
        repo = Repository(this, AppDatabase.get(this))
        Reminders.schedule(this)
        Backup.schedule(this)
        CloudSync.init(this)
        appScope.launch {
            repo.seedIfEmpty()
            repo.processRecurrings()
            // Cotización del dólar (si no hay internet, se usa la última guardada).
            if (repo.db.accounts().all().any { it.isUsd }) Money.refresh()
        }
    }
}

/** Acceso rápido al repositorio desde cualquier Context (actividades, widgets). */
val Context.repo: Repository get() = (applicationContext as CuentasApp).repo
