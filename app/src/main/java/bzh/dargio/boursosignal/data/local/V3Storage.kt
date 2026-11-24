package bzh.dargio.boursosignal.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Conseil enregistré pour le bilan (§23-1). portfolio = "idee" | "reel" | "test". */
@Entity(tableName = "advice_log")
data class AdviceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticker: String,
    val kind: String,
    val portfolio: String,
    val date: String,
    val price: Double,
    val regime: String? = null,
    val createdAt: Long,
    val ret5: Double? = null,
    val ret20: Double? = null,
)

/** Alerte de prix définie par l'utilisateur (§23-5). */
@Entity(tableName = "price_alerts")
data class PriceAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticker: String,
    val above: Boolean,
    val level: Double,
    val active: Boolean = true,
    val createdAt: Long,
    val triggeredAt: Long? = null,
    val triggeredPrice: Double? = null,
)

@Dao
interface AdviceDao {
    @Insert
    suspend fun insert(entity: AdviceLogEntity): Long

    @Query("SELECT COUNT(*) FROM advice_log WHERE ticker = :ticker AND kind = :kind AND portfolio = :portfolio AND date >= :sinceDate")
    suspend fun countSince(ticker: String, kind: String, portfolio: String, sinceDate: String): Int

    @Query("SELECT * FROM advice_log WHERE ret20 IS NULL")
    suspend fun pending(): List<AdviceLogEntity>

    @Query("UPDATE advice_log SET ret5 = :ret5, ret20 = :ret20 WHERE id = :id")
    suspend fun setResults(id: Long, ret5: Double?, ret20: Double?)

    @Query("SELECT * FROM advice_log ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AdviceLogEntity>>

    @Query("SELECT * FROM advice_log WHERE createdAt >= :sinceMillis ORDER BY createdAt DESC")
    suspend fun since(sinceMillis: Long): List<AdviceLogEntity>
}

@Dao
interface PriceAlertDao {
    @Insert
    suspend fun insert(entity: PriceAlertEntity): Long

    @Query("SELECT * FROM price_alerts WHERE ticker = :ticker ORDER BY active DESC, createdAt DESC")
    fun observeFor(ticker: String): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE active = 1")
    suspend fun active(): List<PriceAlertEntity>

    @Query("UPDATE price_alerts SET active = 0, triggeredAt = :at, triggeredPrice = :price WHERE id = :id")
    suspend fun markTriggered(id: Long, at: Long, price: Double)

    @Query("DELETE FROM price_alerts WHERE id = :id")
    suspend fun delete(id: Long)
}

/**
 * Migration 4 → 5 (v3) : ajoute les tables du bilan des conseils et des alertes
 * de prix, SANS toucher aux données existantes (portefeuilles, journal, réglages).
 * Le SQL reproduit exactement le schéma que Room attend pour ces entités.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `advice_log` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ticker` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`portfolio` TEXT NOT NULL, `date` TEXT NOT NULL, `price` REAL NOT NULL, `regime` TEXT, " +
                "`createdAt` INTEGER NOT NULL, `ret5` REAL, `ret20` REAL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `price_alerts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ticker` TEXT NOT NULL, `above` INTEGER NOT NULL, " +
                "`level` REAL NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`triggeredAt` INTEGER, `triggeredPrice` REAL)"
        )
    }
}
