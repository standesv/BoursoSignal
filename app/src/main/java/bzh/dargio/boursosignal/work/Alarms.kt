package bzh.dargio.boursosignal.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import bzh.dargio.boursosignal.BoursoSignalApp
import bzh.dargio.boursosignal.domain.signals.MarketClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Alarmes exactes (§23-9) : pendant la séance, le téléphone est réveillé toutes les
 * 15 min, même en veille profonde, pour vérifier les positions. Nécessite la
 * permission Android « Alarmes et rappels » ; sans elle, la tâche de fond
 * périodique (WorkManager) reste active.
 */
object ExactAlarms {

    private const val REQUEST_CODE = 7101

    fun canSchedule(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } else true

    fun scheduleNext(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!canSchedule(context)) {
            manager.cancel(pending)
            return
        }
        val zone = ZoneId.of("Europe/Paris")
        val next = MarketClock.nextWatchSlot(LocalDateTime.now(zone))
        val at = next.atZone(zone).toInstant().toEpochMilli()
        runCatching { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
    }
}

/** Réveil de séance : vérifie les positions puis programme l'alarme suivante. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as BoursoSignalApp
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(25_000) { runCatching { PositionWatch.runOnce(app, app.container) } }
            } finally {
                ExactAlarms.scheduleNext(app)
                result.finish()
            }
        }
    }
}

/** Après un redémarrage ou une mise à jour de l'app : reprogramme les alarmes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ExactAlarms.scheduleNext(context.applicationContext)
    }
}
