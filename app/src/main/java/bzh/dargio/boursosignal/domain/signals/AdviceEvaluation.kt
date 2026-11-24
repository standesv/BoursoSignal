package bzh.dargio.boursosignal.domain.signals

/** Un conseil enregistré (Achat / Vendre / Alléger) et l'évolution mesurée du cours ensuite. */
data class AdviceRecord(
    val ticker: String,
    val kind: String,          // "BUY" | "SELL" | "TRIM"
    val date: String,          // séance de référence (ISO)
    val price: Double,         // cours au moment du conseil
    val regime: String? = null,
    val ret5: Double? = null,  // variation du cours (%) 5 séances plus tard
    val ret20: Double? = null, // variation du cours (%) 20 séances plus tard
)

/** Réussite d'un groupe de conseils. */
data class AdviceStats(
    val label: String,
    val count: Int,
    val evaluated: Int,          // conseils déjà mesurables à 5 séances
    val hitRatePct: Double?,     // part de conseils justes à 5 séances
    val avgMove5Pct: Double?,    // variation moyenne du cours à 5 séances
    val avgMove20Pct: Double?,   // variation moyenne du cours à 20 séances
)

/**
 * Mesure, avec les vrais cours, si les conseils de l'app étaient justes (§23-1).
 * Achat juste = le cours a monté ensuite ; Vendre / Alléger justes = le cours a
 * baissé ensuite (perte évitée). Logique pure et testable.
 */
object AdviceEvaluator {

    const val SHORT = 5
    const val LONG = 20

    /** Variation (%) entre le cours du conseil et la clôture `horizon` séances plus tard ; null si pas encore connue. */
    fun forwardReturn(candles: List<Candle>, date: String, price: Double, horizon: Int): Double? {
        if (price <= 0.0) return null
        val i = candles.indexOfLast { it.date <= date }
        if (i < 0) return null
        val target = i + horizon
        if (target > candles.lastIndex) return null
        return (candles[target].close - price) / price * 100.0
    }

    fun isSuccess(kind: String, movePct: Double): Boolean = if (kind == "BUY") movePct > 0.0 else movePct < 0.0

    fun kindLabel(kind: String): String = when (kind) {
        "BUY" -> "Achat"
        "SELL" -> "Vendre"
        "TRIM" -> "Alléger"
        else -> kind
    }

    /** Statistiques par type de conseil, dans l'ordre Achat, Vendre, Alléger. */
    fun statsByKind(records: List<AdviceRecord>): List<AdviceStats> =
        listOf("BUY", "SELL", "TRIM").mapNotNull { k ->
            records.filter { it.kind == k }.takeIf { it.isNotEmpty() }?.let { statsOf(kindLabel(k), it) }
        }

    /** Statistiques des conseils d'achat selon la météo du marché au moment du conseil. */
    fun buyStatsByRegime(records: List<AdviceRecord>): List<AdviceStats> =
        records.filter { it.kind == "BUY" }
            .groupBy { it.regime ?: "?" }
            .map { (regime, list) -> statsOf(regimeLabel(regime), list) }

    fun regimeLabel(regime: String): String = when (regime) {
        "RISK_ON" -> "Marché favorable"
        "CAUTION" -> "Marché fragile"
        "RISK_OFF" -> "Marché baissier"
        else -> "Météo inconnue"
    }

    private fun statsOf(label: String, list: List<AdviceRecord>): AdviceStats {
        val ev5 = list.mapNotNull { r -> r.ret5?.let { r.kind to it } }
        val hit = if (ev5.isEmpty()) null else ev5.count { (k, m) -> isSuccess(k, m) } * 100.0 / ev5.size
        val avg5 = list.mapNotNull { it.ret5 }.takeIf { it.isNotEmpty() }?.average()
        val avg20 = list.mapNotNull { it.ret20 }.takeIf { it.isNotEmpty() }?.average()
        return AdviceStats(label, list.size, ev5.size, hit, avg5, avg20)
    }
}

/** Alerte de prix définie par l'utilisateur (§23-5). */
object PriceAlertRule {
    /** Vrai quand le cours franchit le seuil : au-dessus (≥) ou en dessous (≤). */
    fun isTriggered(above: Boolean, level: Double, price: Double): Boolean =
        price > 0.0 && level > 0.0 && (if (above) price >= level else price <= level)
}
