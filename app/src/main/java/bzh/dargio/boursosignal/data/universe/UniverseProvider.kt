package bzh.dargio.boursosignal.data.universe

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Une valeur de l'univers surveillé : ticker Yahoo, nom affichable, secteur et
 * éligibilité au PEA (§23-7, §23-11 : les foncières SIIC sont exclues du PEA).
 */
@Serializable
data class UniverseEntry(
    val ticker: String,
    val name: String,
    val sector: String = "",
    val pea: Boolean = true,
)

/**
 * Fournit l'univers de valeurs surveillées (Euronext Paris élargi), chargé depuis
 * un fichier embarqué `assets/universe_euronext.json`. Liste extensible ; les
 * valeurs aux données non fiables sont de toute façon écartées par le contrôle
 * qualité au moment du scan (§ fiabilité).
 */
class UniverseProvider(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: List<UniverseEntry>? = null

    fun load(): List<UniverseEntry> {
        cache?.let { return it }
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val list = json.decodeFromString<List<UniverseEntry>>(text)
        cache = list
        return list
    }

    private companion object {
        const val ASSET = "universe_euronext.json"
    }
}
