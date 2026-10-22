package bzh.dargio.boursosignal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InstrumentDao {
    @Query("SELECT * FROM instruments ORDER BY ticker")
    fun observeAll(): Flow<List<InstrumentEntity>>

    @Query("SELECT * FROM instruments WHERE inWatchlist = 1 ORDER BY ticker")
    fun observeWatchlist(): Flow<List<InstrumentEntity>>

    @Query("SELECT * FROM instruments WHERE inWatchlist = 1")
    suspend fun watchlist(): List<InstrumentEntity>

    @Query("SELECT * FROM instruments WHERE ticker = :ticker")
    suspend fun find(ticker: String): InstrumentEntity?

    @Upsert
    suspend fun upsert(instrument: InstrumentEntity)

    @Query("DELETE FROM instruments WHERE ticker = :ticker")
    suspend fun delete(ticker: String)
}

@Dao
interface CandleDao {
    @Query("SELECT * FROM candles WHERE ticker = :ticker ORDER BY date ASC")
    suspend fun forTicker(ticker: String): List<CandleEntity>

    @Query("SELECT * FROM candles WHERE ticker = :ticker ORDER BY date ASC")
    fun observeForTicker(ticker: String): Flow<List<CandleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(candles: List<CandleEntity>)

    @Query("DELETE FROM candles WHERE ticker = :ticker")
    suspend fun clear(ticker: String)
}

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM positions ORDER BY ticker")
    fun observePositions(): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions WHERE ticker = :ticker")
    suspend fun position(ticker: String): PositionEntity?

    @Upsert
    suspend fun upsertPosition(position: PositionEntity)

    @Query("DELETE FROM positions WHERE ticker = :ticker")
    suspend fun deletePosition(ticker: String)

    @Insert
    suspend fun insertTransaction(tx: TransactionEntity): Long

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE ticker = :ticker ORDER BY date ASC, id ASC")
    suspend fun transactionsFor(ticker: String): List<TransactionEntity>
}

@Dao
interface SignalDao {
    @Query("SELECT * FROM signals ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE ticker = :ticker ORDER BY createdAt DESC")
    fun observeForTicker(ticker: String): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE ticker = :ticker ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestFor(ticker: String): SignalEntity?

    @Insert
    suspend fun insert(signal: SignalEntity): Long

    @Query("UPDATE signals SET handled = :handled WHERE id = :id")
    suspend fun setHandled(id: Long, handled: Boolean)
}

@Dao
interface StrategyDao {
    @Query("SELECT * FROM strategies ORDER BY id")
    fun observeAll(): Flow<List<StrategyEntity>>

    @Query("SELECT * FROM strategies WHERE enabled = 1 LIMIT 1")
    suspend fun firstEnabled(): StrategyEntity?

    @Query("SELECT COUNT(*) FROM strategies")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(strategy: StrategyEntity)

    @Query("DELETE FROM strategies WHERE id = :id")
    suspend fun delete(id: Long)
}
