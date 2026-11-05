package bzh.dargio.boursosignal.domain.signals

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Horloge du marché Euronext Paris : cotation en continu de 9h00 à 17h30, du lundi
 * au vendredi. Les jours fériés ne sont pas gérés (approximation suffisante pour un
 * usage personnel d'aide à la décision). Logique pure et testable.
 */
object MarketClock {

    val OPEN: LocalTime = LocalTime.of(9, 0)
    val CLOSE: LocalTime = LocalTime.of(17, 30)

    private fun isWeekday(d: DayOfWeek) = d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY

    /** Vrai si le marché est ouvert (en séance) à l'instant donné. */
    fun isOpen(now: LocalDateTime): Boolean {
        if (!isWeekday(now.dayOfWeek)) return false
        val t = now.toLocalTime()
        return !t.isBefore(OPEN) && !t.isAfter(CLOSE)
    }

    /** Prochain fixing d'ouverture (9h00 d'un jour de Bourse) à partir de `now`. */
    fun nextOpen(now: LocalDateTime): LocalDateTime {
        var day = now.toLocalDate()
        val beforeOpenToday = now.toLocalTime().isBefore(OPEN)
        if (!(isWeekday(day.dayOfWeek) && beforeOpenToday)) day = day.plusDays(1)
        while (!isWeekday(day.dayOfWeek)) day = day.plusDays(1)
        return day.atTime(OPEN)
    }

    /**
     * Si l'opération a été passée **hors séance** et n'a pas encore été prise en
     * compte par une ouverture, renvoie l'heure du prochain fixing d'ouverture ;
     * sinon `null` (opération déjà effective/en séance).
     *
     * `recordedAt` (horodatage précis, mode test) est utilisé si disponible ; sinon
     * on se base sur la date (mode réel), considérée « en attente » si c'est la date
     * du jour et que le marché est fermé à cet instant.
     */
    fun pendingNextOpen(recordedDate: LocalDate, recordedAt: LocalDateTime?, now: LocalDateTime): LocalDateTime? {
        if (recordedAt != null) {
            if (isOpen(recordedAt)) return null            // passée en séance → déjà effective
            val next = nextOpen(recordedAt)
            return if (now.isBefore(next)) next else null  // ouverture déjà passée → effective
        }
        if (recordedDate == now.toLocalDate() && !isOpen(now)) return nextOpen(now)
        return null
    }
}
