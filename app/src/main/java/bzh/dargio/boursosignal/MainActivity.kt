package bzh.dargio.boursosignal

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import bzh.dargio.boursosignal.ui.AppRoot
import bzh.dargio.boursosignal.ui.theme.BoursoSignalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as BoursoSignalApp).container

        val requestNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

        setContent {
            BoursoSignalTheme {
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                AppRoot(container)
            }
        }
    }
}
