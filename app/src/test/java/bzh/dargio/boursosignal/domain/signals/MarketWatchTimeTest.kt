package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MarketWatchTimeTest {

    // 2026-11-20 est un vendredi, 2026-11-21 un samedi.
    private fun at(day: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, day, h, m)

    @Test
    fun watches_during_session_and_just_after_close() {
        assertTrue(MarketClock.isWatchTime(at(20, 9, 0)))
        assertTrue(MarketClock.isWatchTime(at(20, 12, 30)))
        assertTrue(MarketClock.isWatchTime(at(20, 17, 40)))   // capte le fixing de clôture
        assertTrue(MarketClock.isWatchTime(at(20, 17, 45)))
    }

    @Test
    fun idle_outside_session() {
        assertFalse(MarketClock.isWatchTime(at(20, 8, 59)))
        assertFalse(MarketClock.isWatchTime(at(20, 17, 46)))
        assertFalse(MarketClock.isWatchTime(at(20, 22, 0)))
        assertFalse(MarketClock.isWatchTime(at(21, 11, 0)))   // samedi
    }
}
