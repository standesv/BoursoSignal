package bzh.dargio.boursosignal.domain.signals

/**
 * Moteur de détection des signaux d'achat / vente (§5). Kotlin pur, testable.
 *
 * Signaux et filtres combinés : croisement EMA, sortie de survente/surachat RSI,
 * confirmation de volume, croisement MACD, bandes de Bollinger, force de tendance
 * (ADX), filtre de tendance de fond (MM longue) et gestion du risque (stop-loss,
 * take-profit, stop de volatilité ATR, stop suiveur). Les signaux complémentaires
 * ne s'activent que lorsque l'historique est suffisant.
 */
class SignalEngine(private val config: StrategyConfig = StrategyConfig()) {

    /**
     * @param regime météo du marché (CAC 40) à la date évaluée ; null = pas de filtre.
     * @param entryDate date d'achat de la position (ISO) : le stop suiveur se mesure
     *                  depuis l'achat, jamais sur un plus haut antérieur à l'achat.
     */
    fun evaluate(
        candles: List<Candle>,
        avgEntryPrice: Double? = null,
        regime: Regime? = null,
        entryDate: String? = null,
    ): SignalResult {
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

        // --- Croisement MACD ---
        val macd = Indicators.macd(closes)
        val mL0 = macd.macd.getOrNull(i - 1); val mL1 = macd.macd.getOrNull(i)
        val mS0 = macd.signal.getOrNull(i - 1); val mS1 = macd.signal.getOrNull(i)
        if (mL0 != null && mL1 != null && mS0 != null && mS1 != null) {
            if (mL0 <= mS0 && mL1 > mS1) {
                buyScore += config.weightMacdCrossUp
                buyReasons += "MACD croise au-dessus de sa ligne de signal"
            }
            if (mL0 >= mS0 && mL1 < mS1) {
                sellScore += config.weightMacdCrossDown
                sellReasons += "MACD croise sous sa ligne de signal"
            }
        }

        // --- Bandes de Bollinger ---
        val boll = Indicators.bollinger(closes, config.bollingerPeriod, config.bollingerMult)
        boll.lower.getOrNull(i)?.let { lower ->
            if (last.close < lower) {
                buyScore += config.weightBollingerReboundUp
                buyReasons += "Cours sous la bande de Bollinger basse (survente)"
            }
        }
        boll.upper.getOrNull(i)?.let { upper ->
            if (last.close > upper) {
                sellScore += config.weightBollingerOverbought
                sellReasons += "Cours au-dessus de la bande de Bollinger haute (excès)"
            }
        }

        // --- Force de tendance (ADX) ---
        val adx = Indicators.adx(candles, config.adxPeriod).lastOrNull { it != null }
        if (adx != null && adx >= config.adxThreshold) {
            val ef = emaFast[i]; val es = emaSlow[i]
            if (ef != null && es != null) {
                if (ef > es) { buyScore += config.weightAdxConfirm; buyReasons += "Tendance forte confirmée (ADX ${fmt(adx)})" }
                else if (ef < es) { sellScore += config.weightAdxConfirm; sellReasons += "Tendance baissière forte (ADX ${fmt(adx)})" }
            }
        }

        // --- Gestion du risque (prioritaire sur une vente de stratégie) ---
        if (avgEntryPrice != null && avgEntryPrice > 0.0) {
            val change = (last.close - avgEntryPrice) / avgEntryPrice
            config.stopLossPct?.let { sl ->
                if (change <= sl) {
                    sellScore += config.sellThreshold
                    sellReasons += "Stop-loss atteint (${fmtPct(change)})"
                }
            }
            config.takeProfitPct?.let { tp ->
                if (change >= tp) {
                    sellScore += config.sellThreshold
                    sellReasons += "Take-profit atteint (${fmtPct(change)})"
                }
            }
            val atr = if (config.useAtrStop || config.useTrailingStop)
                Indicators.atr(candles, config.atrPeriod).lastOrNull { it != null } else null
            if (config.useAtrStop && atr != null && atr > 0.0 && last.close <= avgEntryPrice - config.atrStopMultiple * atr) {
                sellScore += config.sellThreshold
                sellReasons += "Stop de volatilité (ATR) atteint"
            }
            if (config.useTrailingStop && atr != null && atr > 0.0) {
                // Plus haut DEPUIS L'ACHAT : un sommet atteint avant l'achat n'est pas un gain à
                // protéger (sinon une valeur achetée sur repli serait aussitôt « à vendre »).
                // Date d'achat inconnue : on ne protège que des gains (cours au-dessus du PRU).
                val recentHigh = when {
                    entryDate != null -> candles.filter { it.date >= entryDate }.maxOfOrNull { it.close }
                    last.close > avgEntryPrice -> closes.takeLast(config.trailingPeriod).maxOrNull()
                    else -> null
                }
                if (recentHigh != null && last.close <= recentHigh - config.trailingAtrMultiple * atr) {
                    sellScore += config.sellThreshold
                    sellReasons += "Stop suiveur : repli depuis le plus haut récent (${fmt(recentHigh)})"
                }
            }
        }

        // --- Filtre de tendance de fond : pas d'achat sous la moyenne mobile longue ---
        val longMa = if (config.useTrendFilter) Indicators.sma(closes, config.longTrendPeriod).lastOrNull { it != null } else null
        val belowLongTrend = longMa != null && last.close < longMa

        // --- Filtre « météo du marché » : pas d'achat quand le CAC 40 casse ---
        val marketOff = config.useMarketFilter && regime == Regime.RISK_OFF
        val marketFragile = config.useMarketFilter && regime == Regime.CAUTION
        val requiredBuy = config.buyThreshold + if (marketFragile) config.cautionBuyExtra else 0.0

        val buy = buyScore >= requiredBuy && !belowLongTrend && !marketOff
        if (buy && marketFragile) buyReasons += "Marché fragile (CAC 40) : signal retenu car particulièrement solide"
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

    private fun crossedUp(a: List<Double?>, b: List<Double?>, i: Int): Boolean {
        val a0 = a.getOrNull(i - 1); val a1 = a[i]
        val b0 = b.getOrNull(i - 1); val b1 = b[i]
        if (a0 == null || a1 == null || b0 == null || b1 == null) return false
        return a0 <= b0 && a1 > b1
    }

    private fun crossedDown(a: List<Double?>, b: List<Double?>, i: Int): Boolean {
        val a0 = a.getOrNull(i - 1); val a1 = a[i]
        val b0 = b.getOrNull(i - 1); val b1 = b[i]
        if (a0 == null || a1 == null || b0 == null || b1 == null) return false
        return a0 >= b0 && a1 < b1
    }

    private fun fmt(v: Double): String = ((v * 10).toInt() / 10.0).toString()
    private fun fmtPct(v: Double): String = "${((v * 1000).toInt() / 10.0)}%"
}
