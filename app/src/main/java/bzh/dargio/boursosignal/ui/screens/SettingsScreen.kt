package bzh.dargio.boursosignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.work.RefreshWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val settings = container.settings
    val context = LocalContext.current

    var apiKey by remember { mutableStateOf(settings.apiKey ?: "") }
    var demo by remember { mutableStateOf(settings.useDemoProvider) }
    var interval by remember { mutableStateOf(settings.refreshIntervalHours.toString()) }
    var notifications by remember { mutableStateOf(settings.notificationsEnabled) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Réglages") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Source de cours", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Mode démo (hors-ligne, sans clé)")
                Switch(checked = demo, onCheckedChange = { demo = it })
            }
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Clé API (Twelve Data)") },
                enabled = !demo,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            Text("Rafraîchissement", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = interval,
                onValueChange = { interval = it.filter { c -> c.isDigit() } },
                label = { Text("Intervalle (heures)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications")
                Switch(checked = notifications, onCheckedChange = { notifications = it })
            }

            Button(
                onClick = {
                    settings.apiKey = apiKey.ifBlank { null }
                    settings.useDemoProvider = demo
                    settings.refreshIntervalHours = interval.toIntOrNull()?.coerceIn(1, 168) ?: 12
                    settings.notificationsEnabled = notifications
                    RefreshWorker.schedule(context, settings.refreshIntervalHours)
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer") }
            if (saved) Text("Réglages enregistrés.", style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Text("Avertissement", fontWeight = FontWeight.SemiBold)
            Text(
                "Outil personnel d'aide à la décision. Les signaux reposent sur des indicateurs " +
                    "techniques et ne constituent pas un conseil en investissement. Investir comporte un " +
                    "risque de perte en capital. Les ordres restent à passer manuellement dans Boursorama.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
