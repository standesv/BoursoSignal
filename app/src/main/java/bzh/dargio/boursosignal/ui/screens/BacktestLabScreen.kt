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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import bzh.dargio.boursosignal.domain.signals.BacktestLab
import bzh.dargio.boursosignal.domain.signals.LabResult
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestLabScreen(container: AppContainer, onBack: () -> Unit) {
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<LabResult?>(null) }
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backtest de la stratégie") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Mesure objectivement ta stratégie sur tout l'univers : pour chaque valeur, on compare " +
                    "la performance de la stratégie à celle d'un simple achat-conservation sur l'historique. " +
                    "Lance d'abord un scan depuis l'onglet Idées pour disposer des données.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = {
                    scope.launch {
                        running = true
                        val outcome = withContext(Dispatchers.Default) {
                            val cfg = container.strategyRepository.activeConfig()
                            val entries = container.universeProvider.load()
                            names = entries.associate { it.ticker to it.name }
                            val data = entries.associate { it.ticker to container.marketRepository.cachedCandles(it.ticker) }
                                .filterValues { it.isNotEmpty() }
                            BacktestLab.run(data, cfg)
                        }
                        result = outcome
                        running = false
                    }
                },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (running) "Analyse en cours…" else "Lancer le backtest sur l'univers") }

            if (running) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            }

            result?.let { r ->
                if (r.count == 0) {
                    Text("Aucune donnée en cache. Lance un scan depuis l'onglet Idées, puis réessaie.",
                        style = MaterialTheme.typography.bodyMedium)
                    return@let
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Synthèse sur ${r.count} valeurs", style = MaterialTheme.typography.labelMedium)
                        MetricRow("Stratégie (moyenne)", "%+.1f %%".format(r.avgStrategyPct), r.avgStrategyPct >= 0)
                        MetricRow("Achat-conservation (moyenne)", "%+.1f %%".format(r.avgBuyHoldPct), r.avgBuyHoldPct >= 0)
                        HorizontalDivider()
                        Text(
                            "La stratégie bat l'achat-conservation sur ${r.beatCount} / ${r.count} valeurs.",
                            fontWeight = FontWeight.SemiBold,
                            color = if (r.beatCount * 2 >= r.count) BullGreen else BearRed,
                        )
                        Text(
                            if (r.avgStrategyPct >= r.avgBuyHoldPct)
                                "En moyenne, ta stratégie apporte un gain par rapport à l'achat-conservation."
                            else
                                "En moyenne, ta stratégie fait moins bien qu'un simple achat-conservation — il vaut mieux l'ajuster.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Text("Détail par valeur (meilleur écart d'abord)", fontWeight = FontWeight.SemiBold)
                r.perTicker.forEach { t ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(names[t.ticker] ?: t.ticker, fontWeight = FontWeight.Medium)
                                Text("${t.ticker} · ${t.trades} trade(s)", style = MaterialTheme.typography.labelSmall)
                                Text("Stratégie ${"%+.1f %%".format(t.strategyPct)} · Achat-cons. ${"%+.1f %%".format(t.buyHoldPct)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "%+.1f pts".format(t.edgePct),
                                color = if (t.edgePct >= 0) BullGreen else BearRed,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Text(
                    "Écart = stratégie − achat-conservation. Hypothèses : exécution à la clôture, 100 % investi / " +
                        "100 % liquide, sans frais. Les performances passées ne préjugent pas des résultats futurs.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, positive: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium, color = if (positive) BullGreen else BearRed)
    }
}
