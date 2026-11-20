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
            .apply { description = "Alertes d'achat, de vente, d'allègement et conseils sur vos positions" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifySignal(context: Context, id: Int, ticker: String, name: String, type: SignalType, reasons: String) {
        val sens = if (type == SignalType.BUY) "ACHAT" else "VENTE"
        push(context, id, "Signal $sens · $ticker", "$name — $reasons")
    }

    /** Conseil de vente sur une position détenue. */
    fun notifyExit(context: Context, id: Int, ticker: String, reason: String, label: String = "") {
        push(context, id, "Vendre $ticker ?$label", "$reason — passe l'ordre de vente toi-même.")
    }

    /** Conseil d'allègement (prendre une partie des gains) sur une position détenue. */
    fun notifyTrim(context: Context, id: Int, ticker: String, reason: String, label: String = "") {
        push(context, id, "Alléger $ticker ?$label", "$reason — vends ~la moitié pour sécuriser une partie des gains.")
    }

    /** Changement de météo du marché (CAC 40). */
    fun notifyMarket(context: Context, title: String, body: String) {
        push(context, 990002, title, body)
    }

    /** Ordre fictif exécuté automatiquement en mode test. */
    fun notifyPaperTrade(context: Context, id: Int, ticker: String, name: String, type: String, qty: Int) {
        val sens = if (type == "BUY") "Achat" else "Vente"
        push(context, id, "Mode test : $sens de $qty $ticker", "$name — opération simulée automatiquement par l'app")
    }

    /** Notification de test (bouton Réglages). Renvoie false si la permission manque. */
    fun notifyTest(context: Context): Boolean =
        push(context, 990001, "Test de notification ✅", "Si tu vois ceci, les alertes BoursoSignal fonctionnent.")

    private fun push(context: Context, id: Int, title: String, body: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }
}
