package bzh.dargio.boursosignal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PaperDao {

    @Query("SELECT * FROM paper_account WHERE id = 0")
    fun observeAccount(): Flow<PaperAccountEntity?>

    @Query("SELECT * FROM paper_account WHERE id = 0")
    suspend fun account(): PaperAccountEntity?

    @Upsert
    suspend fun upsertAccount(account: PaperAccountEntity)

    @Query("SELECT * FROM paper_positions ORDER BY ticker")
    fun observePositions(): Flow<List<PaperPositionEntity>>

    @Query("SELECT * FROM paper_positions")
    suspend fun positions(): List<PaperPositionEntity>

    @Upsert
    suspend fun upsertPosition(position: PaperPositionEntity)

    @Query("DELETE FROM paper_positions WHERE ticker = :ticker")
    suspend fun deletePosition(ticker: String)

    @Query("DELETE FROM paper_positions")
    suspend fun clearPositions()

    @Insert
    suspend fun insertTrade(trade: PaperTradeEntity): Long

    @Query("SELECT * FROM paper_trades ORDER BY createdAt DESC")
    fun observeTrades(): Flow<List<PaperTradeEntity>>

    @Query("DELETE FROM paper_trades")
    suspend fun clearTrades()
}
