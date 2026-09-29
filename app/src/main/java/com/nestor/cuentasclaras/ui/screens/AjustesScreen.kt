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
import com.nestor.cuentasclaras.backup.Backup
import com.nestor.cuentasclaras.reminders.Reminders
import com.nestor.cuentasclaras.sync.CloudSync
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nestor.cuentasclaras.util.Fmt
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
    var amountDialog by remember { mutableStateOf<String?>(null) }
    var syncMail by remember { mutableStateOf("") }
    var syncPass by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }

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

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(ctx, "Sin permiso de notificaciones no se pueden mostrar los recordatorios", Toast.LENGTH_LONG).show()
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                // Guardar el permiso para poder escribir ahí aunque se reinicie el teléfono.
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                Prefs.backupFolder = uri.toString()
                Backup.schedule(ctx)
                scope.launch {
                    try {
                        Backup.run(ctx)
                        Toast.makeText(ctx, "Listo: backup guardado. Se va a repetir cada semana.", Toast.LENGTH_LONG).show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "No se pudo guardar el backup: ${e.message ?: "error desconocido"}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "No se pudo usar esa carpeta: ${e.message ?: "error desconocido"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun reminderChanged(hourChanged: Boolean = false) {
        Reminders.schedule(ctx, changed = hourChanged)
        if ((Prefs.remindDue || Prefs.remindDaily) && !Reminders.canNotify(ctx) && android.os.Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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

            Section("Apariencia") {
                val themes = listOf("dark" to "Oscuro", "light" to "Claro", "system" to "Como el teléfono")
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("Tema", color = C.Text, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    ChoiceRow(themes.mapIndexed { i, t -> i.toLong() to t.second }, themes.indexOfFirst { it.first == Prefs.theme }.toLong()) { i ->
                        Prefs.theme = themes[i.toInt()].first
                        // Volver a abrir la pantalla para aplicar colores y barras del sistema.
                        (ctx as? android.app.Activity)?.recreate()
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Los widgets siguen el modo claro/oscuro del teléfono.", color = C.Sub, fontSize = 12.sp)
                }
            }

            Section("Recordatorios") {
                SettingRow(Icons.Filled.NotificationsActive, "Avisar vencimientos", trailing = {
                    Switch(Prefs.remindDue, { Prefs.remindDue = it; reminderChanged() }, colors = switchColors)
                })
                if (Prefs.remindDue) {
                    OptionPills("Con cuánta anticipación", listOf(1 to "1 día antes", 2 to "2 días", 3 to "3 días"), Prefs.dueDaysBefore) {
                        Prefs.dueDaysBefore = it
                    }
                    OptionPills("A qué hora", listOf(8, 9, 12, 18, 21).map { it to "$it hs" }, Prefs.dueHour) {
                        Prefs.dueHour = it; reminderChanged(hourChanged = true)
                    }
                }
                RowDivider()
                SettingRow(Icons.Filled.Edit, "Aviso diario: ¿cargaste tus gastos?", trailing = {
                    Switch(Prefs.remindDaily, { Prefs.remindDaily = it; reminderChanged() }, colors = switchColors)
                })
                if (Prefs.remindDaily) {
                    OptionPills("A qué hora", listOf(20, 21, 22, 23).map { it to "$it hs" }, Prefs.dailyHour) {
                        Prefs.dailyHour = it; reminderChanged(hourChanged = true)
                    }
                }
                RowDivider()
                SettingRow(Icons.Filled.CalendarMonth, "Resumen semanal (domingo 20 hs)", trailing = {
                    Switch(Prefs.weeklySummary, { Prefs.weeklySummary = it; reminderChanged() }, colors = switchColors)
                })
                RowDivider()
                SettingRow(Icons.Filled.PieChart, "Presupuesto al 80% y al 100%", trailing = {
                    Switch(Prefs.budgetAlerts, { Prefs.budgetAlerts = it; reminderChanged() }, colors = switchColors)
                })
                RowDivider()
                SettingRow(
                    Icons.Filled.Warning, "Gasto grande",
                    value = if (Prefs.bigExpense > 0) "desde ${Fmt.money(Prefs.bigExpense)}" else "Apagado",
                    onClick = { amountDialog = "big" }
                )
                RowDivider()
                SettingRow(
                    Icons.Filled.Savings, "Poca plata en la cuenta del sueldo",
                    value = if (Prefs.lowBalance > 0) "menos de ${Fmt.money(Prefs.lowBalance)}" else "Apagado",
                    onClick = { amountDialog = "low" }
                )
                RowDivider()
                SettingRow(Icons.Filled.MusicNote, "Sonido y vibración de cada aviso", onClick = {
                    Reminders.openSystemSettings(ctx)
                }, trailing = { Chevron() })
                RowDivider()
                SettingRow(Icons.Filled.NotificationsActive, "Probar notificación", onClick = {
                    if (!Reminders.canNotify(ctx) && android.os.Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Reminders.notify(ctx, 999, "Así se ven los avisos", "Cuentas Claras te va a avisar acá.", tab = 0)
                    }
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

            Section("Backup automático") {
                SettingRow(
                    Icons.Filled.CloudUpload, "Carpeta del backup semanal",
                    value = if (Prefs.backupFolder.isBlank()) "Elegir" else "Cambiar",
                    onClick = { folderPicker.launch(null) }
                )
                if (Prefs.backupFolder.isNotBlank()) {
                    RowDivider()
                    SettingRow(Icons.Filled.Backup, "Hacer backup ahora", onClick = {
                        scope.launch {
                            try {
                                Backup.run(ctx)
                                Toast.makeText(ctx, "Backup guardado", Toast.LENGTH_SHORT).show()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "No se pudo guardar el backup: ${e.message ?: "error desconocido"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }, trailing = { Chevron() })
                    RowDivider()
                    SettingRow(Icons.Filled.CloudOff, "Desactivar backup automático", onClick = {
                        Prefs.backupFolder = ""
                        Backup.schedule(ctx)
                    })
                }
                Text(
                    (if (Prefs.lastBackup.isNotBlank()) "Último backup: ${Prefs.lastBackup}.\n" else "") +
                        "Cada semana se guarda un CSV en esa carpeta. Si elegís una carpeta de Google Drive " +
                        "(o una que se sincronice con Drive), el backup queda en la nube. Para recuperar: Importar datos (CSV).",
                    color = C.Sub, fontSize = 13.sp, modifier = Modifier.padding(16.dp)
                )
            }

            Section("Página web (ver y cargar desde la PC)") {
                if (CloudSync.email.value.isBlank()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Conectá la cuenta que creaste en Firebase para ver todo en vivo desde la página web y cargar movimientos desde la PC.",
                            color = C.Sub, fontSize = 14.sp
                        )
                        Field(syncMail, { syncMail = it }, "Mail")
                        OutlinedTextField(
                            value = syncPass, onValueChange = { syncPass = it }, label = { Text("Contraseña") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        PrimaryButton(if (connecting) "Conectando…" else "Conectar") {
                            if (!connecting && syncMail.isNotBlank() && syncPass.isNotBlank()) scope.launch {
                                connecting = true
                                try {
                                    CloudSync.signIn(ctx, syncMail, syncPass)
                                    syncPass = ""
                                    Toast.makeText(ctx, "Conectado. Ya podés abrir la página web.", Toast.LENGTH_LONG).show()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "No se pudo conectar: revisá mail, contraseña e internet (${e.message})", Toast.LENGTH_LONG).show()
                                } finally {
                                    connecting = false
                                }
                            }
                        }
                    }
                } else {
                    SettingRow(Icons.Filled.CloudDone, "Conectado: ${CloudSync.email.value}", value = CloudSync.status.value.ifBlank { null })
                    RowDivider()
                    SettingRow(Icons.Filled.Sync, "Sincronizar ahora", onClick = {
                        scope.launch {
                            try {
                                CloudSync.pullInbox(ctx)
                                CloudSync.push(ctx)
                                Toast.makeText(ctx, "Sincronizado", Toast.LENGTH_SHORT).show()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "No se pudo sincronizar: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }, trailing = { Chevron() })
                    RowDivider()
                    SettingRow(Icons.Filled.CloudOff, "Desconectar", onClick = { CloudSync.signOut(ctx) })
                    Text(
                        "En la PC abrí nestorchallier.github.io/CuentasClaras y entrá con el mismo mail y contraseña. " +
                            "Lo que cargues en la web aparece acá al abrir la app (o solo, cada 15 minutos).",
                        color = C.Sub, fontSize = 13.sp, modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Section("Doble toque atrás del celular") {
                Text(
                    "Mantené apretado el ícono de Cuentas Claras: aparecen \"Nuevo gasto\" y \"Nuevo ingreso\". " +
                        "Esos accesos se pueden usar con un gesto:\n\n" +
                        "• Con la app gratuita \"Tap, Tap\": Acciones → Doble toque → Abrir acceso directo → Cuentas Claras → Nuevo gasto.\n" +
                        "• Si tu celular trae el gesto (Xiaomi: Ajustes adicionales → Gestos → Toque posterior), elegí abrir el acceso directo.\n\n" +
                        "Se abre la hoja para escribir el monto, igual que el widget.",
                    color = C.Sub, fontSize = 14.sp, modifier = Modifier.padding(16.dp)
                )
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
                "Cuentas Claras 1.5 · tus datos se guardan en este teléfono (y en tu cuenta de Firebase si conectaste la página web). Exportá un CSV cada tanto como respaldo.",
                color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp, start = 4.dp)
            )
        }
    }

    amountDialog?.let { which ->
        val big = which == "big"
        var v by remember { mutableStateOf((if (big) Prefs.bigExpense else Prefs.lowBalance).takeIf { it > 0 }?.let { Fmt.plain(it) } ?: "") }
        AlertDialog(
            onDismissRequest = { amountDialog = null },
            containerColor = C.Card,
            title = { Text(if (big) "Aviso de gasto grande" else "Aviso de poca plata", color = C.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (big) "Te aviso cuando cargues un gasto igual o mayor a este monto (en pesos)."
                        else "Te aviso cuando lo que te queda del mes en la cuenta del sueldo baje de este monto.",
                        color = C.Sub, fontSize = 14.sp
                    )
                    Field(v, { v = it }, "Monto (vacío = apagado)", number = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = Fmt.parse(v).coerceAtLeast(0.0)
                    if (big) Prefs.bigExpense = n else Prefs.lowBalance = n
                    amountDialog = null
                    reminderChanged()
                }) { Text("Guardar", color = C.Teal) }
            },
            dismissButton = { TextButton(onClick = { amountDialog = null }) { Text("Cancelar", color = C.Sub) } }
        )
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

/** Fila de opciones (pastillas) dentro de una sección de Ajustes. */
@Composable
private fun OptionPills(title: String, options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(start = 68.dp, end = 14.dp, bottom = 10.dp)) {
        Text(title, color = C.Sub, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        ChoiceRow(options.map { it.first.toLong() to it.second }, selected.toLong()) { onSelect(it.toInt()) }
    }
}
