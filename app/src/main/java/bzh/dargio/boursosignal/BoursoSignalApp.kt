package bzh.dargio.boursosignal

import android.app.Application
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.work.Notifications
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

        appScope.launch { container.strategyRepository.ensureDefault() }

        RefreshWorker.schedule(this, container.settings.refreshIntervalHours)
        RefreshWorker.scheduleMarketOpen(this)
        RefreshWorker.scheduleNotify(this, container.settings.notifyStartHour)
    }
}
