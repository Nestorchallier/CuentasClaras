# Cuentas Claras — contexto para Claude Code

> Respondé siempre en **español rioplatense**. El dueño del proyecto (Nestor) no habla inglés: explicaciones, commits, mensajes de error traducidos y textos de la app, todo en español.
> Antes de cambios grandes, proponé un plan corto y esperá confirmación. Después de cada cambio, compilá (`./gradlew assembleDebug`) y corregí errores antes de dar la tarea por terminada.

## Qué es
App Android nativa de finanzas personales (gastos, ingresos, cuentas, presupuestos, recurrentes) con **widgets de pantalla de inicio**. Es de **uso personal** de Nestor (y de algún amigo al que le pase el APK). Versión actual **1.5** (versionCode 6). El objetivo es **mejorarla y agregarle funciones**, no publicarla.

Inspiración visual: la app "Quanto: Gastos y Presupuesto" (estética oscura con degradés, barras redondeadas, dona por categoría). Mantener ese estilo en las pantallas nuevas.

## Stack
- Kotlin 2.0.21 · Jetpack Compose (BOM 2024.12.01, Material3, material-icons-extended)
- Jetpack **Glance 1.1.1** para widgets
- **Room 2.6.1** (KSP 2.0.21-1.0.28) — base local `cuentas_claras.db`
- AGP 8.7.3 · Gradle 8.9 · JDK 17/21 (Gradle JDK: no usar 25) · compileSdk/targetSdk 35 · minSdk 26
- Datos locales en Room. Opcional: sincronización con **Firebase** (Auth mail/contraseña + Firestore, proyecto `cuentasclaras-55dd0`, inicializado a mano sin google-services.json) para la página web `web/cuentas-claras-web.html`.
- **No aceptar el "AGP Upgrade Assistant"** salvo que se pida explícitamente; actualizar versiones como tarea aparte y probando.

## Estructura (`app/src/main/java/com/nestor/cuentasclaras/`)
- `CuentasApp.kt` — Application: `Prefs.init`, crea `Repository`, siembra datos por defecto y procesa recurrentes. Extensión `Context.repo`.
- `data/`
  - `Entities.kt` — `Category(id,name,emoji,color:Long,type,budget,position)`, `Account(id,name,emoji,initialBalance,position,isCard,closingDay,dueDay,currency)`, `Tx(id,amount,type,categoryId,accountId,date:epochMillis,note,recurringId?,toAccountId?,installment,installments,groupId?,toAmount?)`, `Recurring(id,name,amount,type,categoryId,accountId,dayOfMonth,lastGenerated "yyyy-MM")`. `TxType.GASTO/INGRESO/TRANSFER` son Strings (las transferencias tienen `categoryId = 0`). `Currency.ARS/USD`.
  - `Daos.kt`, `AppDatabase.kt` (versión 4, `exportSchema = true` en `app/schemas`), `Migrations.kt` (1→2 transferencias, 2→3 tarjetas/cuotas, 3→4 moneda).
  - `Repository.kt` — única puerta de escritura. Toda escritura llama `WidgetUpdater.refresh()`. Incluye `processRecurrings()` (con Mutex + transacción), cuotas (`saveTx` divide si `installments > 1`), `exportCsv()`, `importCsv()` (detecta separador, no duplica).
  - `Defaults.kt` — paleta, categorías/cuentas iniciales, "adiciones rápidas", emojis.
- `util/Prefs.kt` — SharedPreferences expuestas como state de Compose (moneda, centavos, ocultar saldos, tarjetas de General). `GeneralCards` = catálogo de tarjetas.
- `util/Format.kt` — `Fmt` (formato argentino `$ 1.093.500`, `money(v, cuenta)` con US$, compacto, %, `parseOrNull` único para montos), `Dates` (rangos de mes, etiquetas Hoy/Ayer/Mañana), `Projection` (recurrentes programados no generados).
- `util/Balances.kt` (saldos con transferencias, total en pesos), `util/CardCycle.kt` (resúmenes de tarjeta), `util/Money.kt` (cotización dolarapi.com, conversión a pesos).
- `reminders/Reminders.kt` (WorkManager: vencimientos, aviso diario, resumen semanal; horas en Prefs), `reminders/Alerts.kt` (presupuesto 80/100%, gasto grande, poca plata; se llama desde `saveTx`), `backup/Backup.kt` (CSV semanal a carpeta SAF).
- `sync/CloudSync.kt` — sube a `users/{uid}/{accounts,categories,txs,meta}` solo lo que cambió (huellas en `sync_state.txt`); trae lo cargado en la web desde `users/{uid}/inbox`. `web/firestore.rules` = reglas.
- `ui/MainActivity.kt` — navegación propia: 5 pestañas (0 Actividad, 1 Resumen, 2 Presupuesto, 3 General, 4 Cuentas) + pila de `Route` (Editor, Settings, Categories, Recurrings, CategoryDetail). Deep link desde widgets `cuentasclaras://open/<tab>`.
- `ui/MainViewModel.kt` — StateFlows de txs/categorías/cuentas/recurrentes + filtros compartidos (mes, tipo, cuenta).
- `ui/components/` — `Components.kt` (Pill, DropPill, CardBox, EmojiBadge, TxRow, MonthSwitcher, MonthGrid, Field…), `Charts.kt` (BarChart, DonutChart, RingProgress en Canvas).
- `ui/screens/` — Actividad, Resumen, CategoryDetail, Presupuesto, General (tarjetas configurables), Cuentas, Ajustes, Categorías, Recurrentes, `TxEditor` (teclado numérico propio; se reutiliza en la hoja de widgets).
- `ui/theme/Theme.kt` — objeto `C` con los colores en versión oscura y clara (`C.dark`, `Prefs.theme`). Widgets: `widget/WColors.kt` + `drawable-night`.
- `widget/` — Glance: `DashboardWidget` (resumen mes), `QuickAddWidget` (− Gasto / + Ingreso), `MoreWidgets.kt` (Actividad, Resumen/dona, Cuentas; base `SnapshotWidget`), `WidgetData.kt` (Snapshot + bitmaps de gráficos), `WidgetUpdater.kt`, `QuickAddActivity` (hoja translúcida con `TxEditor`).

