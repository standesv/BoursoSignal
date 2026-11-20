package bzh.dargio.boursosignal.domain

import bzh.dargio.boursosignal.data.repo.MarketRepository
import bzh.dargio.boursosignal.data.repo.StrategyRepository
import bzh.dargio.boursosignal.data.universe.UniverseProvider
import bzh.dargio.boursosignal.domain.signals.Candle
import bzh.dargio.boursosignal.domain.signals.MarketRegimeEvaluator
import bzh.dargio.boursosignal.domain.signals.MarketScanner
import bzh.dargio.boursosignal.domain.signals.Opportunity
import bzh.dargio.boursosignal.domain.signals.RegimeReading
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Scanne tout l'univers Euronext Paris : rafraîchit les cours, écarte les valeurs
 * aux données non fiables (contrôle qualité) et publie un classement
 * d'opportunités. Récupère aussi le CAC 40 (^FCHI) comme référence pour la force
 * relative. Le résultat alimente le fil d'opportunités de l'accueil.
 */
class MarketScannerService(
    private val universe: UniverseProvider,
    private val market: MarketRepository,
    private val strategies: StrategyRepository,
) {

    private val _opportunities = MutableStateFlow<List<Opportunity>>(emptyList())
    val opportunities = _opportunities.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()

    /** Météo du marché (CAC 40) issue du dernier scan ou du cache. */
    private val _regime = MutableStateFlow<RegimeReading?>(null)
    val regime = _regime.asStateFlow()

    /** Lit la météo du marché depuis les cours du CAC 40 en cache (et la publie). */
    suspend fun regimeFromCache(): RegimeReading? {
        val index = market.cachedCandles(MarketRegimeEvaluator.INDEX_TICKER)
        if (index.isEmpty()) return _regime.value
        return MarketRegimeEvaluator.evaluate(index).also { _regime.value = it }
    }

    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())
    val names = _names.asStateFlow()

    fun displayName(ticker: String): String = _names.value[ticker] ?: ticker

    /** Rafraîchit l'univers et recalcule le classement d'opportunités. */
    suspend fun scan() {
        if (_scanning.value) return
        _scanning.value = true
        try {
            val config = strategies.activeConfig()
            val entries = universe.load()
            _names.value = entries.associate { it.ticker to it.name }

            // Référence de marché pour la force relative (CAC 40).
            val benchmark = market.refresh(BENCHMARK_TICKER).getOrNull()
            // Météo du marché : conditionne les achats proposés.
            val reading = benchmark?.takeIf { it.isNotEmpty() }?.let { MarketRegimeEvaluator.evaluate(it) }
            if (reading != null) _regime.value = reading

            val data = LinkedHashMap<String, List<Candle>>()
            val today = LocalDate.now().toString()
            for (entry in entries) {
                val candles = market.refresh(entry.ticker).getOrNull()
                if (!candles.isNullOrEmpty()) data[entry.ticker] = candles
                delay(120) // cadence douce pour ménager la source gratuite
            }
            _opportunities.value = MarketScanner.scan(
                data, config, referenceDate = today, benchmark = benchmark, regime = reading?.regime,
            )
        } finally {
            _scanning.value = false
        }
    }

    private companion object {
        const val BENCHMARK_TICKER = "^FCHI" // CAC 40 sur Yahoo Finance
    }
}
