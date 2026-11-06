package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class NotifyWindowTest {

    private fun dt(s: String) = LocalDateTime.parse(s)
    private val weekdays = NotifyWindow.DEFAULT_DAYS

    @Test
    fun allows_weekday_within_range() {
        assertTrue(NotifyWindow.isWithin(weekdays, 9, 20, dt("2026-11-05T10:00")))
        assertTrue(NotifyWindow.isWithin(weekdays, 9, 20, dt("2026-11-05T09:00")))  // borne basse incluse
    }

    @Test
    fun blocks_night_and_out_of_range() {
        assertFalse(NotifyWindow.isWithin(weekdays, 9, 20, dt("2026-11-05T03:00")))
        assertFalse(NotifyWindow.isWithin(weekdays, 9, 20, dt("2026-11-05T22:00")))
        assertFalse(NotifyWindow.isWithin(weekdays, 9, 20, dt("2026-11-05T20:00")))  // borne haute exclue
    }

    @Test
    fun respects_day_selection() {
        assertFalse(NotifyWindow.isWithin(weekdays, 9, 20, dt("2026-11-07T10:00")))        // samedi exclu
        assertTrue(NotifyWindow.isWithin(0b1111111, 9, 20, dt("2026-11-07T10:00")))        // tous les jours
    }

    @Test
    fun supports_overnight_range() {
        assertTrue(NotifyWindow.isWithin(0b1111111, 22, 6, dt("2026-11-05T23:00")))
        assertTrue(NotifyWindow.isWithin(0b1111111, 22, 6, dt("2026-11-05T05:00")))
        assertFalse(NotifyWindow.isWithin(0b1111111, 22, 6, dt("2026-11-05T12:00")))
    }
}
