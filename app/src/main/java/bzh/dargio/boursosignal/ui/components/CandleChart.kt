package bzh.dargio.boursosignal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.domain.signals.Candle
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen

/**
 * Graphique en chandeliers (§F6), dessiné en Canvas Compose — sans bibliothèque
 * externe. Affiche les `maxCandles` dernières bougies et, optionnellement, deux
 * moyennes mobiles superposées.
 */
@Composable
fun CandleChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    maxCandles: Int = 60,
    emaFast: List<Double?>? = null,
    emaSlow: List<Double?>? = null,
) {
    val visible = candles.takeLast(maxCandles)
    if (visible.isEmpty()) return
    val startIndex = candles.size - visible.size

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val lows = visible.map { it.low }
        val highs = visible.map { it.high }
        val minP = lows.min()
        val maxP = highs.max()
        val range = (maxP - minP).takeIf { it > 0 } ?: 1.0
        val w = size.width
        val h = size.height
        val slot = w / visible.size
        val bodyW = (slot * 0.6f).coerceAtLeast(1f)

        fun y(price: Double): Float = (h - ((price - minP) / range * h)).toFloat()

        visible.forEachIndexed { i, c ->
            val cx = slot * i + slot / 2
            val up = c.close >= c.open
            val color = if (up) BullGreen else BearRed
            // Mèche
            drawLine(color, Offset(cx, y(c.high)), Offset(cx, y(c.low)), strokeWidth = 1.5f)
            // Corps
            val top = y(maxOf(c.open, c.close))
            val bottom = y(minOf(c.open, c.close))
            drawRect(
                color = color,
                topLeft = Offset(cx - bodyW / 2, top),
                size = androidx.compose.ui.geometry.Size(bodyW, (bottom - top).coerceAtLeast(1f)),
            )
        }

        fun drawOverlay(series: List<Double?>?, color: Color) {
            if (series == null) return
            var prev: Offset? = null
            visible.indices.forEach { i ->
                val v = series.getOrNull(startIndex + i) ?: run { prev = null; return@forEach }
                val cx = slot * i + slot / 2
                val point = Offset(cx, y(v))
                prev?.let { drawLine(color, it, point, strokeWidth = 2f) }
                prev = point
            }
        }
        drawOverlay(emaFast, Color(0xFF2563EB))
        drawOverlay(emaSlow, Color(0xFFF59E0B))
    }
}
