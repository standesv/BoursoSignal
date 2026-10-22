package bzh.dargio.boursosignal.domain.signals

/**
 * Une bougie (chandelier) journalière : données OHLC + volume pour une date.
 *
 * Type purement Kotlin, sans dépendance Android : il peut être utilisé par la
 * couche domaine de l'application comme par les tests unitaires exécutés sur la JVM.
 *
 * @param date date de la séance au format ISO (ex. "2026-10-21")
 */
data class Candle(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

/** Raccourci : extrait la série des cours de clôture d'une liste de bougies. */
fun List<Candle>.closes(): List<Double> = map { it.close }

/** Raccourci : extrait la série des volumes d'une liste de bougies. */
fun List<Candle>.volumes(): List<Double> = map { it.volume }
