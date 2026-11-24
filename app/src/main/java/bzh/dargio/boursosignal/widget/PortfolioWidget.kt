package bzh.dargio.boursosignal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import bzh.dargio.boursosignal.BoursoSignalApp
import bzh.dargio.boursosignal.MainActivity
import bzh.dargio.boursosignal.R
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.work.PortfolioSnapshots
import bzh.dargio.boursosignal.work.WeeklyDigest
import java.util.Locale

/** Widget d'écran d'accueil (§23-10) : météo du marché, portefeuilles, conseils en attente. */
class PortfolioWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.render(context, appWidgetManager, appWidgetIds)
    }
}

object WidgetUpdater {

    fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, PortfolioWidget::class.java))
        if (ids.isNotEmpty()) render(context, manager, ids)
    }

    fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val settings = (context.applicationContext as BoursoSignalApp).container.settings
        val views = RemoteViews(context.packageName, R.layout.widget_portfolio)
        views.setTextViewText(R.id.widget_regime, settings.widgetRegime ?: "Ouvre l'app pour charger la météo du marché")
        views.setTextViewText(R.id.widget_test, settings.widgetTest ?: "")
        views.setTextViewText(R.id.widget_real, settings.widgetReal ?: "")
        views.setTextViewText(R.id.widget_pending, settings.widgetPending ?: "")
        val tap = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, tap)
        manager.updateAppWidget(ids, views)
    }
}

/** Met à jour les lignes du widget à partir des dernières données, puis le redessine. */
object WidgetSnapshot {
    suspend fun refresh(context: Context, container: AppContainer, pending: Int?) {
        val settings = container.settings
        container.marketScannerService.regimeFromCache()?.let { settings.widgetRegime = "${it.label} (CAC 40)" }
        val test = PortfolioSnapshots.test(container)
        settings.widgetTest = test?.let { t ->
            val perf = t.initialCapital?.takeIf { it > 0.0 }?.let { c -> " (${WeeklyDigest.pctSigned((t.total / c - 1.0) * 100.0)})" } ?: ""
            "Test : ${eur(t.total)}$perf"
        }
        settings.widgetReal = PortfolioSnapshots.real(container)?.let { "Réel : ${eur(it.total)}" }
        if (pending != null) {
            settings.widgetPending = if (pending == 0) "Aucun conseil Vendre / Alléger en attente"
            else "$pending conseil${if (pending > 1) "s" else ""} Vendre / Alléger en attente"
        }
        WidgetUpdater.update(context)
    }

    private fun eur(v: Double): String = String.format(Locale.FRANCE, "%,.0f €", v)
}
