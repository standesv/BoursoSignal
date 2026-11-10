package bzh.dargio.boursosignal.domain.signals

/** Sens d'un signal produit par le moteur. */
enum class SignalType { BUY, SELL, NONE }

/**
 * Résultat de l'évaluation d'une stratégie sur une valeur à une date donnée.
 */
data class SignalResult(
    val type: SignalType,
    val score: Double,
    val reasons: List<String>,
    val date: String,
    val price: Double,
)

/**
 * Paramètres d'une stratégie. Valeurs par défaut = configuration du §5, enrichie
 * de filtres et signaux complémentaires (tendance de fond, force de tendance ADX,
 * croisement MACD, bandes de Bollinger) et d'une gestion du risque (stop-loss,
 * take-profit, stop de volatilité ATR et stop suiveur).
 */
data class StrategyConfig(
    val emaFastPeriod: Int = 20,
    val emaSlowPeriod: Int = 50,
    val rsiPeriod: Int = 14,
    val rsiOversold: Double = 30.0,
    val rsiOverbought: Double = 70.0,
    val volumeAvgPeriod: Int = 20,
    val volumeFactor: Double = 1.2,
    // Poids des conditions d'achat.
    val weightEmaCrossUp: Double = 1.0,
    val weightRsiExitOversold: Double = 1.0,
    val weightVolumeConfirm: Double = 0.5,
    // Poids des conditions de vente.
    val weightEmaCrossDown: Double = 1.0,
    val weightRsiOverbought: Double = 1.0,
    // Gestion du risque (optionnelle). null = désactivé.
    val stopLossPct: Double? = -0.12,
    val takeProfitPct: Double? = null,
    // Seuils de déclenchement (vente aussi exigeante que l'achat : 2 conditions).
    val buyThreshold: Double = 1.5,
    val sellThreshold: Double = 1.5,

    // --- Filtres et signaux complémentaires (activés quand l'historique suffit) ---
    val longTrendPeriod: Int = 200,
    val useTrendFilter: Boolean = true,
    val adxPeriod: Int = 14,
    val adxThreshold: Double = 20.0,
    val weightAdxConfirm: Double = 0.5,
    val weightMacdCrossUp: Double = 0.5,
    val weightMacdCrossDown: Double = 0.5,
    val bollingerPeriod: Int = 20,
    val bollingerMult: Double = 2.0,
    val weightBollingerReboundUp: Double = 0.5,
    val weightBollingerOverbought: Double = 0.5,
    // Stop de volatilité : vendre si la perte dépasse `atrStopMultiple` × ATR.
    val atrPeriod: Int = 14,
    val atrStopMultiple: Double = 2.5,
    val useAtrStop: Boolean = true,
    // Stop suiveur : vendre si le cours reflue de `trailingAtrMultiple` × ATR sous
    // son plus haut des `trailingPeriod` dernières séances (sécurise les gains).
    val useTrailingStop: Boolean = true,
    val trailingAtrMultiple: Double = 3.0,
    val trailingPeriod: Int = 60,
    // Anti-répétition de l'allègement : une fois « Alléger » conseillé, on reste sur
    // « Conserver » tant que le cours n'a pas repris au moins trimReArmPct au-dessus
    // du cours du dernier allègement (nouvelle jambe de hausse).
    val trimReArmPct: Double = 0.10,
) {
    /** Nombre minimal de bougies pour que les indicateurs de base soient définis. */
    val minHistory: Int
        get() = maxOf(emaSlowPeriod, rsiPeriod + 1, volumeAvgPeriod) + 1
}
