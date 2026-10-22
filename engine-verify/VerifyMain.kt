// Harnais de vérification autonome (Kotlin pur, sans JUnit) : reprend tous les
// cas des tests JUnit du module app et les exécute via le compilateur Kotlin
// embarqué dans Gradle, car le réseau Maven est bloqué dans ce conteneur.
// Les mêmes assertions existent en JUnit dans app/src/test (pour Android Studio).
import bzh.dargio.boursosignal.domain.signals.*
import kotlin.math.sqrt
import kotlin.system.exitProcess

object Check {
    var total = 0
    var failed = 0
    fun ok(cond: Boolean, msg: String) {
        total++
        if (cond) println("  PASS  $msg") else { failed++; println("  FAIL  $msg") }
    }
    fun eq(exp: Double, act: Double?, eps: Double, msg: String) =
        ok(act != null && kotlin.math.abs(exp - act) <= eps, "$msg (attendu=$exp, obtenu=$act)")
}

fun main() {
    val eps = 1e-9

    println("[Indicators]")
    Indicators.sma(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3).let {
        Check.ok(it[0] == null && it[1] == null, "SMA chauffe = null")
        Check.eq(2.0, it[2], eps, "SMA[2]"); Check.eq(3.0, it[3], eps, "SMA[3]"); Check.eq(4.0, it[4], eps, "SMA[4]")
    }
    Indicators.ema(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3).let {
        Check.eq(2.0, it[2], eps, "EMA[2]"); Check.eq(3.0, it[3], eps, "EMA[3]"); Check.eq(4.0, it[4], eps, "EMA[4]")
    }
    Indicators.rsi((1..20).map { it.toDouble() }, 14).filterNotNull().let { d ->
        Check.ok(d.isNotEmpty() && d.all { kotlin.math.abs(it - 100.0) <= eps }, "RSI série croissante = 100")
    }
    Indicators.rsi((20 downTo 1).map { it.toDouble() }, 14).filterNotNull().let { d ->
        Check.ok(d.isNotEmpty() && d.all { kotlin.math.abs(it) <= eps }, "RSI série décroissante = 0")
    }
    run {
        val noisy = listOf(44.0, 44.25, 44.5, 43.75, 44.5, 45.0, 47.0, 46.0, 46.5, 46.25,
            45.75, 46.0, 47.0, 48.0, 47.5, 47.0, 46.5, 46.0, 46.25, 47.75, 47.25)
        Check.ok(Indicators.rsi(noisy, 14).filterNotNull().all { it in 0.0..100.0 }, "RSI dans [0,100]")
    }
    Indicators.macd(List(40) { 50.0 }).let {
        Check.eq(0.0, it.macd.last(), 1e-6, "MACD série constante = 0")
        Check.eq(0.0, it.histogram.last(), 1e-6, "Histogramme série constante = 0")
    }
    Check.ok(Indicators.macd((1..40).map { it.toDouble() }).macd.last()!! > 0.0, "MACD > 0 en tendance haussière")
    Indicators.bollinger(List(25) { 10.0 }, 20, 2.0).let {
        Check.eq(10.0, it.middle.last(), eps, "Bollinger milieu série plate")
        Check.eq(10.0, it.upper.last(), eps, "Bollinger haut série plate")
        Check.eq(10.0, it.lower.last(), eps, "Bollinger bas série plate")
    }
    Indicators.bollinger(listOf(2.0, 4.0, 4.0, 4.0, 6.0), 5, 2.0).let {
        Check.eq(4.0, it.middle[4], eps, "Bollinger milieu fenêtre connue")
        Check.eq(4.0 + 2.0 * sqrt(1.6), it.upper[4], 1e-6, "Bollinger haut fenêtre connue")
        Check.eq(4.0 - 2.0 * sqrt(1.6), it.lower[4], 1e-6, "Bollinger bas fenêtre connue")
    }

    println("[SignalEngine]")
    val cfg = StrategyConfig(
        emaFastPeriod = 3, emaSlowPeriod = 5, rsiPeriod = 5, volumeAvgPeriod = 5,
        volumeFactor = 100.0, buyThreshold = 1.0, sellThreshold = 1.0,
        stopLossPct = -0.08, takeProfitPct = 0.15,
    )
    fun candles(prices: List<Double>) = prices.mapIndexed { i, p -> Candle("2026-01-${i + 1}", p, p, p, p, 1000.0) }
    val engine = SignalEngine(cfg)

    Check.ok(engine.evaluate(candles(listOf(10.0, 11.0, 12.0))).type == SignalType.NONE, "Historique insuffisant = NONE")

    engine.evaluate(candles(listOf(100.0, 99.0, 98.0, 97.0, 96.0, 95.0, 94.0, 93.0, 92.0, 91.0, 90.0, 89.0, 88.0, 87.0, 120.0))).let {
        Check.ok(it.type == SignalType.BUY, "Croisement haussier = BUY (obtenu ${it.type}, score ${it.score}, raisons ${it.reasons})")
    }
    engine.evaluate(candles(listOf(80.0, 81.0, 82.0, 83.0, 84.0, 85.0, 86.0, 87.0, 88.0, 89.0, 90.0, 91.0, 92.0, 93.0, 60.0))).let {
        Check.ok(it.type == SignalType.SELL, "Croisement baissier = SELL (obtenu ${it.type}, raisons ${it.reasons})")
    }
    Check.ok(engine.evaluate(candles(List(15) { 100.0 })).type == SignalType.NONE, "Série plate sans position = NONE")
    engine.evaluate(candles(List(15) { 100.0 }), avgEntryPrice = 120.0).let {
        Check.ok(it.type == SignalType.SELL && it.reasons.any { r -> r.contains("Stop-loss") }, "Stop-loss = SELL")
    }
    engine.evaluate(candles(List(15) { 100.0 }), avgEntryPrice = 80.0).let {
        Check.ok(it.type == SignalType.SELL && it.reasons.any { r -> r.contains("Take-profit") }, "Take-profit = SELL")
    }

    println("\nRésultat : ${Check.total - Check.failed}/${Check.total} tests réussis")
    if (Check.failed > 0) { println("ÉCHEC"); exitProcess(1) } else println("SUCCÈS")
}
