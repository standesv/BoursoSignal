package bzh.dargio.boursosignal.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Valeur suivie (action, ETF…). Correspond à l'entité Instrument du §8. */
@Entity(tableName = "instruments")
data class InstrumentEntity(
    @PrimaryKey val ticker: String,   // ex. "AIR.PA"
    val name: String,
    val exchange: String = "Euronext Paris",
    val currency: String = "EUR",
    val inWatchlist: Boolean = true,
)

/** Bougie journalière (cours historique) pour une valeur. Entité CoursOHLC du §8. */
@Entity(tableName = "candles", primaryKeys = ["ticker", "date"])
data class CandleEntity(
    val ticker: String,
    val date: String,        // ISO "2026-10-21"
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

/** Position détenue (une par valeur). Entité Position du §8. */
@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey val ticker: String,
    val quantity: Double,
    val avgPrice: Double,     // prix de revient unitaire (PRU)
)

/** Transaction réellement passée chez Boursorama, saisie par l'utilisateur. */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticker: String,
    val type: String,         // "BUY" | "SELL"
    val quantity: Double,
    val price: Double,
    val fees: Double = 0.0,
    val date: String,
)

/** Signal / alerte émis par le moteur. Entité Signal du §8. */
@Entity(tableName = "signals")
data class SignalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticker: String,
    val type: String,         // "BUY" | "SELL"
    val score: Double,
    val reasons: String,      // raisons jointes par " · "
    val date: String,         // date de la bougie déclencheuse
    val price: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val handled: Boolean = false,
)

/** Stratégie nommée : les paramètres du StrategyConfig (§5) stockés en colonnes. */
@Entity(tableName = "strategies")
data class StrategyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val emaFastPeriod: Int = 20,
    val emaSlowPeriod: Int = 50,
    val rsiPeriod: Int = 14,
    val rsiOversold: Double = 30.0,
    val rsiOverbought: Double = 70.0,
    val volumeAvgPeriod: Int = 20,
    val volumeFactor: Double = 1.2,
    val weightEmaCrossUp: Double = 1.0,
    val weightRsiExitOversold: Double = 1.0,
    val weightVolumeConfirm: Double = 0.5,
    val weightEmaCrossDown: Double = 1.0,
    val weightRsiOverbought: Double = 1.0,
    val stopLossPct: Double? = -0.08,
    val takeProfitPct: Double? = 0.15,
    val buyThreshold: Double = 1.5,
    val sellThreshold: Double = 1.0,
)
