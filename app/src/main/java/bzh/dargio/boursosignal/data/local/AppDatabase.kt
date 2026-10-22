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
         * Base chiffrée en production (SQLCipher, §11). Par défaut, base Room
         * standard : brancher le `SupportFactory` de SQLCipher avec une clé issue
         * du Keystore pour activer le chiffrement.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "boursosignal.db")
                .fallbackToDestructiveMigration()
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
