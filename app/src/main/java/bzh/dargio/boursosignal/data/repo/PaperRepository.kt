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
 * les ordres au portefeuille virtuel via [PaperBroker] (logique pure testée).
 * Deux voies : automatique (sur signal) et manuelle (achat/vente depuis l'app).
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

    /** Applique un signal (mode automatique). Sans effet si le mode test est désactivé. */
    suspend fun applySignal(ticker: String, type: String, price: Double, date: String, reason: String) {
        execute(ticker, type, price, date, reason)
    }

    /** Achat manuel depuis l'app (demande utilisateur). Renvoie vrai si exécuté. */
    suspend fun manualBuy(ticker: String, price: Double): Boolean =
        execute(ticker, "BUY", price, LocalDate.now().toString(), "Achat manuel (mode test)")

    /** Vente manuelle depuis l'app. Renvoie vrai si exécuté. */
    suspend fun manualSell(ticker: String, price: Double): Boolean =
        execute(ticker, "SELL", price, LocalDate.now().toString(), "Vente manuelle (mode test)")

    private suspend fun execute(ticker: String, type: String, price: Double, date: String, reason: String): Boolean {
        val accEntity = dao.account() ?: return false
        if (!accEntity.enabled) return false

        val positions = dao.positions().associate { it.ticker to PaperHolding(it.quantity, it.avgPrice) }
        val account = PaperAccount(accEntity.initialCapital, accEntity.cash, positions)
        val budget = accEntity.initialCapital * BUY_FRACTION

        val exec = when (type) {
            "BUY" -> PaperBroker.buy(account, ticker, price, budget)
            "SELL" -> PaperBroker.sell(account, ticker, price)
            else -> return false
        }
        if (!exec.executed) return false

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
        return true
    }

    private companion object {
        /** Fraction du capital initial investie à chaque achat (≈ 5 positions max). */
        const val BUY_FRACTION = 0.2
    }
}
