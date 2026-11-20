package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketRegimeTest {

    private fun idx(closes: List<Double>) =
        closes.mapIndexed { i, c -> Candle("j%04d".format(i), c, c, c, c, 1_000_000.0) }

    /** CAC 40 en hausse régulière sur ~1 an. */
    private val rising = (0 until 250).map { 100.0 + 0.3 * it }

    @Test
    fun steady_rise_is_risk_on() {
        assertEquals(Regime.RISK_ON, MarketRegimeEvaluator.evaluate(idx(rising)).regime)
    }

    @Test
    fun sharp_drop_is_risk_off() {
        var p = rising.last()
        val crash = (1..5).map { p *= 0.985; p }            // ≈ -7 % en 5 séances
        val r = MarketRegimeEvaluator.evaluate(idx(rising + crash))
        assertEquals(Regime.RISK_OFF, r.regime)
        assertTrue(r.reasons.isNotEmpty())
    }

    @Test
    fun mild_pullback_is_caution() {
        var p = rising.last()
        val pullback = (1..4).map { p *= 0.991; p }         // ≈ -3,6 %
        assertEquals(Regime.CAUTION, MarketRegimeEvaluator.evaluate(idx(rising + pullback)).regime)
    }

    @Test
    fun short_history_does_not_filter() {
        assertEquals(Regime.RISK_ON, MarketRegimeEvaluator.evaluate(idx(List(30) { 100.0 - it })).regime)
    }

    @Test
    fun by_date_never_looks_ahead() {
        var p = rising.last()
        val crash = (1..5).map { p *= 0.985; p }
        val before = MarketRegimeEvaluator.byDate(idx(rising))
        val after = MarketRegimeEvaluator.byDate(idx(rising + crash))
        before.forEach { (date, regime) -> assertEquals("date $date", regime, after[date]) }
        assertEquals(Regime.RISK_OFF, after["j%04d".format(rising.size + crash.size - 1)])
    }

    // --- Effets sur les décisions ---

    private val cfg = StrategyConfig(
        emaFastPeriod = 3, emaSlowPeriod = 5, rsiPeriod = 5, volumeAvgPeriod = 5,
        volumeFactor = 100.0, buyThreshold = 1.0, sellThreshold = 1.0,
        stopLossPct = -0.08, takeProfitPct = 0.15,
    )
    private fun flats(prices: List<Double>) = prices.mapIndexed { i, p -> Candle("d$i", p, p, p, p, 1000.0) }
    private val buySeries = flats(listOf(100.0, 99.0, 98.0, 97.0, 96.0, 95.0, 94.0, 93.0, 92.0, 91.0, 90.0, 89.0, 88.0, 87.0, 120.0))

    @Test
    fun engine_blocks_buy_when_market_breaks() {
        val e = SignalEngine(cfg)
        assertEquals(SignalType.BUY, e.evaluate(buySeries).type)
        assertEquals(SignalType.BUY, e.evaluate(buySeries, regime = Regime.RISK_ON).type)
        assertEquals(SignalType.NONE, e.evaluate(buySeries, regime = Regime.RISK_OFF).type)
        // Filtre désactivable (utilisé par le backtest « sans filtre »).
        assertEquals(SignalType.BUY, SignalEngine(cfg.copy(useMarketFilter = false)).evaluate(buySeries, regime = Regime.RISK_OFF).type)
    }

    @Test
    fun engine_requires_stronger_signal_when_market_fragile() {
        assertEquals(SignalType.NONE, SignalEngine(cfg.copy(cautionBuyExtra = 100.0)).evaluate(buySeries, regime = Regime.CAUTION).type)
        val ok = SignalEngine(cfg.copy(cautionBuyExtra = 0.0)).evaluate(buySeries, regime = Regime.CAUTION)
        assertEquals(SignalType.BUY, ok.type)
        assertTrue(ok.reasons.any { it.contains("Marché fragile") })
    }

    private val trendCfg = StrategyConfig(emaFastPeriod = 3, emaSlowPeriod = 5, rsiPeriod = 5)
    private val up = (0..40).map { 10.0 + 2.0 * it }.mapIndexed { i, p -> Candle("u$i", p, p, p, p, 5000.0) }

    @Test
    fun ideas_downgrade_buy_when_market_breaks() {
        assertEquals(Stance.BUY, StanceEvaluator.evaluate("UP", up, trendCfg).stance)
        val off = StanceEvaluator.evaluate("UP", up, trendCfg, regime = Regime.RISK_OFF)
        assertEquals(Stance.WATCH, off.stance)
        assertTrue(off.reasons.any { it.contains("Marché baissier") })
        assertEquals(Stance.WATCH, StanceEvaluator.evaluate("UP", up, trendCfg.copy(cautionStanceThreshold = 2.0), regime = Regime.CAUTION).stance)
        assertEquals(Stance.BUY, StanceEvaluator.evaluate("UP", up, trendCfg.copy(cautionStanceThreshold = 0.0), regime = Regime.CAUTION).stance)
    }

    @Test
    fun held_position_is_trimmed_when_market_breaks() {
        val flat = List(40) { 100.0 }.mapIndexed { i, p -> Candle("f$i", p, p, p, p, 5000.0) }
        assertEquals(ExitAction.HOLD, ExitAdvisor.advise("X", flat, 100.0, trendCfg).action)
        val a = ExitAdvisor.advise("X", flat, 100.0, trendCfg, regime = Regime.RISK_OFF)
        assertEquals(ExitAction.TRIM, a.action)
        assertTrue(a.reasons.any { it.contains("Marché en repli") })
    }

    @Test
    fun backtest_skips_entries_on_risk_off_days() {
        val wave = flats(listOf(100.0, 99.0, 98.0, 97.0, 96.0, 95.0, 94.0, 93.0, 92.0, 91.0, 90.0, 89.0, 88.0, 87.0,
            120.0, 121.0, 122.0, 123.0, 90.0, 89.0, 88.0, 110.0, 111.0, 112.0, 80.0, 81.0, 82.0, 83.0, 84.0, 85.0))
        val free = Backtester(cfg).run(wave)
        assertTrue(free.tradeCount > 0)
        val allOff = wave.associate { it.date to Regime.RISK_OFF }
        assertEquals(0, Backtester(cfg).run(wave, allOff).tradeCount)
    }
}
