package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MarketClockTest {

    private fun dt(s: String) = LocalDateTime.parse(s)

    @Test
    fun is_open_only_during_weekday_session() {
        assertTrue(MarketClock.isOpen(dt("2026-11-05T10:00")))   // jeudi 10h
        assertFalse(MarketClock.isOpen(dt("2026-11-05T08:00")))  // avant 9h
        assertFalse(MarketClock.isOpen(dt("2026-11-05T18:00")))  // après 17h30
        assertFalse(MarketClock.isOpen(dt("2026-11-07T10:00")))  // samedi
        assertTrue(MarketClock.isOpen(dt("2026-11-05T17:30")))   // borne incluse
    }

    @Test
    fun next_open_skips_evenings_and_weekends() {
        assertEquals(dt("2026-11-05T09:00"), MarketClock.nextOpen(dt("2026-11-05T08:00")))
        assertEquals(dt("2026-11-06T09:00"), MarketClock.nextOpen(dt("2026-11-05T19:00")))
        assertEquals(dt("2026-11-09T09:00"), MarketClock.nextOpen(dt("2026-11-06T19:00"))) // vendredi soir -> lundi
        assertEquals(dt("2026-11-09T09:00"), MarketClock.nextOpen(dt("2026-11-08T10:00"))) // dimanche -> lundi
    }

    @Test
    fun pending_uses_timestamp_when_available() {
        assertEquals(dt("2026-11-06T09:00"), MarketClock.pendingNextOpen(LocalDate.parse("2026-11-05"), dt("2026-11-05T20:00"), dt("2026-11-05T21:00")))
        assertNull(MarketClock.pendingNextOpen(LocalDate.parse("2026-11-05"), dt("2026-11-05T10:00"), dt("2026-11-05T11:00")))
        assertNull(MarketClock.pendingNextOpen(LocalDate.parse("2026-11-06"), dt("2026-11-06T20:00"), dt("2026-11-09T10:00")))
    }

    @Test
    fun pending_falls_back_to_date_for_real_transactions() {
        assertEquals(dt("2026-11-06T09:00"), MarketClock.pendingNextOpen(LocalDate.parse("2026-11-05"), null, dt("2026-11-05T20:00")))
        assertNull(MarketClock.pendingNextOpen(LocalDate.parse("2026-11-05"), null, dt("2026-11-05T10:00")))
        assertNull(MarketClock.pendingNextOpen(LocalDate.parse("2026-11-04"), null, dt("2026-11-05T20:00")))
    }
}
