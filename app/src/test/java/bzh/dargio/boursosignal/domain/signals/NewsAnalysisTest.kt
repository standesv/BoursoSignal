package bzh.dargio.boursosignal.domain.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsAnalysisTest {

    @Test
    fun sentiment_detects_direction() {
        assertEquals(NewsSentiment.POSITIVE, NewsAnalyzer.sentimentOf(listOf("Airbus signe un contrat record")).first)
        assertEquals(NewsSentiment.NEGATIVE, NewsAnalyzer.sentimentOf(listOf("Profit warning : le titre plonge")).first)
        assertEquals(NewsSentiment.NEUTRAL, NewsAnalyzer.sentimentOf(listOf("Résultats conformes aux attentes")).first)
        assertEquals(NewsSentiment.NEUTRAL, NewsAnalyzer.sentimentOf(emptyList()).first)
    }

    @Test
    fun rationale_mentions_name_and_news() {
        val opp = Opportunity("AIR.PA", Stance.BUY, 0.7, listOf("Tendance haussière (EMA20 > EMA50)"), 150.0, "2026-01-01")
        val neg = BuyRationale.whyBuy("Airbus", opp, NewsSentiment.NEGATIVE)
        assertTrue(neg.contains("Airbus"))
        assertTrue(neg.contains("prudence"))
        assertTrue(BuyRationale.whyBuy("Airbus", opp, NewsSentiment.POSITIVE).contains("positive"))
    }
}
