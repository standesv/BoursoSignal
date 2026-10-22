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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import bzh.dargio.boursosignal.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(container: AppContainer, onOpen: (String) -> Unit) {
    val vm: WatchlistViewModel = viewModel(
        factory = viewModelFactory { initializer { WatchlistViewModel(container) } }
    )
    val state by vm.uiState.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Valeurs suivies") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Rafraîchir")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter une valeur")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.rows, key = { it.ticker }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(row.ticker) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(row.ticker, fontWeight = FontWeight.SemiBold)
                            Text(row.name, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            row.signalLabel?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.labelMedium) }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(row.priceLabel)
                            Text(row.changeLabel)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var ticker by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = {
                TextButton(onClick = { vm.add(ticker, name); showAdd = false }) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Annuler") } },
            title = { Text("Ajouter une valeur") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(ticker, { ticker = it }, label = { Text("Ticker (ex. AIR.PA)") })
                    OutlinedTextField(name, { name = it }, label = { Text("Nom (ex. Airbus)") })
                }
            },
        )
    }
}
