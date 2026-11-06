package bzh.dargio.boursosignal.domain.signals

import java.time.LocalDateTime

/**
 * Fenêtre d'autorisation des notifications : jours de la semaine (masque de bits,
 * bit 0 = lundi … bit 6 = dimanche) et plage horaire [startHour, endHour[.
 * Logique pure et testable — permet de ne notifier que pendant les créneaux
 * choisis (ex. jours de Bourse, 9h–20h) et d'éviter les alertes la nuit.
 */
object NotifyWindow {

    /** Du lundi au vendredi (bits 0 à 4). */
    const val DEFAULT_DAYS: Int = 0b0011111
    const val DEFAULT_START: Int = 9
    const val DEFAULT_END: Int = 20

    /** Bit correspondant à un jour (1 = lundi … 7 = dimanche). */
    fun dayBit(dayOfWeek: Int): Int = 1 shl ((dayOfWeek - 1).coerceIn(0, 6))

    /** Vrai si l'instant `now` tombe un jour autorisé et dans la plage horaire. */
    fun isWithin(daysMask: Int, startHour: Int, endHour: Int, now: LocalDateTime): Boolean {
        if (daysMask and dayBit(now.dayOfWeek.value) == 0) return false
        return if (startHour <= endHour) now.hour in startHour until endHour
        else now.hour >= startHour || now.hour < endHour   // plage à cheval sur minuit
    }
}
