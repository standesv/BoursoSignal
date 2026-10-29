package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertTrue
import org.junit.Test

class CandlestickTest {

    private fun c(o: Double, cl: Double, hi: Double = maxOf(o, cl), lo: Double = minOf(o, cl)) =
        Candle("d", o, hi, lo, cl, 1000.0)

    private fun trend(from: Double, step: Double, n: Int, tail: List<Candle>): List<Candle> {
        val base = (0 until n).map { i -> val p = from + step * i; c(p, p + step * 0.2) }
        return base + tail
    }

    private fun has(candles: List<Candle>, name: String) = Candlestick.detect(candles).any { it.name == name }

    @Test
    fun hammer_in_downtrend() {
        val hammer = c(o = 100.0, cl = 101.0, hi = 101.3, lo = 96.0)
        assertTrue(has(trend(130.0, -5.0, 6, listOf(hammer)), "Marteau"))
    }

    @Test
    fun hanging_man_in_uptrend() {
        val shape = c(o = 100.0, cl = 101.0, hi = 101.3, lo = 96.0)
        assertTrue(has(trend(60.0, 5.0, 6, listOf(shape)), "Pendu"))
    }

    @Test
    fun bullish_engulfing() {
        val eng = listOf(c(o = 100.0, cl = 97.0), c(o = 96.0, cl = 101.0))
        assertTrue(has(trend(130.0, -5.0, 6, eng), "Avalement haussier"))
    }

    @Test
    fun morning_star() {
        val morning = listOf(c(o = 110.0, cl = 100.0), c(o = 99.0, cl = 98.5), c(o = 99.0, cl = 108.0))
        assertTrue(has(trend(150.0, -6.0, 6, morning), "Étoile du matin"))
    }

    @Test
    fun evening_star() {
        val evening = listOf(c(o = 100.0, cl = 110.0), c(o = 111.0, cl = 111.5), c(o = 111.0, cl = 101.0))
        assertTrue(has(trend(60.0, 6.0, 6, evening), "Étoile du soir"))
    }

    @Test
    fun flat_series_has_no_directional_pattern() {
        val flat = (0 until 8).map { Candle("d", 100.0, 100.0, 100.0, 100.0, 1000.0) }
        assertTrue(Candlestick.detect(flat).none { it.bias != PatternBias.NEUTRAL })
    }
}
