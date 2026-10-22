package bzh.dargio.boursosignal.data.repo

import bzh.dargio.boursosignal.data.local.PortfolioCashEntity
import bzh.dargio.boursosignal.data.local.PortfolioDao
import bzh.dargio.boursosignal.data.local.PositionEntity
import bzh.dargio.boursosignal.data.local.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** Portefeuille saisi manuellement : positions, transactions et liquidités (§F5, §F11). */
class PortfolioRepository(private val dao: PortfolioDao) {

    fun observePositions(): Flow<List<PositionEntity>> = dao.observePositions()
    fun observeTransactions(): Flow<List<TransactionEntity>> = dao.observeTransactions()
    fun observeCash(): Flow<PortfolioCashEntity?> = dao.observeCash()
    suspend fun position(ticker: String): PositionEntity? = dao.position(ticker)

    /** Définit (ou corrige) le montant de liquidités de base. Valeur absolue. */
    suspend fun setCash(amount: Double) {
        dao.upsertCash(PortfolioCashEntity(id = 0, cash = amount, initialized = true))
    }

    /**
     * Enregistre une transaction réellement passée chez Boursorama et met à jour
     * la position correspondante (prix de revient moyen pondéré, frais inclus).
     * Si les liquidités ont été renseignées, le solde est automatiquement
     * débité (achat : montant + frais) ou crédité (vente : montant − frais).
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
        // Débit/crédit automatique des liquidités (uniquement si elles sont suivies).
        dao.cashRow()?.let { row ->
            val delta = when (type.uppercase()) {
                "BUY" -> -(quantity * price + fees)
                "SELL" -> quantity * price - fees
                else -> 0.0
            }
            dao.upsertCash(row.copy(cash = row.cash + delta))
        }
    }

    /** Correction manuelle d'une position (quantité et PRU), sans toucher aux liquidités. */
    suspend fun updatePosition(ticker: String, quantity: Double, avgPrice: Double) {
        if (quantity > 1e-9) dao.upsertPosition(PositionEntity(ticker, quantity, avgPrice))
        else dao.deletePosition(ticker)
    }

    /** Supprime une position (correction d'erreur), sans toucher aux liquidités. */
    suspend fun deletePosition(ticker: String) = dao.deletePosition(ticker)

    /** Supprime une ligne du journal (correction d'erreur de saisie). */
    suspend fun deleteTransaction(id: Long) = dao.deleteTransaction(id)
}
