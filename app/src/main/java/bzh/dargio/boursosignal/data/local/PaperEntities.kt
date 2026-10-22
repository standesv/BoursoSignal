package bzh.dargio.boursosignal.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entités du portefeuille fictif (mode test / paper trading).
 *
 * Le modèle prévoit une seule ligne de compte pour la v1 (id = 0), mais le champ
 * `strategyName` prépare l'extension à plusieurs portefeuilles fictifs (un par
 * stratégie) sans changer le schéma.
 */
@Entity(tableName = "paper_account")
data class PaperAccountEntity(
    @PrimaryKey val id: Int = 0,
    val enabled: Boolean = false,
    val initialCapital: Double = 10_000.0,
    val cash: Double = 10_000.0,
    val startDate: String,
    val strategyName: String = "défaut",
)

@Entity(tableName = "paper_positions")
data class PaperPositionEntity(
    @PrimaryKey val ticker: String,
    val quantity: Double,
    val avgPrice: Double,
    val openedDate: String,
)

@Entity(tableName = "paper_trades")
data class PaperTradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticker: String,
    val type: String,        // "BUY" | "SELL"
    val quantity: Double,
    val price: Double,
    val date: String,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
)
