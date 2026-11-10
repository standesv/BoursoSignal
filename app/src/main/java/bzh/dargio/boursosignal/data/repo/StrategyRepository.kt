package bzh.dargio.boursosignal.data.repo

import bzh.dargio.boursosignal.data.local.StrategyDao
import bzh.dargio.boursosignal.data.local.StrategyEntity
import bzh.dargio.boursosignal.data.local.toConfig
import bzh.dargio.boursosignal.domain.signals.StrategyConfig
import kotlinx.coroutines.flow.Flow

/** Stratégies nommées et paramétrables (§F8). */
class StrategyRepository(private val dao: StrategyDao) {

    fun observeAll(): Flow<List<StrategyEntity>> = dao.observeAll()

    suspend fun upsert(strategy: StrategyEntity) = dao.upsert(strategy)

    /** Crée une stratégie par défaut au premier lancement si aucune n'existe. */
    suspend fun ensureDefault() {
        val existing = dao.firstEnabled()
        if (existing == null) {
            dao.upsert(StrategyEntity(name = "Stratégie par défaut"))
            return
        }
        // Applique les réglages de risque recommandés même aux installations existantes :
        // pas de take-profit fixe (on laisse courir les gagnantes, sorties gérées par le
        // stop suiveur) et stop-loss élargi pour éviter les ventes prématurées. Idempotent.
        val retuned = existing.copy(stopLossPct = -0.12, takeProfitPct = null)
        if (retuned != existing) dao.upsert(retuned)
    }

    /** Config de la première stratégie active, ou les valeurs par défaut du §5. */
    suspend fun activeConfig(): StrategyConfig =
        dao.firstEnabled()?.toConfig() ?: StrategyConfig()
}
