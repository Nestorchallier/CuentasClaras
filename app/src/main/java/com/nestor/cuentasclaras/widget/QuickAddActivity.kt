package com.nestor.cuentasclaras.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val type = if (intent?.data?.lastPathSegment == TxType.INGRESO) TxType.INGRESO else TxType.GASTO
        val repo = applicationContext.repo

        setContent {
            AppTheme {
                val cats by remember { repo.db.categories().observe() }.collectAsState(initial = emptyList())
                val accs by remember { repo.db.accounts().observe() }.collectAsState(initial = emptyList())
                val scope = rememberCoroutineScope()
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
                        TxEditor(
                            initial = null, initialType = type, categories = cats, accounts = accs, compact = true,
                            onSave = { t ->
                                scope.launch {
                                    repo.saveTx(t)
                                    Toast.makeText(this@QuickAddActivity, "Guardado ✓", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            },
                            onDelete = null,
                            onClose = { finish() }
                        )
                    }
                }
            }
        }
    }
}
