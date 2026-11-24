package bzh.dargio.boursosignal.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import bzh.dargio.boursosignal.MainActivity
import bzh.dargio.boursosignal.R
import bzh.dargio.boursosignal.domain.signals.SignalType

/** Notifications locales des signaux et conseils (§10). Aucun serveur push requis. */
object Notifications {

    private const val CHANNEL_ID = "signals"

    /** Extra de l'intent : valeur dont la fiche doit s'ouvrir à l'appui sur la notification. */
    const val EXTRA_TICKER = "bzh.dargio.boursosignal.extra.TICKER"

    /** Extra de l'intent : écran à ouvrir (ex. « bilan »). */
    const val EXTRA_ROUTE = "bzh.dargio.boursosignal.extra.ROUTE"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Signaux de marché", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Alertes d'achat, de vente, d'allègement et conseils sur vos positions" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Signal d'achat / de vente sur une valeur suivie. Appui → fiche de la valeur. */
    fun notifySignal(context: Context, id: Int, ticker: String, name: String, type: SignalType, reasons: String) {
        val sens = if (type == SignalType.BUY) "Achat" else "Vente"
        push(context, id, "Signal d'${sens.lowercase()} · $name", "$reasons — appuie pour ouvrir la fiche.", ticker)
    }

    /** Conseil de vente sur une position détenue. Appui → fiche de la valeur. */
    fun notifyExit(context: Context, id: Int, ticker: String, name: String, reason: String, label: String = "") {
        push(context, id, "Vendre $name ?$label", "$reason — appuie pour ouvrir la fiche et vendre.", ticker)
    }

    /** Conseil d'allègement (prendre une partie des gains). Appui → fiche de la valeur. */
    fun notifyTrim(context: Context, id: Int, ticker: String, name: String, reason: String, label: String = "") {
        push(context, id, "Alléger $name ?$label",
            "$reason — vends ~la moitié pour sécuriser une partie des gains. Appuie pour ouvrir la fiche.", ticker)
    }

    /** Alerte de prix franchie (§23-5). Appui → fiche de la valeur. */
    fun notifyPriceAlert(context: Context, alertId: Long, ticker: String, name: String, above: Boolean, level: Double, price: Double) {
        val sens = if (above) "au-dessus de" else "en dessous de"
        push(context, 980000 + (alertId % 9999).toInt(), "Alerte de prix · $name",
            "Le cours est passé $sens ${eur(level)} (cours ${eur(price)}). Appuie pour ouvrir la fiche.", ticker)
    }

    /** Publication de résultats proche sur une position détenue (§23-4). */
    fun notifyEarnings(context: Context, ticker: String, name: String, isoDate: String) {
        push(context, (ticker.hashCode() and 0xffff) + 20, "Résultats de $name le ${frDate(isoDate)}",
            "Publication proche : forte volatilité possible. Vérifie ta position et ton stop. Appuie pour ouvrir la fiche.", ticker)
    }

    /** Bilan du vendredi (§23-8). Appui → écran Bilan. */
    fun notifyWeekly(context: Context, body: String) {
        push(context, 990003, "Bilan de la semaine", body, route = "bilan")
    }

    private fun eur(v: Double): String = String.format(java.util.Locale.FRANCE, "%.2f €", v)

    private fun frDate(iso: String): String =
        runCatching { java.time.LocalDate.parse(iso).let { "%02d/%02d".format(it.dayOfMonth, it.monthValue) } }.getOrDefault(iso)

    /** Changement de météo du marché (CAC 40). */
    fun notifyMarket(context: Context, title: String, body: String) {
        push(context, 990002, title, body)
    }

    /** Notification de test (bouton Réglages). Renvoie false si la permission manque. */
    fun notifyTest(context: Context): Boolean =
        push(context, 990001, "Test de notification ✅", "Si tu vois ceci, les alertes BoursoSignal fonctionnent.")

    private fun push(context: Context, id: Int, title: String, body: String, ticker: String? = null, route: String? = null): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        // Appui : ouvre l'app, directement sur la fiche de la valeur si elle est connue.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (ticker != null) putExtra(EXTRA_TICKER, ticker)
            if (route != null) putExtra(EXTRA_ROUTE, route)
        }
        val tap = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }
}
