package com.nestor.cuentasclaras.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = app.repo

    val txs = repo.db.txs().observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories = repo.db.categories().observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val accounts = repo.db.accounts().observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recurrings = repo.db.recurrings().observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Filtros compartidos entre pantallas */
    var month by mutableStateOf(YearMonth.now())
    var typeFilter by mutableStateOf(TxType.GASTO)
    var accountFilter by mutableStateOf<Long?>(null)

    fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
