package com.nestor.cuentasclaras.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nestor.cuentasclaras.capture.QuickText
import com.nestor.cuentasclaras.data.Tx
import com.nestor.cuentasclaras.data.TxType
import com.nestor.cuentasclaras.repo
import com.nestor.cuentasclaras.ui.screens.TxEditor
import com.nestor.cuentasclaras.ui.theme.AppTheme
import com.nestor.cuentasclaras.ui.theme.C
import kotlinx.coroutines.launch

/** Hoja inferior flotante que abren los widgets: cargar un movimiento sin entrar a la app. */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        C.applyTheme(this)
        val type = if (intent?.data?.lastPathSegment == TxType.INGRESO) TxType.INGRESO else TxType.GASTO
        val repo = applicationContext.repo
        // Desde un aviso del banco llegan monto y comercio: se completa la hoja y se adivina la categoría.
        val prefAmount = intent?.data?.getQueryParameter("amount")?.toDoubleOrNull()
        val prefNote = intent?.data?.getQueryParameter("note").orEmpty()
        val openedAt = System.currentTimeMillis()

        setContent {
            AppTheme {
                val cats by remember { repo.db.categories().observe() }.collectAsState(initial = emptyList())
                val accs by remember { repo.db.accounts().observe() }.collectAsState(initial = emptyList())
                val scope = rememberCoroutineScope()
                val prefill = remember(cats, accs) {
                    if (prefAmount == null || cats.isEmpty()) null
                    else {
                        val guess = QuickText.parse("$prefNote 1", cats, accs, forcedType = type)
                        Tx(
                            amount = prefAmount, type = type, categoryId = guess?.category?.id ?: 0L,
                            accountId = guess?.account?.id ?: 0L, date = openedAt, note = prefNote
                        )
                    }
                }
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(C.Bg)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                            .navigationBarsPadding().imePadding()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // key: la hoja se arma de nuevo cuando llegan las categorías (para usar la sugerencia).
                        key(prefill) { TxEditor(
                            initial = null, initialType = type, categories = cats, accounts = accs, compact = true,
                            prefill = prefill,
                            onSave = { t ->
                                scope.launch {
                                    repo.saveTx(t)
                                    Toast.makeText(this@QuickAddActivity, "Guardado ✓", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            },
                            onDelete = null,
                            onClose = { finish() }
                        ) }
                    }
                }
            }
        }
    }
}
