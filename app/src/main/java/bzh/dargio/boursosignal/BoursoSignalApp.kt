package bzh.dargio.boursosignal

import android.app.Application
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.work.Notifications
import bzh.dargio.boursosignal.work.PositionWatchWorker
import bzh.dargio.boursosignal.work.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Point d'entrée de l'application : initialise l'injection et les tâches de fond. */
class BoursoSignalApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)

        appScope.launch {
            container.strategyRepository.ensureDefault()
            // Réglage de risque recommandé appliqué une seule fois ; ensuite l'utilisateur
            // peut l'ajuster dans les Réglages sans qu'il soit réécrasé.
            if (!container.settings.strategyRetunedV2) {
                container.strategyRepository.retuneOnce(stopLossPct = -0.12, takeProfitPct = null)
                container.settings.strategyRetunedV2 = true
            }
        }

        RefreshWorker.schedule(this, container.settings.refreshIntervalHours)
        RefreshWorker.scheduleMarketOpen(this)
        RefreshWorker.scheduleMarketClose(this)
        PositionWatchWorker.schedule(this) // surveillance des positions toutes les ~15 min en séance
        RefreshWorker.scheduleNotify(this, container.settings.notifyStartHour)
    }
}
