package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitAdvisorTest {

    private val cfg = StrategyConfig(emaFastPeriod = 3, emaSlowPeriod = 5, rsiPeriod = 5)
    private fun ser(prices: List<Double>) = prices.mapIndexed { i, p -> Candle("d$i", p, p, p, p, 5000.0) }

    @Test
    fun heavy_loss_triggers_sell() {
        val down = ser((0..40).map { 100.0 - 1.0 * it })
        val a = ExitAdvisor.advise("X", down, avgPrice = 90.0, config = cfg)
        assertEquals(ExitAction.SELL, a.action)
        assertTrue(a.reasons.isNotEmpty())
    }

    @Test
    fun neutral_position_is_hold() {
        val flat = ser(List(40) { 100.0 })
        assertEquals(ExitAction.HOLD, ExitAdvisor.advise("X", flat, avgPrice = 100.0, config = cfg).action)
    }

    @Test
    fun gain_with_overbought_is_trim() {
        val up = ser((0..40).map { 10.0 + 2.0 * it })
        assertEquals(ExitAction.TRIM, ExitAdvisor.advise("X", up, avgPrice = 90.0 / 1.10, config = cfg).action)
    }

    @Test
    fun pl_is_computed() {
        val flat = ser(List(40) { 100.0 })
        assertEquals(-20.0, ExitAdvisor.advise("X", flat, avgPrice = 125.0, config = cfg).plPct, 1e-6)
    }
}
