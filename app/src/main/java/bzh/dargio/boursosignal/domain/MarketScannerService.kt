package bzh.dargio.boursosignal.domain

import bzh.dargio.boursosignal.data.repo.AdviceRepository
import bzh.dargio.boursosignal.data.repo.CalendarRepository
import bzh.dargio.boursosignal.data.repo.MarketRepository
import bzh.dargio.boursosignal.data.repo.StrategyRepository
import bzh.dargio.boursosignal.data.universe.UniverseEntry
import bzh.dargio.boursosignal.data.universe.UniverseProvider
import bzh.dargio.boursosignal.domain.signals.Candle
import bzh.dargio.boursosignal.domain.signals.EarningsGuard
import bzh.dargio.boursosignal.domain.signals.MarketRegimeEvaluator
import bzh.dargio.boursosignal.domain.signals.MarketScanner
import bzh.dargio.boursosignal.domain.signals.Opportunity
import bzh.dargio.boursosignal.domain.signals.RegimeReading
import bzh.dargio.boursosignal.domain.signals.Stance
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
    private val advice: AdviceRepository,
    private val calendar: CalendarRepository,
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

    /** Secteur et éligibilité PEA de chaque valeur de l'univers (§23-7, §23-11). */
    @Volatile
    private var meta: Map<String, UniverseEntry> = emptyMap()

    private fun metaMap(): Map<String, UniverseEntry> {
        if (meta.isEmpty()) meta = runCatching { universe.load().associateBy { it.ticker } }.getOrDefault(emptyMap())
        return meta
    }

    fun sectorOf(ticker: String): String? = metaMap()[ticker]?.sector?.takeIf { it.isNotBlank() }

    /** true = éligible PEA, false = hors PEA, null = valeur hors univers (inconnu). */
    fun isPea(ticker: String): Boolean? = metaMap()[ticker]?.pea

    /** Rafraîchit l'univers et recalcule le classement d'opportunités. */
    suspend fun scan() {
        if (_scanning.value) return
        _scanning.value = true
        try {
            val config = strategies.activeConfig()
            val entries = universe.load()
            _names.value = entries.associate { it.ticker to it.name }
            meta = entries.associateBy { it.ticker }

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
            val scanned = MarketScanner.scan(
                data, config, referenceDate = today, benchmark = benchmark, regime = reading?.regime,
                sectors = entries.associate { it.ticker to it.sector },
            )
            // Calendrier : pas d'achat proposé juste avant une publication de résultats (§23-4).
            val guarded = scanned.map { o ->
                if (o.stance != Stance.BUY) return@map o
                val events = calendar.events(o.ticker)
                if (EarningsGuard.earningsSoon(today, events, config.earningsBlackoutDays)) {
                    o.copy(
                        stance = Stance.WATCH,
                        reasons = o.reasons + "Résultats publiés le ${frDate(events?.earningsDate)} : achat différé (forte volatilité attendue)",
                    )
                } else o
            }
            _opportunities.value = guarded
            // Bilan (§23-1) : enregistre les idées d'achat pour mesurer ensuite leur résultat.
            guarded.filter { it.stance == Stance.BUY }.forEach {
                advice.record(it.ticker, "BUY", "idee", it.date, it.price, reading?.regime?.name)
            }
        } finally {
            _scanning.value = false
        }
    }

    private fun frDate(iso: String?): String =
        iso?.let { runCatching { LocalDate.parse(it).let { d -> "%02d/%02d".format(d.dayOfMonth, d.monthValue) } }.getOrNull() } ?: "?"

    private companion object {
        const val BENCHMARK_TICKER = "^FCHI" // CAC 40 sur Yahoo Finance
    }
}
