package com.nestor.cuentasclaras.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val Bg = Color(0xFF12171B)
    val Card = Color(0xFF242B31)
    val CardHi = Color(0xFF323B42)
    val Text = Color(0xFFF1F5F7)
    val Sub = Color(0xFF8C979F)
    val Green = Color(0xFF34D399)
    val Blue = Color(0xFF38BDF8)
    val Red = Color(0xFFF87171)
    val Teal = Color(0xFF2DD4BF)
    val Purple = Color(0xFFC084FC)

    // Degradés superiores de cada pantalla
    val TopGastos = Color(0xFF1C4257)
    val TopIngresos = Color(0xFF1A4A3B)
    val TopNeutral = Color(0xFF28343C)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
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
        ),
        content = content
    )
}
