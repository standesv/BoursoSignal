package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorsTest {

    private val eps = 1e-9

    @Test
    fun sma_basic() {
        val r = Indicators.sma(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertNull(r[0]); assertNull(r[1])
        assertEquals(2.0, r[2]!!, eps)
        assertEquals(3.0, r[3]!!, eps)
        assertEquals(4.0, r[4]!!, eps)
    }

    @Test
    fun ema_basic_seeded_by_sma() {
        // Sur une série linéaire, EMA amorcée par SMA coïncide avec la SMA.
        val r = Indicators.ema(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertNull(r[0]); assertNull(r[1])
        assertEquals(2.0, r[2]!!, eps)
        assertEquals(3.0, r[3]!!, eps)
        assertEquals(4.0, r[4]!!, eps)
    }

    @Test
    fun rsi_all_gains_is_100() {
        val up = (1..20).map { it.toDouble() }
        val r = Indicators.rsi(up, 14)
        val defined = r.filterNotNull()
        assertTrue(defined.isNotEmpty())
        defined.forEach { assertEquals(100.0, it, eps) }
    }

    @Test
    fun rsi_all_losses_is_0() {
        val down = (20 downTo 1).map { it.toDouble() }
        val r = Indicators.rsi(down, 14)
        val defined = r.filterNotNull()
        assertTrue(defined.isNotEmpty())
        defined.forEach { assertEquals(0.0, it, eps) }
    }

    @Test
    fun rsi_stays_within_bounds() {
        val noisy = listOf(
            44.0, 44.25, 44.5, 43.75, 44.5, 45.0, 47.0, 46.0, 46.5, 46.25,
            45.75, 46.0, 47.0, 48.0, 47.5, 47.0, 46.5, 46.0, 46.25, 47.75, 47.25,
        )
        val r = Indicators.rsi(noisy, 14)
        r.filterNotNull().forEach { assertTrue("RSI hors bornes: $it", it in 0.0..100.0) }
    }

    @Test
    fun macd_constant_series_is_zero() {
        val flat = List(40) { 50.0 }
        val m = Indicators.macd(flat)
        assertEquals(0.0, m.macd.last()!!, 1e-6)
        assertEquals(0.0, m.histogram.last()!!, 1e-6)
    }

    @Test
    fun macd_uptrend_is_positive() {
        val up = (1..40).map { it.toDouble() }
        val m = Indicators.macd(up)
        assertTrue("MACD devrait être > 0 en tendance haussière", m.macd.last()!! > 0.0)
    }

    @Test
    fun bollinger_constant_series_has_zero_width() {
        val flat = List(25) { 10.0 }
        val b = Indicators.bollinger(flat, 20, 2.0)
        assertEquals(10.0, b.middle.last()!!, eps)
        assertEquals(10.0, b.upper.last()!!, eps)
        assertEquals(10.0, b.lower.last()!!, eps)
    }

    @Test
    fun bollinger_known_window() {
        // Fenêtre [2,4,4,4,6], moyenne 4, écart-type population = sqrt(1.6).
        val values = listOf(2.0, 4.0, 4.0, 4.0, 6.0)
        val b = Indicators.bollinger(values, 5, 2.0)
        assertEquals(4.0, b.middle[4]!!, eps)
        assertEquals(4.0 + 2.0 * Math.sqrt(1.6), b.upper[4]!!, 1e-6)
        assertEquals(4.0 - 2.0 * Math.sqrt(1.6), b.lower[4]!!, 1e-6)
    }
}
