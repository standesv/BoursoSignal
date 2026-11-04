package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewSignalsTest {

    private fun c(o: Double, h: Double, l: Double, cl: Double, v: Double = 1000.0) = Candle("d", o, h, l, cl, v)
    private fun flat(p: Double) = Candle("d", p, p, p, p, 1000.0)
    private fun flats(prices: List<Double>) = prices.map { flat(it) }

    @Test
    fun atr_positive_in_trend_zero_on_flat() {
        val up = (0..40).map { val p = 100.0 + it; c(p, p + 2, p - 1, p + 1) }
        assertTrue((Indicators.atr(up, 14).lastOrNull { it != null } ?: 0.0) > 0.0)
        assertEquals(0.0, Indicators.atr(List(40) { flat(100.0) }, 14).lastOrNull { it != null } ?: -1.0, 1e-9)
    }

    @Test
    fun adx_strong_trend_above_threshold_flat_below() {
        val strongUp = (0..60).map { val p = 100.0 + 2.0 * it; c(p - 1, p + 1, p - 2, p) }
        val adxUp = Indicators.adx(strongUp, 14).lastOrNull { it != null }
        assertNotNull(adxUp)
        assertTrue(adxUp!! in 0.0..100.0)
        assertTrue(adxUp >= 20.0)
        assertTrue((Indicators.adx(List(60) { flat(100.0) }, 14).lastOrNull { it != null } ?: 0.0) < 20.0)
    }

    @Test
    fun relative_strength_detects_out_and_underperformance() {
        val stock = (0..80).map { 100.0 * (1.0 + 0.20 * it / 80.0) }
        val bench = (0..80).map { 100.0 * (1.0 + 0.05 * it / 80.0) }
        assertTrue((Indicators.relativeStrengthPct(stock, bench, 63) ?: 0.0) > 10.0)
        assertTrue((Indicators.relativeStrengthPct(bench, stock, 63) ?: 0.0) < -10.0)
        assertNull(Indicators.relativeStrengthPct(listOf(1.0, 2.0), listOf(1.0, 2.0), 63))
    }

    @Test
    fun trend_filter_blocks_buy_below_long_ma() {
        val cfg = StrategyConfig(
            emaFastPeriod = 3, emaSlowPeriod = 5, rsiPeriod = 5, volumeAvgPeriod = 5,
            volumeFactor = 100.0, buyThreshold = 1.0, sellThreshold = 1.0, longTrendPeriod = 50,
        )
        val series = buildList {
            repeat(54) { add(100.0) }
            addAll(listOf(96.0, 92.0, 89.0, 87.0))
            addAll(listOf(90.0, 94.0))
        }
        val candles = flats(series)
        assertEquals(SignalType.BUY, SignalEngine(cfg.copy(useTrendFilter = false)).evaluate(candles).type)
        assertEquals(SignalType.NONE, SignalEngine(cfg).evaluate(candles).type)
    }

    @Test
    fun existing_scenarios_still_hold_with_new_defaults() {
        val cfg = StrategyConfig(
            emaFastPeriod = 3, emaSlowPeriod = 5, rsiPeriod = 5, volumeAvgPeriod = 5,
            volumeFactor = 100.0, buyThreshold = 1.0, sellThreshold = 1.0,
            stopLossPct = -0.08, takeProfitPct = 0.15,
        )
        val e = SignalEngine(cfg)
        assertEquals(SignalType.BUY, e.evaluate(flats(listOf(100.0,99.0,98.0,97.0,96.0,95.0,94.0,93.0,92.0,91.0,90.0,89.0,88.0,87.0,120.0))).type)
        assertEquals(SignalType.SELL, e.evaluate(flats(listOf(80.0,81.0,82.0,83.0,84.0,85.0,86.0,87.0,88.0,89.0,90.0,91.0,92.0,93.0,60.0))).type)
        assertEquals(SignalType.NONE, e.evaluate(flats(List(15) { 100.0 })).type)
        assertTrue(e.evaluate(flats(List(15) { 100.0 }), avgEntryPrice = 120.0).reasons.any { it.contains("Stop-loss") })
    }
}
