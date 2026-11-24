package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Évolutions v3 du cahier des charges (§23). */
class V3FeaturesTest {

    private fun bars(closes: List<Double>, spread: Double = 1.0) =
        closes.mapIndexed { i, c -> Candle("j%03d".format(i), c, c + spread, c - spread, c, 5000.0) }

    // ---- §23-1 Bilan des conseils ----
    @Test
    fun forward_return_and_stats() {
        val c = bars((0..30).map { 100.0 + it })
        assertEquals(115.0 / 110.0 * 100 - 100, AdviceEvaluator.forwardReturn(c, "j010", 110.0, 5)!!, 1e-9)
        assertNull(AdviceEvaluator.forwardReturn(c, "j028", 128.0, 5))      // séances pas encore là
        val stats = AdviceEvaluator.statsByKind(listOf(
            AdviceRecord("A", "BUY", "j1", 10.0, ret5 = 2.0),
            AdviceRecord("B", "BUY", "j1", 10.0, ret5 = -1.0),
            AdviceRecord("C", "SELL", "j1", 10.0, ret5 = -3.0),
            AdviceRecord("D", "BUY", "j1", 10.0),                           // pas encore évalué
        ))
        assertEquals("Achat", stats[0].label)
        assertEquals(3, stats[0].count)
        assertEquals(2, stats[0].evaluated)
        assertEquals(50.0, stats[0].hitRatePct!!, 1e-9)
        assertEquals(100.0, stats[1].hitRatePct!!, 1e-9)                    // vente suivie d'une baisse = juste
    }

    // ---- §23-5 Alertes de prix ----
    @Test
    fun price_alert_rule() {
        assertTrue(PriceAlertRule.isTriggered(above = true, level = 50.0, price = 50.2))
        assertFalse(PriceAlertRule.isTriggered(above = true, level = 50.0, price = 49.9))
        assertTrue(PriceAlertRule.isTriggered(above = false, level = 46.0, price = 45.8))
        assertFalse(PriceAlertRule.isTriggered(above = false, level = 46.0, price = 0.0))
    }

    // ---- §23-3 Plan chiffré ----
    private val cfg = StrategyConfig(emaFastPeriod = 3, emaSlowPeriod = 5, rsiPeriod = 5)

    @Test
    fun plan_at_new_highs_aims_two_times_the_risk() {
        val up = bars((0..60).map { 100.0 + it })                       // au plus haut : pas de résistance
        val p = TradePlanner.plan(up, cfg)!!
        assertNull(p.resistance)
        assertEquals(2.0, p.rewardRisk, 1e-9)
        assertTrue(p.stop < p.entry && p.target > p.entry)
        // stop = le plus serré de l'ATR (2,5 × ~2) et du stop-loss -12 %
        assertEquals(p.entry - 2.5 * 2.0, p.stop, 0.2)
    }

    @Test
    fun plan_caps_target_at_nearby_resistance() {
        val s = bars((0..60).map { 100.0 + it } + listOf(158.0, 157.0))  // repli juste sous le plus haut 160
        val p = TradePlanner.plan(s, cfg)!!
        assertEquals(160.0, p.resistance!!, 1e-9)
        assertEquals(160.0, p.target, 1e-9)
        assertTrue(p.rewardRisk < 1.5)
    }

    @Test
    fun position_size_risks_one_percent_and_respects_caps() {
        val plan = TradePlan(entry = 100.0, stop = 95.0, target = 110.0, rewardRisk = 2.0, resistance = null)
        assertEquals(20, TradePlanner.size(plan, capital = 10_000.0, cash = null).quantity)   // 100 € / 5 €
        val byCash = TradePlanner.size(plan, capital = 10_000.0, cash = 1_500.0)
        assertEquals(15, byCash.quantity)
        assertEquals("liquidités disponibles", byCash.limitedBy)
        val wide = TradePlan(100.0, 99.0, 102.0, 2.0, null)                                   // stop serré : plafond 20 %
        assertEquals(20, TradePlanner.size(wide, 10_000.0, null).quantity)
    }

    @Test
    fun ideas_buy_is_withdrawn_when_resistance_is_too_close() {
        val flat = { closes: List<Double> -> closes.mapIndexed { i, c -> Candle("u%03d".format(i), c, c, c, c, 5000.0) } }
        val up = (0..40).map { 10.0 + 2.0 * it }
        val withDip = flat(up + listOf(89.0))                           // 1 % sous le plus haut 90
        val free = StanceEvaluator.evaluate("X", withDip, cfg.copy(useTradePlanFilter = false))
        assertEquals(Stance.BUY, free.stance)
        val filtered = StanceEvaluator.evaluate("X", withDip, cfg)
        assertEquals(Stance.WATCH, filtered.stance)
        assertTrue(filtered.reasons.any { it.contains("Résistance proche") })
        assertNotNull(filtered.plan)
    }

