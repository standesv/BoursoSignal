package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyLabTest {

    private fun flat(p: Double) = Candle("d", p, p, p, p, 1000.0)
    private fun bar(c: Double) = Candle("d", c, c + 1, c - 1, c, 1000.0)
    private fun flats(prices: List<Double>) = prices.map { flat(it) }

    private val cfg = StrategyConfig(
        emaFastPeriod = 3, emaSlowPeriod = 5, rsiPeriod = 5, volumeAvgPeriod = 5,
        volumeFactor = 100.0, buyThreshold = 1.0, sellThreshold = 1.0,
        stopLossPct = -0.08, takeProfitPct = 0.15,
    )

    @Test
    fun existing_scenarios_unchanged() {
        val e = SignalEngine(cfg)
        assertEquals(SignalType.BUY, e.evaluate(flats(listOf(100.0,99.0,98.0,97.0,96.0,95.0,94.0,93.0,92.0,91.0,90.0,89.0,88.0,87.0,120.0))).type)
        assertEquals(SignalType.SELL, e.evaluate(flats(listOf(80.0,81.0,82.0,83.0,84.0,85.0,86.0,87.0,88.0,89.0,90.0,91.0,92.0,93.0,60.0))).type)
        assertEquals(SignalType.NONE, e.evaluate(flats(List(15) { 100.0 })).type)
    }

    @Test
    fun trailing_stop_sells_on_pullback_from_recent_high() {
        val trail = cfg.copy(stopLossPct = null, takeProfitPct = null, useAtrStop = false,
            trailingPeriod = 20, trailingAtrMultiple = 2.0, atrPeriod = 5)
        val series = (100..130).map { bar(it.toDouble()) } + (129 downTo 118).map { bar(it.toDouble()) }
        val r = SignalEngine(trail).evaluate(series, avgEntryPrice = 110.0)
        assertEquals(SignalType.SELL, r.type)
        assertTrue(r.reasons.any { it.contains("Stop suiveur") })
    }

    @Test
    fun trailing_stop_quiet_during_rally() {
        val trail = cfg.copy(stopLossPct = null, takeProfitPct = null, useAtrStop = false,
            trailingPeriod = 20, trailingAtrMultiple = 2.0, atrPeriod = 5)
        val rally = (100..130).map { bar(it.toDouble()) }
        assertTrue(SignalEngine(trail).evaluate(rally, avgEntryPrice = 110.0).reasons.none { it.contains("Stop suiveur") })
    }

    @Test
    fun backtest_lab_aggregates_and_excludes_short_history() {
        val wave = flats(listOf(100.0,99.0,98.0,97.0,96.0,95.0,94.0,93.0,92.0,91.0,90.0,89.0,88.0,87.0,
            120.0,121.0,122.0,123.0,90.0,89.0,88.0,110.0,111.0,112.0,80.0,81.0,82.0,83.0,84.0,85.0))
        val data = mapOf("A" to wave, "B" to wave, "SHORT" to flats(List(5) { 100.0 }))
        val lab = BacktestLab.run(data, cfg)
        assertEquals(2, lab.count)
        assertEquals(2, lab.perTicker.size)
        assertTrue(lab.beatCount in 0..2)
    }
}
