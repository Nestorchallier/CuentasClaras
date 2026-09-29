package com.nestor.cuentasclaras.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.ui.MainViewModel
import com.nestor.cuentasclaras.ui.components.*
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Balances
import com.nestor.cuentasclaras.util.CardCycle
import com.nestor.cuentasclaras.util.Fmt
import com.nestor.cuentasclaras.util.Prefs

/** Detalle de una tarjeta de crédito: lo que hay que pagar, los próximos resúmenes y sus movimientos. */
@Composable
fun CardDetailScreen(vm: MainViewModel, cardId: Long, onBack: () -> Unit, onOpenTx: (Tx) -> Unit) {
    val all by vm.txs.collectAsState()
    val accs by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    val recs by vm.recurrings.collectAsState()
    val card = accs.firstOrNull { it.id == cardId }
    var editing by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf(false) }
    var open by remember { mutableIntStateOf(0) }

    if (card == null) {
        // La tarjeta se borró: volver.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val catMap = remember(cats) { cats.associateBy { it.id } }
    val debt = remember(all, accs) { Balances.of(accs, all)[card.id] ?: 0.0 }
    val toPay = remember(all, card) { CardCycle.toPay(card, all) }
    val upcoming = remember(all, card) { CardCycle.upcoming(card, all, 6) }

    GradientBg(C.TopNeutral) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                BackHeader("${card.emoji} ${card.name}", onBack) {
                    RoundIcon(Icons.Filled.Edit, "Editar tarjeta") { editing = true }
                }
            }
            item {
                CardBox {
                    Text("Deuda total de la tarjeta", color = C.Sub, fontSize = 14.sp)
                    Text(Fmt.money(-debt), color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("Incluye las cuotas que faltan.", color = C.Sub, fontSize = 12.sp)
                    if (card.closingDay == 0 || card.dueDay == 0) {
                        Spacer(Modifier.height(6.dp))
                        Text("Cargá el día de cierre y de vencimiento (lápiz arriba) para ver bien cada resumen.", color = C.Red, fontSize = 12.sp)
                    }
                }
            }
            if (toPay != null) {
                item {
                    CardBox {
                        Text("Resumen cerrado · vence ${Fmt.shortDate(toPay.due)}", color = C.Sub, fontSize = 14.sp)
                        Text(Fmt.money(toPay.total), color = C.Red, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton("Pagar tarjeta") { paying = true }
                    }
                }
            } else {
                item { PrimaryButton("Pagar tarjeta") { paying = true } }
            }
            item {
                Text("Próximos resúmenes", color = C.Sub, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            }
            items(upcoming.indices.toList()) { i ->
                val st = upcoming[i]
                TxGroup(
                    "Cierra ${Fmt.shortDate(st.closing)} · vence ${Fmt.shortDate(st.due)}",
                    Fmt.money(st.total)
                ) {
                    if (st.txs.isEmpty()) {
                        Text("Sin movimientos", color = C.Sub, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
                    } else if (open != i) {
                        Text(
                            "${st.txs.size} movimientos · tocá para ver",
                            color = C.Sub, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clickable { open = i }.padding(14.dp)
                        )
                    } else {
                        st.txs.forEachIndexed { k, t ->
                            if (k > 0) RowDivider()
                            TxRow(t, catMap[t.categoryId], card) { onOpenTx(t) }
                        }
                    }
                }
            }
        }
    }

    if (editing) {
        AccountDialog(
            initial = card,
            usage = all.count { it.accountId == card.id || it.toAccountId == card.id } + recs.count { it.accountId == card.id },
            targets = accs.filter { it.id != card.id },
            isMain = false,
            onDismiss = { editing = false },
            onSave = { a, _ -> vm.launch { vm.repo.saveAccount(a) }; editing = false },
            onDelete = { a, moveTo ->
                if (a.id == Prefs.mainAccountId) Prefs.mainAccountId = moveTo ?: -1L
                vm.launch { vm.repo.deleteAccount(a, moveTo) }
                editing = false
            }
        )
    }
    if (paying) {
        TransferDialog(
            initial = null, accounts = accs,
            onDismiss = { paying = false },
            onSave = { t -> vm.launch { vm.repo.saveTx(t) }; paying = false },
            onDelete = null,
            presetTo = card.id,
            presetAmount = toPay?.total
        )
    }
}
