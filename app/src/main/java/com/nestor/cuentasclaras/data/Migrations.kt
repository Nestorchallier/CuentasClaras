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

    /** v3: tarjetas de crédito (cierre y vencimiento) y compras en cuotas. */
    val M2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN isCard INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE accounts ADD COLUMN closingDay INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE accounts ADD COLUMN dueDay INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE transactions ADD COLUMN installment INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE transactions ADD COLUMN installments INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE transactions ADD COLUMN groupId INTEGER")
        }
    }

    val ALL = arrayOf(M1_2, M2_3)
}
