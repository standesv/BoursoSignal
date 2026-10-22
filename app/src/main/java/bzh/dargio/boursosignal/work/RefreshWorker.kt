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
import java.util.concurrent.TimeUnit

/**
 * Tâche de fond (§10, §13) : scanne l'univers Euronext Paris, évalue les stratégies
 * sur la watchlist, notifie les nouveaux signaux et, si le mode test est actif, les
 * applique au portefeuille fictif. S'exécute même application fermée.
 */
class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BoursoSignalApp
        val container = app.container
        return try {
            val emitted = container.signalRefresher.refreshAll()
            if (container.settings.notificationsEnabled) {
                emitted.forEachIndexed { index, s ->
                    Notifications.notifySignal(
                        context = applicationContext,
                        id = (s.ticker.hashCode() and 0xffff) + index,
                        ticker = s.ticker,
                        name = s.name,
                        type = s.result.type,
                        reasons = s.result.reasons.joinToString(" · "),
                    )
                }
            }
            // Mode test : applique les signaux au portefeuille fictif (sans effet si désactivé).
            container.paperTradingService.apply(emitted)
            // Rafraîchit le classement d'opportunités sur tout l'univers.
            container.marketScannerService.scan()
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "boursosignal_refresh"

        /** Planifie (ou replanifie) le rafraîchissement périodique. */
        fun schedule(context: Context, intervalHours: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(
                intervalHours.coerceAtLeast(1).toLong(), TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
