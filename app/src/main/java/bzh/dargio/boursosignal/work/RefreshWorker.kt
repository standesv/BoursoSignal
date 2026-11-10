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
import bzh.dargio.boursosignal.domain.signals.SignalType
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Tâche de fond (§10, §13) : rafraîchit les données en continu, mais ne **notifie**
 * que pendant la plage autorisée (jours + heures choisis dans les Réglages). Les
 * signaux détectés hors plage restent « non traités » et sont délivrés au prochain
 * passage dans la plage — rien n'est perdu.
 */
class RefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BoursoSignalApp
        val container = app.container
        return try {
            // 1. Rafraîchissement des données (toujours, quelle que soit l'heure).
            val emitted = container.signalRefresher.refreshAll()
            val paperTrades = container.paperTradingService.apply(emitted)
            container.marketScannerService.scan()

            // 2. Notifications : uniquement dans la plage autorisée.
            val now = LocalDateTime.now(ZoneId.of("Europe/Paris"))
            val canNotify = container.settings.notificationsEnabled && container.settings.isWithinNotifyWindow(now)
            if (canNotify) {
                // Signaux non encore notifiés (y compris ceux reportés depuis la nuit / hors plage).
                container.signalRepository.unhandled().forEachIndexed { index, s ->
                    Notifications.notifySignal(
                        context = applicationContext,
                        id = (s.ticker.hashCode() and 0xffff) + index,
                        ticker = s.ticker,
                        name = container.marketScannerService.displayName(s.ticker),
                        type = runCatching { SignalType.valueOf(s.type) }.getOrDefault(SignalType.NONE),
                        reasons = s.reasons,
                    )
                    container.signalRepository.setHandled(s.id, true)
                }

                // Ordres fictifs exécutés durant ce cycle.
                val nameByTicker = emitted.associate { it.ticker to it.name }
                paperTrades.forEach { t ->
                    Notifications.notifyPaperTrade(
                        applicationContext,
                        (t.ticker.hashCode() and 0xffff) + 3,
                        t.ticker,
                        nameByTicker[t.ticker] ?: container.marketScannerService.displayName(t.ticker),
                        t.type,
                        t.qty,
                    )
                }

                // Conseils sur les positions réelles détenues : vendre OU alléger.
                val cfg = container.strategyRepository.activeConfig()
                container.portfolioRepository.observePositions().first().forEach { p ->
                    val candles = container.marketRepository.refresh(p.ticker).getOrNull()
                        ?: container.marketRepository.cachedCandles(p.ticker)
                    if (candles.isNotEmpty()) {
                        val advice = ExitAdvisor.advise(p.ticker, candles, p.avgPrice, cfg,
                            lastTrimPrice = container.settings.trimAnchor(p.ticker))
                        container.settings.setTrimAnchor(p.ticker, advice.trimAnchor)
                        val reason = advice.reasons.firstOrNull() ?: "Conditions réunies"
                        when (advice.action) {
                            ExitAction.SELL -> Notifications.notifyExit(applicationContext, (p.ticker.hashCode() and 0xffff) + 1, p.ticker, reason)
                            ExitAction.TRIM -> Notifications.notifyTrim(applicationContext, (p.ticker.hashCode() and 0xffff) + 4, p.ticker, reason)
                            ExitAction.HOLD -> {}
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
        private const val NOTIFY_WORK = "boursosignal_notify"

        fun schedule(context: Context, intervalHours: Int) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(intervalHours.coerceAtLeast(1).toLong(), TimeUnit.HOURS)
                .setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Rafraîchissement quotidien peu après l'ouverture d'Euronext Paris (~9h10). */
        fun scheduleMarketOpen(context: Context) {
            enqueueDaily(context, MARKET_OPEN_WORK, hour = 9, minute = 10)
        }

        /** Passage quotidien calé sur le début de la plage de notifications choisie. */
        fun scheduleNotify(context: Context, startHour: Int) {
            enqueueDaily(context, NOTIFY_WORK, hour = startHour.coerceIn(0, 23), minute = 5)
        }

        private fun enqueueDaily(context: Context, name: String, hour: Int, minute: Int) {
            val zone = ZoneId.of("Europe/Paris")
            val now = ZonedDateTime.now(zone)
            var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delayMinutes = Duration.between(now, next).toMinutes().coerceAtLeast(1)
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
