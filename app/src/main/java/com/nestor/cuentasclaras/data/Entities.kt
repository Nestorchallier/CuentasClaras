package com.nestor.cuentasclaras.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object TxType {
    const val GASTO = "GASTO"
    const val INGRESO = "INGRESO"
    /** Pasa plata de [Tx.accountId] a [Tx.toAccountId]. No cuenta como gasto ni como ingreso. */
    const val TRANSFER = "TRANSFER"
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val color: Long,
    val type: String,
    /** Presupuesto mensual (0 = sin presupuesto). Solo aplica a gastos. */
    val budget: Double = 0.0,
    val position: Int = 0
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val initialBalance: Double = 0.0,
    val position: Int = 0
)

@Entity(
    tableName = "transactions",
    indices = [Index("date"), Index("categoryId"), Index("accountId")]
)
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: String,
    val categoryId: Long,
    val accountId: Long,
    /** Epoch millis */
    val date: Long,
    val note: String = "",
    /** Si fue generado por un gasto recurrente */
    val recurringId: Long? = null,
    /** Solo en transferencias: cuenta que recibe la plata (categoryId queda en 0). */
    val toAccountId: Long? = null
) {
    val isTransfer: Boolean get() = type == TxType.TRANSFER
}

@Entity(tableName = "recurrings")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val type: String,
    val categoryId: Long,
    val accountId: Long,
    val dayOfMonth: Int,
    /** Último mes generado, formato yyyy-MM */
    val lastGenerated: String = ""
)
