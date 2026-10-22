package bzh.dargio.boursosignal.domain.signals

/**
 * Moteur de détection des signaux d'achat / vente (§5 du cahier des charges).
 *
 * Kotlin pur, sans dépendance Android : testable sur la JVM. À partir d'une
 * série de bougies et d'une configuration de stratégie, il calcule les
 * indicateurs, évalue des règles pondérées et produit un [SignalResult] pour la
 * dernière bougie.
 *
 * Le scoring additionne les poids des conditions satisfaites :
 *   S = Σ wᵢ · cᵢ   →   signal si S ≥ seuil
 * Quand achat et vente dépassent tous deux leur seuil, le score le plus élevé
 * l'emporte (sécurité : on ne renvoie jamais deux sens contradictoires).
 */
class SignalEngine(private val config: StrategyConfig = StrategyConfig()) {

    /**
     * Évalue la stratégie sur la dernière bougie de la série.
     *
     * @param candles historique de bougies, de la plus ancienne à la plus récente
     * @param avgEntryPrice prix de revient de la position détenue (pour stop-loss /
     *   take-profit), ou null si aucune position
     * @return le signal de la dernière bougie, ou NONE si l'historique est insuffisant
     */
    fun evaluate(candles: List<Candle>, avgEntryPrice: Double? = null): SignalResult {
        val last = candles.lastOrNull()
            ?: return SignalResult(SignalType.NONE, 0.0, emptyList(), "", 0.0)

        if (candles.size < config.minHistory) {
            return SignalResult(SignalType.NONE, 0.0, listOf("Historique insuffisant"), last.date, last.close)
        }

        val closes = candles.closes()
        val volumes = candles.volumes()
        val i = candles.lastIndex

        val emaFast = Indicators.ema(closes, config.emaFastPeriod)
        val emaSlow = Indicators.ema(closes, config.emaSlowPeriod)
        val rsi = Indicators.rsi(closes, config.rsiPeriod)
        val volAvg = Indicators.sma(volumes, config.volumeAvgPeriod)

        val buyReasons = mutableListOf<String>()
        val sellReasons = mutableListOf<String>()
        var buyScore = 0.0
        var sellScore = 0.0

        // --- Conditions d'ACHAT ---
        if (crossedUp(emaFast, emaSlow, i)) {
            buyScore += config.weightEmaCrossUp
            buyReasons += "EMA${config.emaFastPeriod} croise au-dessus de EMA${config.emaSlowPeriod}"
        }
        val rsiPrev = rsi.getOrNull(i - 1)
        val rsiNow = rsi[i]
        if (rsiPrev != null && rsiNow != null &&
            rsiPrev < config.rsiOversold && rsiNow >= config.rsiOversold
        ) {
            buyScore += config.weightRsiExitOversold
            buyReasons += "RSI sort de la zone de survente (${fmt(rsiPrev)}→${fmt(rsiNow)})"
        }
        val vAvg = volAvg[i]
        if (vAvg != null && last.volume > config.volumeFactor * vAvg) {
            buyScore += config.weightVolumeConfirm
            buyReasons += "Volume confirmé (> ${config.volumeFactor}× moyenne)"
        }

        // --- Conditions de VENTE ---
        if (crossedDown(emaFast, emaSlow, i)) {
            sellScore += config.weightEmaCrossDown
            sellReasons += "EMA${config.emaFastPeriod} croise sous EMA${config.emaSlowPeriod}"
        }
        if (rsiPrev != null && rsiNow != null &&
            rsiPrev > config.rsiOverbought && rsiNow <= config.rsiOverbought
        ) {
            sellScore += config.weightRsiOverbought
            sellReasons += "RSI redescend sous le surachat (${fmt(rsiPrev)}→${fmt(rsiNow)})"
        }

        // --- Gestion du risque (prioritaire sur une vente de stratégie) ---
        if (avgEntryPrice != null && avgEntryPrice > 0.0) {
            val change = (last.close - avgEntryPrice) / avgEntryPrice
            config.stopLossPct?.let { sl ->
                if (change <= sl) {
                    sellScore += config.sellThreshold // garantit le déclenchement
                    sellReasons += "Stop-loss atteint (${fmtPct(change)})"
                }
            }
            config.takeProfitPct?.let { tp ->
                if (change >= tp) {
                    sellScore += config.sellThreshold
                    sellReasons += "Take-profit atteint (${fmtPct(change)})"
                }
            }
        }

        val buy = buyScore >= config.buyThreshold
        val sell = sellScore >= config.sellThreshold

        return when {
            buy && sell ->
                if (buyScore >= sellScore)
                    SignalResult(SignalType.BUY, buyScore, buyReasons, last.date, last.close)
                else
                    SignalResult(SignalType.SELL, sellScore, sellReasons, last.date, last.close)
            buy -> SignalResult(SignalType.BUY, buyScore, buyReasons, last.date, last.close)
            sell -> SignalResult(SignalType.SELL, sellScore, sellReasons, last.date, last.close)
            else -> SignalResult(SignalType.NONE, maxOf(buyScore, sellScore), emptyList(), last.date, last.close)
        }
    }

    /** Vrai si `a` passe au-dessus de `b` entre i-1 et i (croisement haussier). */
    private fun crossedUp(a: List<Double?>, b: List<Double?>, i: Int): Boolean {
        val a0 = a.getOrNull(i - 1); val a1 = a[i]
        val b0 = b.getOrNull(i - 1); val b1 = b[i]
        if (a0 == null || a1 == null || b0 == null || b1 == null) return false
        return a0 <= b0 && a1 > b1
    }

    /** Vrai si `a` passe sous `b` entre i-1 et i (croisement baissier). */
    private fun crossedDown(a: List<Double?>, b: List<Double?>, i: Int): Boolean {
        val a0 = a.getOrNull(i - 1); val a1 = a[i]
        val b0 = b.getOrNull(i - 1); val b1 = b[i]
        if (a0 == null || a1 == null || b0 == null || b1 == null) return false
        return a0 >= b0 && a1 < b1
    }

    private fun fmt(v: Double): String = ((v * 10).toInt() / 10.0).toString()
    private fun fmtPct(v: Double): String = "${((v * 1000).toInt() / 10.0)}%"
}
