package com.nestor.cuentasclaras.data

import androidx.room.ColumnInfo
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
    val position: Int = 0,
    /** Tarjeta de crédito: su saldo es lo que se debe; se paga con una transferencia desde otra cuenta. */
    @ColumnInfo(defaultValue = "0") val isCard: Boolean = false,
    /** Día del mes en que cierra el resumen (solo tarjetas). */
    @ColumnInfo(defaultValue = "0") val closingDay: Int = 0,
    /** Día del mes en que vence el resumen (solo tarjetas). */
    @ColumnInfo(defaultValue = "0") val dueDay: Int = 0
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
    val toAccountId: Long? = null,
    /** Compra en cuotas: número de esta cuota (1..installments). 0 = no es cuota. */
    @ColumnInfo(defaultValue = "0") val installment: Int = 0,
    /**
     * Cantidad total de cuotas. Al guardar un movimiento nuevo con installments > 1 e installment = 0,
     * el repositorio lo divide en esa cantidad de cuotas mensuales.
     */
    @ColumnInfo(defaultValue = "0") val installments: Int = 0,
    /** Identifica todas las cuotas de una misma compra. */
    val groupId: Long? = null
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
