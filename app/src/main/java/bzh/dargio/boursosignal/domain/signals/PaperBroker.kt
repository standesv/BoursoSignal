package bzh.dargio.boursosignal.domain.signals

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
        /** Crée un compte neuf, tout en liquidités. */
        fun fresh(initialCapital: Double) =
            PaperAccount(initialCapital, initialCapital, emptyMap())
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
 * Broker fictif (§ mode test / paper trading). Logique pure et testable : applique
 * un signal d'achat/vente au portefeuille virtuel, valorise et mesure la
 * performance. Fractions d'actions autorisées (simulation). Sans frais en v1.
 *
 * Règle d'achat v1 : on n'ouvre qu'une position par valeur, en investissant au
 * plus `budget` de liquidités. Règle de vente : on liquide toute la position.
 */
object PaperBroker {

    /** Ouvre une position sur `ticker` en investissant au plus `budget` de cash. */
    fun buy(account: PaperAccount, ticker: String, price: Double, budget: Double): PaperExecution {
        if (price <= 0.0 || account.cash <= 0.0 || account.positions.containsKey(ticker)) {
            return PaperExecution(account, 0.0, price)
        }
        val spend = minOf(account.cash, budget).coerceAtLeast(0.0)
        if (spend <= 0.0) return PaperExecution(account, 0.0, price)
        val qty = spend / price
        val newAccount = account.copy(
            cash = account.cash - spend,
            positions = account.positions + (ticker to PaperHolding(qty, price)),
        )
        return PaperExecution(newAccount, qty, price)
    }

    /** Liquide entièrement la position sur `ticker`. */
    fun sell(account: PaperAccount, ticker: String, price: Double): PaperExecution {
        val holding = account.positions[ticker] ?: return PaperExecution(account, 0.0, price)
        val proceeds = holding.quantity * price
        val newAccount = account.copy(
            cash = account.cash + proceeds,
            positions = account.positions - ticker,
        )
        return PaperExecution(newAccount, holding.quantity, price)
    }

    /** Valeur totale du portefeuille (liquidités + positions aux cours fournis). */
    fun equity(account: PaperAccount, prices: Map<String, Double>): Double =
        account.cash + account.positions.entries.sumOf { (ticker, h) ->
            h.quantity * (prices[ticker] ?: h.avgPrice)
        }

    /** Performance totale en % par rapport au capital de départ. */
    fun totalReturnPct(account: PaperAccount, prices: Map<String, Double>): Double {
        if (account.initialCapital <= 0.0) return 0.0
        return (equity(account, prices) - account.initialCapital) / account.initialCapital * 100.0
    }
}
