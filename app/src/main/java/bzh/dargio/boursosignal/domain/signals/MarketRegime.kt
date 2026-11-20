package bzh.dargio.boursosignal.domain.signals

import kotlin.math.sqrt

/** État du marché global (CAC 40), qui conditionne les nouveaux achats. */
enum class Regime { RISK_ON, CAUTION, RISK_OFF }

/** Lecture de la « météo du marché » à une date, avec ses raisons. */
data class RegimeReading(
    val regime: Regime,
    val reasons: List<String>,
    val date: String,
) {
    val label: String
        get() = when (regime) {
            Regime.RISK_ON -> "Marché favorable"
            Regime.CAUTION -> "Marché fragile"
            Regime.RISK_OFF -> "Marché baissier"
        }
}

/**
 * « Météo du marché » : lit l'état du CAC 40 pour éviter d'acheter des valeurs en
 * tendance haussière au moment où le marché global se retourne — la plupart des
 * actions suivent alors l'indice, quelle que soit leur tendance propre.
 *
 * Critères (clôtures journalières) :
 *  - position du CAC 40 vs ses moyennes 50 et 200 jours, pente de la moyenne 50 j ;
 *  - repli depuis le plus haut des 20 dernières séances ;
 *  - variation sur 5 séances (chute brutale) ;
 *  - volatilité récente vs normale (nervosité).
 *
 * Limite assumée : basé sur la fin de séance, il réagit en quelques séances à une
 * dégradation mais ne peut pas prévoir un choc soudain. Logique pure et testable.
 */
object MarketRegimeEvaluator {

    /** Ticker Yahoo du CAC 40. */
    const val INDEX_TICKER = "^FCHI"

    /** Nombre minimal de séances du CAC 40 pour juger (en dessous : pas de filtre). */
    const val MIN_HISTORY = 61

    fun evaluate(index: List<Candle>): RegimeReading {
        val last = index.lastOrNull()
            ?: return RegimeReading(Regime.RISK_ON, listOf("Pas de donnée sur le CAC 40"), "")
        if (index.size < MIN_HISTORY) {
            return RegimeReading(Regime.RISK_ON, listOf("Historique du CAC 40 insuffisant pour juger"), last.date)
        }

        val closes = index.closes()
        val close = last.close
        val sma50 = Indicators.sma(closes, 50)
        val ma50 = sma50.last()
        val ma50Before = sma50.getOrNull(closes.lastIndex - 10)
        val ma200 = if (closes.size >= 200) Indicators.sma(closes, 200).last() else null

        val high20 = closes.takeLast(20).max()
        val dd20 = (close - high20) / high20 * 100.0                 // ≤ 0
        val ret5 = (close / closes[closes.lastIndex - 5] - 1.0) * 100.0
        val volRatio = volatilityRatio(closes)

        val below50 = ma50 != null && close < ma50
        val below200 = ma200 != null && close < ma200
        val ma50Falling = ma50 != null && ma50Before != null && ma50 < ma50Before

        val off = mutableListOf<String>()
        val warn = mutableListOf<String>()

        when {
            dd20 <= -6.0 -> off += "Correction en cours : ${f(dd20)} % depuis le plus haut d'un mois"
            dd20 <= -3.0 -> warn += "Repli de ${f(dd20)} % depuis le plus haut d'un mois"
        }
        when {
            ret5 <= -4.0 -> off += "Chute brutale : ${f(ret5)} % en 5 séances"
            ret5 <= -2.5 -> warn += "Baisse marquée : ${f(ret5)} % en 5 séances"
        }
        when {
            below50 && below200 -> off += "CAC 40 sous ses moyennes 50 et 200 jours (tendance baissière)"
            below50 && ma50Falling && dd20 <= -3.0 -> off += "CAC 40 sous sa moyenne 50 jours, elle-même en baisse"
            below50 && ma50Falling -> warn += "CAC 40 sous sa moyenne 50 jours, elle-même en baisse"
            below50 -> warn += "CAC 40 repassé sous sa moyenne 50 jours"
            ma50Falling -> warn += "Moyenne 50 jours du CAC 40 orientée à la baisse"
        }
        if (volRatio != null && volRatio >= 1.6) {
            warn += "Nervosité en hausse (volatilité ×${f(volRatio)} par rapport à la normale)"
        }

        return when {
            off.isNotEmpty() -> RegimeReading(Regime.RISK_OFF, off + warn, last.date)
            warn.isNotEmpty() -> RegimeReading(Regime.CAUTION, warn, last.date)
            else -> RegimeReading(
                Regime.RISK_ON,
                listOf("CAC 40 au-dessus de sa moyenne 50 jours, sans repli ni nervosité marqués"),
                last.date,
            )
        }
    }

    /**
     * Régime à chaque date, calculé uniquement avec les séances passées (aucune
     * information future) : sert au backtest pour rejouer le filtre honnêtement.
     */
    fun byDate(index: List<Candle>): Map<String, Regime> {
        val out = HashMap<String, Regime>()
        for (i in MIN_HISTORY - 1 until index.size) {
            out[index[i].date] = evaluate(index.subList(0, i + 1)).regime
        }
        return out
    }

    /** Volatilité des 10 dernières séances rapportée à celle des 60 dernières. */
    private fun volatilityRatio(closes: List<Double>): Double? {
        if (closes.size < 61) return null
        val rets = (1 until closes.size).map { closes[it] / closes[it - 1] - 1.0 }
        val recent = stdev(rets.takeLast(10))
        val normal = stdev(rets.takeLast(60))
        return if (normal > 0.0) recent / normal else null
    }

    private fun stdev(xs: List<Double>): Double {
        val m = xs.average()
        return sqrt(xs.sumOf { (it - m) * (it - m) } / xs.size)
    }

    private fun f(v: Double): String = String.format(java.util.Locale.FRANCE, "%.1f", v)
}
