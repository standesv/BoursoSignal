package bzh.dargio.boursosignal.domain.signals

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Contrôle qualité des données d'une valeur, AVANT toute analyse (§ fiabilité).
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

        val eps = 1e-6
        val anomaly = candles.any { c ->
            c.open <= 0 || c.high <= 0 || c.low <= 0 || c.close <= 0 ||
                c.high + eps < c.low ||
                c.high + eps < maxOf(c.open, c.close) ||
                c.low - eps > minOf(c.open, c.close)
        }
        if (anomaly) reasons += "Anomalies de cours détectées"

        if (candles.isNotEmpty()) {
            val window = candles.takeLast(20)
            val avgTurnover = window.sumOf { it.close * it.volume } / window.size
            if (avgTurnover < minAvgTurnover) {
                reasons += "Liquidité trop faible (${avgTurnover.toLong()} € < ${minAvgTurnover.toLong()} €)"
            }
        }

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
    val risk: RiskLevel = RiskLevel.MEDIUM,
)

/**
 * Évalue la TENDANCE ACTUELLE d'une valeur (continue). Donne toujours une lecture
 * exploitable (Achat / Surveiller / Éviter / Neutre). Si `benchCloses` est fourni
 * (cours du CAC 40), la force relative au marché est prise en compte.
 */
object StanceEvaluator {

    fun evaluate(
        ticker: String,
        candles: List<Candle>,
        config: StrategyConfig = StrategyConfig(),
        benchCloses: List<Double>? = null,
    ): Opportunity {
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

        val macd = if (closes.size > 26) Indicators.macd(closes).macd.lastOrNull { it != null } else null
        if (macd != null) {
            if (macd > 0) { score += 0.1; reasons += "MACD positif (dynamique favorable)" }
            else { score -= 0.1; reasons += "MACD négatif (dynamique faible)" }
        }

        // Force de tendance : une tendance marquée (ADX élevé) renforce la lecture directionnelle.
        val adx = Indicators.adx(candles, config.adxPeriod).lastOrNull { it != null }
        if (adx != null && adx >= config.adxThreshold) {
            val boost = if (emaFast >= emaSlow) 0.1 else -0.1
            score += boost
            reasons += "Tendance marquée (ADX ${fmt(adx)})"
        }

        // Force relative au CAC 40 : privilégier les valeurs qui battent le marché.
        if (benchCloses != null) {
            val rs = Indicators.relativeStrengthPct(closes, benchCloses)
            if (rs != null) {
                when {
                    rs > 2.0 -> { score += 0.15; reasons += "Surperforme le CAC 40 (+${fmt(rs)} pts / ~3 mois)" }
                    rs < -2.0 -> { score -= 0.15; reasons += "Sous-performe le CAC 40 (${fmt(rs)} pts / ~3 mois)" }
                    else -> reasons += "En ligne avec le CAC 40"
                }
            }
        }

        val risk = Analyzer.riskFrom(Analyzer.annualVolatilityPct(closes))
        if (risk == RiskLevel.HIGH) { score -= 0.1; reasons += "Volatilité élevée (risque accru)" }

        score = score.coerceIn(-1.0, 1.0)

        val stance = when {
            score >= 0.5 -> Stance.BUY
            score >= 0.15 -> Stance.WATCH
            score <= -0.4 -> Stance.AVOID
            else -> Stance.NEUTRAL
        }
        return Opportunity(ticker, stance, score, reasons, last.close, last.date, risk)
    }

    private fun fmt(v: Double): String = ((v * 10).toInt() / 10.0).toString()
}

/**
 * Scanner de marché : analyse tout un univers, écarte les valeurs aux données non
 * fiables (via [DataQuality]) et renvoie les opportunités classées par score.
 * `benchmark` (cours du CAC 40) alimente la force relative, si fourni.
 */
object MarketScanner {

    fun scan(
        data: Map<String, List<Candle>>,
        config: StrategyConfig = StrategyConfig(),
        referenceDate: String? = null,
        minHistory: Int = 120,
        minAvgTurnover: Double = 200_000.0,
        benchmark: List<Candle>? = null,
    ): List<Opportunity> {
        val benchCloses = benchmark?.closes()
        return data.entries
            .filter { (_, candles) ->
                DataQuality.check(candles, referenceDate, minHistory, minAvgTurnover = minAvgTurnover).ok
            }
            .map { (ticker, candles) -> StanceEvaluator.evaluate(ticker, candles, config, benchCloses) }
            .sortedByDescending { it.score }
    }
}
