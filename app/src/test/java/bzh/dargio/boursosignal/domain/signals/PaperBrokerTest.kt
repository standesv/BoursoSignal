package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperBrokerTest {

    private val eps = 1e-9

    @Test
    fun fresh_account_equals_capital() {
        val acc = PaperAccount.fresh(10_000.0)
        assertEquals(10_000.0, acc.cash, eps)
        assertEquals(10_000.0, PaperBroker.equity(acc, emptyMap()), eps)
        assertEquals(0.0, PaperBroker.totalReturnPct(acc, emptyMap()), eps)
    }

    @Test
    fun buy_spends_budget_and_keeps_equity() {
        val acc = PaperAccount.fresh(10_000.0)
        val ex = PaperBroker.buy(acc, "AIR", 100.0, 2_000.0)
        assertEquals(20.0, ex.executedQty, eps)
        assertEquals(8_000.0, ex.account.cash, eps)
        assertEquals(10_000.0, PaperBroker.equity(ex.account, mapOf("AIR" to 100.0)), eps)
    }

    @Test
    fun no_double_position_on_same_ticker() {
        val acc = PaperBroker.buy(PaperAccount.fresh(10_000.0), "AIR", 100.0, 2_000.0).account
        val again = PaperBroker.buy(acc, "AIR", 100.0, 2_000.0)
        assertFalse(again.executed)
        assertEquals(1, again.account.positions.size)
    }

    @Test
    fun buy_is_capped_by_available_cash() {
        val acc = PaperBroker.buy(PaperAccount.fresh(10_000.0), "AIR", 100.0, 2_000.0).account
        val ex = PaperBroker.buy(acc, "BNP", 50.0, 20_000.0)
        assertEquals(0.0, ex.account.cash, eps)
    }

    @Test
    fun sell_realizes_gain() {
        val acc = PaperBroker.buy(PaperAccount.fresh(10_000.0), "AIR", 100.0, 2_000.0).account
        val sell = PaperBroker.sell(acc, "AIR", 110.0)
        assertEquals(10_200.0, sell.account.cash, eps)
        assertTrue(sell.account.positions.isEmpty())
        assertEquals(2.0, PaperBroker.totalReturnPct(sell.account, emptyMap()), eps)
    }

    @Test
    fun unrealized_return_tracks_current_prices() {
        val acc = PaperBroker.buy(PaperAccount.fresh(10_000.0), "AIR", 100.0, 2_000.0).account
        assertEquals(4.0, PaperBroker.totalReturnPct(acc, mapOf("AIR" to 120.0)), eps)
    }
}
