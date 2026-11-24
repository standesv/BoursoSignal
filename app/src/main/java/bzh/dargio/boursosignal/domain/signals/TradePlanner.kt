package bzh.dargio.boursosignal.domain.signals

import kotlin.math.floor

/**
 * Plan chiffré d'un achat (§23-3) : niveau d'entrée, stop, objectif et rapport
 * gain/risque. Le stop est le PLUS SERRÉ des deux règles qui déclencheront le
 * conseil de vente (stop de volatilité ATR et stop-loss en %).
 */
data class TradePlan(
    val entry: Double,
    val stop: Double,
    val target: Double,
    val rewardRisk: Double,
    val resistance: Double?,   // plus haut récent au-dessus du cours, s'il limite l'objectif
) {
    val riskPerShare: Double get() = entry - stop
}

/** Quantité conseillée pour un plan, selon le capital et les liquidités. */
data class PositionSize(
    val quantity: Int,
    val riskEuros: Double,     // perte si le stop est touché
    val amount: Double,        // montant investi (hors frais)
    val limitedBy: String?,    // ce qui a plafonné la quantité, le cas échéant
)

object TradePlanner {

    fun plan(candles: List<Candle>, config: StrategyConfig = StrategyConfig()): TradePlan? {
        val last = candles.lastOrNull() ?: return null
        val entry = last.close
        if (entry <= 0.0) return null
        val atr = Indicators.atr(candles, config.atrPeriod).lastOrNull { it != null }
        val atrStop = atr?.takeIf { it > 0.0 }?.let { entry - config.atrStopMultiple * it }
        val pctStop = config.stopLossPct?.let { entry * (1.0 + it) }
        val stop = listOfNotNull(atrStop, pctStop).maxOrNull() ?: return null
        val risk = entry - stop
        if (risk <= 0.0) return null
        val defaultTarget = entry + config.targetRiskMultiple * risk
        val recentHigh = candles.takeLast(config.resistanceLookback).maxOf { it.close }
        val resistance = recentHigh.takeIf { it > entry * 1.01 }
        val target = if (resistance != null && resistance < defaultTarget) resistance else defaultTarget
        return TradePlan(entry, stop, target, (target - entry) / risk, resistance)
    }

    /**
     * Quantité pour ne risquer que `riskPerTradePct` % du capital si le stop est
     * touché, plafonnée à `maxPositionPct` % du portefeuille et aux liquidités.
     */
    fun size(plan: TradePlan, capital: Double, cash: Double?, config: StrategyConfig = StrategyConfig()): PositionSize {
        if (capital <= 0.0 || plan.riskPerShare <= 0.0) return PositionSize(0, 0.0, 0.0, "capital inconnu")
        var qty = floor(capital * config.riskPerTradePct / 100.0 / plan.riskPerShare).toInt()
        var limit: String? = null
        val byWeight = floor(capital * config.maxPositionPct / 100.0 / plan.entry).toInt()
        if (byWeight < qty) { qty = byWeight; limit = "plafond de ${config.maxPositionPct.toInt()} % du portefeuille" }
        if (cash != null) {
            val byCash = floor(cash / plan.entry).toInt()
            if (byCash < qty) { qty = byCash; limit = "liquidités disponibles" }
        }
        qty = qty.coerceAtLeast(0)
        return PositionSize(qty, qty * plan.riskPerShare, qty * plan.entry, limit)
    }
}
