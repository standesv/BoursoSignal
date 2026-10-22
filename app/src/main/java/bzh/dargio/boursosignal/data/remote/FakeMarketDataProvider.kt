package bzh.dargio.boursosignal.data.remote

import bzh.dargio.boursosignal.domain.signals.Candle
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

/**
 * Source de cours de DÉMONSTRATION : génère un historique synthétique déterministe
 * (marche aléatoire ensemencée par le ticker) pour faire tourner l'application sans
 * clé API ni réseau. À remplacer par [TwelveDataProvider] en usage réel.
 */
class FakeMarketDataProvider : MarketDataProvider {

    override val displayName: String = "Démo (hors-ligne)"

    override suspend fun dailyCandles(ticker: String, outputSize: Int): Result<List<Candle>> =
        runCatching {
            val rnd = Random(ticker.hashCode().toLong())
            var price = 50.0 + abs(ticker.hashCode() % 150)
            val start = LocalDate.now().minusDays(outputSize.toLong())
            (0 until outputSize).map { i ->
                val drift = (rnd.nextDouble() - 0.48) * 2.0           // léger biais haussier
                val open = price
                val close = (price + drift).coerceAtLeast(1.0)
                val high = maxOf(open, close) + rnd.nextDouble() * 0.8
                val low = minOf(open, close) - rnd.nextDouble() * 0.8
                val volume = 100_000 + rnd.nextInt(80_000).toDouble()
                price = close
                Candle(start.plusDays(i.toLong()).toString(), open, high, low, close, volume)
            }
        }

    override suspend fun latestQuote(ticker: String): Result<Quote> =
        dailyCandles(ticker, 2).map { candles ->
            val last = candles.last()
            val prev = candles[candles.size - 2].close
            Quote(ticker, last.close, (last.close - prev) / prev * 100.0, last.date)
        }
}
