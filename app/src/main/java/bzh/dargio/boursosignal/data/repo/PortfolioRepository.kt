package bzh.dargio.boursosignal.data.repo

import bzh.dargio.boursosignal.data.local.PortfolioDao
import bzh.dargio.boursosignal.data.local.PositionEntity
import bzh.dargio.boursosignal.data.local.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** Portefeuille saisi manuellement : positions et transactions (§F5, §F11). */
class PortfolioRepository(private val dao: PortfolioDao) {

    fun observePositions(): Flow<List<PositionEntity>> = dao.observePositions()
    fun observeTransactions(): Flow<List<TransactionEntity>> = dao.observeTransactions()
    suspend fun position(ticker: String): PositionEntity? = dao.position(ticker)

    /**
     * Enregistre une transaction réellement passée chez Boursorama et met à jour
     * la position correspondante (prix de revient moyen pondéré, frais inclus).
     */
    suspend fun recordTransaction(
        ticker: String,
        type: String,       // "BUY" | "SELL"
        quantity: Double,
        price: Double,
        fees: Double,
        date: String,
    ) {
        dao.insertTransaction(
            TransactionEntity(ticker = ticker, type = type, quantity = quantity, price = price, fees = fees, date = date)
        )
        val current = dao.position(ticker)
        when (type.uppercase()) {
            "BUY" -> {
                val oldQty = current?.quantity ?: 0.0
                val oldCost = oldQty * (current?.avgPrice ?: 0.0)
                val newQty = oldQty + quantity
                val newAvg = if (newQty > 0) (oldCost + quantity * price + fees) / newQty else 0.0
                dao.upsertPosition(PositionEntity(ticker, newQty, newAvg))
            }
            "SELL" -> {
                val newQty = (current?.quantity ?: 0.0) - quantity
                if (newQty > 1e-9) {
                    dao.upsertPosition(PositionEntity(ticker, newQty, current?.avgPrice ?: price))
                } else {
                    dao.deletePosition(ticker)
                }
            }
        }
    }
}
