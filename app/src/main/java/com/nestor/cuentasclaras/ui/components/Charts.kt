package com.nestor.cuentasclaras.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nestor.cuentasclaras.ui.theme.C
import com.nestor.cuentasclaras.util.Fmt
import kotlin.math.max

private fun DrawScope.label(
    text: String, x: Float, y: Float, color: Color,
    sizeSp: Float = 11f, align: Paint.Align = Paint.Align.CENTER, bold: Boolean = false
) {
    val p = Paint().apply {
        this.color = color.toArgb()
        textSize = sizeSp * density * fontScale
        isAntiAlias = true
        textAlign = align
        isFakeBoldText = bold
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, p)
}

/**
 * Gráfico de barras redondeadas (una o dos series), estilo "pastilla".
 * Eje de valores a la derecha, línea punteada de promedio opcional y selección por toque.
 */
@Composable
fun BarChart(
    series: List<List<Double>>,
    colors: List<Color>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    selected: Int = -1,
    avg: Double? = null,
    onSelect: ((Int) -> Unit)? = null
) {
    val n = series.firstOrNull()?.size ?: 0
    val tap = if (onSelect != null && n > 0) Modifier.pointerInput(n) {
        detectTapGestures { pos ->
            val chartW = size.width - 44.dp.toPx()
            val i = (pos.x / (chartW / n)).toInt()
            if (i in 0 until n) onSelect(i)
        }
    } else Modifier
    // Fondo de cada barra y línea de promedio: siguen el tema (claro/oscuro).
    val track = C.Text.copy(alpha = 0.05f)
    val avgLine = C.Text.copy(alpha = 0.75f)

    Canvas(modifier.then(tap)) {
        if (n == 0) return@Canvas
        val axisW = 44.dp.toPx()
        val labelH = 24.dp.toPx()
        val w = size.width - axisW
        val h = size.height - labelH
        val maxV = max(series.flatten().maxOrNull() ?: 0.0, avg ?: 0.0).let { if (it <= 0.0) 1.0 else it }
        val slot = w / n
        val k = series.size
        val gap = if (k > 1) 4.dp.toPx() else 0f
        val bw = ((slot * 0.7f - gap * (k - 1)) / k).coerceAtMost(30.dp.toPx())
        val totalW = bw * k + gap * (k - 1)

        for (i in 0 until n) {
            val x0 = i * slot + (slot - totalW) / 2
            series.forEachIndexed { s, values ->
                val x = x0 + s * (bw + gap)
                drawRoundRect(track, Offset(x, 0f), Size(bw, h), CornerRadius(bw / 2))
                val v = values[i]
                if (v > 0) {
                    val bh = max((v / maxV * h).toFloat(), bw)
                    val c = colors[s].let { if (selected >= 0 && i != selected) it.copy(alpha = 0.5f) else it }
                    drawRoundRect(c, Offset(x, h - bh), Size(bw, bh), CornerRadius(bw / 2))
                }
            }
            val lb = labels.getOrElse(i) { "" }
            if (lb.isNotEmpty()) {
                label(lb, i * slot + slot / 2, size.height - 4.dp.toPx(),
                    if (i == selected) C.Text else C.Sub, 11f, bold = i == selected)
            }
        }
        label(Fmt.compact(maxV), w + 6.dp.toPx(), 12.dp.toPx(), C.Sub, 11f, Paint.Align.LEFT)
        label("0", w + 6.dp.toPx(), h, C.Sub, 11f, Paint.Align.LEFT)
        if (avg != null && avg > 0) {
            val y = h - (avg / maxV * h).toFloat()
            drawLine(
                avgLine, Offset(0f, y), Offset(w, y), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            )
            label(Fmt.compact(avg), w + 6.dp.toPx(), y + 4.dp.toPx(), C.Text, 11f, Paint.Align.LEFT)
        }
    }
}

/** Dona por categorías con pequeñas separaciones entre segmentos. */
@Composable
fun DonutChart(
    slices: List<Pair<Double, Color>>,
    modifier: Modifier = Modifier,
    stroke: Dp = 22.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val d = size.minDimension - sw
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arc = Size(d, d)
            val total = slices.sumOf { it.first }
            if (total <= 0.0) {
                drawArc(C.CardHi, 0f, 360f, false, tl, arc, style = Stroke(sw))
                return@Canvas
            }
            val gap = if (slices.count { it.first > 0 } > 1) 4f else 0f
            var start = -90f
            slices.forEach { (v, c) ->
                if (v <= 0) return@forEach
                val sweep = (360.0 * v / total).toFloat()
                drawArc(c, start + gap / 2, (sweep - gap).coerceAtLeast(0.8f), false, tl, arc, style = Stroke(sw))
                start += sweep
            }
        }
        content()
    }
}

/** Anillo de progreso (presupuesto disponible). */
@Composable
fun RingProgress(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 26.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val d = size.minDimension - sw
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arc = Size(d, d)
            drawArc(C.CardHi, 0f, 360f, false, tl, arc, style = Stroke(sw))
            val p = progress.coerceIn(0f, 1f)
            if (p > 0f) drawArc(color, -90f, 360f * p, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}