## Convenciones y trampas conocidas
- Navegación: usar `stack.removeAt(stack.lastIndex)`, **nunca `removeLast()`** (crash en Android < 15 con compileSdk 35).
- Widgets: leen Room con `Flow` + `collectAsState` dentro de `provideContent`; fondos con drawables redondeados (`widget_bg`, `widget_btn_*`) para que funcionen en Android < 12. Glance no tiene Canvas: los gráficos van como Bitmap.
- Intents de widgets: diferenciarlos con `data` (URI) y no solo con extras, para que los PendingIntent no choquen.
- Montos en `Double`. Si se agrega multimoneda, evaluar pasar a centavos `Long`.
- Textos de UI en español (hardcodeados en las pantallas).
- Cualquier cambio de entidades Room ⇒ subir `version`, escribir `Migration` y activar `exportSchema = true` con `room.schemaLocation`. **Nunca** `fallbackToDestructiveMigration` (se borran los datos del usuario).
- Se instala como APK debug (`Build → Generate App Bundles or APKs → Generate APKs`) **encima** de la versión anterior. La firma es la clave debug de la PC de Nestor: no cambiar la configuración de firma, o el celular no deja actualizar y habría que desinstalar (se pierden los datos).

## Cómo trabajar en cada actualización
1. Antes de tocar nada: `git status` limpio (si no hay git, inicializarlo y hacer commit).
2. Proponer un plan corto en español y esperar el OK.
3. Implementar de a una función por vez, sin tocar lo que ya anda.
4. Subir `versionCode` (+1) y `versionName` en `app/build.gradle.kts`, y el texto de versión en `AjustesScreen`.
5. Compilar con `./gradlew assembleDebug` y corregir todo error/advertencia importante.
6. Si se modificó alguna entidad de Room: subir `version` de `AppDatabase`, escribir la `Migration` y probar instalando encima de la versión anterior.
7. Commit con mensaje en español y resumen de qué probar en el celular.
8. Recordarle a Nestor exportar un CSV antes de instalar si el cambio toca la base de datos.

## Ideas para agregar (uso personal, en orden sugerido)
### Útiles ya
1. **Transferencias entre cuentas** (ej. retiro del banco a efectivo) que no cuenten como gasto ni ingreso.
2. **Tarjeta de crédito y cuotas**: cargar una compra en N cuotas y que genere las cuotas futuras; ver cuánto viene en el próximo resumen.
3. **Recordatorios**: notificación el día antes de cada recurrente y un aviso diario opcional "¿cargaste tus gastos de hoy?" (WorkManager).
4. **Editar/duplicar rápido**: mantener presionado un movimiento → duplicar o mover a otra fecha.
5. **Backup automático** del CSV (o de la base) a una carpeta elegida o a Google Drive una vez por semana.

### Para más adelante
6. **Pesos y dólares**: movimientos en USD con cotización (oficial/MEP/blue desde una API pública) y saldo total convertido.
7. **Metas de ahorro** con barra de progreso y widget propio.
8. **Bloqueo con huella/PIN** al abrir la app.
9. **Adjuntar foto del ticket** a un movimiento.
10. **Reporte mensual en PDF** para guardar o compartir.
11. **Tema claro** y elegir color de acento.
12. **Configurar widgets** (qué cuenta o categoría muestran) con una pantalla de configuración de Glance.

## Cómo probar
- Probar siempre: carga desde widget, meses futuros en Actividad, recurrentes/programados, pantalla General configurable, exportar/importar CSV, ocultar saldos y **actualizar encima de la versión instalada sin perder datos**.
