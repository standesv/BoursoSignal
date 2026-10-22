package bzh.dargio.boursosignal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(container: AppContainer, onOpen: (String) -> Unit) {
    val signals by container.signalRepository.observeAll().collectAsState(initial = emptyList())

    Scaffold(topBar = { TopAppBar(title = { Text("Alertes") }) }) { padding ->
        if (signals.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Aucun signal pour l'instant.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Les alertes apparaissent ici quand le moteur détecte un signal d'achat ou de vente.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(signals, key = { it.id }) { s ->
                    val buy = s.type == "BUY"
                    Card(Modifier.fillMaxWidth().clickable { onOpen(s.ticker) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "${if (buy) "▲ ACHAT" else "▼ VENTE"} · ${s.ticker}",
                                color = if (buy) BullGreen else BearRed,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("Cours ${"%.2f €".format(s.price)} · ${s.date} · score ${"%.1f".format(s.score)}",
                                style = MaterialTheme.typography.bodySmall)
                            Text(s.reasons, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
