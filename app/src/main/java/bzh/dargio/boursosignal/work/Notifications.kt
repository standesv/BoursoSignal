package bzh.dargio.boursosignal.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import bzh.dargio.boursosignal.R
import bzh.dargio.boursosignal.domain.signals.SignalType

/** Notifications locales des signaux (§10). Aucun serveur push requis. */
object Notifications {

    private const val CHANNEL_ID = "signals"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Signaux de marché",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Alertes d'achat et de vente détectées par le moteur" }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun notifySignal(context: Context, id: Int, ticker: String, name: String, type: SignalType, reasons: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val sens = if (type == SignalType.BUY) "ACHAT" else "VENTE"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Signal $sens · $ticker")
            .setContentText("$name — $reasons")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$name\n$reasons"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
