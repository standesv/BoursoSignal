package bzh.dargio.boursosignal.domain.signals

import kotlin.math.max

/**
 * Profil de tarification du courtage, utilisé pour **simuler** les frais
 * Boursorama en mode test (§F8). Logique pure et testable.
 *
 * Modèle simplifié et paramétrable : frais = max(minFee, montant × percent/100).
 * La grille réelle de Boursorama comporte des paliers par montant d'ordre ; ce
 * modèle en est une approximation éditable dans les Réglages.
 */
data class FeeProfile(
    val key: String,
    val label: String,
    val percent: Double,
    val minFee: Double,
) {
    /** Frais pour un ordre d'un montant donné (0 si montant nul/négatif). */
    fun feeFor(amount: Double): Double =
        if (amount <= 0.0) 0.0 else max(minFee, amount * percent / 100.0)

    companion object {
        /** Profil neutre (aucun frais) — défaut pour la logique pure et les tests. */
        val NONE = FeeProfile("none", "Sans frais", 0.0, 0.0)
    }
}

/**
 * Valeurs par défaut des profils Boursorama/BoursoBank (tarifs 2026, offre
 * BoursoBank). Éditables par l'utilisateur dans les Réglages.
 */
object Fees {
    val DECOUVERTE = FeeProfile("decouverte", "Découverte", percent = 0.60, minFee = 1.99)
    val CLASSIC = FeeProfile("classic", "Classic", percent = 0.48, minFee = 5.50)
    val TRADER = FeeProfile("trader", "Trader", percent = 0.22, minFee = 16.65)

    /** Les trois profils dans l'ordre d'affichage. */
    val DEFAULTS = listOf(DECOUVERTE, CLASSIC, TRADER)

    fun byKey(key: String): FeeProfile = DEFAULTS.firstOrNull { it.key == key } ?: DECOUVERTE
}
