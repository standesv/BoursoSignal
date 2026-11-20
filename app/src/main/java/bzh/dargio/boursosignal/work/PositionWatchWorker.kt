package bzh.dargio.boursosignal.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import bzh.dargio.boursosignal.BoursoSignalApp
import bzh.dargio.boursosignal.domain.signals.MarketClock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Surveillance rapprochée des positions détenues PENDANT la séance : toutes les
 * ~15 min (minimum Android), on recharge uniquement les positions et le CAC 40,
 * et on notifie aussitôt un nouveau conseil « Vendre » / « Alléger ».
 * Hors séance, la tâche se termine immédiatement (aucun coût).
 */
class PositionWatchWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = LocalDateTime.now(ZoneId.of("Europe/Paris"))
        if (!MarketClock.isWatchTime(now)) return Result.success()
        val container = (applicationContext as BoursoSignalApp).container
        return try {
            PositionAlerts.checkRegime(applicationContext, container, now, refreshIndex = true)
            PositionAlerts.checkHeld(applicationContext, container, now)
            container.settings.lastScanAt = System.currentTimeMillis()
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "boursosignal_position_watch"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<PositionWatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
