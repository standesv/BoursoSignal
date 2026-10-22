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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import kotlin.math.floor

private data class ChartRange(val label: String, val days: Int)
private val CHART_RANGES = listOf(
    ChartRange("1M", 21), ChartRange("3M", 63), ChartRange("6M", 126),
    ChartRange("1A", 252), ChartRange("Max", Int.MAX_VALUE),
)

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
    val paperAccount by container.paperRepository.observeAccount().collectAsState(initial = null)
    var manualResult by remember { mutableStateOf<SignalResult?>(null) }
    var paperMsg by remember { mutableStateOf<String?>(null) }
    var selectedRange by remember { mutableStateOf(CHART_RANGES[2]) }
    var orderMode by remember { mutableStateOf<String?>(null) } // "BUY" | "SELL" | null

    val name = remember(ticker) { container.marketScannerService.displayName(ticker).ifBlank { ticker } }

    val config by produceState(initialValue = StrategyConfig(), ticker) {
        value = container.strategyRepository.activeConfig()
        if (container.marketRepository.cachedCandles(ticker).isEmpty()) {
            container.marketRepository.refresh(ticker)
        }
    }

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

    val price = candles.lastOrNull()?.close ?: 0.0
    val cash = paperAccount?.cash ?: 0.0
    val enabled = paperAccount?.enabled == true
    val heldQty = paperPositions.firstOrNull { it.ticker == ticker }?.quantity ?: 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (price > 0) "%.2f €".format(price) else "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                opp?.let {
                    val (label, color) = stanceLabel(it.stance)
                    Text(label, color = color, fontWeight = FontWeight.Bold)
                }
            }

            opp?.let {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Notre analyse", fontWeight = FontWeight.SemiBold)
                        Text(BuyRationale.whyBuy(name, it, sentiment))
                        Text("Actualité récente : ${sentimentText(sentiment)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Sélecteur de période du graphique.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CHART_RANGES.forEach { r ->
                    FilterChip(selected = selectedRange == r, onClick = { selectedRange = r }, label = { Text(r.label) })
                }
            }
            CandleChart(
                candles = candles,
                maxCandles = if (selectedRange.days == Int.MAX_VALUE) candles.size.coerceAtLeast(1) else selectedRange.days,
                emaFast = emaFast,
                emaSlow = emaSlow,
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Indicateurs", fontWeight = FontWeight.SemiBold)
                    Text("EMA${config.emaFastPeriod} : ${emaFast?.lastOrNull { it != null }?.let { "%.2f".format(it) } ?: "—"}")
                    Text("EMA${config.emaSlowPeriod} : ${emaSlow?.lastOrNull { it != null }?.let { "%.2f".format(it) } ?: "—"}")
                    Text("RSI${config.rsiPeriod} : ${rsi?.let { "%.1f".format(it) } ?: "—"}")
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (!enabled) paperMsg = "Active le mode test (onglet Idées → Portefeuille test)."
                        else if (price > 0) orderMode = "BUY"
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Acheter en test") }
                OutlinedButton(
                    onClick = { if (price > 0) orderMode = "SELL" },
                    enabled = heldQty > 0,
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

            Text("Actualités", fontWeight = FontWeight.SemiBold)
            if (news.isEmpty()) {
                Text("Pas d'actualité récente trouvée.", style = MaterialTheme.typography.bodySmall)
            } else {
                news.forEach { item ->
                    Card(Modifier.fillMaxWidth().clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
                    }) {
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

        if (orderMode != null) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(onDismissRequest = { orderMode = null }, sheetState = sheetState) {
                OrderSheet(
                    isBuy = orderMode == "BUY",
                    name = name,
                    price = price,
                    cash = cash,
                    heldQty = heldQty,
                    onBuyShares = { n -> scope.launch { paperMsg = buyMsg(container.paperRepository.buyShares(ticker, price, n)); orderMode = null } },
                    onBuyAmount = { amt -> scope.launch { paperMsg = buyMsg(container.paperRepository.buyAmount(ticker, price, amt)); orderMode = null } },
                    onSellShares = { n -> scope.launch { paperMsg = sellMsg(container.paperRepository.sellShares(ticker, price, n)); orderMode = null } },
                    onSellAll = { scope.launch { paperMsg = sellMsg(container.paperRepository.sellAll(ticker, price)); orderMode = null } },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderSheet(
    isBuy: Boolean,
    name: String,
    price: Double,
    cash: Double,
    heldQty: Double,
    onBuyShares: (Int) -> Unit,
    onBuyAmount: (Double) -> Unit,
    onSellShares: (Int) -> Unit,
    onSellAll: () -> Unit,
) {
    var byAmount by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val maxShares = if (price > 0) floor(cash / price).toInt() else 0
    val heldInt = floor(heldQty).toInt()

    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${if (isBuy) "Acheter" else "Vendre"} $name (mode test)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text("Cours : %.2f €".format(price), style = MaterialTheme.typography.bodyMedium)
        if (isBuy) Text("Liquidités disponibles : %.2f €".format(cash), style = MaterialTheme.typography.bodySmall)
        else Text("Actions détenues : $heldInt", style = MaterialTheme.typography.bodySmall)

        if (isBuy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !byAmount, onClick = { byAmount = false; input = "" }, label = { Text("Quantité") })
                FilterChip(selected = byAmount, onClick = { byAmount = true; input = "" }, label = { Text("Montant €") })
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { v -> input = v.filter { it.isDigit() } },
            label = { Text(if (isBuy && byAmount) "Montant (€)" else "Quantité (actions)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        // Raccourcis.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isBuy && byAmount) {
                FilterChip(false, { input = (cash * 0.25).toInt().toString() }, { Text("25%") })
                FilterChip(false, { input = (cash * 0.5).toInt().toString() }, { Text("50%") })
                FilterChip(false, { input = cash.toInt().toString() }, { Text("Max") })
            } else if (isBuy) {
                FilterChip(false, { input = maxShares.toString() }, { Text("Max ($maxShares)") })
            } else {
                FilterChip(false, { input = (heldInt / 2).toString() }, { Text("50%") })
                FilterChip(false, { onSellAll() }, { Text("Tout vendre") })
            }
        }

        // Aperçu.
        val n = input.toIntOrNull() ?: 0
        val shares = if (isBuy && byAmount) (if (price > 0) floor(n / price).toInt() else 0) else n
        val estimate = shares * price
        val valid = if (isBuy) shares in 1..maxShares else shares in 1..heldInt
        Text(
            if (isBuy) "≈ $shares action(s) pour %.2f €".format(estimate)
            else "$shares action(s) pour %.2f €".format(estimate),
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            onClick = {
                if (isBuy && byAmount) onBuyAmount(n.toDouble())
                else if (isBuy) onBuyShares(n)
                else onSellShares(n)
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isBuy) "Confirmer l'achat" else "Confirmer la vente") }
    }
}

private fun buyMsg(n: Int): String = if (n > 0) "Acheté $n action(s) en mode test." else "Achat impossible (liquidités insuffisantes ou mode test désactivé)."
private fun sellMsg(n: Int): String = if (n > 0) "Vendu $n action(s) en mode test." else "Vente impossible."

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
