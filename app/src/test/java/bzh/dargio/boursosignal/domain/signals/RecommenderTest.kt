package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {

    private fun ex(action: ExitAction, reasons: List<String> = listOf("raison")) =
        ExitAdvice("X", action, 5.0, 100.0, reasons)

    @Test
    fun not_held_uses_entry_stance() {
        assertEquals("Achat", Recommender.of(Stance.BUY, null).label)
        assertEquals("Éviter", Recommender.of(Stance.AVOID, null).label)
        assertTrue(!Recommender.of(Stance.BUY, null).held)
    }

    @Test
    fun held_good_signals_trim_suggests_securing_and_rebuy() {
        val r = Recommender.of(Stance.BUY, ex(ExitAction.TRIM))
        assertTrue(r.held)
        assertTrue(r.label.startsWith("Alléger"))
        assertTrue(r.note?.contains("rachat") == true)
        assertEquals(RecoTone.CAUTION, r.tone)
    }

    @Test
    fun held_good_signals_sell_is_shown_as_trim_not_sell() {
        val r = Recommender.of(Stance.WATCH, ex(ExitAction.SELL))
        assertTrue(r.label.contains("Alléger"))
        assertEquals(RecoTone.CAUTION, r.tone)
    }

    @Test
    fun held_bad_signals_sell_is_sell() {
        val r = Recommender.of(Stance.AVOID, ex(ExitAction.SELL, listOf("Stop-loss atteint")))
        assertEquals("Vendre", r.label)
        assertEquals(RecoTone.NEGATIVE, r.tone)
    }

    @Test
    fun held_hold_is_conserver() {
        assertEquals("Conserver", Recommender.of(Stance.BUY, ex(ExitAction.HOLD)).label)
    }
}
