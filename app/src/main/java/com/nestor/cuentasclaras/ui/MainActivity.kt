package com.nestor.cuentasclaras.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestor.cuentasclaras.ui.components.GradientBg
import com.nestor.cuentasclaras.ui.screens.*
import com.nestor.cuentasclaras.ui.theme.AppTheme
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.reminders.Reminders
import com.nestor.cuentasclaras.capture.TelegramBot
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.nestor.cuentasclaras.util.Prefs

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        C.applyTheme(this)
        // Los widgets abren la app en una pestaña: cuentasclaras://open/<n>
        val startTab = intent?.data?.takeIf { it.host == "open" }?.lastPathSegment?.toIntOrNull()?.coerceIn(0, 4) ?: 0
        setContent { AppTheme { AppRoot(vm, startTab) } }

        // Bot de Telegram: con la app abierta, esperar mensajes en vivo.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (!TelegramBot.enabled) { delay(5_000); continue }
                    try {
                        TelegramBot.poll(this@MainActivity, waitSeconds = 25)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        delay(15_000) // sin internet o token inválido: reintentar más tarde
                    }
                }
            }
        }

        // Recordatorios: en Android 13+ hay que pedir permiso para notificar (una sola vez).
        if (Build.VERSION.SDK_INT >= 33 && Prefs.remindDue && !Reminders.canNotify(this) && !Prefs.askedNotifications) {
            Prefs.askedNotifications = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // Por si cambió el mes con la app abierta: generar recurrentes pendientes
        vm.launch { vm.repo.processRecurrings() }
    }
}

/** Pantallas que se apilan sobre las pestañas */
sealed interface Route {
    data class Editor(val txId: Long?, val type: String) : Route
    data object Settings : Route
    data object Categories : Route
    data object Recurrings : Route
    data class CategoryDetail(val id: Long) : Route
    data class CardDetail(val id: Long) : Route
}

@Composable
fun AppRoot(vm: MainViewModel, startTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    val stack = remember { mutableStateListOf<Route>() }
    fun push(r: Route) { stack.add(r) }
    // removeAt(lastIndex) en vez de removeLast(): evita crash en Android < 15
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        when (val top = stack.lastOrNull()) {
            null -> {
                when (tab) {
                    0 -> ActividadScreen(
                        vm, onOpenTx = { push(Route.Editor(it.id, it.type)) }, onSettings = { push(Route.Settings) },
                        onOpenRecurrings = { push(Route.Recurrings) }
                    )
                    1 -> ResumenScreen(vm, onSettings = { push(Route.Settings) }, onOpenCategory = { push(Route.CategoryDetail(it)) })
                    2 -> PresupuestoScreen(vm, onEditBudgets = { push(Route.Categories) }, onSettings = { push(Route.Settings) })
                    3 -> GeneralScreen(vm, onSettings = { push(Route.Settings) }, onRecurrings = { push(Route.Recurrings) })
                    else -> CuentasScreen(vm, onSettings = { push(Route.Settings) }, onOpenCard = { push(Route.CardDetail(it)) })
                }
                // Botón + flotante
                Box(
                    Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                        .padding(end = 20.dp, bottom = 96.dp).size(62.dp).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(C.FabTop, C.FabBottom)))
                        .clickable { push(Route.Editor(null, vm.typeFilter)) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Add, "Nuevo movimiento", tint = C.Bg, modifier = Modifier.size(30.dp)) }
                BottomBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
            }

            is Route.Editor -> key(top) {
                val all by vm.txs.collectAsState()
                val cats by vm.categories.collectAsState()
                val accs by vm.accounts.collectAsState()
                val tx = top.txId?.let { id -> all.firstOrNull { it.id == id } }
                GradientBg(C.TopNeutral) {
                    TxEditor(
                        initial = tx, initialType = top.type, categories = cats, accounts = accs, compact = false,
                        onSave = { t -> vm.launch { vm.repo.saveTx(t) }; pop() },
                        onDelete = tx?.let { t -> { vm.launch { vm.repo.deleteTx(t) }; pop() } },
                        onDeleteAll = tx?.groupId?.let { g -> { vm.launch { vm.repo.deleteInstallments(g) }; pop() } },
                        onClose = { pop() },
                        modifier = Modifier.statusBarsPadding().navigationBarsPadding().imePadding()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }

            Route.Settings -> AjustesScreen(
                vm, onBack = { pop() },
                onCategories = { push(Route.Categories) },
                onRecurrings = { push(Route.Recurrings) }
            )
            Route.Categories -> CategoriasScreen(vm, onBack = { pop() })
            Route.Recurrings -> RecurrentesScreen(vm, onBack = { pop() })
            is Route.CategoryDetail -> CategoryDetailScreen(
                vm, top.id, onBack = { pop() },
                onOpenTx = { push(Route.Editor(it.id, it.type)) }
            )
            is Route.CardDetail -> CardDetailScreen(
                vm, top.id, onBack = { pop() },
                onOpenTx = { push(Route.Editor(it.id, it.type)) }
            )
        }
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items: List<Pair<ImageVector, String>> = listOf(
        Icons.Filled.Receipt to "Actividad",
        Icons.Filled.PieChart to "Resumen",
        Icons.Filled.AccountBalanceWallet to "Presupuesto",
        Icons.Filled.BarChart to "General",
        Icons.Filled.AccountBalance to "Cuentas"
    )
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth()
            .clip(RoundedCornerShape(32.dp)).background(C.Card.copy(alpha = 0.97f)).padding(6.dp)
    ) {
        items.forEachIndexed { i, (icon, label) ->
            val sel = i == tab
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(26.dp))
                    .background(if (sel) C.CardHi else Color.Transparent)
                    .clickable { onTab(i) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, label, tint = if (sel) C.Text else C.Sub, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(2.dp))
                Text(label, color = if (sel) C.Text else C.Sub, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
