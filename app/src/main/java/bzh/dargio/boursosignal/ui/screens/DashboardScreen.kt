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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.domain.signals.Opportunity
import bzh.dargio.boursosignal.domain.signals.Stance
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    container: AppContainer,
    onOpen: (String) -> Unit,
    onPaper: () -> Unit,
    onSearch: () -> Unit,
) {
    val scanner = container.marketScannerService
    val opportunities by scanner.opportunities.collectAsState()
    val scanning by scanner.scanning.collectAsState()
    val scope = rememberCoroutineScope()

    // Lance un premier scan à l'ouverture si le classement est vide.
    LaunchedEffect(Unit) {
        if (opportunities.isEmpty() && !scanning) scanner.scan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Idées du jour") },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Rechercher une valeur") }
                    IconButton(onClick = { scope.launch { scanner.scan() } }) { Icon(Icons.Filled.Refresh, "Relancer le scan") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())

            Card(
                Modifier.fillMaxWidth().padding(12.dp).clickable { onPaper() },
            ) {
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

            when {
                opportunities.isEmpty() && scanning ->
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Analyse de l'univers Euronext Paris…", style = MaterialTheme.typography.bodySmall)
                    }

                opportunities.isEmpty() ->
                    Text("Aucune opportunité pour l'instant. Relance le scan avec l'icône ↻.",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(opportunities, key = { it.ticker }) { opp ->
                        OpportunityCard(opp, scanner.displayName(opp.ticker)) { onOpen(opp.ticker) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpportunityCard(opp: Opportunity, name: String, onClick: () -> Unit) {
    val (label, color) = stanceLabel(opp.stance)
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(opp.ticker, style = MaterialTheme.typography.labelSmall)
                opp.reasons.firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(label, color = color, fontWeight = FontWeight.Bold)
                Text("%.2f €".format(opp.price), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun stanceLabel(stance: Stance): Pair<String, Color> = when (stance) {
    Stance.BUY -> "Achat" to BullGreen
    Stance.WATCH -> "Surveiller" to Color(0xFFF59E0B)
    Stance.AVOID -> "Éviter" to BearRed
    Stance.NEUTRAL -> "Neutre" to Color(0xFF6B7280)
}
