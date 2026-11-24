package bzh.dargio.boursosignal.data.repo

import bzh.dargio.boursosignal.data.local.AdviceDao
import bzh.dargio.boursosignal.data.local.AdviceLogEntity
import bzh.dargio.boursosignal.data.local.PriceAlertDao
import bzh.dargio.boursosignal.data.local.PriceAlertEntity
import bzh.dargio.boursosignal.domain.signals.AdviceEvaluator
import bzh.dargio.boursosignal.domain.signals.AdviceRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Journal des conseils et mesure de leur résultat (§23-1). */
class AdviceRepository(private val dao: AdviceDao) {

    fun observeAll(): Flow<List<AdviceLogEntity>> = dao.observeAll()

    suspend fun since(sinceMillis: Long): List<AdviceLogEntity> = dao.since(sinceMillis)

    /**
     * Enregistre un conseil, sauf s'il a déjà été enregistré pour la même valeur, le
     * même type et le même portefeuille dans les 7 derniers jours (une idée qui reste
     * « Achat » plusieurs jours ne compte qu'une fois).
     */
    suspend fun record(ticker: String, kind: String, portfolio: String, date: String, price: Double, regime: String?) {
        if (price <= 0.0 || date.isBlank()) return
        val since = runCatching { LocalDate.parse(date).minusDays(7).toString() }.getOrDefault(date)
        if (dao.countSince(ticker, kind, portfolio, since) > 0) return
        dao.insert(
            AdviceLogEntity(
                ticker = ticker, kind = kind, portfolio = portfolio, date = date, price = price,
                regime = regime, createdAt = System.currentTimeMillis(),
            )
        )
    }

    /** Mesure les conseils dont les séances à 5 / 20 jours sont maintenant disponibles. */
    suspend fun evaluatePending(market: MarketRepository) {
        dao.pending().groupBy { it.ticker }.forEach { (ticker, list) ->
            val candles = market.cachedCandles(ticker)
            if (candles.isEmpty()) return@forEach
            list.forEach { e ->
                val r5 = e.ret5 ?: AdviceEvaluator.forwardReturn(candles, e.date, e.price, AdviceEvaluator.SHORT)
                val r20 = AdviceEvaluator.forwardReturn(candles, e.date, e.price, AdviceEvaluator.LONG)
                if (r5 != e.ret5 || r20 != null) dao.setResults(e.id, r5, r20)
            }
        }
    }
}

fun AdviceLogEntity.toRecord(): AdviceRecord = AdviceRecord(ticker, kind, date, price, regime, ret5, ret20)

/** Alertes de prix (§23-5). */
class PriceAlertRepository(private val dao: PriceAlertDao) {
    fun observeFor(ticker: String): Flow<List<PriceAlertEntity>> = dao.observeFor(ticker)
    suspend fun active(): List<PriceAlertEntity> = dao.active()
    suspend fun add(ticker: String, above: Boolean, level: Double) {
        if (level <= 0.0) return
        dao.insert(PriceAlertEntity(ticker = ticker, above = above, level = level, createdAt = System.currentTimeMillis()))
    }
    suspend fun markTriggered(id: Long, price: Double) = dao.markTriggered(id, System.currentTimeMillis(), price)
    suspend fun delete(id: Long) = dao.delete(id)
}

/**
 * Calendrier des sociétés avec cache (§23-4) : 24 h quand les dates sont connues,
 * 6 h après un échec, pour ne pas solliciter Yahoo inutilement.
 * Renvoie null si le calendrier est indisponible.
 */
class CalendarRepository(
    private val provider: bzh.dargio.boursosignal.data.remote.CalendarProvider,
    private val settings: bzh.dargio.boursosignal.data.prefs.SettingsStore,
) {
    suspend fun events(ticker: String): bzh.dargio.boursosignal.domain.signals.CompanyEvents? {
        val now = System.currentTimeMillis()
        settings.eventsCache(ticker)?.split("|")?.takeIf { it.size == 4 }?.let { parts ->
            val at = parts[3].toLongOrNull() ?: 0L
            val failed = parts[0] == "ERR"
            if (now - at < (if (failed) 6 else 24) * HOUR_MS) {
                return if (failed) null
                else bzh.dargio.boursosignal.domain.signals.CompanyEvents(parts[1].ifBlank { null }, parts[2].ifBlank { null })
            }
        }
        val events = provider.events(ticker).getOrNull()
        settings.setEventsCache(
            ticker,
            if (events == null) "ERR|||$now" else "OK|${events.earningsDate.orEmpty()}|${events.exDividendDate.orEmpty()}|$now",
        )
        return events
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
