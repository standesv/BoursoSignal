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
        if (dao.count() == 0) {
            dao.upsert(StrategyEntity(name = "Stratégie par défaut"))
        }
    }

    /** Config de la première stratégie active, ou les valeurs par défaut du §5. */
    suspend fun activeConfig(): StrategyConfig =
        dao.firstEnabled()?.toConfig() ?: StrategyConfig()
}
