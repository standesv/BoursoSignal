package bzh.dargio.boursosignal.domain.signals

/** Orientation de l'actualité récente d'une valeur (indicatif). */
enum class NewsSentiment { POSITIVE, NEGATIVE, NEUTRAL }

/**
 * Analyse d'actualité **indicative** par mots-clés (FR + EN). Ce n'est pas une
 * compréhension fine du contenu : c'est un garde-fou simple pour tempérer un
 * signal technique quand les titres récents penchent clairement d'un côté.
 * Logique pure et testable ; une vraie analyse de sentiment viendrait d'une
 * source dédiée (voir cahier des charges).
 */
object NewsAnalyzer {

    private val positive = listOf(
        "record", "hausse", "bond", "contrat", "rachat", "acquisition", "bénéfice", "profit",
        "relèvement", "surperform", "upgrade", "croissance", "commande", "partenariat",
        "dividende", "augmente", "gagne", "succès",
    )
    private val negative = listOf(
        "chute", "plonge", "avertissement", "profit warning", "baisse", "recul", "perte",
        "downgrade", "enquête", "fraude", "rappel", "grève", "sanction", "litige", "dette",
        "dégrad", "warning", "alerte", "suspension", "licenci",
    )

    /** Renvoie l'orientation et un score normalisé dans [-1, 1]. */
    fun sentimentOf(headlines: List<String>): Pair<NewsSentiment, Double> {
        if (headlines.isEmpty()) return NewsSentiment.NEUTRAL to 0.0
        var raw = 0
        for (h in headlines) {
            val t = h.lowercase()
            raw += positive.count { t.contains(it) }
            raw -= negative.count { t.contains(it) }
        }
        val score = (raw.toDouble() / headlines.size).coerceIn(-1.0, 1.0)
        val sentiment = when {
            raw > 0 -> NewsSentiment.POSITIVE
            raw < 0 -> NewsSentiment.NEGATIVE
            else -> NewsSentiment.NEUTRAL
        }
        return sentiment to score
    }
}

/**
 * Génère la phrase « pourquoi acheter » affichée sur un signal vert, en combinant
 * la raison technique principale et l'orientation de l'actualité. Logique pure.
 */
object BuyRationale {

    fun whyBuy(name: String, opp: Opportunity, news: NewsSentiment): String {
        val tech = opp.reasons.firstOrNull()?.replaceFirstChar { it.lowercase() }
            ?: "configuration technique favorable"
        val newsPart = when (news) {
            NewsSentiment.POSITIVE -> " ; l'actualité récente est plutôt positive."
            NewsSentiment.NEGATIVE -> " ; mais l'actualité récente est négative — prudence."
            NewsSentiment.NEUTRAL -> "."
        }
        return "$name : $tech$newsPart"
    }
}
