package bzh.dargio.boursosignal.domain.signals

import kotlin.math.sqrt

/** Niveau de risque dérivé de la volatilité. */
enum class RiskLevel { LOW, MEDIUM, HIGH }

/**
 * Rapport d'analyse détaillé d'une valeur (analyse technique multi-facteurs).
 * Tout est calculé à partir de l'historique de cours ; pur et testable.
 */
data class AnalysisReport(
    val price: Double,
    val trendUp: Boolean,
    val emaFast: Double?,
    val emaSlow: Double?,
    val rsi: Double?,
    val macdPositive: Boolean?,
    val perf1M: Double?,
    val perf3M: Double?,
    val perf6M: Double?,
    val perf1Y: Double?,
    val annualVolatilityPct: Double?,
    val risk: RiskLevel,
    val high52: Double?,
    val low52: Double?,
    val fromHighPct: Double?,
    val volumeVsAvgPct: Double?,
    val highlights: List<String>,
    val suggestion: String,
)

object Analyzer {

    /** Volatilité annualisée en % (écart-type des rendements quotidiens × √252). */
    fun annualVolatilityPct(closes: List<Double>, window: Int = 252): Double? {
        if (closes.size < 20) return null
        val rets = ArrayList<Double>(closes.size)
        for (i in 1 until closes.size) {
            val prev = closes[i - 1]
            if (prev > 0) rets += (closes[i] - prev) / prev
        }
        val sample = rets.takeLast(window)
        if (sample.size < 10) return null
        val mean = sample.average()
        val variance = sample.sumOf { (it - mean) * (it - mean) } / sample.size
        return sqrt(variance) * sqrt(252.0) * 100.0
    }

    fun riskFrom(volatilityPct: Double?): RiskLevel = when {
        volatilityPct == null -> RiskLevel.MEDIUM
        volatilityPct < 20.0 -> RiskLevel.LOW
        volatilityPct <= 35.0 -> RiskLevel.MEDIUM
        else -> RiskLevel.HIGH
    }

    private fun perfOver(closes: List<Double>, n: Int): Double? {
        if (closes.size <= n) return null
        val ref = closes[closes.size - 1 - n]
        if (ref <= 0) return null
        return (closes.last() - ref) / ref * 100.0
    }

    fun analyze(candles: List<Candle>, config: StrategyConfig = StrategyConfig()): AnalysisReport {
        val last = candles.lastOrNull()
        val closes = candles.closes()
        val emaFast = Indicators.ema(closes, config.emaFastPeriod).lastOrNull { it != null }
        val emaSlow = Indicators.ema(closes, config.emaSlowPeriod).lastOrNull { it != null }
        val rsi = Indicators.rsi(closes, config.rsiPeriod).lastOrNull { it != null }
        val macd = if (closes.size > 26) Indicators.macd(closes).macd.lastOrNull { it != null } else null
        val vol = annualVolatilityPct(closes)
        val risk = riskFrom(vol)
        val trendUp = emaFast != null && emaSlow != null && emaFast >= emaSlow

        val window = closes.takeLast(252)
        val high52 = window.maxOrNull()
        val low52 = window.minOrNull()
        val price = last?.close ?: 0.0
        val fromHigh = if (high52 != null && high52 > 0) (price - high52) / high52 * 100.0 else null

        val volumes = candles.volumes()
        val lastVol = volumes.lastOrNull()
        val avgVol = volumes.takeLast(20).takeIf { it.isNotEmpty() }?.average()
        val volVsAvg = if (lastVol != null && avgVol != null && avgVol > 0) (lastVol - avgVol) / avgVol * 100.0 else null

        val p1 = perfOver(closes, 21); val p3 = perfOver(closes, 63)
        val p6 = perfOver(closes, 126); val p12 = perfOver(closes, 252)

        val highlights = buildList {
            add(if (trendUp) "Tendance de fond haussière (EMA${config.emaFastPeriod} au-dessus de EMA${config.emaSlowPeriod})"
            else "Tendance de fond baissière (EMA${config.emaFastPeriod} sous EMA${config.emaSlowPeriod})")
            rsi?.let {
                val zone = when { it > config.rsiOverbought -> "surachat, risque de repli"; it < config.rsiOversold -> "survente"; else -> "zone saine" }
                add("Momentum : RSI ${fmt1(it)} ($zone)")
            }
            macdPositiveOf(macd)?.let { add(if (it) "MACD positif : dynamique favorable" else "MACD négatif : dynamique faible") }
            val perfParts = listOfNotNull(
                p1?.let { "1M ${signed(it)}" }, p3?.let { "3M ${signed(it)}" },
                p6?.let { "6M ${signed(it)}" }, p12?.let { "1A ${signed(it)}" },
            )
            if (perfParts.isNotEmpty()) add("Performance : ${perfParts.joinToString(" · ")}")
            vol?.let { add("Volatilité annualisée ~${fmt0(it)} % → risque ${riskLabel(risk).lowercase()}") }
            fromHigh?.let { if (it < 0) add("À ${fmt1(it)} % de son plus haut sur 1 an") else add("Au plus haut sur 1 an") }
            volVsAvg?.let { if (it > 20) add("Volume récent soutenu (+${fmt0(it)} % vs moyenne)") else if (it < -20) add("Volume récent faible (${fmt0(it)} % vs moyenne)") }
        }

        val suggestion = buildSuggestion(trendUp, rsi, risk, config)

        return AnalysisReport(
            price, trendUp, emaFast, emaSlow, rsi, macdPositiveOf(macd),
            p1, p3, p6, p12, vol, risk, high52, low52, fromHigh, volVsAvg, highlights, suggestion,
        )
    }

    private fun buildSuggestion(trendUp: Boolean, rsi: Double?, risk: RiskLevel, config: StrategyConfig): String {
        val base = if (trendUp) "Profil plutôt favorable à l'achat : la tendance de fond est haussière"
        else "Profil prudent : la tendance de fond est baissière, mieux vaut attendre un retournement"
        val rsiNote = when {
            rsi == null -> ""
            rsi > config.rsiOverbought -> ", mais le RSI est en surachat — attention à un repli à court terme"
            rsi < config.rsiOversold -> ", et le RSI est bas — un rebond est possible mais à confirmer"
            else -> ", avec un momentum sain"
        }
        val riskNote = " Risque ${riskLabel(risk).lowercase()} (volatilité)."
        return "$base$rsiNote.$riskNote"
    }

    private fun macdPositiveOf(macd: Double?): Boolean? = macd?.let { it > 0 }
    private fun signed(v: Double): String = "${if (v >= 0) "+" else ""}${fmt1(v)} %"
    private fun fmt0(v: Double): String = v.toInt().toString()
    private fun fmt1(v: Double): String = ((v * 10).toInt() / 10.0).toString()

    fun riskLabel(r: RiskLevel): String = when (r) {
        RiskLevel.LOW -> "Faible"; RiskLevel.MEDIUM -> "Modéré"; RiskLevel.HIGH -> "Élevé"
    }
}
