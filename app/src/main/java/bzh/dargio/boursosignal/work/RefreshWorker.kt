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
import bzh.dargio.boursosignal.domain.signals.ExitAction
import bzh.dargio.boursosignal.domain.signals.ExitAdvisor
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Tâche de fond (§10, §13) : scanne l'univers, évalue les stratégies, notifie les
 * signaux, applique le mode test, et conseille la VENTE sur les positions détenues.
 */
class RefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BoursoSignalApp
        val container = app.container
        return try {
            val emitted = container.signalRefresher.refreshAll()
            val notify = container.settings.notificationsEnabled
            if (notify) {
                emitted.forEachIndexed { index, s ->
                    Notifications.notifySignal(
                        context = applicationContext,
                        id = (s.ticker.hashCode() and 0xffff) + index,
                        ticker = s.ticker, name = s.name, type = s.result.type,
                        reasons = s.result.reasons.joinToString(" · "),
                    )
                }
            }
            container.paperTradingService.apply(emitted)
            container.marketScannerService.scan()

            // Conseil de vente sur les positions réelles détenues.
            if (notify) {
                val cfg = container.strategyRepository.activeConfig()
                val positions = container.portfolioRepository.observePositions().first()
                positions.forEach { p ->
                    val candles = container.marketRepository.refresh(p.ticker).getOrNull()
                        ?: container.marketRepository.cachedCandles(p.ticker)
                    if (candles.isNotEmpty()) {
                        val advice = ExitAdvisor.advise(p.ticker, candles, p.avgPrice, cfg)
                        if (advice.action == ExitAction.SELL) {
                            Notifications.notifyExit(
                                applicationContext,
                                (p.ticker.hashCode() and 0xffff) + 1,
                                p.ticker,
                                advice.reasons.firstOrNull() ?: "Conditions de vente réunies",
                            )
                        }
                    }
                }
            }
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "boursosignal_refresh"

        fun schedule(context: Context, intervalHours: Int) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(intervalHours.coerceAtLeast(1).toLong(), TimeUnit.HOURS)
                .setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
