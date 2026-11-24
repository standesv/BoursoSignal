package bzh.dargio.boursosignal.work

import android.content.Context
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.domain.signals.HoldingWeight
import bzh.dargio.boursosignal.domain.signals.MarketClock
import bzh.dargio.boursosignal.domain.signals.MarketRegimeEvaluator
import bzh.dargio.boursosignal.domain.signals.WeeklyLine
import bzh.dargio.boursosignal.domain.signals.WeeklyReport
import bzh.dargio.boursosignal.domain.signals.WeeklySummary
import bzh.dargio.boursosignal.widget.WidgetSnapshot
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** Photo d'un portefeuille (réel ou test) aux derniers cours connus. */
data class PortfolioSnapshot(
    val total: Double,              // positions + liquidités connues
    val cash: Double?,
    val initialCapital: Double?,    // test uniquement
    val holdings: List<HoldingWeight>,
    val lines: List<WeeklyLine>,
)

object PortfolioSnapshots {

    /** Portefeuille test, ou null s'il n'est pas activé. */
    suspend fun test(container: AppContainer): PortfolioSnapshot? {
        val account = container.paperRepository.observeAccount().first() ?: return null
        if (!account.enabled) return null
        val positions = container.paperRepository.observePositions().first()
        return build(container, positions.map { Triple(it.ticker, it.quantity, it.avgPrice) }, account.cash, account.initialCapital)
    }

    /** Portefeuille réel, ou null s'il est vide (ni position ni liquidités renseignées). */
    suspend fun real(container: AppContainer): PortfolioSnapshot? {
        val positions = container.portfolioRepository.observePositions().first()
        val cash = container.portfolioRepository.observeCash().first()?.takeIf { it.initialized }?.cash
        if (positions.isEmpty() && cash == null) return null
        return build(container, positions.map { Triple(it.ticker, it.quantity, it.avgPrice) }, cash, null)
    }

    private suspend fun build(
        container: AppContainer,
        positions: List<Triple<String, Double, Double>>,
        cash: Double?,
        initialCapital: Double?,
    ): PortfolioSnapshot {
        val holdings = mutableListOf<HoldingWeight>()
        val lines = mutableListOf<WeeklyLine>()
        for ((ticker, quantity, avgPrice) in positions) {
            val candles = container.marketRepository.cachedCandles(ticker)
            val price = candles.lastOrNull()?.close ?: avgPrice
            holdings += HoldingWeight(ticker, PositionAlerts.displayName(container, ticker), quantity * price, container.marketScannerService.sectorOf(ticker))
            lines += WeeklyLine(ticker, quantity, candles)
        }
        return PortfolioSnapshot(holdings.sumOf { it.value } + (cash ?: 0.0), cash, initialCapital, holdings, lines)
    }
}

/** Contenu du bilan hebdomadaire (§23-8). */
data class WeeklyDigestData(
    val test: WeeklySummary?,
    val real: WeeklySummary?,
    val cacPct: Double?,
    val adviceCounts: Map<String, Int>,   // "BUY" / "SELL" / "TRIM" → nombre sur 7 jours
    val attention: List<String>,
)

object WeeklyDigest {

    suspend fun compute(container: AppContainer): WeeklyDigestData {
        val index = container.marketRepository.cachedCandles(MarketRegimeEvaluator.INDEX_TICKER)
        val test = PortfolioSnapshots.test(container)
        val real = PortfolioSnapshots.real(container)
        val since = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        val advices = container.adviceRepository.since(since)
        return WeeklyDigestData(
            test = test?.takeIf { it.lines.isNotEmpty() }?.let { WeeklyReport.positionsChange(it.lines) },
            real = real?.takeIf { it.lines.isNotEmpty() }?.let { WeeklyReport.positionsChange(it.lines) },
            cacPct = WeeklyReport.indexChangePct(index),
            adviceCounts = advices.groupingBy { it.kind }.eachCount(),
            attention = PositionAlerts.attentionNames(container),
        )
    }

    fun text(d: WeeklyDigestData): String {
        val parts = mutableListOf<String>()
        d.test?.let { parts += "Positions test : ${eurSigned(it.changeEuros)}${it.changePct?.let { p -> " (${pctSigned(p)})" } ?: ""}" }
        d.real?.let { parts += "Positions réelles : ${eurSigned(it.changeEuros)}${it.changePct?.let { p -> " (${pctSigned(p)})" } ?: ""}" }
        d.cacPct?.let { parts += "CAC 40 : ${pctSigned(it)}" }
        val counts = listOf("BUY" to "achat", "SELL" to "vente", "TRIM" to "allègement")
            .mapNotNull { (k, label) -> d.adviceCounts[k]?.let { n -> "$n $label${if (n > 1) "s" else ""}" } }
        val head = if (parts.isEmpty()) "Pas de position suivie cette semaine." else parts.joinToString(" · ") + " (5 séances)."
        val advice = if (counts.isEmpty()) " Aucun conseil cette semaine." else " Conseils : ${counts.joinToString(", ")}."
        val watch = if (d.attention.isEmpty()) "" else " À surveiller : ${d.attention.joinToString(", ")}."
        return head + advice + watch
    }

    fun eurSigned(v: Double): String = String.format(Locale.FRANCE, "%+.0f €", v)
    fun pctSigned(v: Double): String = String.format(Locale.FRANCE, "%+.1f %%", v)
}

/** Une vérification de séance : météo, positions, alertes de prix, widget (§23-9). */
object PositionWatch {
    suspend fun runOnce(context: Context, container: AppContainer, now: LocalDateTime = LocalDateTime.now(ZoneId.of("Europe/Paris"))) {
        if (!MarketClock.isWatchTime(now)) return
        PositionAlerts.checkRegime(context, container, now, refreshIndex = true)
        val pending = PositionAlerts.checkHeld(context, container, now)
        PositionAlerts.checkPriceAlerts(context, container, now)
        container.settings.lastScanAt = System.currentTimeMillis()
        WidgetSnapshot.refresh(context, container, pending)
    }
}
