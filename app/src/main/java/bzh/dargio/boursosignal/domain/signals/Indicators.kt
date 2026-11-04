package bzh.dargio.boursosignal.domain.signals

import kotlin.math.abs
import kotlin.math.max
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

    /**
     * ATR (Average True Range) de Wilder, mesure de volatilité absolue.
     * Sert à poser un stop adapté à l'agitation réelle de la valeur.
     */
    fun atr(candles: List<Candle>, period: Int = 14): List<Double?> {
        require(period > 0)
        val n = candles.size
        val out = arrayOfNulls<Double>(n)
        if (n <= period) return out.toList()
        val tr = DoubleArray(n)
        tr[0] = candles[0].high - candles[0].low
        for (i in 1 until n) {
            val h = candles[i].high; val l = candles[i].low; val pc = candles[i - 1].close
            tr[i] = max(h - l, max(abs(h - pc), abs(l - pc)))
        }
        var sum = 0.0
        for (i in 1..period) sum += tr[i]
        var atr = sum / period
        out[period] = atr
        for (i in period + 1 until n) {
            atr = (atr * (period - 1) + tr[i]) / period
            out[i] = atr
        }
        return out.toList()
    }

    /**
     * ADX (Average Directional Index) de Wilder : force de la tendance, ∈ [0, 100].
     * Au-dessus de ~20-25, la tendance est suffisamment marquée pour être suivie ;
     * en dessous, le marché est sans direction (croisements peu fiables).
     */
    fun adx(candles: List<Candle>, period: Int = 14): List<Double?> {
        require(period > 0)
        val n = candles.size
        val out = arrayOfNulls<Double>(n)
        if (n <= 2 * period) return out.toList()

        val plusDM = DoubleArray(n)
        val minusDM = DoubleArray(n)
        val tr = DoubleArray(n)
        for (i in 1 until n) {
            val up = candles[i].high - candles[i - 1].high
            val down = candles[i - 1].low - candles[i].low
            plusDM[i] = if (up > down && up > 0) up else 0.0
            minusDM[i] = if (down > up && down > 0) down else 0.0
            val h = candles[i].high; val l = candles[i].low; val pc = candles[i - 1].close
            tr[i] = max(h - l, max(abs(h - pc), abs(l - pc)))
        }

        var trS = 0.0; var pS = 0.0; var mS = 0.0
        for (i in 1..period) { trS += tr[i]; pS += plusDM[i]; mS += minusDM[i] }
        val dx = DoubleArray(n)
        dx[period] = dxOf(trS, pS, mS)
        for (i in period + 1 until n) {
            trS = trS - trS / period + tr[i]
            pS = pS - pS / period + plusDM[i]
            mS = mS - mS / period + minusDM[i]
            dx[i] = dxOf(trS, pS, mS)
        }

        // Premier ADX = moyenne des `period` premières valeurs de DX.
        var sum = 0.0
        for (i in period + 1..2 * period) sum += dx[i]
        var adx = sum / period
        out[2 * period] = adx
        for (i in 2 * period + 1 until n) {
            adx = (adx * (period - 1) + dx[i]) / period
            out[i] = adx
        }
        return out.toList()
    }

    private fun dxOf(trSum: Double, pSum: Double, mSum: Double): Double {
        if (trSum == 0.0) return 0.0
        val pDI = 100.0 * pSum / trSum
        val mDI = 100.0 * mSum / trSum
        val denom = pDI + mDI
        return if (denom == 0.0) 0.0 else 100.0 * abs(pDI - mDI) / denom
    }

    /**
     * Force relative : surperformance (en points de %) d'une valeur par rapport à
     * un indice de référence (ex. CAC 40) sur `window` séances. > 0 = la valeur
     * fait mieux que le marché. null si l'historique est insuffisant.
     */
    fun relativeStrengthPct(stockCloses: List<Double>, benchCloses: List<Double>, window: Int = 63): Double? {
        if (stockCloses.size <= window || benchCloses.size <= window) return null
        val sNow = stockCloses.last(); val sThen = stockCloses[stockCloses.size - 1 - window]
        val bNow = benchCloses.last(); val bThen = benchCloses[benchCloses.size - 1 - window]
        if (sThen <= 0 || bThen <= 0) return null
        val sRet = (sNow - sThen) / sThen
        val bRet = (bNow - bThen) / bThen
        return (sRet - bRet) * 100.0
    }
}
