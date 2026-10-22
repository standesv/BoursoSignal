package bzh.dargio.boursosignal.domain.signals

/** Sens d'un signal produit par le moteur. */
enum class SignalType { BUY, SELL, NONE }

/**
 * Résultat de l'évaluation d'une stratégie sur une valeur à une date donnée.
 *
 * @param type    sens du signal (achat, vente ou neutre)
 * @param score   score pondéré total des conditions satisfaites
 * @param reasons libellés des règles ayant contribué (pour une alerte argumentée)
 * @param date    date de la bougie évaluée
 * @param price   cours de clôture déclencheur
 */
data class SignalResult(
    val type: SignalType,
    val score: Double,
    val reasons: List<String>,
    val date: String,
    val price: Double,
)

/**
 * Paramètres d'une stratégie. Tout est réglable par l'utilisateur (§F8 du cahier
 * des charges) ; ces valeurs par défaut correspondent à la configuration décrite
 * au §5.
 *
 * Les poids servent au scoring : un signal n'est émis que si la somme des poids
 * des conditions satisfaites atteint le seuil correspondant.
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
    // Seuils de déclenchement.
    val buyThreshold: Double = 1.5,
    val sellThreshold: Double = 1.0,
) {
    /** Nombre minimal de bougies pour que tous les indicateurs soient définis. */
    val minHistory: Int
        get() = maxOf(emaSlowPeriod, rsiPeriod + 1, volumeAvgPeriod) + 1
}
