package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeesTest {

    private val eps = 1e-6

    @Test
    fun none_profile_has_no_fee() {
        assertEquals(0.0, FeeProfile.NONE.feeFor(1_000.0), eps)
    }

    @Test
    fun percent_above_minimum() {
        assertEquals(6.0, Fees.DECOUVERTE.feeFor(1_000.0), eps)   // 0,60 %
        assertEquals(22.0, Fees.TRADER.feeFor(10_000.0), eps)      // 0,22 %
    }

    @Test
    fun minimum_applies_on_small_orders() {
        assertEquals(1.99, Fees.DECOUVERTE.feeFor(100.0), eps)
        assertEquals(5.50, Fees.CLASSIC.feeFor(100.0), eps)
    }

    @Test
    fun zero_amount_is_free() {
        assertEquals(0.0, Fees.DECOUVERTE.feeFor(0.0), eps)
    }

    @Test
    fun unknown_key_falls_back_to_decouverte() {
        assertEquals("decouverte", Fees.byKey("inconnu").key)
    }

    @Test
    fun buy_deducts_fee_from_cash_pru_excludes_fee() {
        val ex = PaperBroker.buyShares(PaperAccount.fresh(10_000.0), "AIR", 100.0, 10, Fees.DECOUVERTE)
        assertEquals(10.0, ex.executedQty, eps)
        assertEquals(6.0, ex.fee, eps)
        assertEquals(8_994.0, ex.account.cash, eps)
        assertEquals(100.0, ex.account.positions.getValue("AIR").avgPrice, eps)
    }

    @Test
    fun sell_deducts_fee_from_proceeds() {
        val a = PaperBroker.buyShares(PaperAccount.fresh(10_000.0), "AIR", 100.0, 10, Fees.DECOUVERTE).account
        val s = PaperBroker.sellShares(a, "AIR", 110.0, 10, Fees.DECOUVERTE)
        assertEquals(6.60, s.fee, eps)
        assertEquals(10_087.40, s.account.cash, eps)
        assertTrue(s.account.positions.isEmpty())
    }

    @Test
    fun fee_reduces_quantity_when_cash_is_tight() {
        val ex = PaperBroker.buyShares(PaperAccount.fresh(1_000.0), "X", 100.0, 50, Fees.DECOUVERTE)
        assertEquals(9.0, ex.executedQty, eps)
        assertTrue(ex.account.cash >= 0.0)
    }
}
