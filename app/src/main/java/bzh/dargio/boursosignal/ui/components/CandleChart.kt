package bzh.dargio.boursosignal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.domain.signals.Candle
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Graphique en chandeliers (§F6) avec axes (prix en ordonnée, dates en abscisse),
 * zoom à deux doigts (pincer) et défilement horizontal. Dessiné en Canvas Compose,
 * sans bibliothèque externe.
 */
@Composable
fun CandleChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    maxCandles: Int = 60,
    emaFast: List<Double?>? = null,
    emaSlow: List<Double?>? = null,
) {
    if (candles.isEmpty()) return
    val initial = min(maxCandles, candles.size).coerceAtLeast(10)
    var visible by remember(candles.size, maxCandles) { mutableIntStateOf(initial) }
    var end by remember(candles.size, maxCandles) { mutableIntStateOf(candles.lastIndex) }

    val axisText = Color(0xFF6B7280)
    val grid = Color(0xFFCBD5E1)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .pointerInput(candles.size) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newVisible = (visible / zoom).roundToInt().coerceIn(10, candles.size)
                    val slot = size.width / visible.coerceAtLeast(1)
                    val shift = (-pan.x / slot).roundToInt()
                    visible = newVisible
                    end = (end + shift).coerceIn(newVisible - 1, candles.lastIndex)
                }
            },
    ) {
        val leftPad = 48f
        val bottomPad = 26f
        val topPad = 6f
        val plotW = size.width - leftPad
        val plotH = size.height - bottomPad - topPad

        val start = (end - visible + 1).coerceAtLeast(0)
        val win = candles.subList(start, end + 1)
        if (win.isEmpty()) return@Canvas

        val minP = win.minOf { it.low }
        val maxP = win.maxOf { it.high }
        val range = (maxP - minP).takeIf { it > 0 } ?: 1.0
        fun y(p: Double): Float = (topPad + (maxP - p) / range * plotH).toFloat()
        val slot = plotW / win.size
        val bodyW = (slot * 0.6f).coerceAtLeast(1f)
        fun cx(i: Int): Float = leftPad + slot * i + slot / 2

        // Graduations de prix (min / milieu / max).
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(107, 114, 128); textSize = 24f; isAntiAlias = true
            }
            listOf(maxP, (maxP + minP) / 2, minP).forEach { level ->
                val yy = y(level)
                drawLine(grid, Offset(leftPad, yy), Offset(size.width, yy), strokeWidth = 1f)
                canvas.nativeCanvas.drawText("%.1f".format(level), 2f, yy + 8f, paint)
            }
            // Dates (début, milieu, fin).
            val datePaint = android.graphics.Paint(paint).apply { textSize = 22f }
            fun label(i: Int) = win[i].date.takeLast(5)
            canvas.nativeCanvas.drawText(label(0), leftPad, size.height - 6f, datePaint)
            canvas.nativeCanvas.drawText(label(win.size / 2), leftPad + plotW / 2 - 24f, size.height - 6f, datePaint)
            datePaint.textAlign = android.graphics.Paint.Align.RIGHT
            canvas.nativeCanvas.drawText(label(win.lastIndex), size.width, size.height - 6f, datePaint)
        }

        // Chandeliers.
        win.forEachIndexed { i, c ->
            val color = if (c.close >= c.open) BullGreen else BearRed
            val x = cx(i)
            drawLine(color, Offset(x, y(c.high)), Offset(x, y(c.low)), strokeWidth = 1.5f)
            val top = y(max(c.open, c.close))
            val bottom = y(min(c.open, c.close))
            drawRect(color, topLeft = Offset(x - bodyW / 2, top), size = Size(bodyW, (bottom - top).coerceAtLeast(1f)))
        }

        // Moyennes mobiles superposées.
        fun overlay(series: List<Double?>?, color: Color) {
            if (series == null) return
            var prev: Offset? = null
            win.indices.forEach { i ->
                val v = series.getOrNull(start + i) ?: run { prev = null; return@forEach }
                val point = Offset(cx(i), y(v))
                prev?.let { drawLine(color, it, point, strokeWidth = 2f) }
                prev = point
            }
        }
        overlay(emaFast, Color(0xFF2563EB))
        overlay(emaSlow, Color(0xFFF59E0B))
    }
}
