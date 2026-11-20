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
import bzh.dargio.boursosignal.domain.signals.SignalType
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

            // 2. Notifications.
            val now = LocalDateTime.now(ZoneId.of("Europe/Paris"))
            val inWindow = container.settings.notificationsEnabled && container.settings.isWithinNotifyWindow(now)

            // (a) Signaux de la watchlist + ordres fictifs : uniquement dans la plage autorisée.
            if (inWindow) {
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
            }

            // (b) Météo du marché, puis conseils sur les positions détenues (réel + test) :
            //     logique partagée avec la surveillance en séance et avec les écrans.
            PositionAlerts.checkRegime(applicationContext, container, now, refreshIndex = false)
            PositionAlerts.checkHeld(applicationContext, container, now)

            // 3. Preuve de bon fonctionnement : horodatage du dernier scan réussi.
            container.settings.lastScanAt = System.currentTimeMillis()
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "boursosignal_refresh"
        private const val MARKET_OPEN_WORK = "boursosignal_market_open"
        private const val NOTIFY_WORK = "boursosignal_notify"
        private const val MARKET_CLOSE_WORK = "boursosignal_market_close"

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

        /** Rafraîchissement quotidien peu après la clôture d'Euronext Paris (~17h40) :
         *  la bougie du jour est disponible, les ventes sont détectées le soir même. */
        fun scheduleMarketClose(context: Context) {
            enqueueDaily(context, MARKET_CLOSE_WORK, hour = 17, minute = 40)
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
