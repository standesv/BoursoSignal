package bzh.dargio.boursosignal

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import bzh.dargio.boursosignal.ui.AppRoot
import bzh.dargio.boursosignal.ui.theme.BoursoSignalTheme
import bzh.dargio.boursosignal.work.Notifications

class MainActivity : ComponentActivity() {

    /** Valeur dont la fiche doit s'ouvrir (appui sur une notification). */
    private var tickerToOpen by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) tickerToOpen = intent?.getStringExtra(Notifications.EXTRA_TICKER)
        val container = (application as BoursoSignalApp).container

        val requestNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

        setContent {
            BoursoSignalTheme {
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                AppRoot(
                    container,
                    openTicker = tickerToOpen,
                    onTickerOpened = {
                        tickerToOpen = null
                        intent?.removeExtra(Notifications.EXTRA_TICKER) // pas de réouverture après rotation
                    },
                )
            }
        }
    }

    /** App déjà ouverte : l'appui sur une notification arrive ici. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(Notifications.EXTRA_TICKER)?.let { tickerToOpen = it }
    }
}
