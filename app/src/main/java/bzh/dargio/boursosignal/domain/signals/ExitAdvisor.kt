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
    /** Cours à mémoriser comme ancre d'allègement (null = aucune / à effacer). */
    val trimAnchor: Double? = null,
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
        lastTrimPrice: Double? = null,
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

        val baseAction = when {
            sell >= 2 -> ExitAction.SELL
            sell == 1 -> ExitAction.TRIM
            else -> ExitAction.HOLD
        }

        // Anti-répétition de l'allègement : si on a déjà conseillé d'alléger cette valeur
        // plus bas, on repasse sur « Conserver » tant que le cours n'a pas repris au moins
        // trimReArmPct au-dessus du cours de ce dernier allègement (nouvelle jambe de hausse).
        val reArmed = lastTrimPrice == null || price > lastTrimPrice * (1 + config.trimReArmPct)
        val action = if (baseAction == ExitAction.TRIM && !reArmed) {
            reasons += "Déjà allégé plus bas — on conserve le reste tant que la hausse " +
                "ne repart pas d'au moins ${(config.trimReArmPct * 100).toInt()} %"
            ExitAction.HOLD
        } else baseAction

        if (action == ExitAction.HOLD && baseAction == ExitAction.HOLD)
            reasons += "Configuration encore favorable — conserver"

        // Ancre à persister par l'appelant : cours du jour si on vient de conseiller
        // d'alléger ; effacée si on vend (sortie) ; inchangée sinon.
        val newAnchor = when (action) {
            ExitAction.TRIM -> price
            ExitAction.SELL -> null
            ExitAction.HOLD -> lastTrimPrice
        }

        return ExitAdvice(ticker, action, plPct, price, reasons, newAnchor)
    }

    private fun fmt(v: Double): String = "${if (v >= 0) "+" else ""}${(v * 10).toInt() / 10.0}"
}
