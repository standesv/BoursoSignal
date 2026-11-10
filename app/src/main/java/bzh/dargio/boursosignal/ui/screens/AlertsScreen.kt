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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(container: AppContainer, onOpen: (String) -> Unit) {
    val signals by container.signalRepository.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alertes") },
                actions = {
                    if (signals.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, "Tout vider") }
                    }
                },
            )
        },
    ) { padding ->
        if (signals.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Aucune alerte pour l'instant.", style = MaterialTheme.typography.bodyMedium)
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
                    val stamp = runCatching {
                        Instant.ofEpochMilli(s.createdAt).atZone(ZoneId.of("Europe/Paris")).format(DATE_TIME)
                    }.getOrDefault(s.date)
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.weight(1f).clickable { onOpen(s.ticker) }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                            ) {
                                Text(
                                    "${if (buy) "▲ ACHAT" else "▼ VENTE"} · ${s.ticker}",
                                    color = if (buy) BullGreen else BearRed,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text("Reçue le $stamp", style = MaterialTheme.typography.labelSmall)
                                Text("Cours ${"%.2f €".format(s.price)} · séance ${s.date} · score ${"%.1f".format(s.score)}",
                                    style = MaterialTheme.typography.bodySmall)
                                Text(s.reasons, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { scope.launch { container.signalRepository.delete(s.id) } }) {
                                Icon(Icons.Filled.Delete, "Supprimer cette alerte")
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.signalRepository.clearAll() }
                    confirmClear = false
                }) { Text("Tout vider") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
            title = { Text("Vider les alertes ?") },
            text = { Text("Toutes les alertes seront effacées. Cela n'affecte ni tes positions ni tes portefeuilles.") },
        )
    }
}
