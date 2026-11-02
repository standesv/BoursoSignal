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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.domain.signals.Analyzer
import bzh.dargio.boursosignal.domain.signals.BuyRationale
import bzh.dargio.boursosignal.domain.signals.ExitAdvice
import bzh.dargio.boursosignal.domain.signals.ExitAdvisor
import bzh.dargio.boursosignal.domain.signals.NewsSentiment
import bzh.dargio.boursosignal.domain.signals.Opportunity
import bzh.dargio.boursosignal.domain.signals.RecoTone
import bzh.dargio.boursosignal.domain.signals.Recommender
import bzh.dargio.boursosignal.domain.signals.RiskLevel
import bzh.dargio.boursosignal.domain.signals.Stance
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen

private val AMBER = Color(0xFFF59E0B)
private val GREY = Color(0xFF6B7280)

/** Détention d'une valeur, réelle ou fictive (mode test). */
private data class HeldInfo(val avgPrice: Double, val source: String)

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
    val realPositions by container.portfolioRepository.observePositions().collectAsState(initial = emptyList())
    val paperPositions by container.paperRepository.observePositions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var added by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) { if (opportunities.isEmpty() && !scanning) scanner.scan() }

    // Valeurs détenues, réelles OU fictives. Le réel l'emporte si détenu dans les deux.
    val heldByTicker = remember(realPositions, paperPositions) {
        val m = HashMap<String, HeldInfo>()
        paperPositions.forEach { m[it.ticker] = HeldInfo(it.avgPrice, "test") }
        realPositions.forEach { m[it.ticker] = HeldInfo(it.avgPrice, "réel") }
        m
    }

    // Pour les valeurs détenues (réel ou test), conseil de gestion de position
    // (conserver/alléger/vendre) afin que l'avis des Idées soit cohérent avec le
    // portefeuille. Seules les valeurs détenues entraînent un calcul.
    val exitByTicker by produceState(initialValue = emptyMap<String, ExitAdvice>(), opportunities, realPositions, paperPositions) {
        if (heldByTicker.isEmpty()) { value = emptyMap(); return@produceState }
        val cfg = container.strategyRepository.activeConfig()
        val map = mutableMapOf<String, ExitAdvice>()
        opportunities.forEach { opp ->
            val held = heldByTicker[opp.ticker] ?: return@forEach
            val candles = container.marketRepository.cachedCandles(opp.ticker)
                .ifEmpty { container.marketRepository.refresh(opp.ticker).getOrNull() ?: emptyList() }
            if (candles.isNotEmpty()) map[opp.ticker] = ExitAdvisor.advise(opp.ticker, candles, held.avgPrice, cfg)
        }
        value = map
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

            Card(Modifier.fillMaxWidth().padding(12.dp).clickable { onPaper() }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Science, contentDescription = null)
                    Column {
                        Text("Portefeuille test", fontWeight = FontWeight.SemiBold)
                        Text("Suis tes positions fictives dans l'onglet Portefeuille (section Test).", style = MaterialTheme.typography.bodySmall)
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
                    Text("Aucune opportunité pour l'instant. Relance le scan avec l'icône ↻.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)

                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(opportunities, key = { it.ticker }) { opp ->
                        val displayName = scanner.displayName(opp.ticker)
                        OpportunityCard(
                            opp = opp,
                            name = displayName,
                            held = heldByTicker[opp.ticker],
                            exit = exitByTicker[opp.ticker],
                            isAdded = opp.ticker in added,
                            onOpen = { onOpen(opp.ticker) },
                            onAdd = { scope.launch { container.watchlistRepository.add(opp.ticker, displayName); added = added + opp.ticker } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpportunityCard(
    opp: Opportunity,
    name: String,
    held: HeldInfo?,
    exit: ExitAdvice?,
    isAdded: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
) {
    val reco = Recommender.of(opp.stance, exit)
    val color = toneColor(reco.tone)

    val explanation = when {
        reco.note != null -> reco.note!!
        opp.stance == Stance.BUY || opp.stance == Stance.WATCH -> BuyRationale.whyBuy(name, opp, NewsSentiment.NEUTRAL)
        else -> opp.reasons.firstOrNull() ?: ""
    }

    Card(Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text("${opp.ticker} · Risque ${Analyzer.riskLabel(opp.risk)}", style = MaterialTheme.typography.labelSmall, color = riskColor(opp.risk))
                if (held != null) {
                    val plPct = if (held.avgPrice > 0) (opp.price - held.avgPrice) / held.avgPrice * 100.0 else 0.0
                    Text(
                        "Déjà en portefeuille (${held.source}) · PRU ${"%.2f €".format(held.avgPrice)} · ${"%+.1f %%".format(plPct)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (plPct >= 0) BullGreen else BearRed,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (explanation.isNotBlank()) Text(explanation, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(reco.label, color = color, fontWeight = FontWeight.Bold)
                Text("%.2f €".format(opp.price), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = onAdd) {
                    if (isAdded) Icon(Icons.Filled.Check, "Suivie") else Icon(Icons.Filled.Add, "Suivre cette valeur")
                }
            }
        }
    }
}

private fun toneColor(tone: RecoTone): Color = when (tone) {
    RecoTone.POSITIVE -> BullGreen
    RecoTone.CAUTION -> AMBER
    RecoTone.NEGATIVE -> BearRed
    RecoTone.NEUTRAL -> GREY
}

private fun riskColor(r: RiskLevel): Color = when (r) {
    RiskLevel.LOW -> BullGreen; RiskLevel.MEDIUM -> AMBER; RiskLevel.HIGH -> BearRed
}
