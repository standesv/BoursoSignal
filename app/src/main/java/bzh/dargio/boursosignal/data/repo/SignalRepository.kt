package bzh.dargio.boursosignal.data.repo

import bzh.dargio.boursosignal.data.local.SignalDao
import bzh.dargio.boursosignal.data.local.SignalEntity
import kotlinx.coroutines.flow.Flow

/** Journal des signaux / alertes (§F7). */
class SignalRepository(private val dao: SignalDao) {

    fun observeAll(): Flow<List<SignalEntity>> = dao.observeAll()
    fun observeForTicker(ticker: String): Flow<List<SignalEntity>> = dao.observeForTicker(ticker)
    suspend fun latestFor(ticker: String): SignalEntity? = dao.latestFor(ticker)
    suspend fun insert(signal: SignalEntity): Long = dao.insert(signal)
    suspend fun setHandled(id: Long, handled: Boolean) = dao.setHandled(id, handled)
}
