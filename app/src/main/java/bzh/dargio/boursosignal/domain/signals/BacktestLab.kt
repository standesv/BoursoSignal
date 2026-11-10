package bzh.dargio.boursosignal.domain.signals

/** Résultat de backtest d'une valeur (stratégie vs achat-conservation). */
data class LabTickerResult(
    val ticker: String,
    val strategyPct: Double,
    val buyHoldPct: Double,
    val trades: Int,
) {
    /** Sur/sous-performance de la stratégie par rapport à l'achat-conservation. */
    val edgePct: Double get() = strategyPct - buyHoldPct
}

/** Synthèse d'un backtest sur tout un univers de valeurs. */
data class LabResult(
    val perTicker: List<LabTickerResult>,
    val avgStrategyPct: Double,
    val avgBuyHoldPct: Double,
    val beatCount: Int,   // nombre de valeurs où la stratégie bat l'achat-conservation
    val count: Int,       // nombre de valeurs évaluées
)

/**
 * Évalue la stratégie sur un univers de valeurs et agrège les résultats, pour
 * mesurer objectivement si elle apporte un gain par rapport à l'achat-conservation.
 * Logique pure et testable (s'appuie sur [Backtester]).
 */
object BacktestLab {

    fun run(data: Map<String, List<Candle>>, config: StrategyConfig = StrategyConfig()): LabResult {
        val results = data.entries.mapNotNull { (ticker, candles) ->
            if (candles.size <= config.minHistory + 5) return@mapNotNull null
            val r = Backtester(config).run(candles)
            LabTickerResult(ticker, r.totalReturnPct, r.buyHoldReturnPct, r.tradeCount)
        }
        val count = results.size
        val avgStrategy = if (count > 0) results.sumOf { it.strategyPct } / count else 0.0
        val avgBuyHold = if (count > 0) results.sumOf { it.buyHoldPct } / count else 0.0
        val beat = results.count { it.strategyPct > it.buyHoldPct }
        return LabResult(
            perTicker = results.sortedByDescending { it.edgePct },
            avgStrategyPct = avgStrategy,
            avgBuyHoldPct = avgBuyHold,
            beatCount = beat,
            count = count,
        )
    }
}
