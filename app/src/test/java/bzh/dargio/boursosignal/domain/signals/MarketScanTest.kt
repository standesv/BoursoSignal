package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketScanTest {

    private fun series(prices: List<Double>, vol: Double) =
        prices.mapIndexed { i, p -> Candle("d$i", p, p, p, p, vol) }

    private val cfg = StrategyConfig(emaFastPeriod = 3, emaSlowPeriod = 5, rsiPeriod = 5)

    @Test
    fun quality_accepts_long_liquid_series() {
        val good = series((0..149).map { 30.0 + 0.5 * it }, 5000.0)
        assertTrue(DataQuality.check(good, minHistory = 120).ok)
    }

    @Test
    fun quality_rejects_short_illiquid_and_anomalous() {
        assertFalse(DataQuality.check(series(List(50) { 100.0 }, 5000.0), minHistory = 120).ok)
        assertFalse(DataQuality.check(series(List(150) { 100.0 }, 10.0), minHistory = 120).ok)
        val bad = series((0..149).map { 100.0 }, 5000.0).toMutableList()
            .also { it[0] = Candle("x", 100.0, 1.0, 100.0, 50.0, 5000.0) }
        assertFalse(DataQuality.check(bad, minHistory = 120).ok)
    }

    @Test
    fun stance_reads_trend_direction() {
        assertEquals(Stance.BUY, StanceEvaluator.evaluate("UP", series((1..40).map { 10.0 + 2.0 * it }, 5000.0), cfg).stance)
        assertEquals(Stance.AVOID, StanceEvaluator.evaluate("DOWN", series((1..40).map { 100.0 - 2.0 * it }, 5000.0), cfg).stance)
        assertEquals(Stance.NEUTRAL, StanceEvaluator.evaluate("FLAT", series(List(40) { 50.0 }, 5000.0), cfg).stance)
    }

    @Test
    fun scanner_excludes_unreliable_and_ranks_best_first() {
        val data = mapOf(
            "UP" to series((0..149).map { 30.0 + 0.8 * it }, 5000.0),
            "DOWN" to series((0..149).map { 150.0 - 0.8 * it }, 20000.0),
            "SHORT" to series(List(50) { 100.0 }, 5000.0),
            "ILLIQ" to series(List(150) { 100.0 }, 10.0),
        )
        val res = MarketScanner.scan(data, cfg, minHistory = 120, minAvgTurnover = 200_000.0)
        val tickers = res.map { it.ticker }
        assertFalse("SHORT" in tickers)
        assertFalse("ILLIQ" in tickers)
        assertTrue("UP" in tickers && "DOWN" in tickers)
        assertEquals("UP", res.first().ticker)
    }
}
