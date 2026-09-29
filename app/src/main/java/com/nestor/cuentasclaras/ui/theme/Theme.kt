package com.nestor.cuentasclaras.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.nestor.cuentasclaras.util.Prefs

/**
 * Colores de la app. Cada color tiene versión oscura y clara; [dark] elige cuál se usa.
 * Como [dark] es state de Compose, al cambiar el tema toda la pantalla se redibuja sola.
 */
object C {
    var dark by mutableStateOf(true)

    private fun pick(night: Long, day: Long) = Color(if (dark) night else day)

    val Bg: Color get() = pick(0xFF12171B, 0xFFF3F5F7)
    val Card: Color get() = pick(0xFF242B31, 0xFFFFFFFF)
    val CardHi: Color get() = pick(0xFF323B42, 0xFFE4E9ED)
    val Text: Color get() = pick(0xFFF1F5F7, 0xFF12171B)
    val Sub: Color get() = pick(0xFF8C979F, 0xFF5F6B74)
    val Green: Color get() = pick(0xFF34D399, 0xFF059669)
    val Blue: Color get() = pick(0xFF38BDF8, 0xFF0284C7)
    val Red: Color get() = pick(0xFFF87171, 0xFFDC2626)
    val Teal: Color get() = pick(0xFF2DD4BF, 0xFF0D9488)
    val Purple: Color get() = pick(0xFFC084FC, 0xFF9333EA)

    // Degradés superiores de cada pantalla
    val TopGastos: Color get() = pick(0xFF1C4257, 0xFFCFE6F3)
    val TopIngresos: Color get() = pick(0xFF1A4A3B, 0xFFD2EFE2)
    val TopNeutral: Color get() = pick(0xFF28343C, 0xFFE2E8EC)
    val TopRecurring: Color get() = pick(0xFF1E3F36, 0xFFD5EDE4)

    // Botón flotante "+"
    val FabTop: Color get() = pick(0xFFFFFFFF, 0xFF1F272D)
    val FabBottom: Color get() = pick(0xFFD5DBE0, 0xFF12171B)

    /**
     * Decide el tema según Ajustes ("dark", "light" o "system") y ajusta las barras del sistema.
     * Llamar en onCreate de cada actividad, antes de setContent.
     */
    fun apply(activity: ComponentActivity) {
        dark = isDark(activity)
        val transparent = android.graphics.Color.TRANSPARENT
        val bars = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }

    fun isDark(context: Context): Boolean = when (Prefs.theme) {
        "light" -> false
        "system" -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        else -> true
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (C.dark) {
        darkColorScheme(
            primary = C.Teal,
            onPrimary = Color(0xFF06231F),
            secondary = C.Blue,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Card,
            onSurface = C.Text,
            surfaceVariant = C.CardHi,
            onSurfaceVariant = C.Sub,
            surfaceContainer = C.Card,
            surfaceContainerHigh = C.Card,
            surfaceContainerHighest = C.CardHi
        )
    } else {
        lightColorScheme(
            primary = C.Teal,
            onPrimary = Color.White,
            secondary = C.Blue,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Card,
            onSurface = C.Text,
            surfaceVariant = C.CardHi,
            onSurfaceVariant = C.Sub,
            surfaceContainer = C.Card,
            surfaceContainerHigh = C.Card,
            surfaceContainerHighest = C.CardHi
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
