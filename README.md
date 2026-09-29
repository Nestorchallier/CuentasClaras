# Cuentas Claras

App Android nativa (Kotlin + Jetpack Compose) para registrar gastos e ingresos, con widgets de pantalla principal.

## Qué incluye
- **Actividad**: total del mes, gráfico de barras por día con promedio, filtros (gastos/ingresos, cuenta, categoría, búsqueda) y lista por día.
- **Resumen**: dona por categoría con % y comparación contra el mes anterior. Tocando una categoría ves su tendencia de 12 meses.
- **Presupuesto**: anillo de "disponible este mes" y avance por categoría.
- **General**: ingresos vs gastos (6 meses), ingresos restantes ($ o %), recurrentes con calendario y calendario de gastos.
- **Cuentas**: saldo total y por cuenta (saldo inicial + ingresos − gastos).
- **Ajustes**: categorías y presupuestos, recurrentes, símbolo de moneda, centavos, ocultar saldos, exportar/importar CSV, borrar movimientos.
- **Widgets**: "Carga rápida" (botones − Gasto / + Ingreso que abren una hoja flotante) y "Resumen del mes" (gastado, gráfico diario, presupuesto).

## Compilar
1. Abrir la carpeta en Android Studio (Ladybug o más nuevo) y esperar la sincronización de Gradle.
2. Conectar el celular con depuración USB y tocar ▶ Run, o usar Build → Build APK(s).

Datos 100% locales (Room/SQLite). minSdk 26 (Android 8+).
