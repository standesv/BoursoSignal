package bzh.dargio.boursosignal.domain.signals

import kotlin.math.max

/** Une opération simulée fermée pendant un backtest. */
data class BacktestTrade(
    val entryDate: String,
    val entryPrice: Double,
    val exitDate: String,
    val exitPrice: Double,
) {
    val returnPct: Double get() = if (entryPrice > 0) (exitPrice - entryPrice) / entryPrice * 100.0 else 0.0
}

/** Résultat d'un backtest d'une stratégie sur un historique. */
data class BacktestResult(
    val trades: List<BacktestTrade>,
    val totalReturnPct: Double,   // performance de la stratégie (capital initial → final)
    val buyHoldReturnPct: Double, // performance d'un simple achat-conservation sur la même période
    val winRatePct: Double,       // part des trades gagnants
    val maxDrawdownPct: Double,   // pire repli de l'équité
) {
    val tradeCount: Int get() = trades.size
}

/**
 * Backtesting (§F10) : rejoue la stratégie jour après jour sur l'historique, en
 * tout-ou-rien (100 % investi / 100 % liquide), et mesure sa performance.
 *
 * Kotlin pur, sans dépendance Android : testable sur la JVM, comme le moteur.
 * Hypothèses volontairement simples (MVP) : exécution à la clôture du jour du
 * signal, pas de frais ni de slippage (paramétrables plus tard).
 */
class Backtester(private val config: StrategyConfig = StrategyConfig()) {

    fun run(candles: List<Candle>): BacktestResult {
        val engine = SignalEngine(config)
        val trades = mutableListOf<BacktestTrade>()

        var inPosition = false
        var entryPrice = 0.0
        var entryDate = ""
        var equity = 1.0          // capital normalisé (1.0 = 100 %)
        var peak = 1.0
        var maxDrawdown = 0.0

        // On commence dès qu'il y a assez d'historique pour les indicateurs.
        val startFirstEval = config.minHistory
        if (candles.size <= startFirstEval) {
            return BacktestResult(emptyList(), 0.0, 0.0, 0.0, 0.0)
        }

        for (i in startFirstEval until candles.size) {
            val window = candles.subList(0, i + 1)
            val price = candles[i].close
            val entry = if (inPosition) entryPrice else null
            val signal = engine.evaluate(window, entry)

            if (!inPosition && signal.type == SignalType.BUY) {
                inPosition = true
                entryPrice = price
                entryDate = candles[i].date
            } else if (inPosition && signal.type == SignalType.SELL) {
                equity *= price / entryPrice
                trades += BacktestTrade(entryDate, entryPrice, candles[i].date, price)
                inPosition = false
                peak = max(peak, equity)
                maxDrawdown = max(maxDrawdown, (peak - equity) / peak)
            }

            // Suivi du drawdown sur l'équité marquée au marché.
            val marked = if (inPosition) equity * price / entryPrice else equity
            peak = max(peak, marked)
            maxDrawdown = max(maxDrawdown, (peak - marked) / peak)
        }

        // Clôture d'une position encore ouverte à la dernière clôture.
        if (inPosition) {
            val last = candles.last()
            equity *= last.close / entryPrice
            trades += BacktestTrade(entryDate, entryPrice, last.date, last.close)
        }

        val firstClose = candles[startFirstEval].close
        val lastClose = candles.last().close
        val buyHold = if (firstClose > 0) (lastClose - firstClose) / firstClose * 100.0 else 0.0
        val wins = trades.count { it.returnPct > 0 }
        val winRate = if (trades.isNotEmpty()) wins.toDouble() / trades.size * 100.0 else 0.0

        return BacktestResult(
            trades = trades,
            totalReturnPct = (equity - 1.0) * 100.0,
            buyHoldReturnPct = buyHold,
            winRatePct = winRate,
            maxDrawdownPct = maxDrawdown * 100.0,
        )
    }
}
