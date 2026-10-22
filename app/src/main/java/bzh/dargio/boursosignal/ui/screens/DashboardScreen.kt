package bzh.dargio.boursosignal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(container: AppContainer, onOpen: (String) -> Unit, onPaper: () -> Unit) {
    val positions by container.portfolioRepository.observePositions().collectAsState(initial = emptyList())
    val signals by container.signalRepository.observeAll().collectAsState(initial = emptyList())
    val watchlist by container.watchlistRepository.observeWatchlist().collectAsState(initial = emptyList())

    val invested = positions.sumOf { it.quantity * it.avgPrice }

    Scaffold(topBar = { TopAppBar(title = { Text("BoursoSignal") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Investi", "%.0f €".format(invested), Modifier.weight(1f))
                StatCard("Positions", positions.size.toString(), Modifier.weight(1f))
                StatCard("Suivies", watchlist.size.toString(), Modifier.weight(1f))
            }

            Card(Modifier.fillMaxWidth().clickable { onPaper() }) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Science, contentDescription = null)
                    Column {
                        Text("Portefeuille test (mode auto)", fontWeight = FontWeight.SemiBold)
                        Text("Teste les signaux avec de l'argent fictif, sans risque.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text("Derniers signaux", fontWeight = FontWeight.SemiBold)
            if (signals.isEmpty()) {
                Text("Aucun signal récent.", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(signals.take(8), key = { it.id }) { s ->
                        val buy = s.type == "BUY"
                        Card(Modifier.fillMaxWidth().clickable { onOpen(s.ticker) }) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${if (buy) "▲" else "▼"} ${s.ticker}",
                                    color = if (buy) BullGreen else BearRed, fontWeight = FontWeight.SemiBold)
                                Text(s.date, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
    }
}
