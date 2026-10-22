package bzh.dargio.boursosignal.data.remote

import bzh.dargio.boursosignal.domain.signals.Candle

/** Cotation courante résumée d'une valeur. */
data class Quote(
    val ticker: String,
    val price: Double,
    val changePct: Double,
    val date: String,
)

/**
 * Abstraction de la source de cours (§6 du cahier des charges). L'application ne
 * dépend que de cette interface : on peut changer de fournisseur (Twelve Data,
 * Yahoo, …) ou passer en mode démo sans toucher au reste du code.
 */
interface MarketDataProvider {

    /** Nom affichable du fournisseur (pour les réglages). */
    val displayName: String

    /**
     * Historique de bougies journalières, de la plus ancienne à la plus récente.
     * @param outputSize nombre de bougies souhaité (défaut : assez pour les indicateurs).
     */
    suspend fun dailyCandles(ticker: String, outputSize: Int = 250): Result<List<Candle>>

    /** Dernière cotation connue (cours + variation du jour). */
    suspend fun latestQuote(ticker: String): Result<Quote>
}
