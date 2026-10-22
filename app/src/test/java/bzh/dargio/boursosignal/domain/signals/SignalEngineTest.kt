package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalEngineTest {

    /** Config aux périodes courtes pour tester avec des séries compactes. */
    private val cfg = StrategyConfig(
        emaFastPeriod = 3,
        emaSlowPeriod = 5,
        rsiPeriod = 5,
        volumeAvgPeriod = 5,
        volumeFactor = 100.0, // le volume ne déclenche pas dans ces tests
        buyThreshold = 1.0,
        sellThreshold = 1.0,
        stopLossPct = -0.08,
        takeProfitPct = 0.15,
    )

    private fun candles(prices: List<Double>, vol: Double = 1000.0): List<Candle> =
        prices.mapIndexed { i, p -> Candle("2026-01-${i + 1}", p, p, p, p, vol) }

    @Test
    fun insufficient_history_returns_none() {
        val r = SignalEngine(cfg).evaluate(candles(listOf(10.0, 11.0, 12.0)))
        assertEquals(SignalType.NONE, r.type)
    }

    @Test
    fun bullish_cross_triggers_buy() {
        // Déclin régulier puis forte hausse finale : l'EMA courte repasse au-dessus
        // de l'EMA longue sur la dernière bougie.
        val prices = listOf(
            100.0, 99.0, 98.0, 97.0, 96.0, 95.0, 94.0, 93.0, 92.0, 91.0,
            90.0, 89.0, 88.0, 87.0, 120.0,
        )
        val r = SignalEngine(cfg).evaluate(candles(prices))
        assertEquals("devrait être un signal d'achat", SignalType.BUY, r.type)
        assertTrue(r.score >= cfg.buyThreshold)
        assertTrue(r.reasons.any { it.contains("EMA") })
    }

    @Test
    fun bearish_cross_triggers_sell() {
        // Hausse régulière puis forte chute finale : croisement baissier.
        val prices = listOf(
            80.0, 81.0, 82.0, 83.0, 84.0, 85.0, 86.0, 87.0, 88.0, 89.0,
            90.0, 91.0, 92.0, 93.0, 60.0,
        )
        val r = SignalEngine(cfg).evaluate(candles(prices))
        assertEquals("devrait être un signal de vente", SignalType.SELL, r.type)
        assertTrue(r.reasons.any { it.contains("EMA") })
    }

    @Test
    fun flat_series_without_position_is_none() {
        val r = SignalEngine(cfg).evaluate(candles(List(15) { 100.0 }))
        assertEquals(SignalType.NONE, r.type)
    }

    @Test
    fun stop_loss_triggers_sell_even_without_cross() {
        // Série plate (aucun croisement) mais position en perte de 16 %.
        val r = SignalEngine(cfg).evaluate(candles(List(15) { 100.0 }), avgEntryPrice = 120.0)
        assertEquals(SignalType.SELL, r.type)
        assertTrue("devrait mentionner le stop-loss", r.reasons.any { it.contains("Stop-loss") })
    }

    @Test
    fun take_profit_triggers_sell() {
        val r = SignalEngine(cfg).evaluate(candles(List(15) { 100.0 }), avgEntryPrice = 80.0)
        assertEquals(SignalType.SELL, r.type)
        assertTrue(r.reasons.any { it.contains("Take-profit") })
    }
}
