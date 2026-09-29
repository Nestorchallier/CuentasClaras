package com.nestor.cuentasclaras.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.nestor.cuentasclaras.ui.MainViewModel
import com.nestor.cuentasclaras.ui.components.*
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Prefs
import com.nestor.cuentasclaras.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AjustesScreen(vm: MainViewModel, onBack: () -> Unit, onCategories: () -> Unit, onRecurrings: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var currencyDialog by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                }
                val r = vm.repo.importCsv(text)
                val msg = buildString {
                    append("Se importaron ${r.imported} movimientos")
                    if (r.duplicates > 0) append("\n${r.duplicates} ya estaban cargados (se saltearon)")
                    if (r.failed > 0) append("\n${r.failed} filas no se pudieron leer")
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(ctx, "No se pudo importar el archivo: ${e.message ?: "error desconocido"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val switchColors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = C.Text)

    GradientBg(C.TopNeutral) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 30.dp)
        ) {
            BackHeader("Ajustes", onBack)

            Section("General") {
                SettingRow(Icons.Filled.Category, "Categorías y presupuestos", onClick = onCategories, trailing = { Chevron() })
                RowDivider()
                SettingRow(Icons.Filled.Repeat, "Gastos recurrentes", onClick = onRecurrings, trailing = { Chevron() })
            }

            Section("Preferencias") {
                SettingRow(Icons.Filled.AttachMoney, "Símbolo de moneda", value = Prefs.currency, onClick = { currencyDialog = true })
                RowDivider()
                SettingRow(Icons.Filled.Savings, "Mostrar centavos", trailing = {
                    Switch(Prefs.showCents, { Prefs.showCents = it; scope.launch { WidgetUpdater.refresh(ctx) } }, colors = switchColors)
                })
            }

            Section("Privacidad") {
                SettingRow(Icons.Filled.VisibilityOff, "Ocultar saldos", trailing = {
                    Switch(Prefs.hideBalances, { Prefs.hideBalances = it; scope.launch { WidgetUpdater.refresh(ctx) } }, colors = switchColors)
                })
            }

            Section("Datos") {
                SettingRow(Icons.Filled.FileUpload, "Exportar datos (CSV)", onClick = {
                    scope.launch {
                        try {
                            val file = vm.repo.exportCsv()
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(send, "Exportar movimientos"))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "No se pudo exportar: ${e.message ?: "error desconocido"}", Toast.LENGTH_LONG).show()
                        }
                    }
                }, trailing = { Chevron() })
                RowDivider()
                SettingRow(Icons.Filled.FileDownload, "Importar datos (CSV)", onClick = {
                    importer.launch(arrayOf("text/*", "application/octet-stream", "application/vnd.ms-excel"))
                }, trailing = { Chevron() })
                RowDivider()
                SettingRow(Icons.Filled.DeleteForever, "Borrar movimientos", onClick = { confirmClear = true }, trailing = { Chevron() })
            }

            Section("Widgets") {
                Text(
                    "Mantené presionada la pantalla de inicio → Widgets → Cuentas Claras.\n\n" +
                        "• Carga rápida (4×1): botones para cargar un gasto o un ingreso en segundos.\n" +
                        "• Resumen del mes (4×2): gastos del mes, gráfico por día y presupuesto disponible.\n" +
                        "• Últimos movimientos (4×3): tus últimos gastos e ingresos y lo gastado en el mes.\n" +
                        "• Gastos por categoría (4×2): dona con la distribución de gastos del mes.\n" +
                        "• Saldos de cuentas (3×2): saldo total y de cada cuenta.",
                    color = C.Sub, fontSize = 14.sp, modifier = Modifier.padding(16.dp)
                )
            }

            Text(
                "Cuentas Claras 1.2 · tus datos se guardan solo en este teléfono. Exportá un CSV cada tanto como respaldo.",
                color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp, start = 4.dp)
            )
        }
    }

    if (currencyDialog) {
        var v by remember { mutableStateOf(Prefs.currency) }
        AlertDialog(
            onDismissRequest = { currencyDialog = false },
            containerColor = C.Card,
            title = { Text("Símbolo de moneda", color = C.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceRow(listOf(0L to "$", 1L to "ARS", 2L to "US$", 3L to "€"), null) { i ->
                        v = listOf("$", "ARS", "US$", "€")[i.toInt()]
                    }
                    Field(v, { v = it.take(5) }, "Símbolo")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.currency = v.ifBlank { "$" }
                    currencyDialog = false
                    scope.launch { WidgetUpdater.refresh(ctx) }
                }) { Text("Guardar", color = C.Teal) }
            },
            dismissButton = { TextButton(onClick = { currencyDialog = false }) { Text("Cancelar", color = C.Sub) } }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = C.Card,
            title = { Text("¿Borrar todos los movimientos?", color = C.Text) },
            text = { Text("Se eliminan todos los gastos e ingresos. Las categorías, cuentas y recurrentes se mantienen. No se puede deshacer.", color = C.Sub) },
            confirmButton = {
                TextButton(onClick = {
                    vm.launch { vm.repo.clearTransactions() }
                    confirmClear = false
                    Toast.makeText(ctx, "Movimientos borrados", Toast.LENGTH_SHORT).show()
                }) { Text("Borrar", color = C.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar", color = C.Sub) } }
        )
    }
}
