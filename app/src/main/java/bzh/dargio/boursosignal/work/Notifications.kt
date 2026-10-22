package bzh.dargio.boursosignal.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import bzh.dargio.boursosignal.R
import bzh.dargio.boursosignal.domain.signals.SignalType

/** Notifications locales des signaux et conseils (§10). Aucun serveur push requis. */
object Notifications {

    private const val CHANNEL_ID = "signals"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Signaux de marché", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Alertes d'achat, de vente et conseils sur vos positions" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifySignal(context: Context, id: Int, ticker: String, name: String, type: SignalType, reasons: String) {
        val sens = if (type == SignalType.BUY) "ACHAT" else "VENTE"
        push(context, id, "Signal $sens · $ticker", "$name — $reasons")
    }

    /** Conseil de vente sur une position détenue. */
    fun notifyExit(context: Context, id: Int, ticker: String, reason: String) {
        push(context, id, "Vendre $ticker ?", reason)
    }

    private fun push(context: Context, id: Int, title: String, body: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
