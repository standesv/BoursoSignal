package bzh.dargio.boursosignal.domain.signals

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Contrôle qualité des données d'une valeur, AVANT toute analyse (§ fiabilité).
 *
 * L'univers « Euronext Paris élargi » contient beaucoup de petites valeurs dont
 * les données gratuites sont lacunaires. Ce filtre écarte celles qui ne sont pas
 * exploitables : historique trop court, données périmées, illiquidité, anomalies
 * de cours. Seules les valeurs qui passent sont analysées et affichées.
 */
object DataQuality {

    data class Report(val ok: Boolean, val reasons: List<String>)

    fun check(
        candles: List<Candle>,
        referenceDate: String? = null,
        minHistory: Int = 120,
        maxStaleDays: Long = 7,
        minAvgTurnover: Double = 200_000.0,
    ): Report {
        val reasons = mutableListOf<String>()

        if (candles.size < minHistory) {
            reasons += "Historique insuffisant (${candles.size} < $minHistory)"
        }

        // Intégrité : prix positifs et cohérents (high >= low, corps dans la mèche).
        val eps = 1e-6
        val anomaly = candles.any { c ->
            c.open <= 0 || c.high <= 0 || c.low <= 0 || c.close <= 0 ||
                c.high + eps < c.low ||
                c.high + eps < maxOf(c.open, c.close) ||
                c.low - eps > minOf(c.open, c.close)
        }
        if (anomaly) reasons += "Anomalies de cours détectées"

        // Liquidité : capital échangé moyen (cours × volume) sur les 20 dernières séances.
        if (candles.isNotEmpty()) {
            val window = candles.takeLast(20)
            val avgTurnover = window.sumOf { it.close * it.volume } / window.size
            if (avgTurnover < minAvgTurnover) {
                reasons += "Liquidité trop faible (${avgTurnover.toLong()} € < ${minAvgTurnover.toLong()} €)"
            }
        }

        // Fraîcheur : la dernière bougie ne doit pas être périmée.
        if (referenceDate != null && candles.isNotEmpty()) {
            runCatching {
                val last = LocalDate.parse(candles.last().date)
                val ref = LocalDate.parse(referenceDate)
                if (ChronoUnit.DAYS.between(last, ref) > maxStaleDays) {
                    reasons += "Données périmées (dernière séance ${candles.last().date})"
                }
            }
        }

        return Report(reasons.isEmpty(), reasons)
    }
}

/** Position actuelle (lecture continue), au-delà du signal ponctuel. */
enum class Stance { BUY, WATCH, AVOID, NEUTRAL }

/** Opportunité détectée sur une valeur par le scanner. */
data class Opportunity(
    val ticker: String,
    val stance: Stance,
    val score: Double,       // [-1, 1], plus c'est haut, plus c'est haussier
    val reasons: List<String>,
    val price: Double,
    val date: String,
)

/**
 * Évalue la TENDANCE ACTUELLE d'une valeur (continue), pas seulement l'événement
 * de croisement. Donne toujours une lecture exploitable (Achat / Surveiller /
 * Éviter / Neutre) à partir de la configuration des indicateurs au dernier cours.
 */
object StanceEvaluator {

    fun evaluate(ticker: String, candles: List<Candle>, config: StrategyConfig = StrategyConfig()): Opportunity {
        val last = candles.lastOrNull()
            ?: return Opportunity(ticker, Stance.NEUTRAL, 0.0, listOf("Pas de donnée"), 0.0, "")

        val closes = candles.closes()
        val emaFast = Indicators.ema(closes, config.emaFastPeriod).lastOrNull { it != null }
        val emaSlow = Indicators.ema(closes, config.emaSlowPeriod).lastOrNull { it != null }
        val rsi = Indicators.rsi(closes, config.rsiPeriod).lastOrNull { it != null }

        if (emaFast == null || emaSlow == null || rsi == null || last.close <= 0) {
            return Opportunity(ticker, Stance.NEUTRAL, 0.0, listOf("Historique insuffisant"), last.close, last.date)
        }

        val reasons = mutableListOf<String>()
        // La direction de la tendance domine, ajustée par sa force et la zone RSI.
        val fastAboveSlow = (emaFast - emaSlow) / emaSlow
        var score = when {
            emaFast > emaSlow -> 0.55
            emaFast < emaSlow -> -0.55
            else -> 0.0
        }
        score += (fastAboveSlow * 10.0).coerceIn(-0.3, 0.3)
        reasons += if (emaFast >= emaSlow)
            "Tendance haussière (EMA${config.emaFastPeriod} > EMA${config.emaSlowPeriod})"
        else
            "Tendance baissière (EMA${config.emaFastPeriod} < EMA${config.emaSlowPeriod})"

        when {
            rsi > config.rsiOverbought -> {
                score -= 0.25
                reasons += "RSI en surachat (${fmt(rsi)}) — risque de repli"
            }
            rsi < config.rsiOversold -> {
                score -= 0.1
                reasons += "RSI en survente (${fmt(rsi)})"
            }
            else -> reasons += "RSI sain (${fmt(rsi)})"
        }
        score = score.coerceIn(-1.0, 1.0)

        val stance = when {
            score >= 0.5 -> Stance.BUY
            score >= 0.15 -> Stance.WATCH
            score <= -0.4 -> Stance.AVOID
            else -> Stance.NEUTRAL
        }
        return Opportunity(ticker, stance, score, reasons, last.close, last.date)
    }

    private fun fmt(v: Double): String = ((v * 10).toInt() / 10.0).toString()
}

/**
 * Scanner de marché : analyse tout un univers, écarte les valeurs aux données non
 * fiables (via [DataQuality]) et renvoie les opportunités classées par score.
 */
object MarketScanner {

    fun scan(
        data: Map<String, List<Candle>>,
        config: StrategyConfig = StrategyConfig(),
        referenceDate: String? = null,
        minHistory: Int = 120,
        minAvgTurnover: Double = 200_000.0,
    ): List<Opportunity> =
        data.entries
            .filter { (_, candles) ->
                DataQuality.check(candles, referenceDate, minHistory, minAvgTurnover = minAvgTurnover).ok
            }
            .map { (ticker, candles) -> StanceEvaluator.evaluate(ticker, candles, config) }
            .sortedByDescending { it.score }
}
