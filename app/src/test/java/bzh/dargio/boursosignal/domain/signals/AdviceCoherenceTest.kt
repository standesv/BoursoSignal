package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cohérence achat → vente : un achat conseillé ne doit pas être contredit par un
 * « Vendre » sans information nouvelle (cas Accor du 23–24/11/2026).
 */
class AdviceCoherenceTest {

    private fun bars(closes: List<Double>) =
        closes.mapIndexed { i, c -> Candle("j%03d".format(i), c, c + 1, c - 1, c, 1000.0) }

    private val trailCfg = StrategyConfig(
        emaFastPeriod = 3, emaSlowPeriod = 5, rsiPeriod = 5, volumeAvgPeriod = 5,
        volumeFactor = 100.0, buyThreshold = 1.0, sellThreshold = 1.0,
        stopLossPct = null, takeProfitPct = null, useAtrStop = false,
        trailingPeriod = 20, trailingAtrMultiple = 2.0, atrPeriod = 5,
    )

    /** Hausse jusqu'à 130 puis repli à 112 : on achète SUR le repli. */
    private val pullback = bars((100..130).map { it.toDouble() } + (129 downTo 112).map { it.toDouble() })

    @Test
    fun buying_on_a_pullback_does_not_trigger_trailing_stop() {
        val bought = SignalEngine(trailCfg).evaluate(pullback, avgEntryPrice = 112.0, entryDate = pullback.last().date)
        assertTrue(bought.reasons.none { it.contains("Stop suiveur") })
        // Date d'achat inconnue et position pas en gain : pas de stop suiveur non plus.
        val unknown = SignalEngine(trailCfg).evaluate(pullback, avgEntryPrice = 112.0)
        assertTrue(unknown.reasons.none { it.contains("Stop suiveur") })
    }

    @Test
    fun trailing_stop_still_protects_gains_made_since_purchase() {
        // Acheté à 125 pendant la hausse (j025) ; sommet 130 depuis l'achat, puis repli à 118.
        val series = bars((100..130).map { it.toDouble() } + (129 downTo 118).map { it.toDouble() })
        val r = SignalEngine(trailCfg).evaluate(series, avgEntryPrice = 125.0, entryDate = "j025")
        assertEquals(SignalType.SELL, r.type)
        assertTrue(r.reasons.any { it.contains("Stop suiveur") })
    }

    private val cfg = StrategyConfig(emaFastPeriod = 3, emaSlowPeriod = 5, rsiPeriod = 5)
    private val down = (0..40).map { 100.0 - it }.mapIndexed { i, p -> Candle("j%03d".format(i), p, p, p, p, 5000.0) }

    @Test
    fun no_reversal_before_a_new_session_since_purchase() {
        // Tendance baissière (« Éviter ») mais perte faible : sans séance depuis l'achat → Conserver.
        val fresh = ExitAdvisor.advise("X", down, avgPrice = 61.0, config = cfg, entryDate = down.last().date)
        assertEquals(ExitAction.HOLD, fresh.action)
        assertTrue(fresh.reasons.any { it.contains("pas encore de nouvelle séance") })
        // Avec des séances depuis l'achat, le conseil de vente reprend ses droits.
        val later = ExitAdvisor.advise("X", down, avgPrice = 61.0, config = cfg, entryDate = "j030")
        assertEquals(ExitAction.SELL, later.action)
    }

    @Test
    fun stop_loss_always_applies_even_just_after_purchase() {
        val r = ExitAdvisor.advise("X", down, avgPrice = 90.0, config = cfg, entryDate = down.last().date)
        assertEquals(ExitAction.SELL, r.action)
    }
}
