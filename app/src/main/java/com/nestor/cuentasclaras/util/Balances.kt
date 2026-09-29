package com.nestor.cuentasclaras.util

import com.nestor.cuentasclaras.data.Account
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType

/** Cálculo de saldos en un solo lugar (pantalla Cuentas, General y widgets). */
object Balances {
    /** Saldo de cada cuenta = saldo inicial + ingresos − gastos ± transferencias. */
    fun of(accounts: List<Account>, txs: List<Tx>): Map<Long, Double> {
        val m = HashMap<Long, Double>()
        accounts.forEach { m[it.id] = it.initialBalance }
        for (t in txs) {
            when (t.type) {
                TxType.INGRESO -> m.computeIfPresent(t.accountId) { _, v -> v + t.amount }
                TxType.GASTO -> m.computeIfPresent(t.accountId) { _, v -> v - t.amount }
                TxType.TRANSFER -> {
                    m.computeIfPresent(t.accountId) { _, v -> v - t.amount }
                    t.toAccountId?.let { to -> m.computeIfPresent(to) { _, v -> v + t.amount } }
                }
            }
        }
        return m
    }
}
