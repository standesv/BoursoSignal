package bzh.dargio.boursosignal.domain.signals

/** Recommandation sur une position détenue. */
enum class ExitAction { HOLD, TRIM, SELL }

/** Conseil de sortie pour une position (conserver / alléger / vendre). */
data class ExitAdvice(
    val ticker: String,
    val action: ExitAction,
    val plPct: Double,     // plus/moins-value vs prix de revient, en %
    val price: Double,
    val reasons: List<String>,
)

/**
 * Conseille s'il faut CONSERVER, ALLÉGER ou VENDRE une position détenue, en
 * combinant : signal du moteur (qui intègre déjà stop-loss / take-profit vs le
 * prix de revient), tendance, RSI, et actualité. Logique pure et testable.
 *
 * La vente reste à exécuter manuellement (pas d'API Boursorama).
 */
object ExitAdvisor {

    fun advise(
        ticker: String,
        candles: List<Candle>,
        avgPrice: Double,
        config: StrategyConfig = StrategyConfig(),
        news: NewsSentiment = NewsSentiment.NEUTRAL,
    ): ExitAdvice {
        val last = candles.lastOrNull()
            ?: return ExitAdvice(ticker, ExitAction.HOLD, 0.0, 0.0, listOf("Pas de donnée"))
        val price = last.close
        val plPct = if (avgPrice > 0) (price - avgPrice) / avgPrice * 100.0 else 0.0
        val reasons = mutableListOf<String>()
        var sell = 0

        // Signal du moteur, évalué AVEC le prix de revient (stop-loss / take-profit inclus).
        val sig = SignalEngine(config).evaluate(candles, avgPrice.takeIf { it > 0 })
        if (sig.type == SignalType.SELL) {
            sell += 2
            reasons += "Signal de vente : ${sig.reasons.joinToString(" · ")}"
        }

        // Tendance de fond.
        val opp = StanceEvaluator.evaluate(ticker, candles, config)
        when (opp.stance) {
            Stance.AVOID -> { sell += 2; reasons += "Tendance défavorable (Éviter)" }
            Stance.WATCH -> reasons += "Tendance à surveiller"
            else -> {}
        }

        // Garde-fou explicite stop-loss (au cas où le seuil du score ne l'aurait pas déclenché).
        config.stopLossPct?.let { if (plPct / 100.0 <= it) { sell += 2; reasons += "Stop-loss atteint (${fmt(plPct)} %)" } }

        // RSI en surachat ET position en gain : envisager d'alléger.
        val rsi = Indicators.rsi(candles.closes(), config.rsiPeriod).lastOrNull { it != null }
        if (rsi != null && rsi > config.rsiOverbought && plPct > 2.0) {
            sell += 1
            reasons += "RSI en surachat (${fmt(rsi)}) alors que la position est en gain"
        }

        // Actualité récente négative.
        if (news == NewsSentiment.NEGATIVE) { sell += 1; reasons += "Actualité récente négative" }

        val action = when {
            sell >= 2 -> ExitAction.SELL
            sell == 1 -> ExitAction.TRIM
            else -> ExitAction.HOLD
        }
        if (action == ExitAction.HOLD) reasons += "Configuration encore favorable — conserver"

        return ExitAdvice(ticker, action, plPct, price, reasons)
    }

    private fun fmt(v: Double): String = "${if (v >= 0) "+" else ""}${(v * 10).toInt() / 10.0}"
}
