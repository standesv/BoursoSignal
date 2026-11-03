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
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Tâche de fond (§10, §13) : scanne l'univers, évalue les stratégies, notifie les
 * signaux, applique le mode test (et notifie ses ordres), et conseille la VENTE
 * sur les positions détenues.
 */
class RefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BoursoSignalApp
        val container = app.container
        return try {
            val emitted = container.signalRefresher.refreshAll()
            val notify = container.settings.notificationsEnabled
            val nameByTicker = emitted.associate { it.ticker to it.name }
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

            // Mode test automatique : applique les signaux et notifie chaque ordre fictif.
            val paperTrades = container.paperTradingService.apply(emitted)
            if (notify) {
                paperTrades.forEach { t ->
                    Notifications.notifyPaperTrade(
                        applicationContext,
                        (t.ticker.hashCode() and 0xffff) + 3,
                        t.ticker,
                        nameByTicker[t.ticker] ?: t.ticker,
                        t.type,
                        t.qty,
                    )
                }
            }

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
        private const val MARKET_OPEN_WORK = "boursosignal_market_open"

        fun schedule(context: Context, intervalHours: Int) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(intervalHours.coerceAtLeast(1).toLong(), TimeUnit.HOURS)
                .setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /**
         * Rafraîchissement quotidien calé peu après l'ouverture d'Euronext Paris
         * (~9h10, heure de Paris), pour que les positions prises avant l'ouverture
         * se valorisent dès le début de séance. Le timing exact dépend d'Android
         * (Doze), d'où l'intérêt d'autoriser l'exécution en arrière-plan.
         */
        fun scheduleMarketOpen(context: Context) {
            val zone = ZoneId.of("Europe/Paris")
            val now = ZonedDateTime.now(zone)
            var next = now.withHour(9).withMinute(10).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delayMinutes = Duration.between(now, next).toMinutes().coerceAtLeast(1)
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(MARKET_OPEN_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
