package com.nestor.cuentasclaras.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider

/**
 * Colores de los widgets: cada uno con versión de día y de noche.
 * Siguen el modo claro/oscuro del teléfono (los fondos están en drawable y drawable-night).
 */
internal object W {
    val Text = ColorProvider(day = Color(0xFF12171B), night = Color(0xFFF1F5F7))
    val Sub = ColorProvider(day = Color(0xFF5F6B74), night = Color(0xFF8C979F))
    val Green = ColorProvider(day = Color(0xFF059669), night = Color(0xFF34D399))
    val Red = ColorProvider(day = Color(0xFFDC2626), night = Color(0xFFF87171))
    val Teal = ColorProvider(day = Color(0xFF0D9488), night = Color(0xFF2DD4BF))
    /** Texto sobre el botón principal (widget_btn_white): claro de día, oscuro de noche. */
    val OnButton = ColorProvider(day = Color(0xFFF1F5F7), night = Color(0xFF12171B))
    /** Texto sobre el botón verde: siempre oscuro. */
    val OnGreen = ColorProvider(day = Color(0xFF12171B), night = Color(0xFF12171B))
    val Track = ColorProvider(day = Color(0xFFE4E9ED), night = Color(0xFF323B42))

    /** Para los gráficos (que son imágenes): si el teléfono está en modo oscuro. */
    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
