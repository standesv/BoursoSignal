package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisTest {

    private fun candles(prices: List<Double>, vol: Double = 5000.0) =
        prices.mapIndexed { i, p -> Candle("d$i", p, p, p, p, vol) }

    @Test
    fun risk_thresholds() {
        assertEquals(RiskLevel.LOW, Analyzer.riskFrom(10.0))
        assertEquals(RiskLevel.MEDIUM, Analyzer.riskFrom(30.0))
        assertEquals(RiskLevel.HIGH, Analyzer.riskFrom(50.0))
        assertEquals(RiskLevel.MEDIUM, Analyzer.riskFrom(null))
    }

    @Test
    fun flat_series_is_low_volatility() {
        val vol = Analyzer.annualVolatilityPct(List(60) { 100.0 })
        assertNotNull(vol)
        assertTrue(vol!! < 1.0)
    }

    @Test
    fun choppy_series_is_high_risk() {
        val swing = List(60) { if (it % 2 == 0) 100.0 else 118.0 }
        assertEquals(RiskLevel.HIGH, Analyzer.riskFrom(Analyzer.annualVolatilityPct(swing)))
    }

    @Test
    fun analyze_uptrend_is_rich_and_low_risk() {
        val report = Analyzer.analyze(candles((0..149).map { 30.0 + 0.5 * it }))
        assertTrue(report.trendUp)
        assertTrue(report.highlights.size >= 3)
        assertTrue(report.suggestion.isNotBlank())
        assertNotNull(report.perf1M)
        assertEquals(RiskLevel.LOW, report.risk)
    }
}
