package bzh.dargio.boursosignal.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.data.remote.NewsItem
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.domain.signals.BuyRationale
import bzh.dargio.boursosignal.domain.signals.Indicators
import bzh.dargio.boursosignal.domain.signals.NewsAnalyzer
import bzh.dargio.boursosignal.domain.signals.NewsSentiment
import bzh.dargio.boursosignal.domain.signals.SignalEngine
import bzh.dargio.boursosignal.domain.signals.SignalResult
import bzh.dargio.boursosignal.domain.signals.Stance
import bzh.dargio.boursosignal.domain.signals.StanceEvaluator
import bzh.dargio.boursosignal.domain.signals.StrategyConfig
import bzh.dargio.boursosignal.domain.signals.closes
import bzh.dargio.boursosignal.ui.components.CandleChart
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentDetailScreen(
    container: AppContainer,
    ticker: String,
    onBack: () -> Unit,
    onBacktest: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val candles by container.marketRepository.observeCandles(ticker).collectAsState(initial = emptyList())
    val signals by container.signalRepository.observeForTicker(ticker).collectAsState(initial = emptyList())
    val paperPositions by container.paperRepository.observePositions().collectAsState(initial = emptyList())
    var manualResult by remember { mutableStateOf<SignalResult?>(null) }
    var paperMsg by remember { mutableStateOf<String?>(null) }

    val name = remember(ticker) { container.marketScannerService.displayName(ticker).ifBlank { ticker } }

    val config by produceState(initialValue = StrategyConfig(), ticker) {
        value = container.strategyRepository.activeConfig()
        if (container.marketRepository.cachedCandles(ticker).isEmpty()) {
            container.marketRepository.refresh(ticker)
        }
    }

    // Actualités + sentiment indicatif.
    val news by produceState(initialValue = emptyList<NewsItem>(), name) {
        value = container.newsProvider.forQuery(name).getOrDefault(emptyList())
    }
    val sentiment = remember(news) { NewsAnalyzer.sentimentOf(news.map { it.title }).first }

    val closes = candles.closes()
    val emaFast = if (closes.isNotEmpty()) Indicators.ema(closes, config.emaFastPeriod) else null
    val emaSlow = if (closes.isNotEmpty()) Indicators.ema(closes, config.emaSlowPeriod) else null
    val rsi = if (closes.isNotEmpty()) Indicators.rsi(closes, config.rsiPeriod).lastOrNull { it != null } else null
    val opp = remember(candles, config) {
        if (candles.isNotEmpty()) StanceEvaluator.evaluate(ticker, candles, config) else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(last?.let { "%.2f €".format(it.close) } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                opp?.let {
                    val (label, color) = stanceLabel(it.stance)
                    Text(label, color = color, fontWeight = FontWeight.Bold)
                }
            }

            // Pourquoi acheter / éviter (analyse technique + actualité).
            opp?.let {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Notre analyse", fontWeight = FontWeight.SemiBold)
                        Text(BuyRationale.whyBuy(name, it, sentiment))
                        Text("Actualité récente : ${sentimentText(sentiment)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            CandleChart(candles = candles, emaFast = emaFast, emaSlow = emaSlow)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Indicateurs", fontWeight = FontWeight.SemiBold)
                    Text("EMA${config.emaFastPeriod} : ${emaFast?.lastOrNull { it != null }?.let { "%.2f".format(it) } ?: "—"}")
                    Text("EMA${config.emaSlowPeriod} : ${emaSlow?.lastOrNull { it != null }?.let { "%.2f".format(it) } ?: "—"}")
                    Text("RSI${config.rsiPeriod} : ${rsi?.let { "%.1f".format(it) } ?: "—"}")
                }
            }

            // Achat / vente en mode test (demande utilisateur).
            val heldInPaper = paperPositions.any { it.ticker == ticker }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val price = candles.lastOrNull()?.close ?: return@Button
                        scope.launch {
                            paperMsg = if (container.paperRepository.manualBuy(ticker, price))
                                "Acheté en mode test." else "Active le mode test (onglet Idées → Portefeuille test)."
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Acheter en test") }
                OutlinedButton(
                    onClick = {
                        val price = candles.lastOrNull()?.close ?: return@OutlinedButton
                        scope.launch {
                            paperMsg = if (container.paperRepository.manualSell(ticker, price))
                                "Vendu en mode test." else "Aucune position test sur cette valeur."
                        }
                    },
                    enabled = heldInPaper,
                    modifier = Modifier.weight(1f),
                ) { Text("Vendre en test") }
            }
            paperMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            OutlinedButton(onClick = { onBacktest(ticker) }, modifier = Modifier.fillMaxWidth()) {
                Text("Backtester cette stratégie")
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

            // Actualités cliquables.
            Text("Actualités", fontWeight = FontWeight.SemiBold)
            if (news.isEmpty()) {
                Text("Pas d'actualité récente trouvée.", style = MaterialTheme.typography.bodySmall)
            } else {
                news.forEach { item ->
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
                        },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyMedium)
                                if (item.publisher.isNotBlank()) Text(item.publisher, style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Ouvrir")
                        }
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

private fun stanceLabel(stance: Stance): Pair<String, Color> = when (stance) {
    Stance.BUY -> "Achat" to BullGreen
    Stance.WATCH -> "Surveiller" to Color(0xFFF59E0B)
    Stance.AVOID -> "Éviter" to BearRed
    Stance.NEUTRAL -> "Neutre" to Color(0xFF6B7280)
}

private fun sentimentText(s: NewsSentiment): String = when (s) {
    NewsSentiment.POSITIVE -> "plutôt positive"
    NewsSentiment.NEGATIVE -> "plutôt négative — prudence"
    NewsSentiment.NEUTRAL -> "neutre ou indéterminée"
}
