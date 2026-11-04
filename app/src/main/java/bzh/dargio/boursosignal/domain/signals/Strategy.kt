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
 * croisement MACD, bandes de Bollinger, stop de volatilité ATR).
 *
 * Les poids servent au scoring : un signal n'est émis que si la somme des poids
 * des conditions satisfaites atteint le seuil. Le seuil de vente est au niveau de
 * l'achat : une seule condition « molle » ne suffit pas. Les règles de risque
 * (stop-loss / take-profit / stop ATR) restent toujours déclenchantes.
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
    val stopLossPct: Double? = -0.08,
    val takeProfitPct: Double? = 0.15,
    // Seuils de déclenchement (vente aussi exigeante que l'achat : 2 conditions).
    val buyThreshold: Double = 1.5,
    val sellThreshold: Double = 1.5,

    // --- Filtres et signaux complémentaires (activés quand l'historique suffit) ---
    // Filtre de régime : pas d'achat sous la moyenne mobile longue (tendance de fond).
    val longTrendPeriod: Int = 200,
    val useTrendFilter: Boolean = true,
    // Force de tendance (ADX) : confirme un signal directionnel quand la tendance est marquée.
    val adxPeriod: Int = 14,
    val adxThreshold: Double = 20.0,
    val weightAdxConfirm: Double = 0.5,
    // Croisement MACD (ligne MACD vs ligne signal).
    val weightMacdCrossUp: Double = 0.5,
    val weightMacdCrossDown: Double = 0.5,
    // Bandes de Bollinger : rebond sous la bande basse / excès au-dessus de la bande haute.
    val bollingerPeriod: Int = 20,
    val bollingerMult: Double = 2.0,
    val weightBollingerReboundUp: Double = 0.5,
    val weightBollingerOverbought: Double = 0.5,
    // Stop de volatilité : vendre si la perte dépasse `atrStopMultiple` × ATR.
    val atrPeriod: Int = 14,
    val atrStopMultiple: Double = 2.5,
    val useAtrStop: Boolean = true,
) {
    /** Nombre minimal de bougies pour que les indicateurs de base soient définis. */
    val minHistory: Int
        get() = maxOf(emaSlowPeriod, rsiPeriod + 1, volumeAvgPeriod) + 1
}
