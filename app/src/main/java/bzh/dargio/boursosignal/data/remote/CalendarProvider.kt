package bzh.dargio.boursosignal.data.remote

import bzh.dargio.boursosignal.domain.signals.CompanyEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Calendrier des sociétés (§23-4) : prochaine publication de résultats et prochain
 * détachement de dividende, via l'API Yahoo « quoteSummary » (module calendarEvents).
 *
 * Yahoo exige un cookie de session et un jeton (« crumb ») : on les obtient au
 * premier appel. Endpoint non officiel, susceptible d'être refusé (notamment selon
 * le consentement RGPD) : en cas d'échec, l'app affiche « indisponible » et ne
 * bloque rien.
 */
class CalendarProvider {

    private val jar = mutableListOf<Cookie>()

    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                synchronized(jar) {
                    jar.removeAll { old -> cookies.any { it.name == old.name && it.domain == old.domain } }
                    jar.addAll(cookies)
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(jar) { jar.filter { it.matches(url) } }
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var crumb: String? = null
    private val zone = ZoneId.of("Europe/Paris")

    suspend fun events(ticker: String): Result<CompanyEvents> = withContext(Dispatchers.IO) {
        runCatching {
            runCatching { fetch(ticker) }.getOrElse {
                crumb = null // jeton expiré ou refusé : on en redemande un, une seule fois
                fetch(ticker)
            }
        }
    }

    private fun fetch(ticker: String): CompanyEvents {
        val token = crumb ?: newCrumb().also { crumb = it }
        val url = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/" +
            URLEncoder.encode(ticker, "UTF-8") + "?modules=calendarEvents&crumb=" + URLEncoder.encode(token, "UTF-8")
        return parse(get(url))
    }

    private fun newCrumb(): String {
        runCatching { get("https://fc.yahoo.com", acceptErrors = true) } // dépose le cookie de session
        val token = get("https://query2.finance.yahoo.com/v1/test/getcrumb").trim()
        require(token.isNotEmpty() && token.length < 64 && !token.contains('<') && !token.contains('{')) {
            "Jeton Yahoo indisponible"
        }
        return token
    }

    private fun get(url: String, acceptErrors: Boolean = false): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful && !acceptErrors) throw IllegalStateException("HTTP ${response.code}")
            body
        }
    }

    private fun parse(json: String): CompanyEvents {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: error("Réponse illisible")
        val result = ((root["quoteSummary"] as? JsonObject)?.get("result") as? JsonArray)?.firstOrNull() as? JsonObject
            ?: error("Calendrier absent")
        val cal = result["calendarEvents"] as? JsonObject ?: return CompanyEvents(null, null)
        val earnings = ((cal["earnings"] as? JsonObject)?.get("earningsDate") as? JsonArray)
            ?.mapNotNull { raw(it) }
            ?.minOrNull()
        val exDividend = raw(cal["exDividendDate"])
        return CompanyEvents(earnings?.let { isoDate(it) }, exDividend?.let { isoDate(it) })
    }

    private fun raw(element: JsonElement?): Long? = ((element as? JsonObject)?.get("raw") as? JsonPrimitive)?.longOrNull

    private fun isoDate(epochSeconds: Long): String = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate().toString()

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
