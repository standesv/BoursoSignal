package bzh.dargio.boursosignal.domain.signals

import kotlin.math.floor
import kotlin.math.min

/** Une position détenue dans le portefeuille fictif. */
data class PaperHolding(val quantity: Double, val avgPrice: Double)

/**
 * État d'un portefeuille fictif (paper trading) : capital de départ, liquidités
 * disponibles et positions ouvertes. Immuable — chaque opération renvoie un
 * nouvel état.
 */
data class PaperAccount(
    val initialCapital: Double,
    val cash: Double,
    val positions: Map<String, PaperHolding>,
) {
    companion object {
        fun fresh(initialCapital: Double) = PaperAccount(initialCapital, initialCapital, emptyMap())
    }
}

/** Résultat d'une opération fictive : nouvel état + quantité/prix exécutés. */
data class PaperExecution(
    val account: PaperAccount,
    val executedQty: Double,
    val price: Double,
) {
    val executed: Boolean get() = executedQty > 0.0
}

/**
 * Broker fictif (mode test / paper trading). Logique pure et testable.
 *
 * Les ordres se font en **nombre entier d'actions** (comme un vrai ordre
 * Boursorama) : le reliquat non investi reste en liquidités. Un rachat sur une
 * valeur déjà détenue augmente la position au prix de revient moyen pondéré.
 * Sans frais en v1.
 */
object PaperBroker {

    /** Achète un nombre entier d'actions, plafonné par les liquidités disponibles. */
    fun buyShares(account: PaperAccount, ticker: String, price: Double, shares: Int): PaperExecution {
        if (price <= 0.0 || shares <= 0 || account.cash <= 0.0) return PaperExecution(account, 0.0, price)
        val affordable = floor(account.cash / price).toInt()
        val qty = min(shares, affordable)
        if (qty <= 0) return PaperExecution(account, 0.0, price)
        val cost = qty * price
        val existing = account.positions[ticker]
        val holding = if (existing != null) {
            val total = existing.quantity + qty
            PaperHolding(total, (existing.quantity * existing.avgPrice + cost) / total)
        } else {
            PaperHolding(qty.toDouble(), price)
        }
        return PaperExecution(
            account.copy(cash = account.cash - cost, positions = account.positions + (ticker to holding)),
            qty.toDouble(), price,
        )
    }

    /** Achète pour au plus `budget` euros, en nombre entier d'actions. */
    fun buyBudget(account: PaperAccount, ticker: String, price: Double, budget: Double): PaperExecution {
        if (price <= 0.0) return PaperExecution(account, 0.0, price)
        val usable = min(budget, account.cash)
        val shares = floor(usable / price).toInt()
        return buyShares(account, ticker, price, shares)
    }

    /** Vend un nombre entier d'actions (vente partielle possible). */
    fun sellShares(account: PaperAccount, ticker: String, price: Double, shares: Int): PaperExecution {
        val h = account.positions[ticker] ?: return PaperExecution(account, 0.0, price)
        val held = floor(h.quantity).toInt()
        val qty = min(shares, held)
        if (qty <= 0) return PaperExecution(account, 0.0, price)
        val proceeds = qty * price
        val remaining = h.quantity - qty
        val positions = if (remaining > 1e-9) account.positions + (ticker to h.copy(quantity = remaining))
        else account.positions - ticker
        return PaperExecution(account.copy(cash = account.cash + proceeds, positions = positions), qty.toDouble(), price)
    }

    /** Liquide entièrement la position (y compris d'éventuelles fractions héritées). */
    fun sellAll(account: PaperAccount, ticker: String, price: Double): PaperExecution {
        val h = account.positions[ticker] ?: return PaperExecution(account, 0.0, price)
        val proceeds = h.quantity * price
        return PaperExecution(account.copy(cash = account.cash + proceeds, positions = account.positions - ticker), h.quantity, price)
    }

    /** Valeur totale du portefeuille (liquidités + positions aux cours fournis). */
    fun equity(account: PaperAccount, prices: Map<String, Double>): Double =
        account.cash + account.positions.entries.sumOf { (ticker, h) -> h.quantity * (prices[ticker] ?: h.avgPrice) }

    /** Performance totale en % par rapport au capital de départ. */
    fun totalReturnPct(account: PaperAccount, prices: Map<String, Double>): Double {
        if (account.initialCapital <= 0.0) return 0.0
        return (equity(account, prices) - account.initialCapital) / account.initialCapital * 100.0
    }
}
