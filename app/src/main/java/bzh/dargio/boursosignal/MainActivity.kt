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

    /** Écran à ouvrir suite à l'appui sur une notification (fiche d'une valeur ou Bilan). */
    private var routeToOpen by mutableStateOf<String?>(null)

    private fun routeFrom(intent: Intent?): String? =
        intent?.getStringExtra(Notifications.EXTRA_ROUTE)
            ?: intent?.getStringExtra(Notifications.EXTRA_TICKER)?.let { "detail/$it" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) routeToOpen = routeFrom(intent)
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
                    openRoute = routeToOpen,
                    onRouteOpened = {
                        routeToOpen = null
                        intent?.removeExtra(Notifications.EXTRA_TICKER) // pas de réouverture après rotation
                        intent?.removeExtra(Notifications.EXTRA_ROUTE)
                    },
                )
            }
        }
    }

    /** App déjà ouverte : l'appui sur une notification arrive ici. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFrom(intent)?.let { routeToOpen = it }
    }
}
