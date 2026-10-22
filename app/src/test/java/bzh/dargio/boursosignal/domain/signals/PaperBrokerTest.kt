package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperBrokerTest {

    private val eps = 1e-9

    @Test
    fun fresh_account_equals_capital() {
        val acc = PaperAccount.fresh(10_000.0)
        assertEquals(10_000.0, PaperBroker.equity(acc, emptyMap()), eps)
        assertEquals(0.0, PaperBroker.totalReturnPct(acc, emptyMap()), eps)
    }

    @Test
    fun buy_budget_rounds_to_whole_shares() {
        val ex = PaperBroker.buyBudget(PaperAccount.fresh(10_000.0), "SOP", 181.20, 2_000.0)
        assertEquals(11.0, ex.executedQty, eps)
        assertEquals(11.0, ex.account.positions.getValue("SOP").quantity, eps)
    }

    @Test
    fun buy_shares_averages_cost() {
        val a = PaperBroker.buyShares(PaperAccount.fresh(10_000.0), "AIR", 100.0, 20).account
        val b = PaperBroker.buyShares(a, "AIR", 120.0, 20).account
        assertEquals(40.0, b.positions.getValue("AIR").quantity, eps)
        assertEquals(110.0, b.positions.getValue("AIR").avgPrice, eps)
    }

    @Test
    fun buy_is_capped_by_cash() {
        val ex = PaperBroker.buyShares(PaperAccount.fresh(1_000.0), "X", 100.0, 50)
        assertEquals(10.0, ex.executedQty, eps)
    }

    @Test
    fun partial_sell_keeps_remaining() {
        val a = PaperBroker.buyShares(PaperAccount.fresh(10_000.0), "AIR", 100.0, 20).account
        val s = PaperBroker.sellShares(a, "AIR", 110.0, 5)
        assertEquals(5.0, s.executedQty, eps)
        assertEquals(15.0, s.account.positions.getValue("AIR").quantity, eps)
    }

    @Test
    fun sell_all_realizes_gain() {
        val a = PaperBroker.buyBudget(PaperAccount.fresh(10_000.0), "AIR", 100.0, 2_000.0).account
        val s = PaperBroker.sellAll(a, "AIR", 110.0)
        assertEquals(10_200.0, s.account.cash, eps)
        assertTrue(s.account.positions.isEmpty())
        assertEquals(2.0, PaperBroker.totalReturnPct(s.account, emptyMap()), eps)
    }

    @Test
    fun unrealized_return_tracks_prices() {
        val a = PaperBroker.buyBudget(PaperAccount.fresh(10_000.0), "AIR", 100.0, 2_000.0).account
        assertEquals(4.0, PaperBroker.totalReturnPct(a, mapOf("AIR" to 120.0)), eps)
    }
}
