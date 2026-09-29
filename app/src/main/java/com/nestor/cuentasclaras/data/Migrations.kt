package com.nestor.cuentasclaras.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migraciones de la base. Nunca usar fallbackToDestructiveMigration: borraría los datos de Nestor.
 * Cada una agrega columnas con valores por defecto, así lo cargado antes sigue igual.
 */
object Migrations {
    /** v2: transferencias entre cuentas (cuenta destino). */
    val M1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN toAccountId INTEGER")
        }
    }

    val ALL = arrayOf(M1_2)
}
