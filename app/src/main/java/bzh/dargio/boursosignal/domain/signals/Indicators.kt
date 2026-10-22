package bzh.dargio.boursosignal.domain.signals

import kotlin.math.sqrt

/**
 * Indicateurs techniques (Kotlin pur, sans dépendance Android).
 *
 * Convention : chaque fonction renvoie une liste de la même taille que l'entrée.
 * Les positions de "chauffe" (pas assez d'historique pour calculer la valeur)
 * valent `null`. Cela rend l'alignement temporel explicite : l'indice i de la
 * sortie correspond toujours à l'indice i de la série de prix en entrée.
 */
object Indicators {

    /** Moyenne mobile simple (SMA) sur `period` valeurs. */
    fun sma(values: List<Double>, period: Int): List<Double?> {
        require(period > 0) { "La période doit être strictement positive" }
        val out = arrayOfNulls<Double>(values.size)
        if (values.size < period) return out.toList()
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            if (i >= period - 1) out[i] = sum / period
        }
        return out.toList()
    }

    /**
     * Moyenne mobile exponentielle (EMA) sur `period` valeurs.
     * Facteur de lissage k = 2 / (period + 1), amorçage par la SMA des `period`
     * premières valeurs (convention la plus répandue).
     */
    fun ema(values: List<Double>, period: Int): List<Double?> {
        require(period > 0) { "La période doit être strictement positive" }
        val out = arrayOfNulls<Double>(values.size)
        if (values.size < period) return out.toList()
        val k = 2.0 / (period + 1)
        // Amorçage : SMA des `period` premières valeurs, placée à l'indice period-1.
        var seed = 0.0
        for (i in 0 until period) seed += values[i]
        var prev = seed / period
        out[period - 1] = prev
        for (i in period until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out[i] = prev
        }
        return out.toList()
    }

    /**
     * RSI (Relative Strength Index) selon la méthode de lissage de Wilder.
     * `period` = 14 par défaut côté stratégie. RSI ∈ [0, 100].
     * Série strictement croissante → 100 ; strictement décroissante → 0.
     */
    fun rsi(values: List<Double>, period: Int = 14): List<Double?> {
        require(period > 0) { "La période doit être strictement positive" }
        val out = arrayOfNulls<Double>(values.size)
        if (values.size <= period) return out.toList()

        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val change = values[i] - values[i - 1]
            if (change >= 0) gainSum += change else lossSum -= change
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        out[period] = rsiFrom(avgGain, avgLoss)

        for (i in period + 1 until values.size) {
            val change = values[i] - values[i - 1]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            out[i] = rsiFrom(avgGain, avgLoss)
        }
        return out.toList()
    }

    private fun rsiFrom(avgGain: Double, avgLoss: Double): Double {
        if (avgLoss == 0.0) return 100.0
        if (avgGain == 0.0) return 0.0
        val rs = avgGain / avgLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    /** Résultat MACD aligné sur la série d'entrée. */
    data class Macd(
        val macd: List<Double?>,
        val signal: List<Double?>,
        val histogram: List<Double?>,
    )

    /**
     * MACD : ligne MACD = EMA(fast) − EMA(slow), ligne signal = EMA(signal) de la
     * ligne MACD, histogramme = MACD − signal. Valeurs par défaut (12, 26, 9).
     */
    fun macd(values: List<Double>, fast: Int = 12, slow: Int = 26, signal: Int = 9): Macd {
        val emaFast = ema(values, fast)
        val emaSlow = ema(values, slow)
        val macdLine = values.indices.map { i ->
            val f = emaFast[i]; val s = emaSlow[i]
            if (f != null && s != null) f - s else null
        }
        // EMA de la ligne MACD, calculée uniquement sur sa partie définie, puis réalignée.
        val firstDefined = macdLine.indexOfFirst { it != null }
        val signalLine = arrayOfNulls<Double>(values.size)
        if (firstDefined >= 0) {
            val defined = macdLine.drop(firstDefined).map { it ?: 0.0 }
            val emaSignal = ema(defined, signal)
            for (j in emaSignal.indices) signalLine[firstDefined + j] = emaSignal[j]
        }
        val histogram = values.indices.map { i ->
            val m = macdLine[i]; val s = signalLine[i]
            if (m != null && s != null) m - s else null
        }
        return Macd(macdLine, signalLine.toList(), histogram)
    }

    /** Résultat des bandes de Bollinger aligné sur la série d'entrée. */
    data class Bollinger(
        val middle: List<Double?>,
        val upper: List<Double?>,
        val lower: List<Double?>,
    )

    /**
     * Bandes de Bollinger : bande médiane = SMA(period), bandes haute/basse =
     * médiane ± mult × écart-type (population) sur la fenêtre. Défauts (20, 2.0).
     */
    fun bollinger(values: List<Double>, period: Int = 20, mult: Double = 2.0): Bollinger {
        require(period > 0) { "La période doit être strictement positive" }
        val middle = sma(values, period)
        val upper = arrayOfNulls<Double>(values.size)
        val lower = arrayOfNulls<Double>(values.size)
        for (i in values.indices) {
            val mid = middle[i] ?: continue
            var variance = 0.0
            for (j in i - period + 1..i) {
                val d = values[j] - mid
                variance += d * d
            }
            val sd = sqrt(variance / period)
            upper[i] = mid + mult * sd
            lower[i] = mid - mult * sd
        }
        return Bollinger(middle, upper.toList(), lower.toList())
    }
}
