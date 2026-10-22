package bzh.dargio.boursosignal.domain

import bzh.dargio.boursosignal.data.repo.PaperRepository

/**
 * Applique les signaux émis au portefeuille fictif (mode test automatique).
 * Appelé après chaque cycle de rafraîchissement quand le mode test est activé.
 */
class PaperTradingService(private val paper: PaperRepository) {

    suspend fun apply(emitted: List<EmittedSignal>) {
        if (!paper.isEnabled()) return
        for (s in emitted) {
            paper.applySignal(
                ticker = s.ticker,
                type = s.result.type.name,
                price = s.result.price,
                date = s.result.date,
                reason = s.result.reasons.joinToString(" · "),
            )
        }
    }
}
