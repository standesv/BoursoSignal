package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BacktesterTest {

    private val cfg = StrategyConfig(
        emaFastPeriod = 3, emaSlowPeriod = 5, rsiPeriod = 5, volumeAvgPeriod = 5,
        volumeFactor = 100.0, buyThreshold = 1.0, sellThreshold = 1.0,
        stopLossPct = -0.08, takeProfitPct = 0.15,
    )

    private fun candles(prices: List<Double>) =
        prices.mapIndexed { i, p -> Candle("2026-01-${i + 1}", p, p, p, p, 1000.0) }

    @Test
    fun flat_series_has_no_trades() {
        val r = Backtester(cfg).run(candles(List(40) { 100.0 }))
        assertEquals(0, r.tradeCount)
        assertEquals(0.0, r.totalReturnPct, 1e-6)
    }

    @Test
    fun is_deterministic() {
        val wave = candles(
            listOf(100.0, 99.0, 98.0, 97.0, 96.0, 95.0, 94.0, 93.0, 92.0, 91.0, 90.0, 89.0, 88.0, 87.0,
                120.0, 121.0, 122.0, 123.0, 90.0, 89.0, 88.0, 110.0, 111.0, 112.0, 80.0)
        )
        val bt = Backtester(cfg)
        val r1 = bt.run(wave)
        val r2 = bt.run(wave)
        assertEquals(r1.tradeCount, r2.tradeCount)
        assertEquals(r1.totalReturnPct, r2.totalReturnPct, 1e-9)
    }

    @Test
    fun produces_trades_and_consistent_bookkeeping() {
        val wave = candles(
            listOf(100.0, 99.0, 98.0, 97.0, 96.0, 95.0, 94.0, 93.0, 92.0, 91.0, 90.0, 89.0, 88.0, 87.0,
                120.0, 121.0, 122.0, 123.0, 90.0, 89.0, 88.0, 110.0, 111.0, 112.0, 80.0)
        )
        val r = Backtester(cfg).run(wave)
        assertTrue("au moins un trade attendu", r.tradeCount >= 1)
        assertTrue(r.winRatePct in 0.0..100.0)
        assertTrue(r.maxDrawdownPct >= 0.0)
        val t = r.trades.first()
        assertEquals((t.exitPrice - t.entryPrice) / t.entryPrice * 100.0, t.returnPct, 1e-9)
    }
}
