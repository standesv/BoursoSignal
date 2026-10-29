package bzh.dargio.boursosignal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import bzh.dargio.boursosignal.domain.signals.Candle
import bzh.dargio.boursosignal.domain.signals.StrategyConfig

@Database(
    entities = [
        InstrumentEntity::class,
        CandleEntity::class,
        PositionEntity::class,
        TransactionEntity::class,
        PortfolioCashEntity::class,
        SignalEntity::class,
        StrategyEntity::class,
        PaperAccountEntity::class,
        PaperPositionEntity::class,
        PaperTradeEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun instrumentDao(): InstrumentDao
    abstract fun candleDao(): CandleDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun signalDao(): SignalDao
    abstract fun strategyDao(): StrategyDao
    abstract fun paperDao(): PaperDao

    companion object {
        /**
         * Base de données persistante. Les mises à jour de l'app **conservent les
         * données** : on n'efface plus la base lors d'une montée de version.
         *
         * IMPORTANT pour les évolutions futures : tout changement de schéma doit
         * incrémenter `version` ET fournir une `Migration` via `.addMigrations(...)`
         * ci-dessous. Sans migration, une montée de version lèvera une exception
         * (c'est volontaire : cela force à écrire la migration plutôt que d'effacer
         * les données de l'utilisateur). Seules les RÉTROgradations (version
         * installée plus récente que l'app) repartent d'une base neuve.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "boursosignal.db")
                // .addMigrations(MIGRATION_4_5, …)  // à ajouter lors des futurs changements de schéma
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}

/** Conversion d'une bougie stockée vers le modèle du moteur de signaux. */
fun CandleEntity.toDomain(): Candle = Candle(date, open, high, low, close, volume)

/** Conversion de la liste d'entités vers la liste de bougies du domaine. */
fun List<CandleEntity>.toDomain(): List<Candle> = map { it.toDomain() }

/** Construit un StrategyConfig (domaine, pur) à partir de l'entité stockée. */
fun StrategyEntity.toConfig(): StrategyConfig = StrategyConfig(
    emaFastPeriod = emaFastPeriod,
    emaSlowPeriod = emaSlowPeriod,
    rsiPeriod = rsiPeriod,
    rsiOversold = rsiOversold,
    rsiOverbought = rsiOverbought,
    volumeAvgPeriod = volumeAvgPeriod,
    volumeFactor = volumeFactor,
    weightEmaCrossUp = weightEmaCrossUp,
    weightRsiExitOversold = weightRsiExitOversold,
    weightVolumeConfirm = weightVolumeConfirm,
    weightEmaCrossDown = weightEmaCrossDown,
    weightRsiOverbought = weightRsiOverbought,
    stopLossPct = stopLossPct,
    takeProfitPct = takeProfitPct,
    buyThreshold = buyThreshold,
    sellThreshold = sellThreshold,
)
