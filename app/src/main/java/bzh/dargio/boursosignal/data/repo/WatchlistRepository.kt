package bzh.dargio.boursosignal.data.repo

import bzh.dargio.boursosignal.data.local.InstrumentDao
import bzh.dargio.boursosignal.data.local.InstrumentEntity
import kotlinx.coroutines.flow.Flow

/** Gère la liste des valeurs suivies (§F1). */
class WatchlistRepository(private val dao: InstrumentDao) {

    fun observeWatchlist(): Flow<List<InstrumentEntity>> = dao.observeWatchlist()

    suspend fun watchlist(): List<InstrumentEntity> = dao.watchlist()

    suspend fun find(ticker: String): InstrumentEntity? = dao.find(ticker)

    suspend fun add(ticker: String, name: String) =
        dao.upsert(InstrumentEntity(ticker = ticker.trim().uppercase(), name = name.trim(), inWatchlist = true))

    suspend fun remove(ticker: String) = dao.delete(ticker)
}