    // ---- §23-6 Diversification ----
    @Test
    fun diversification_warnings() {
        val h = listOf(
            HoldingWeight("A", "BNP", 3000.0, "Banques"),
            HoldingWeight("B", "SocGen", 1000.0, "Banques"),
            HoldingWeight("C", "Crédit Agricole", 1000.0, "Banques"),
            HoldingWeight("D", "LVMH", 1000.0, "Luxe"),
        )
        val w = Diversification.warnings(h, total = 10_000.0)
        assertTrue(w.any { it.startsWith("BNP pèse 30") })
        assertTrue(w.any { it.startsWith("Secteur Banques : 50") })
        assertTrue(w.any { it.startsWith("3 positions dans le secteur Banques") })
        val buy = Diversification.warningsForBuy(h, 10_000.0, "D", "LVMH", "Luxe", 1_500.0)
        assertTrue(buy.any { it.startsWith("LVMH pèse 25") })
        assertTrue(Diversification.warningsForBuy(h, 10_000.0, "E", "Orange", "Télécoms", 500.0).isEmpty())
    }

    // ---- §23-7 Force des secteurs ----
    @Test
    fun sector_strength_averages_members_vs_index() {
        val bench = List(70) { 100.0 }
        fun line(endPct: Double) = bars((0 until 70).map { 100.0 * (1 + endPct / 100.0 * it / 69.0) })
        val data = mapOf("A" to line(10.0), "B" to line(6.0), "C" to line(-8.0), "D" to line(-4.0), "E" to line(20.0))
        val sectors = mapOf("A" to "Tech", "B" to "Tech", "C" to "Immobilier", "D" to "Immobilier", "E" to "Seul")
        val rs = SectorStrength.compute(data, sectors, bench)
        assertTrue(rs.getValue("Tech") > 2.0)
        assertTrue(rs.getValue("Immobilier") < -2.0)
        assertFalse(rs.containsKey("Seul"))                               // un seul membre : non significatif
    }

    // ---- §23-4 Calendrier ----
    @Test
    fun earnings_blackout() {
        val e = CompanyEvents(earningsDate = "2026-11-26", exDividendDate = null)
        assertTrue(EarningsGuard.earningsSoon("2026-11-24", e, 3))
        assertFalse(EarningsGuard.earningsSoon("2026-11-20", e, 3))
        assertFalse(EarningsGuard.earningsSoon("2026-11-27", e, 3))      // déjà passé
        assertFalse(EarningsGuard.earningsSoon("2026-11-24", null, 3))
    }

    // ---- §23-8 Bilan hebdomadaire ----
    @Test
    fun weekly_change_of_held_positions() {
        val s = WeeklyReport.positionsChange(listOf(WeeklyLine("A", 10.0, bars((0..10).map { 100.0 + it }))))
        assertEquals(1100.0, s.valueNow, 1e-9)
        assertEquals(1050.0, s.valueWeekAgo, 1e-9)
        assertEquals(50.0 / 1050.0 * 100, s.changePct!!, 1e-9)
        assertEquals(110.0 / 105.0 * 100 - 100, WeeklyReport.indexChangePct(bars((0..10).map { 100.0 + it }))!!, 1e-9)
    }

    // ---- §23-2 Backtest réaliste ----
    @Test
    fun realistic_backtest_uses_next_open_fees_and_two_periods() {
        // Vagues haussières : des achats et des sorties ont lieu.
        val closes = (0 until 160).map { 100.0 + it * 0.3 + 8.0 * kotlin.math.sin(it / 6.0) }
        val candles = closes.mapIndexed { i, c -> Candle("k%03d".format(i), c - 0.5, c + 1.0, c - 1.5, c, 5000.0) }
        val free = AdviceBacktester(cfg, FeeProfile.NONE).run("X", candles)!!
        val paid = AdviceBacktester(cfg, Fees.DECOUVERTE).run("X", candles)!!
        assertTrue(free.trades > 0)
        assertEquals(0.0, free.feesEuros, 1e-9)
        assertTrue(paid.feesEuros > 0.0)
        assertTrue(paid.strategyPct < free.strategyPct)
        // Les deux moitiés se composent exactement en la performance totale.
        val composed = ((1 + paid.firstHalfPct / 100) * (1 + paid.secondHalfPct / 100) - 1) * 100
        assertEquals(paid.strategyPct, composed, 1e-6)
        val lab = RealisticBacktestLab.run(mapOf("X" to candles, "Y" to candles), cfg, fee = Fees.DECOUVERTE)
        assertEquals(2, lab.count)
        assertEquals(paid.strategyPct, lab.avgStrategyPct, 1e-9)
    }
}
