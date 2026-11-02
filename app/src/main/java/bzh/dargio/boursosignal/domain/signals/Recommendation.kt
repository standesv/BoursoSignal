package bzh.dargio.boursosignal.domain.signals

/** Teinte d'affichage d'une recommandation. */
enum class RecoTone { POSITIVE, CAUTION, NEGATIVE, NEUTRAL }

/**
 * Recommandation **unifiée** affichée dans l'onglet Idées, cohérente avec le
 * portefeuille : pour une valeur détenue, elle reflète la gestion de la position
 * (conserver / alléger / vendre) plutôt qu'un « Achat » qui contredirait l'onglet
 * Portefeuille. Pour une valeur non détenue, c'est l'avis d'entrée habituel.
 *
 * @param held true si la valeur est déjà en portefeuille
 */
data class Recommendation(
    val label: String,
    val note: String?,
    val tone: RecoTone,
    val held: Boolean,
)

/**
 * Combine l'avis d'entrée (stance du scan) et, pour une valeur détenue, le conseil
 * de sortie ([ExitAdvice]) en une seule recommandation cohérente. Logique pure et
 * testable.
 *
 * Nuance clé (Spec 1) : quand une valeur détenue doit être allégée/vendue **mais
 * que les signaux restent bons**, on propose d'« alléger pour sécuriser les gains »
 * en la gardant comme candidate au rachat sur repli — au lieu d'un « Vendre » sec
 * qui contredirait l'avis d'achat.
 */
object Recommender {

    private const val REBUY = "Setup toujours porteur : candidate au rachat sur repli"

    /** Recommandation pour une valeur : [exit] = null si non détenue. */
    fun of(stance: Stance, exit: ExitAdvice?): Recommendation =
        if (exit == null) forIdea(stance) else forHeld(exit, stance)

    private fun forIdea(stance: Stance): Recommendation = when (stance) {
        Stance.BUY -> Recommendation("Achat", null, RecoTone.POSITIVE, false)
        Stance.WATCH -> Recommendation("Surveiller", null, RecoTone.CAUTION, false)
        Stance.AVOID -> Recommendation("Éviter", null, RecoTone.NEGATIVE, false)
        Stance.NEUTRAL -> Recommendation("Neutre", null, RecoTone.NEUTRAL, false)
    }

    private fun forHeld(exit: ExitAdvice, stance: Stance): Recommendation {
        val signalsGood = stance == Stance.BUY || stance == Stance.WATCH
        return when (exit.action) {
            ExitAction.HOLD -> Recommendation(
                "Conserver",
                if (signalsGood) "Tendance toujours favorable" else null,
                RecoTone.POSITIVE, true,
            )
            ExitAction.TRIM -> if (signalsGood)
                Recommendation("Alléger (sécuriser les gains)", REBUY, RecoTone.CAUTION, true)
            else
                Recommendation("Alléger", "Réduire l'exposition", RecoTone.CAUTION, true)
            ExitAction.SELL -> if (signalsGood)
                Recommendation("Alléger fortement (sécuriser les gains)", REBUY, RecoTone.CAUTION, true)
            else
                Recommendation("Vendre", exit.reasons.firstOrNull() ?: "Conditions de sortie réunies", RecoTone.NEGATIVE, true)
        }
    }
}
