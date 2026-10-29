package bzh.dargio.boursosignal.domain.signals

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Orientation d'une figure en chandelier. */
enum class PatternBias { BULLISH, BEARISH, NEUTRAL }

/**
 * Figure en chandelier japonais détectée sur la dernière bougie d'une série.
 *
 * @param name  nom de la figure (français)
 * @param bias  sens du retournement attendu (haussier, baissier ou indécision)
 * @param detail courte explication affichable
 */
data class CandlePattern(val name: String, val bias: PatternBias, val detail: String)

/**
 * Reconnaissance des principales figures en chandelier de retournement de
 * tendance (marteau, pendu, avalement, harami, étoiles, pénétrante, nuage noir,
 * trois soldats / trois corbeaux, doji). Logique pure et testable, sans
 * dépendance Android.
 *
 * Les figures sont évaluées sur la ou les dernières bougies de la série ; le
 * contexte de tendance (haussière/baissière) qui précède décide, pour une même
 * forme, du sens du signal (ex. marteau vs pendu).
 */
object Candlestick {

    private fun Candle.body() = abs(close - open)
    private fun Candle.range() = high - low
    private fun Candle.upperWick() = high - max(open, close)
    private fun Candle.lowerWick() = min(open, close) - low
    private fun Candle.bull() = close > open
    private fun Candle.bear() = close < open
    private fun Candle.bodyTop() = max(open, close)
    private fun Candle.bodyBottom() = min(open, close)
    private fun Candle.mid() = (open + close) / 2.0

    /** Tendance des quelques séances précédant la figure : -1 baissière, +1 haussière, 0 neutre. */
    private fun priorTrend(candles: List<Candle>, patternLen: Int): Int {
        val i = candles.size - patternLen - 1
        if (i < 4) return 0
        val recent = candles[i].close
        val past = candles[i - 4].close
        return when {
            recent < past * 0.995 -> -1
            recent > past * 1.005 -> 1
            else -> 0
        }
    }

