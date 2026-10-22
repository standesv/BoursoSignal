package bzh.dargio.boursosignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.domain.signals.Indicators
import bzh.dargio.boursosignal.domain.signals.SignalEngine
import bzh.dargio.boursosignal.domain.signals.SignalResult
import bzh.dargio.boursosignal.domain.signals.StrategyConfig
import bzh.dargio.boursosignal.domain.signals.closes
import bzh.dargio.boursosignal.ui.components.CandleChart
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentDetailScreen(container: AppContainer, ticker: String, onBack: () -> Unit) {
    val candles by container.marketRepository.observeCandles(ticker).collectAsState(initial = emptyList())
    val signals by container.signalRepository.observeForTicker(ticker).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var manualResult by remember { mutableStateOf<SignalResult?>(null) }

    val config by produceState(initialValue = StrategyConfig(), ticker) {
        value = container.strategyRepository.activeConfig()
        if (container.marketRepository.cachedCandles(ticker).isEmpty()) {
            container.marketRepository.refresh(ticker)
        }
    }

    val closes = candles.closes()
    val emaFast = if (closes.isNotEmpty()) Indicators.ema(closes, config.emaFastPeriod) else null
    val emaSlow = if (closes.isNotEmpty()) Indicators.ema(closes, config.emaSlowPeriod) else null
    val rsi = if (closes.isNotEmpty()) Indicators.rsi(closes, config.rsiPeriod).lastOrNull { it != null } else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ticker) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val last = candles.lastOrNull()
            Text(
                last?.let { "%.2f €".format(it.close) } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            CandleChart(candles = candles, emaFast = emaFast, emaSlow = emaSlow)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Indicateurs", fontWeight = FontWeight.SemiBold)
                    Text("EMA${config.emaFastPeriod} : ${emaFast?.lastOrNull { it != null }?.let { "%.2f".format(it) } ?: "—"}")
                    Text("EMA${config.emaSlowPeriod} : ${emaSlow?.lastOrNull { it != null }?.let { "%.2f".format(it) } ?: "—"}")
                    Text("RSI${config.rsiPeriod} : ${rsi?.let { "%.1f".format(it) } ?: "—"}")
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        val avg = container.portfolioRepository.position(ticker)?.avgPrice
                        manualResult = SignalEngine(config).evaluate(container.marketRepository.cachedCandles(ticker), avg)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Évaluer maintenant") }

            manualResult?.let { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Résultat : ${r.type}", fontWeight = FontWeight.SemiBold)
                        if (r.reasons.isNotEmpty()) Text(r.reasons.joinToString(" · "))
                        else Text("Aucune condition déclenchée.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text("Historique des signaux", fontWeight = FontWeight.SemiBold)
            if (signals.isEmpty()) {
                Text("Aucun signal enregistré pour cette valeur.", style = MaterialTheme.typography.bodySmall)
            } else {
                signals.forEach { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${if (s.type == "BUY") "▲ Achat" else "▼ Vente"} · ${s.date}", fontWeight = FontWeight.Medium)
                            Text(s.reasons, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
