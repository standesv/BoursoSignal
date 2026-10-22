package bzh.dargio.boursosignal.domain

import bzh.dargio.boursosignal.data.repo.MarketRepository
import bzh.dargio.boursosignal.data.repo.StrategyRepository
import bzh.dargio.boursosignal.data.universe.UniverseProvider
import bzh.dargio.boursosignal.domain.signals.Candle
import bzh.dargio.boursosignal.domain.signals.MarketScanner
import bzh.dargio.boursosignal.domain.signals.Opportunity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Scanne tout l'univers Euronext Paris : rafraîchit les cours, écarte les valeurs
 * aux données non fiables (contrôle qualité) et publie un classement
 * d'opportunités. Le résultat alimente le fil d'opportunités de l'accueil.
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

            val data = LinkedHashMap<String, List<Candle>>()
            val today = LocalDate.now().toString()
            for (entry in entries) {
                val candles = market.refresh(entry.ticker).getOrNull()
                if (!candles.isNullOrEmpty()) data[entry.ticker] = candles
                delay(120) // cadence douce pour ménager la source gratuite
            }
            _opportunities.value = MarketScanner.scan(data, config, referenceDate = today)
        } finally {
            _scanning.value = false
        }
    }
}
