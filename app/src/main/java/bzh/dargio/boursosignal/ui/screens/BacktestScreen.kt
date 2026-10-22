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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.domain.signals.BacktestResult
import bzh.dargio.boursosignal.domain.signals.Backtester
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(container: AppContainer, ticker: String, onBack: () -> Unit) {
    val result by produceState<BacktestResult?>(initialValue = null, ticker) {
        val config = container.strategyRepository.activeConfig()
        val candles = container.marketRepository.cachedCandles(ticker)
        value = withContext(Dispatchers.Default) { Backtester(config).run(candles) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backtest · $ticker") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
            )
        },
    ) { padding ->
        val r = result
        if (r == null) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Performance de la stratégie", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "%+.1f %%".format(r.totalReturnPct),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (r.totalReturnPct >= 0) BullGreen else BearRed,
                    )
                    HorizontalDivider()
                    MetricRow("Achat-conservation (référence)", "%+.1f %%".format(r.buyHoldReturnPct))
                    MetricRow("Nombre de trades", r.tradeCount.toString())
                    MetricRow("Taux de réussite", "%.0f %%".format(r.winRatePct))
                    MetricRow("Drawdown max", "-%.1f %%".format(r.maxDrawdownPct))
                }
            }

            Text(
                if (r.totalReturnPct >= r.buyHoldReturnPct)
                    "La stratégie fait mieux que l'achat-conservation sur cette période."
                else
                    "La stratégie fait moins bien que l'achat-conservation sur cette période.",
                style = MaterialTheme.typography.bodySmall,
            )

            Text("Opérations simulées", fontWeight = FontWeight.SemiBold)
            if (r.trades.isEmpty()) {
                Text("Aucune opération déclenchée sur l'historique disponible.", style = MaterialTheme.typography.bodySmall)
            } else {
                r.trades.forEach { t ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Achat ${"%.2f".format(t.entryPrice)} · ${t.entryDate}", style = MaterialTheme.typography.bodySmall)
                                Text("Vente ${"%.2f".format(t.exitPrice)} · ${t.exitDate}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "%+.1f %%".format(t.returnPct),
                                color = if (t.returnPct >= 0) BullGreen else BearRed,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Text(
                "Hypothèses : exécution à la clôture du signal, 100 % investi / 100 % liquide, sans frais. " +
                    "Les performances passées ne préjugent pas des résultats futurs.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
