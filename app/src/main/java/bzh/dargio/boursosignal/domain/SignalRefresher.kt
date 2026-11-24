package bzh.dargio.boursosignal.domain

import bzh.dargio.boursosignal.data.local.SignalEntity
import bzh.dargio.boursosignal.data.repo.MarketRepository
import bzh.dargio.boursosignal.data.repo.PortfolioRepository
import bzh.dargio.boursosignal.data.repo.SignalRepository
import bzh.dargio.boursosignal.data.repo.StrategyRepository
import bzh.dargio.boursosignal.data.repo.WatchlistRepository
import bzh.dargio.boursosignal.domain.signals.MarketRegimeEvaluator
import bzh.dargio.boursosignal.domain.signals.SignalEngine
import bzh.dargio.boursosignal.domain.signals.SignalResult
import bzh.dargio.boursosignal.domain.signals.SignalType
import bzh.dargio.boursosignal.domain.signals.TradePlanner

/** Un signal nouvellement émis, destiné aux notifications. */
data class EmittedSignal(val ticker: String, val name: String, val result: SignalResult)

/**
 * Orchestrateur du cycle d'évaluation (§3, §10) : pour chaque valeur suivie, il
 * rafraîchit les cours, exécute le moteur avec la stratégie active et la position
 * détenue, puis journalise tout nouveau signal (avec déduplication).
 *
 * Code sans dépendance Android : réutilisable par le WorkManager et par un appel
 * manuel depuis l'UI.
 */
class SignalRefresher(
    private val watchlist: WatchlistRepository,
    private val market: MarketRepository,
    private val strategies: StrategyRepository,
    private val portfolio: PortfolioRepository,
    private val signals: SignalRepository,
) {

    /** Évalue toute la watchlist et renvoie les signaux nouvellement émis. */
    suspend fun refreshAll(): List<EmittedSignal> {
        val config = strategies.activeConfig()
        val engine = SignalEngine(config)
        val emitted = mutableListOf<EmittedSignal>()

        // Météo du marché (CAC 40) : pas de signal d'achat quand le marché casse.
        val index = market.refresh(MarketRegimeEvaluator.INDEX_TICKER).getOrNull()
            ?: market.cachedCandles(MarketRegimeEvaluator.INDEX_TICKER)
        val regime = if (index.isNotEmpty()) MarketRegimeEvaluator.evaluate(index).regime else null

        for (instrument in watchlist.watchlist()) {
            val candles = market.refresh(instrument.ticker).getOrNull() ?: continue
            if (candles.isEmpty()) continue
            val avgEntry = portfolio.position(instrument.ticker)?.avgPrice
            val result = engine.evaluate(candles, avgEntry, regime)
            if (result.type == SignalType.NONE) continue
            // Cohérence avec Idées (§23-3) : pas de signal d'achat si une résistance proche limite le gain.
            if (result.type == SignalType.BUY && config.useTradePlanFilter) {
                val plan = TradePlanner.plan(candles, config)
                if (plan != null && plan.rewardRisk < config.minRewardRisk) continue
            }

            // Déduplication : pas de ré-émission pour la même bougie et le même sens.
            val last = signals.latestFor(instrument.ticker)
            val isDuplicate = last != null && last.date == result.date && last.type == result.type.name
            if (isDuplicate) continue

            signals.insert(
                SignalEntity(
                    ticker = instrument.ticker,
                    type = result.type.name,
                    score = result.score,
                    reasons = result.reasons.joinToString(" · "),
                    date = result.date,
                    price = result.price,
                )
            )
            emitted += EmittedSignal(instrument.ticker, instrument.name, result)
        }
        return emitted
    }
}
