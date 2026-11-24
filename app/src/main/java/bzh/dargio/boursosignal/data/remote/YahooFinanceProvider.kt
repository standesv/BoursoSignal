package bzh.dargio.boursosignal.data.remote

import bzh.dargio.boursosignal.domain.signals.Candle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant
import java.time.ZoneId

/**
 * Source de cours GRATUITE basée sur l'API chart de Yahoo Finance (§6).
 *
 * Avantage : couvre Euronext Paris via le suffixe `.PA` (ex. "AIR.PA") sans clé
 * API, contrairement à l'offre gratuite de Twelve Data qui exclut les actions
 * européennes. Endpoint non officiel (pas de SLA) : adapté à un usage personnel.
 */
class YahooFinanceProvider : MarketDataProvider {

    override val displayName: String = "Yahoo Finance (gratuit)"

    private val zone = ZoneId.of("Europe/Paris")
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (compatible; BoursoSignal/1.0)")
                    .build()
            )
        }
        .build()

    @Suppress("OPT_IN_USAGE")
    private val service: YahooService = Retrofit.Builder()
        .baseUrl("https://query1.finance.yahoo.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(YahooService::class.java)

    override suspend fun dailyCandles(ticker: String, outputSize: Int): Result<List<Candle>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = service.chart(symbol = ticker, interval = "1d", range = rangeFor(outputSize))
                val result = resp.chart?.result?.firstOrNull()
                    ?: throw ProviderException("Aucune donnée pour $ticker")
                val ts = result.timestamp ?: emptyList()
                val q = result.indicators?.quote?.firstOrNull()
                    ?: throw ProviderException("Données de cours absentes")
                val candles = ts.indices.mapNotNull { i ->
                    val close = q.close?.getOrNull(i) ?: return@mapNotNull null
                    Candle(
                        date = Instant.ofEpochSecond(ts[i]).atZone(zone).toLocalDate().toString(),
                        open = q.open?.getOrNull(i) ?: close,
                        high = q.high?.getOrNull(i) ?: close,
                        low = q.low?.getOrNull(i) ?: close,
                        close = close,
                        volume = q.volume?.getOrNull(i) ?: 0.0,
                    )
                }.toMutableList()

                // Yahoo publie parfois la dernière séance SANS cours de clôture (null) : elle
                // disparaissait alors et la valeur semblait figée à la séance précédente.
                // On la complète avec le dernier cours connu (regularMarketPrice).
                val livePrice = result.meta?.regularMarketPrice
                val liveTime = result.meta?.regularMarketTime
                if (livePrice != null && livePrice > 0.0 && liveTime != null) {
                    val liveDate = Instant.ofEpochSecond(liveTime).atZone(zone).toLocalDate().toString()
                    val lastDate = candles.lastOrNull()?.date
                    if (lastDate == null || liveDate > lastDate) {
                        val i = ts.indexOfLast { Instant.ofEpochSecond(it).atZone(zone).toLocalDate().toString() == liveDate }
                        val open = (if (i >= 0) q.open?.getOrNull(i) else null) ?: livePrice
                        val high = (if (i >= 0) q.high?.getOrNull(i) else null) ?: livePrice
                        val low = (if (i >= 0) q.low?.getOrNull(i) else null) ?: livePrice
                        candles += Candle(
                            date = liveDate,
                            open = open,
                            high = maxOf(high, open, livePrice),
                            low = minOf(low, open, livePrice),
                            close = livePrice,
                            volume = (if (i >= 0) q.volume?.getOrNull(i) else null) ?: 0.0,
                        )
                    }
                }
                candles
            }
        }

    override suspend fun latestQuote(ticker: String): Result<Quote> =
        dailyCandles(ticker, outputSize = 5).map { candles ->
            val last = candles.lastOrNull() ?: throw ProviderException("Aucune donnée")
            val prev = candles.getOrNull(candles.size - 2)?.close ?: last.close
            val change = if (prev != 0.0) (last.close - prev) / prev * 100.0 else 0.0
            Quote(ticker, last.close, change, last.date)
        }

    private fun rangeFor(outputSize: Int): String = when {
        outputSize <= 5 -> "5d"
        outputSize <= 130 -> "6mo"
        outputSize <= 260 -> "1y"
        else -> "2y"
    }
}

internal interface YahooService {
    @GET("v8/finance/chart/{symbol}")
    suspend fun chart(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("range") range: String,
    ): YahooChartResponse
}

@Serializable
internal data class YahooChartResponse(val chart: YahooChart? = null)

@Serializable
internal data class YahooChart(val result: List<YahooResult>? = null)

@Serializable
internal data class YahooResult(
    val meta: YahooMeta? = null,
    val timestamp: List<Long>? = null,
    val indicators: YahooIndicators? = null,
)

/** Métadonnées : dernier cours connu et son horodatage (epoch secondes). */
@Serializable
internal data class YahooMeta(
    val regularMarketPrice: Double? = null,
    val regularMarketTime: Long? = null,
)

@Serializable
internal data class YahooIndicators(val quote: List<YahooQuote>? = null)

@Serializable
internal data class YahooQuote(
    val open: List<Double?>? = null,
    val high: List<Double?>? = null,
    val low: List<Double?>? = null,
    val close: List<Double?>? = null,
    val volume: List<Double?>? = null,
)
