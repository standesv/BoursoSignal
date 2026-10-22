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
                ts.indices.mapNotNull { i ->
                    val close = q.close?.getOrNull(i) ?: return@mapNotNull null
                    Candle(
                        date = Instant.ofEpochSecond(ts[i]).atZone(zone).toLocalDate().toString(),
                        open = q.open?.getOrNull(i) ?: close,
                        high = q.high?.getOrNull(i) ?: close,
                        low = q.low?.getOrNull(i) ?: close,
                        close = close,
                        volume = q.volume?.getOrNull(i) ?: 0.0,
                    )
                }
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
    val timestamp: List<Long>? = null,
    val indicators: YahooIndicators? = null,
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
