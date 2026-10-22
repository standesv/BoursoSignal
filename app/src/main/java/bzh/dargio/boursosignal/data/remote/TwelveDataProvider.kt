package bzh.dargio.boursosignal.data.remote

import bzh.dargio.boursosignal.domain.signals.Candle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Implémentation de [MarketDataProvider] sur l'API Twelve Data (§6).
 *
 * Offre gratuite limitée (quotas) : convient à un rafraîchissement en fin de
 * séance sur une watchlist réduite. La clé API est fournie par l'utilisateur
 * (§11) et n'est jamais codée en dur.
 *
 * Symboles : le ticker interne "AIR.PA" est traduit en symbole "AIR" + code de
 * place MIC "XPAR" (Euronext Paris) via [toProviderSymbol]. Le MIC est le plus
 * fiable pour lever l'ambiguïté entre les places Euronext.
 */
class TwelveDataProvider(
    private val apiKeyProvider: () -> String?,
    okHttpClient: OkHttpClient = OkHttpClient(),
) : MarketDataProvider {

    override val displayName: String = "Twelve Data"

    private val json = Json { ignoreUnknownKeys = true }

    @Suppress("OPT_IN_USAGE")
    private val service: TwelveDataService = Retrofit.Builder()
        .baseUrl("https://api.twelvedata.com/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TwelveDataService::class.java)

    override suspend fun dailyCandles(ticker: String, outputSize: Int): Result<List<Candle>> =
        withContext(Dispatchers.IO) {
            val key = apiKeyProvider()
                ?: return@withContext Result.failure(MissingApiKeyException())
            runCatching {
                val (symbol, micCode) = ticker.toProviderSymbol()
                val resp = service.timeSeries(
                    symbol = symbol,
                    micCode = micCode,
                    interval = "1day",
                    outputSize = outputSize,
                    apiKey = key,
                )
                if (resp.status != "ok" || resp.values == null) {
                    throw ProviderException(resp.message ?: "Réponse invalide du fournisseur")
                }
                // Twelve Data renvoie les valeurs du plus récent au plus ancien.
                resp.values.asReversed().map { v ->
                    Candle(
                        date = v.datetime,
                        open = v.open.toDouble(),
                        high = v.high.toDouble(),
                        low = v.low.toDouble(),
                        close = v.close.toDouble(),
                        volume = v.volume?.toDoubleOrNull() ?: 0.0,
                    )
                }
            }
        }

    override suspend fun latestQuote(ticker: String): Result<Quote> =
        dailyCandles(ticker, outputSize = 2).map { candles ->
            val last = candles.lastOrNull() ?: throw ProviderException("Aucune donnée")
            val prev = candles.getOrNull(candles.size - 2)?.close ?: last.close
            val change = if (prev != 0.0) (last.close - prev) / prev * 100.0 else 0.0
            Quote(ticker, last.close, change, last.date)
        }
}

/**
 * Traduit un ticker interne ("AIR.PA") en (symbole, code MIC) pour le fournisseur.
 * Sans suffixe reconnu, renvoie le ticker tel quel et aucun MIC.
 */
fun String.toProviderSymbol(): Pair<String, String?> {
    val dot = lastIndexOf('.')
    if (dot <= 0) return this to null
    val base = substring(0, dot)
    val mic = when (substring(dot + 1).uppercase()) {
        "PA" -> "XPAR"   // Euronext Paris
        "AS" -> "XAMS"   // Euronext Amsterdam
        "BR" -> "XBRU"   // Euronext Bruxelles
        "LS" -> "XLIS"   // Euronext Lisbonne
        "MI" -> "XMIL"   // Borsa Italiana
        "DE" -> "XETR"   // Xetra
        "MC" -> "XMAD"   // Bolsa de Madrid
        "L" -> "XLON"    // London Stock Exchange
        else -> null
    }
    return base to mic
}

class MissingApiKeyException : Exception("Clé API manquante : renseignez-la dans les réglages.")
class ProviderException(message: String) : Exception(message)

internal interface TwelveDataService {
    @GET("time_series")
    suspend fun timeSeries(
        @Query("symbol") symbol: String,
        @Query("mic_code") micCode: String?,
        @Query("interval") interval: String,
        @Query("outputsize") outputSize: Int,
        @Query("apikey") apiKey: String,
    ): TimeSeriesResponse
}

@Serializable
internal data class TimeSeriesResponse(
    val status: String? = null,
    val message: String? = null,
    val values: List<TsValue>? = null,
)

@Serializable
internal data class TsValue(
    val datetime: String,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    @SerialName("volume") val volume: String? = null,
)