    /** Détecte les figures se terminant sur la dernière bougie, de la plus forte à la plus faible. */
    fun detect(candles: List<Candle>): List<CandlePattern> {
        if (candles.size < 5) return emptyList()
        val out = mutableListOf<CandlePattern>()
        val n = candles.size
        val c1 = candles[n - 1]            // dernière bougie
        val c2 = candles[n - 2]
        val c3 = candles[n - 3]

        // --- Figures à trois bougies (les plus fiables) ---
        val t3 = priorTrend(candles, 3)
        if (isStar(c3, c2, c1, bullishReversal = true) && t3 <= 0) {
            out += CandlePattern("Étoile du matin", PatternBias.BULLISH, "Retournement haussier après une baisse (3 bougies)")
        }
        if (isStar(c3, c2, c1, bullishReversal = false) && t3 >= 0) {
            out += CandlePattern("Étoile du soir", PatternBias.BEARISH, "Retournement baissier après une hausse (3 bougies)")
        }
        if (c3.bull() && c2.bull() && c1.bull() && c1.close > c2.close && c2.close > c3.close &&
            c3.body() > 0 && c2.body() > 0 && c1.body() > 0
        ) {
            out += CandlePattern("Trois soldats blancs", PatternBias.BULLISH, "Trois hausses consécutives : dynamique haussière")
        }
        if (c3.bear() && c2.bear() && c1.bear() && c1.close < c2.close && c2.close < c3.close &&
            c3.body() > 0 && c2.body() > 0 && c1.body() > 0
        ) {
            out += CandlePattern("Trois corbeaux", PatternBias.BEARISH, "Trois baisses consécutives : dynamique baissière")
        }

        // --- Figures à deux bougies ---
        val t2 = priorTrend(candles, 2)
        if (c2.bear() && c1.bull() && c1.bodyTop() >= c2.bodyTop() && c1.bodyBottom() <= c2.bodyBottom() &&
            c1.body() > c2.body() && t2 <= 0
        ) {
            out += CandlePattern("Avalement haussier", PatternBias.BULLISH, "La bougie verte englobe la rouge précédente : retournement haussier")
        }
        if (c2.bull() && c1.bear() && c1.bodyTop() >= c2.bodyTop() && c1.bodyBottom() <= c2.bodyBottom() &&
            c1.body() > c2.body() && t2 >= 0
        ) {
            out += CandlePattern("Avalement baissier", PatternBias.BEARISH, "La bougie rouge englobe la verte précédente : retournement baissier")
        }
        if (c2.bear() && c1.bull() && c1.bodyTop() <= c2.bodyTop() && c1.bodyBottom() >= c2.bodyBottom() &&
            c1.body() < c2.body() && c2.body() > 0 && t2 <= 0
        ) {
            out += CandlePattern("Harami haussier", PatternBias.BULLISH, "Petite bougie verte contenue dans la rouge : essoufflement de la baisse")
        }
        if (c2.bull() && c1.bear() && c1.bodyTop() <= c2.bodyTop() && c1.bodyBottom() >= c2.bodyBottom() &&
            c1.body() < c2.body() && c2.body() > 0 && t2 >= 0
        ) {
            out += CandlePattern("Harami baissier", PatternBias.BEARISH, "Petite bougie rouge contenue dans la verte : essoufflement de la hausse")
        }
        if (c2.bear() && c1.bull() && c1.open <= c2.close && c1.close > c2.mid() && c1.close < c2.open && c2.body() > 0 && t2 <= 0) {
            out += CandlePattern("Pénétrante haussière", PatternBias.BULLISH, "Clôture au-dessus du milieu de la bougie rouge : pression acheteuse")
        }
        if (c2.bull() && c1.bear() && c1.open >= c2.close && c1.close < c2.mid() && c1.close > c2.open && c2.body() > 0 && t2 >= 0) {
            out += CandlePattern("Couverture en nuage noir", PatternBias.BEARISH, "Clôture sous le milieu de la bougie verte : pression vendeuse")
        }

        // --- Figures à une bougie (marteau / pendu / étoile filante / doji) ---
        val t1 = priorTrend(candles, 1)
        val range = c1.range()
        if (range > 0 && c1.body() > 0) {
            val longLower = c1.lowerWick() >= 2 * c1.body() && c1.upperWick() <= c1.body()
            val longUpper = c1.upperWick() >= 2 * c1.body() && c1.lowerWick() <= c1.body()
            if (longLower && t1 < 0) out += CandlePattern("Marteau", PatternBias.BULLISH, "Longue mèche basse après une baisse : rejet des plus bas")
            if (longLower && t1 > 0) out += CandlePattern("Pendu", PatternBias.BEARISH, "Longue mèche basse après une hausse : signal d'alerte")
            if (longUpper && t1 < 0) out += CandlePattern("Marteau inversé", PatternBias.BULLISH, "Longue mèche haute après une baisse : tentative de rebond")
            if (longUpper && t1 > 0) out += CandlePattern("Étoile filante", PatternBias.BEARISH, "Longue mèche haute après une hausse : rejet des plus hauts")
        }
        if (range > 0 && c1.body() <= 0.1 * range) {
            out += CandlePattern("Doji", PatternBias.NEUTRAL, "Indécision : ouverture et clôture quasi identiques")
        }

        return out
    }

    /** Résumé : la figure dominante (la première détectée, les plus fiables d'abord), ou null. */
    fun dominant(candles: List<Candle>): CandlePattern? = detect(candles).firstOrNull { it.bias != PatternBias.NEUTRAL }

    /** Étoile du matin / du soir : grande bougie, petit corps (étoile), grande bougie opposée. */
    private fun isStar(c3: Candle, c2: Candle, c1: Candle, bullishReversal: Boolean): Boolean {
        if (c3.body() <= 0 || c1.body() <= 0) return false
        val smallMiddle = c2.body() <= 0.5 * c3.body()
        if (!smallMiddle) return false
        return if (bullishReversal) {
            c3.bear() && c1.bull() && c1.close >= c3.mid()
        } else {
            c3.bull() && c1.bear() && c1.close <= c3.mid()
        }
    }
}
