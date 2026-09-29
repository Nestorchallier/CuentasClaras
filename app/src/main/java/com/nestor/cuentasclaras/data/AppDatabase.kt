package com.nestor.cuentasclaras.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Category::class, Account::class, Tx::class, Recurring::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun accounts(): AccountDao
    abstract fun txs(): TxDao
    abstract fun recurrings(): RecurringDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cuentas_claras.db"
                ).addMigrations(*Migrations.ALL).build().also { INSTANCE = it }
            }
    }
}
