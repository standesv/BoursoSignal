package bzh.dargio.boursosignal.domain.signals

import kotlin.math.floor

/** Résultat réaliste d'une valeur : conseils de l'app rejoués, frais et exécution à l'ouverture suivante. */
data class RealisticTickerResult(
    val ticker: String,
    val strategyPct: Double,
    val buyHoldPct: Double,
    val trades: Int,
    val feesEuros: Double,
    val firstHalfPct: Double,      // stratégie, 1re moitié de la période
    val secondHalfPct: Double,     // stratégie, 2de moitié
    val bhFirstHalfPct: Double,    // achat-conservation, 1re moitié
    val bhSecondHalfPct: Double,   // achat-conservation, 2de moitié
) {
    val edgePct: Double get() = strategyPct - buyHoldPct
}

/** Synthèse du backtest réaliste sur l'univers. */
data class RealisticLabResult(
    val perTicker: List<RealisticTickerResult>,
    val avgStrategyPct: Double,
    val avgBuyHoldPct: Double,
    val beatCount: Int,
    val count: Int,
    val avgFirstHalfPct: Double,
    val avgSecondHalfPct: Double,
    val avgBhFirstHalfPct: Double,
    val avgBhSecondHalfPct: Double,
    val totalTrades: Int,
    val totalFeesEuros: Double,
)

/**
 * Backtest réaliste (§23-2) : rejoue LES CONSEILS DE L'APP tels qu'ils sont affichés
 * — entrée quand Idées dit « Achat », sortie totale sur « Vendre », moitié sur
 * « Alléger » — avec exécution à l'OUVERTURE de la séance suivante (jamais au cours
 * du jour du signal) et les frais du profil de courtage. La période est coupée en
 * deux moitiés pour vérifier que le résultat tient sur des marchés différents.
 *
 * Simplifications assumées : actualité neutre, pas de force relative ni sectorielle
 * (dates non alignables simplement), un capital fixe par valeur.
 */
class AdviceBacktester(
    private val config: StrategyConfig = StrategyConfig(),
    private val fee: FeeProfile = FeeProfile.NONE,
    private val capital: Double = 2_000.0,
) {

    fun run(ticker: String, candles: List<Candle>, regimeByDate: Map<String, Regime>? = null): RealisticTickerResult? {
        val start = config.minHistory
        if (candles.size < start + 12) return null
        val last = candles.lastIndex
        val mid = start + 1 + (last - start - 1) / 2

        var cash = capital
        var shares = 0
        var avg = 0.0
        var entryDate: String? = null
        var anchor: Double? = null
        var trades = 0
        var fees = 0.0
        var equityAtMid = capital

        for (i in start until last) {
            val window = candles.subList(maxOf(0, i - WINDOW + 1), i + 1)
            val regime = regimeByDate?.get(candles[i].date)
            val next = candles[i + 1]
            val px = next.open.takeIf { it > 0.0 } ?: next.close

            if (shares == 0) {
                val opp = StanceEvaluator.evaluate(ticker, window, config, regime = regime)
                if (opp.stance == Stance.BUY) {
                    var q = floor(cash / px).toInt()
                    while (q > 0 && q * px + fee.feeFor(q * px) > cash) q--
                    if (q > 0) {
                        val f = fee.feeFor(q * px)
                        cash -= q * px + f
                        fees += f
                        shares = q
                        avg = px
                        entryDate = next.date
                        anchor = null
                        trades++
                    }
                }
            } else {
                val adv = ExitAdvisor.advise(ticker, window, avg, config, lastTrimPrice = anchor, regime = regime, entryDate = entryDate)
                anchor = adv.trimAnchor
                val toSell = when (adv.action) {
                    ExitAction.SELL -> shares
                    ExitAction.TRIM -> shares / 2
                    ExitAction.HOLD -> 0
                }
                if (toSell > 0) {
                    val amount = toSell * px
                    val f = fee.feeFor(amount)
                    cash += amount - f
                    fees += f
                    shares -= toSell
                    if (shares == 0) { entryDate = null; anchor = null }
                }
            }
            if (i + 1 == mid) equityAtMid = cash + shares * candles[mid].close
        }
        if (shares > 0) {
            val amount = shares * candles[last].close
            val f = fee.feeFor(amount)
            cash += amount - f
            fees += f
        }

        // Achat-conservation : achat à l'ouverture de la 1re séance évaluée, frais d'entrée et de sortie inclus.
        val bhPx = candles[start + 1].open.takeIf { it > 0.0 } ?: candles[start + 1].close
        var bhQ = floor(capital / bhPx).toInt()
        while (bhQ > 0 && bhQ * bhPx + fee.feeFor(bhQ * bhPx) > capital) bhQ--
        val bhFinal = if (bhQ > 0) {
            val rest = capital - bhQ * bhPx - fee.feeFor(bhQ * bhPx)
            rest + bhQ * candles[last].close - fee.feeFor(bhQ * candles[last].close)
        } else capital

        return RealisticTickerResult(
            ticker = ticker,
            strategyPct = (cash / capital - 1.0) * 100.0,
            buyHoldPct = (bhFinal / capital - 1.0) * 100.0,
            trades = trades,
            feesEuros = fees,
            firstHalfPct = (equityAtMid / capital - 1.0) * 100.0,
            secondHalfPct = if (equityAtMid > 0.0) (cash / equityAtMid - 1.0) * 100.0 else 0.0,
            bhFirstHalfPct = (candles[mid].close / bhPx - 1.0) * 100.0,
            bhSecondHalfPct = (candles[last].close / candles[mid].close - 1.0) * 100.0,
        )
    }

    private companion object {
        /** Fenêtre glissante d'historique évaluée chaque jour (suffit pour la MM200). */
        const val WINDOW = 300
    }
}

object RealisticBacktestLab {
    fun run(
        data: Map<String, List<Candle>>,
        config: StrategyConfig = StrategyConfig(),
        index: List<Candle>? = null,
        fee: FeeProfile = FeeProfile.NONE,
        capital: Double = 2_000.0,
    ): RealisticLabResult {
        val regimes = index?.takeIf { it.size >= MarketRegimeEvaluator.MIN_HISTORY }?.let { MarketRegimeEvaluator.byDate(it) }
        val bt = AdviceBacktester(config, fee, capital)
        val results = data.mapNotNull { (ticker, candles) -> bt.run(ticker, candles, regimes) }
        val n = results.size
        fun avg(f: (RealisticTickerResult) -> Double) = if (n > 0) results.sumOf(f) / n else 0.0
        return RealisticLabResult(
            perTicker = results.sortedByDescending { it.edgePct },
            avgStrategyPct = avg { it.strategyPct },
            avgBuyHoldPct = avg { it.buyHoldPct },
            beatCount = results.count { it.strategyPct > it.buyHoldPct },
            count = n,
            avgFirstHalfPct = avg { it.firstHalfPct },
            avgSecondHalfPct = avg { it.secondHalfPct },
            avgBhFirstHalfPct = avg { it.bhFirstHalfPct },
            avgBhSecondHalfPct = avg { it.bhSecondHalfPct },
            totalTrades = results.sumOf { it.trades },
            totalFeesEuros = results.sumOf { it.feesEuros },
        )
    }
}
