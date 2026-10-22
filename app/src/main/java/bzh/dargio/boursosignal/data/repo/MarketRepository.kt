package bzh.dargio.boursosignal.data.repo

import bzh.dargio.boursosignal.data.local.CandleDao
import bzh.dargio.boursosignal.data.local.CandleEntity
import bzh.dargio.boursosignal.data.local.toDomain
import bzh.dargio.boursosignal.data.prefs.SettingsStore
import bzh.dargio.boursosignal.data.remote.FakeMarketDataProvider
import bzh.dargio.boursosignal.data.remote.MarketDataProvider
import bzh.dargio.boursosignal.data.remote.YahooFinanceProvider
import bzh.dargio.boursosignal.domain.signals.Candle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Accès aux cours : choisit la source selon les réglages, récupère l'historique
 * et le met en cache local (§6, §13 mode hors-ligne).
 *
 * Source réelle par défaut : Yahoo Finance (gratuit, couvre Euronext Paris).
 * Twelve Data reste disponible dans le code pour qui dispose d'un plan payant.
 */
class MarketRepository(
    private val candleDao: CandleDao,
    private val settings: SettingsStore,
) {

    /** Fournisseur courant, reconstruit à chaque appel selon les réglages. */
    fun currentProvider(): MarketDataProvider =
        if (settings.useDemoProvider) FakeMarketDataProvider() else YahooFinanceProvider()

    /** Bougies en cache pour une valeur (flux réactif). */
    fun observeCandles(ticker: String): Flow<List<Candle>> =
        candleDao.observeForTicker(ticker).map { it.toDomain() }

    /** Bougies en cache (lecture ponctuelle). */
    suspend fun cachedCandles(ticker: String): List<Candle> =
        candleDao.forTicker(ticker).toDomain()

    /**
     * Rafraîchit l'historique d'une valeur depuis la source et le stocke.
     * En cas d'échec réseau, renvoie le cache existant (dégradation gracieuse §13).
     */
    suspend fun refresh(ticker: String, outputSize: Int = 250): Result<List<Candle>> {
        val result = currentProvider().dailyCandles(ticker, outputSize)
        result.onSuccess { candles ->
            candleDao.upsertAll(candles.map {
                CandleEntity(ticker, it.date, it.open, it.high, it.low, it.close, it.volume)
            })
        }
        return result.recoverCatching {
            val cache = cachedCandles(ticker)
            if (cache.isNotEmpty()) cache else throw it
        }
    }
}
