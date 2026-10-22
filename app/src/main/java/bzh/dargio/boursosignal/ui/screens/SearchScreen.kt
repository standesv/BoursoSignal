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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import bzh.dargio.boursosignal.data.universe.UniverseEntry
import bzh.dargio.boursosignal.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(container: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var added by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    val universe by produceState(initialValue = emptyList<UniverseEntry>()) {
        value = withContext(Dispatchers.IO) { container.universeProvider.load() }
    }

    val results = remember(query, universe) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) universe
        else universe.filter { it.name.lowercase().contains(q) || it.ticker.lowercase().contains(q) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rechercher une valeur") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Nom ou code (ex. Air Liquide, AIR.PA)") },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(results, key = { it.ticker }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(entry.ticker) }.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, fontWeight = FontWeight.Medium)
                            Text(entry.ticker, style = MaterialTheme.typography.labelSmall)
                        }
                        val isAdded = entry.ticker in added
                        IconButton(onClick = {
                            scope.launch {
                                container.watchlistRepository.add(entry.ticker, entry.name)
                                added = added + entry.ticker
                            }
                        }) {
                            if (isAdded) Icon(Icons.Filled.Check, "Ajoutée")
                            else Icon(Icons.Filled.Add, "Ajouter à ma watchlist")
                        }
                    }
                }
            }
        }
    }
}
