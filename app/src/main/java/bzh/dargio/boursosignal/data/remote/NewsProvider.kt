package bzh.dargio.boursosignal.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/** Un titre d'actualité relié à une valeur. */
data class NewsItem(
    val title: String,
    val publisher: String,
    val url: String,
    val publishedEpoch: Long,
)

/**
 * Récupère les actualités récentes d'une valeur via le point d'accès de recherche
 * de Yahoo Finance (gratuit, sans clé). Sert à afficher les titres et à alimenter
 * l'analyse d'actualité indicative (§ actualité). Point d'accès non officiel :
 * à usage personnel.
 */
class NewsProvider(okHttpClient: OkHttpClient? = null) {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = okHttpClient ?: OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (compatible; BoursoSignal/1.0)")
                    .build()
            )
        }
        .build()

    @Suppress("OPT_IN_USAGE")
    private val service: YahooSearchService = Retrofit.Builder()
        .baseUrl("https://query1.finance.yahoo.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(YahooSearchService::class.java)

    /** Actualités pour une requête (nom de société de préférence, sinon ticker). */
    suspend fun forQuery(query: String, count: Int = 8): Result<List<NewsItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = service.search(query = query, newsCount = count, quotesCount = 0)
                (resp.news ?: emptyList()).mapNotNull { n ->
                    val title = n.title ?: return@mapNotNull null
                    val link = n.link ?: return@mapNotNull null
                    NewsItem(title, n.publisher ?: "", link, n.providerPublishTime ?: 0L)
                }
            }
        }
}

internal interface YahooSearchService {
    @GET("v1/finance/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("newsCount") newsCount: Int,
        @Query("quotesCount") quotesCount: Int,
    ): YahooSearchResponse
}

@Serializable
internal data class YahooSearchResponse(val news: List<YahooNews>? = null)

@Serializable
internal data class YahooNews(
    val title: String? = null,
    val link: String? = null,
    val publisher: String? = null,
    val providerPublishTime: Long? = null,
)
