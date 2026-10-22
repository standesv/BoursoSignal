package bzh.dargio.boursosignal.data.repo

import bzh.dargio.boursosignal.data.local.PaperAccountEntity
import bzh.dargio.boursosignal.data.local.PaperDao
import bzh.dargio.boursosignal.data.local.PaperPositionEntity
import bzh.dargio.boursosignal.data.local.PaperTradeEntity
import bzh.dargio.boursosignal.domain.signals.PaperAccount
import bzh.dargio.boursosignal.domain.signals.PaperBroker
import bzh.dargio.boursosignal.domain.signals.PaperHolding
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Portefeuille fictif (mode test / paper trading). Persiste l'état et applique
 * les signaux au portefeuille virtuel via [PaperBroker] (logique pure testée).
 */
class PaperRepository(private val dao: PaperDao) {

    fun observeAccount(): Flow<PaperAccountEntity?> = dao.observeAccount()
    fun observePositions(): Flow<List<PaperPositionEntity>> = dao.observePositions()
    fun observeTrades(): Flow<List<PaperTradeEntity>> = dao.observeTrades()

    suspend fun isEnabled(): Boolean = dao.account()?.enabled == true

    /** Active le mode test avec un capital de départ, en repartant de zéro. */
    suspend fun activate(initialCapital: Double) {
        dao.clearPositions()
        dao.clearTrades()
        dao.upsertAccount(
            PaperAccountEntity(
                id = 0,
                enabled = true,
                initialCapital = initialCapital,
                cash = initialCapital,
                startDate = LocalDate.now().toString(),
            )
        )
    }

    /** Désactive le mode test (conserve l'historique). */
    suspend fun setEnabled(enabled: Boolean) {
        val acc = dao.account() ?: return
        dao.upsertAccount(acc.copy(enabled = enabled))
    }

    /** Remet le portefeuille fictif à zéro avec un nouveau capital. */
    suspend fun reset(initialCapital: Double) = activate(initialCapital)

    /**
     * Applique un signal au portefeuille fictif (mode automatique) : achat d'une
     * tranche de liquidités sur BUY, liquidation sur SELL. Sans effet si le mode
     * test est désactivé.
     */
    suspend fun applySignal(ticker: String, type: String, price: Double, date: String, reason: String) {
        val accEntity = dao.account() ?: return
        if (!accEntity.enabled) return

        val positions = dao.positions().associate { it.ticker to PaperHolding(it.quantity, it.avgPrice) }
        val account = PaperAccount(accEntity.initialCapital, accEntity.cash, positions)
        val budget = accEntity.initialCapital * BUY_FRACTION

        val exec = when (type) {
            "BUY" -> PaperBroker.buy(account, ticker, price, budget)
            "SELL" -> PaperBroker.sell(account, ticker, price)
            else -> return
        }
        if (!exec.executed) return

        dao.upsertAccount(accEntity.copy(cash = exec.account.cash))
        if (type == "BUY") {
            val h = exec.account.positions.getValue(ticker)
            dao.upsertPosition(PaperPositionEntity(ticker, h.quantity, h.avgPrice, date))
        } else {
            dao.deletePosition(ticker)
        }
        dao.insertTrade(
            PaperTradeEntity(ticker = ticker, type = type, quantity = exec.executedQty, price = price, date = date, reason = reason)
        )
    }

    private companion object {
        /** Fraction du capital initial investie à chaque achat (≈ 5 positions max). */
        const val BUY_FRACTION = 0.2
    }
}
