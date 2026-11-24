package bzh.dargio.boursosignal.domain.signals

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Poids d'une position (valeur de marché) pour les contrôles de diversification. */
data class HoldingWeight(val ticker: String, val name: String, val value: Double, val sector: String?)

/** Contrôles de diversification (§23-6). Logique pure et testable. */
object Diversification {

    fun warnings(holdings: List<HoldingWeight>, total: Double, config: StrategyConfig = StrategyConfig()): List<String> {
        if (total <= 0.0) return emptyList()
        val out = mutableListOf<String>()
        holdings.sortedByDescending { it.value }.forEach { h ->
            val w = h.value / total * 100.0
            if (w > config.maxPositionPct) out += "${h.name} pèse ${pct(w)} du portefeuille (seuil ${config.maxPositionPct.toInt()} %)"
        }
        holdings.filter { !it.sector.isNullOrBlank() }.groupBy { it.sector!! }.forEach { (sector, list) ->
            val w = list.sumOf { it.value } / total * 100.0
            if (w > config.maxSectorPct) out += "Secteur $sector : ${pct(w)} du portefeuille (seuil ${config.maxSectorPct.toInt()} %)"
            if (list.size >= config.maxPerSector) out += "${list.size} positions dans le secteur $sector : elles risquent de baisser ensemble"
        }
        return out
    }

    /** Avertissements, après l'achat de `amount` €, qui concernent cette valeur ou son secteur. */
    fun warningsForBuy(
        holdings: List<HoldingWeight>,
        totalAfter: Double,
        ticker: String,
        name: String,
        sector: String?,
        amount: Double,
        config: StrategyConfig = StrategyConfig(),
    ): List<String> {
        val existing = holdings.find { it.ticker == ticker }
        val after = holdings.filter { it.ticker != ticker } +
            HoldingWeight(ticker, name, (existing?.value ?: 0.0) + amount, sector)
        return warnings(after, totalAfter, config).filter { w ->
            w.startsWith(name) || (!sector.isNullOrBlank() && w.contains("secteur $sector", ignoreCase = true))
        }
    }

    private fun pct(v: Double) = "${Math.round(v)} %"
}

/** Force des secteurs vs CAC 40 (§23-7). */
object SectorStrength {
    /** Moyenne, par secteur, de la force relative de ses valeurs (points vs CAC 40, ~3 mois). */
    fun compute(data: Map<String, List<Candle>>, sectorOf: Map<String, String>, benchCloses: List<Double>): Map<String, Double> {
        val bySector = HashMap<String, MutableList<Double>>()
        data.forEach { (ticker, candles) ->
            val sector = sectorOf[ticker]?.takeIf { it.isNotBlank() } ?: return@forEach
            val rs = Indicators.relativeStrengthPct(candles.closes(), benchCloses) ?: return@forEach
            bySector.getOrPut(sector) { mutableListOf() } += rs
        }
        return bySector.filterValues { it.size >= 2 }.mapValues { it.value.average() }
    }
}

/** Prochaines dates connues d'une société (§23-4). */
data class CompanyEvents(val earningsDate: String?, val exDividendDate: String?)

object EarningsGuard {
    fun daysUntil(today: String, date: String?): Long? =
        date?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(today), LocalDate.parse(it)) }.getOrNull() }

    /** Publication de résultats dans les `days` prochains jours (aujourd'hui compris). */
    fun earningsSoon(today: String, events: CompanyEvents?, days: Int): Boolean {
        val d = daysUntil(today, events?.earningsDate) ?: return false
        return d in 0..days.toLong()
    }
}

/** Une position pour le bilan hebdomadaire (§23-8). */
data class WeeklyLine(val ticker: String, val quantity: Double, val candles: List<Candle>)

data class WeeklySummary(val valueNow: Double, val valueWeekAgo: Double, val changeEuros: Double, val changePct: Double?)

object WeeklyReport {
    /** Variation, sur les 5 dernières séances, de la valeur des positions détenues aujourd'hui. */
    fun positionsChange(lines: List<WeeklyLine>): WeeklySummary {
        var now = 0.0
        var before = 0.0
        lines.forEach { l ->
            val c = l.candles
            if (c.isEmpty()) return@forEach
            now += l.quantity * c.last().close
            before += l.quantity * (c.getOrNull(c.lastIndex - 5)?.close ?: c.first().close)
        }
        val chg = now - before
        return WeeklySummary(now, before, chg, if (before > 0.0) chg / before * 100.0 else null)
    }

    /** Variation (%) de l'indice sur les 5 dernières séances. */
    fun indexChangePct(index: List<Candle>): Double? {
        if (index.size < 6) return null
        val ago = index[index.lastIndex - 5].close
        return if (ago > 0.0) (index.last().close / ago - 1.0) * 100.0 else null
    }
}
