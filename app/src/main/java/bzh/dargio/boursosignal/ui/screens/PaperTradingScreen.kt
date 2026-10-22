package bzh.dargio.boursosignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.data.local.PaperAccountEntity
import bzh.dargio.boursosignal.data.local.PaperPositionEntity
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperTradingScreen(container: AppContainer, onBack: () -> Unit) {
    val paper = container.paperRepository
    val account by paper.observeAccount().collectAsState(initial = null)
    val positions by paper.observePositions().collectAsState(initial = emptyList())
    val trades by paper.observeTrades().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val prices by produceState(initialValue = emptyMap<String, Double>(), positions) {
        val map = mutableMapOf<String, Double>()
        positions.forEach { p ->
            container.marketRepository.cachedCandles(p.ticker).lastOrNull()?.let { map[p.ticker] = it.close }
        }
        value = map
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Portefeuille test") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        val acc = account
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (acc == null || !acc.enabled) {
                InactiveState(existing = acc, onActivate = { capital -> scope.launch { paper.activate(capital) } })
                return@Column
            }

            val invested = positions.sumOf { it.quantity * (prices[it.ticker] ?: it.avgPrice) }
            val equity = acc.cash + invested
            val returnPct = if (acc.initialCapital > 0) (equity - acc.initialCapital) / acc.initialCapital * 100 else 0.0

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("TEST") }, colors = AssistChipDefaults.assistChipColors(labelColor = BullGreen))
                Text("Depuis le ${acc.startDate}", style = MaterialTheme.typography.bodySmall)
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Valeur totale", style = MaterialTheme.typography.labelMedium)
                    Text("%.2f €".format(equity), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("%+.2f € (%+.1f %%) vs capital de départ".format(equity - acc.initialCapital, returnPct),
                        color = if (returnPct >= 0) BullGreen else BearRed)
                    HorizontalDivider()
                    LineItem("Solde disponible (liquidités)", "%.2f €".format(acc.cash))
                    LineItem("Investi (valeur des positions)", "%.2f €".format(invested))
                    LineItem("Capital initial", "%.0f €".format(acc.initialCapital))
                }
            }

            Text("Positions fictives", fontWeight = FontWeight.SemiBold)
            if (positions.isEmpty()) {
                Text("Aucune position ouverte. Achète depuis la fiche d'une valeur, ou laisse le mode auto agir.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                positions.forEach { p -> PositionRow(p, prices[p.ticker]) }
            }

            Text("Journal des opérations fictives", fontWeight = FontWeight.SemiBold)
            if (trades.isEmpty()) {
                Text("Aucune opération simulée pour l'instant.", style = MaterialTheme.typography.bodySmall)
            } else {
                trades.forEach { t ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${if (t.type == "BUY") "▲ Achat" else "▼ Vente"} ${"%.0f".format(t.quantity)} ${t.ticker} · ${"%.2f".format(t.price)} € · ${t.date}",
                                fontWeight = FontWeight.Medium)
                            if (t.reason.isNotBlank()) Text(t.reason, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { scope.launch { paper.setEnabled(false) } }, modifier = Modifier.weight(1f)) { Text("Désactiver") }
                Button(onClick = { scope.launch { paper.reset(acc.initialCapital) } }, modifier = Modifier.weight(1f)) { Text("Réinitialiser") }
            }
            Text(
                "Mode automatique : à chaque signal, l'app passe un ordre fictif (nombre entier d'actions). " +
                    "Tu peux aussi acheter/vendre à la main depuis la fiche d'une valeur. Aucun argent réel, aucun lien avec Boursorama.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun LineItem(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PositionRow(p: PaperPositionEntity, current: Double?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(p.ticker, fontWeight = FontWeight.SemiBold)
                Text("Qté ${"%.0f".format(p.quantity)} · PRU ${"%.2f €".format(p.avgPrice)}", style = MaterialTheme.typography.bodySmall)
            }
            if (current != null) {
                val plPct = if (p.avgPrice > 0) (current - p.avgPrice) / p.avgPrice * 100 else 0.0
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.2f €".format(current))
                    Text("%+.1f %%".format(plPct), color = if (plPct >= 0) BullGreen else BearRed)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InactiveState(existing: PaperAccountEntity?, onActivate: (Double) -> Unit) {
    var capital by remember { mutableStateOf("10000") }
    Text("Mode test (portefeuille fictif)", fontWeight = FontWeight.SemiBold)
    Text(
        "Teste l'efficacité des signaux avec de l'argent fictif : l'app suit automatiquement ses propres " +
            "recommandations, et tu peux aussi acheter/vendre à la main. Sans aucun risque.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = capital,
        onValueChange = { capital = it.filter { c -> c.isDigit() } },
        label = { Text("Capital de départ (€)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onActivate(capital.toDoubleOrNull()?.coerceAtLeast(100.0) ?: 10_000.0) }, modifier = Modifier.fillMaxWidth()) {
        Text(if (existing == null) "Activer le mode test" else "Réactiver (remet à zéro)")
    }
    if (existing != null) {
        Text("Un portefeuille test existe (désactivé). Le réactiver repart d'un capital neuf.", style = MaterialTheme.typography.bodySmall)
    }
}
