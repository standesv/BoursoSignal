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
import bzh.dargio.boursosignal.domain.signals.MarketRegimeEvaluator
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.IsoFields
import java.util.concurrent.TimeUnit

/** Bilan du vendredi soir (§23-8) : une notification par semaine, qui ouvre l'écran Bilan. */
class WeeklyReportWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = LocalDateTime.now(ZoneId.of("Europe/Paris"))
        if (now.dayOfWeek != DayOfWeek.FRIDAY) return Result.success()
        val container = (applicationContext as BoursoSignalApp).container
        val settings = container.settings
        val week = "${now.get(IsoFields.WEEK_BASED_YEAR)}-W${now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
        if (settings.lastWeeklyReport == week) return Result.success()
        return try {
            container.marketRepository.refresh(MarketRegimeEvaluator.INDEX_TICKER)
            container.adviceRepository.evaluatePending(container.marketRepository)
            val digest = WeeklyDigest.compute(container)
            if (settings.notificationsEnabled) Notifications.notifyWeekly(applicationContext, WeeklyDigest.text(digest))
            settings.lastWeeklyReport = week
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "boursosignal_weekly"

        /** Passage quotidien à 18h05 ; seul celui du vendredi produit le bilan. */
        fun schedule(context: Context) {
            val zone = ZoneId.of("Europe/Paris")
            val now = ZonedDateTime.now(zone)
            var next = now.withHour(18).withMinute(5).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes().coerceAtLeast(1)
            val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
