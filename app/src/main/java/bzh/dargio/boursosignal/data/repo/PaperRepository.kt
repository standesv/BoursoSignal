package bzh.dargio.boursosignal.data.repo

import bzh.dargio.boursosignal.data.local.PaperAccountEntity
import bzh.dargio.boursosignal.data.local.PaperDao
import bzh.dargio.boursosignal.data.local.PaperPositionEntity
import bzh.dargio.boursosignal.data.local.PaperTradeEntity
import bzh.dargio.boursosignal.domain.signals.FeeProfile
import bzh.dargio.boursosignal.domain.signals.PaperAccount
import bzh.dargio.boursosignal.domain.signals.PaperBroker
import bzh.dargio.boursosignal.domain.signals.PaperExecution
import bzh.dargio.boursosignal.domain.signals.PaperHolding
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Portefeuille fictif (mode test / paper trading). Persiste l'état et applique
 * les ordres au portefeuille virtuel via [PaperBroker] (logique pure testée).
 * Ordres en nombre entier d'actions ; voies automatique (sur signal) et manuelle.
 *
 * Les frais de courtage simulés proviennent du profil actif ([activeFee]),
 * configuré dans les Réglages. Ils sont prélevés par le broker et journalisés.
 */
class PaperRepository(
    private val dao: PaperDao,
    private val activeFee: () -> FeeProfile = { FeeProfile.NONE },
) {

    fun observeAccount(): Flow<PaperAccountEntity?> = dao.observeAccount()
    fun observePositions(): Flow<List<PaperPositionEntity>> = dao.observePositions()
    fun observeTrades(): Flow<List<PaperTradeEntity>> = dao.observeTrades()

    suspend fun isEnabled(): Boolean = dao.account()?.enabled == true

    suspend fun activate(initialCapital: Double) {
        dao.clearPositions()
        dao.clearTrades()
        dao.upsertAccount(
            PaperAccountEntity(
                id = 0, enabled = true, initialCapital = initialCapital,
                cash = initialCapital, startDate = LocalDate.now().toString(),
            )
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        val acc = dao.account()
        if (acc == null) {
            // Première activation sans compte existant : crée un portefeuille neuf.
            if (enabled) activate(10_000.0)
            return
        }
        dao.upsertAccount(acc.copy(enabled = enabled))
    }

    suspend fun reset(initialCapital: Double) = activate(initialCapital)

    /** Mode automatique : une position par valeur, tranche fixe du capital. */
    suspend fun applySignal(ticker: String, type: String, price: Double, date: String, reason: String) {
        val loaded = load() ?: return
        val (acc, account) = loaded
        if (!acc.enabled) return
        val fee = activeFee()
        val exec = when (type) {
            "BUY" -> if (account.positions.containsKey(ticker)) return
            else PaperBroker.buyBudget(account, ticker, price, acc.initialCapital * BUY_FRACTION, fee)
            "SELL" -> PaperBroker.sellAll(account, ticker, price, fee)
            else -> return
        }
        persist(acc, exec, ticker, type, date, reason)
    }

    /** Achat manuel d'un nombre d'actions. Renvoie le nombre réellement acheté. */
    suspend fun buyShares(ticker: String, price: Double, shares: Int): Int = manual("BUY", ticker, "Achat manuel (mode test)") { account ->
        PaperBroker.buyShares(account, ticker, price, shares, activeFee())
    }

    /** Achat manuel pour un montant (converti en actions entières). */
    suspend fun buyAmount(ticker: String, price: Double, amount: Double): Int = manual("BUY", ticker, "Achat manuel (mode test)") { account ->
        PaperBroker.buyBudget(account, ticker, price, amount, activeFee())
    }

    /** Vente manuelle d'un nombre d'actions. */
    suspend fun sellShares(ticker: String, price: Double, shares: Int): Int = manual("SELL", ticker, "Vente manuelle (mode test)") { account ->
        PaperBroker.sellShares(account, ticker, price, shares, activeFee())
    }

    /** Vente totale de la position. */
    suspend fun sellAll(ticker: String, price: Double): Int = manual("SELL", ticker, "Vente manuelle (mode test)") { account ->
        PaperBroker.sellAll(account, ticker, price, activeFee())
    }

    private suspend fun manual(type: String, ticker: String, reason: String, op: (PaperAccount) -> PaperExecution): Int {
        val loaded = load() ?: return 0
        val (acc, account) = loaded
        if (!acc.enabled) return 0
        return persist(acc, op(account), ticker, type, LocalDate.now().toString(), reason)
    }

    private suspend fun load(): Pair<PaperAccountEntity, PaperAccount>? {
        val acc = dao.account() ?: return null
        val positions = dao.positions().associate { it.ticker to PaperHolding(it.quantity, it.avgPrice) }
        return acc to PaperAccount(acc.initialCapital, acc.cash, positions)
    }

    private suspend fun persist(acc: PaperAccountEntity, exec: PaperExecution, ticker: String, type: String, date: String, reason: String): Int {
        if (!exec.executed) return 0
        dao.upsertAccount(acc.copy(cash = exec.account.cash))
        val h = exec.account.positions[ticker]
        if (h != null) dao.upsertPosition(PaperPositionEntity(ticker, h.quantity, h.avgPrice, date))
        else dao.deletePosition(ticker)
        dao.insertTrade(
            PaperTradeEntity(
                ticker = ticker, type = type, quantity = exec.executedQty,
                price = exec.price, date = date, reason = reason, fees = exec.fee,
            )
        )
        return exec.executedQty.toInt()
    }

    private companion object {
        const val BUY_FRACTION = 0.2
    }
}
