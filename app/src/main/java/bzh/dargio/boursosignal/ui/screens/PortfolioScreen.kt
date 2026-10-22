package bzh.dargio.boursosignal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.data.local.PaperPositionEntity
import bzh.dargio.boursosignal.data.local.PositionEntity
import bzh.dargio.boursosignal.data.universe.UniverseEntry
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.domain.signals.ExitAction
import bzh.dargio.boursosignal.domain.signals.ExitAdvice
import bzh.dargio.boursosignal.domain.signals.ExitAdvisor
import bzh.dargio.boursosignal.domain.signals.NewsAnalyzer
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val AMBER = Color(0xFFF59E0B)

private enum class PortfolioMode { REAL, TEST }

private data class PositionView(val position: PositionEntity, val price: Double?, val advice: ExitAdvice?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(container: AppContainer) {
    var mode by remember { mutableStateOf(PortfolioMode.REAL) }
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Portefeuille") }) },
        floatingActionButton = {
            if (mode == PortfolioMode.REAL) {
                FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Saisir une transaction") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                SegmentedButton(
                    selected = mode == PortfolioMode.REAL,
                    onClick = { mode = PortfolioMode.REAL },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Réel") }
                SegmentedButton(
                    selected = mode == PortfolioMode.TEST,
                    onClick = { mode = PortfolioMode.TEST },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Test") }
            }

            when (mode) {
                PortfolioMode.REAL -> RealPortfolio(container)
                PortfolioMode.TEST -> TestPortfolio(container)
            }
        }
    }

    if (showAdd) {
        AddTransactionDialog(
            container = container,
            onDismiss = { showAdd = false },
            onConfirm = { ticker, type, qty, price, fees, date ->
                scope.launch { container.portfolioRepository.recordTransaction(ticker.uppercase(), type, qty, price, fees, date) }
                showAdd = false
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Portefeuille réel
// ---------------------------------------------------------------------------

@Composable
private fun RealPortfolio(container: AppContainer) {
    val positions by container.portfolioRepository.observePositions().collectAsState(initial = emptyList())

    val views by produceState(initialValue = emptyList<PositionView>(), positions) {
        val cfg = container.strategyRepository.activeConfig()
        value = positions.map { p ->
            val candles = container.marketRepository.refresh(p.ticker).getOrNull()
                ?: container.marketRepository.cachedCandles(p.ticker)
            val name = container.marketScannerService.displayName(p.ticker).ifBlank { p.ticker }
            val news = container.newsProvider.forQuery(name).getOrDefault(emptyList())
            val sentiment = NewsAnalyzer.sentimentOf(news.map { it.title }).first
            val advice = if (candles.isNotEmpty()) ExitAdvisor.advise(p.ticker, candles, p.avgPrice, cfg, sentiment) else null
            PositionView(p, candles.lastOrNull()?.close, advice)
        }.sortedBy { priority(it.advice?.action) }
    }

    val invested = views.sumOf { it.position.quantity * it.position.avgPrice }
    val value = views.sumOf { it.position.quantity * (it.price ?: it.position.avgPrice) }
    val pnl = value - invested

    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Valeur estimée", style = MaterialTheme.typography.labelMedium)
                Text("%.2f €".format(value), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                Text("P/L latent : %+.2f € (%+.1f %%)".format(pnl, if (invested > 0) pnl / invested * 100 else 0.0),
                    color = if (pnl >= 0) BullGreen else BearRed)
            }
        }
        if (views.isEmpty()) {
            Text("Aucune position. Saisis tes achats avec le bouton +, l'app te dira quand vendre.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        } else LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(views, key = { it.position.ticker }) { v -> RealPositionCard(v) }
        }
    }
}

@Composable
private fun RealPositionCard(v: PositionView) {
    val p = v.position
    val cur = v.price ?: p.avgPrice
    val pnlPct = if (p.avgPrice > 0) (cur - p.avgPrice) / p.avgPrice * 100 else 0.0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(p.ticker, fontWeight = FontWeight.SemiBold)
                    Text("Qté ${"%.0f".format(p.quantity)} · PRU ${"%.2f €".format(p.avgPrice)}", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.2f €".format(cur))
                    Text("%+.1f %%".format(pnlPct), color = if (pnlPct >= 0) BullGreen else BearRed)
                }
            }
            v.advice?.let { a ->
                val (label, color) = adviceLabel(a.action)
                Text("Conseil : $label", color = color, fontWeight = FontWeight.Bold)
                a.reasons.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Portefeuille test (paper trading)
// ---------------------------------------------------------------------------

@Composable
private fun TestPortfolio(container: AppContainer) {
    val paper = container.paperRepository
    val account by paper.observeAccount().collectAsState(initial = null)
    val positions by paper.observePositions().collectAsState(initial = emptyList())
    val trades by paper.observeTrades().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val acc = account
    if (acc == null || !acc.enabled) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mode test désactivé.", fontWeight = FontWeight.SemiBold)
            Text(
                "Active le portefeuille test dans l'onglet Réglages pour simuler des achats/ventes " +
                    "avec de l'argent fictif et suivre l'efficacité des signaux.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    // Rafraîchit les cours des positions + calcule un conseil de sortie (sans actualité, plus léger).
    val enriched by produceState(initialValue = emptyMap<String, Pair<Double, ExitAdvice?>>(), positions) {
        val cfg = container.strategyRepository.activeConfig()
        val map = mutableMapOf<String, Pair<Double, ExitAdvice?>>()
        positions.forEach { p ->
            val candles = container.marketRepository.refresh(p.ticker).getOrNull()
                ?: container.marketRepository.cachedCandles(p.ticker)
            val price = candles.lastOrNull()?.close ?: p.avgPrice
            val advice = if (candles.isNotEmpty()) ExitAdvisor.advise(p.ticker, candles, p.avgPrice, cfg) else null
            map[p.ticker] = price to advice
        }
        value = map
    }

    val invested = positions.sumOf { it.quantity * (enriched[it.ticker]?.first ?: it.avgPrice) }
    val equity = acc.cash + invested
    val returnPct = if (acc.initialCapital > 0) (equity - acc.initialCapital) / acc.initialCapital * 100 else 0.0

    var sellTarget by remember { mutableStateOf<PaperPositionEntity?>(null) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Valeur totale (TEST)", style = MaterialTheme.typography.labelMedium)
                Text("%.2f €".format(equity), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
            Text("Aucune position ouverte. Le mode auto agit sur signal, ou achète depuis la fiche d'une valeur.",
                style = MaterialTheme.typography.bodySmall)
        } else positions.forEach { p ->
            val (price, advice) = enriched[p.ticker] ?: (p.avgPrice to null)
            PaperPositionCard(p, price, advice, onSell = { sellTarget = p })
        }

        Text("Journal des opérations fictives", fontWeight = FontWeight.SemiBold)
        if (trades.isEmpty()) {
            Text("Aucune opération simulée pour l'instant.", style = MaterialTheme.typography.bodySmall)
        } else trades.forEach { t ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "${if (t.type == "BUY") "▲ Achat" else "▼ Vente"} ${"%.0f".format(t.quantity)} ${t.ticker} · " +
                            "${"%.2f".format(t.price)} € · ${t.date}",
                        fontWeight = FontWeight.Medium,
                    )
                    val sub = buildString {
                        if (t.fees > 0.0) append("Frais ${"%.2f".format(t.fees)} €")
                        if (t.reason.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(t.reason)
                        }
                    }
                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        HorizontalDivider()
        Text(
            "Variation calculée d'une séance à l'autre (données journalières). Frais de courtage " +
                "simulés selon le profil choisi dans les Réglages. Aucun argent réel, aucun lien avec Boursorama.",
            style = MaterialTheme.typography.labelSmall,
        )
    }

    sellTarget?.let { pos ->
        val price = enriched[pos.ticker]?.first ?: pos.avgPrice
        SellPaperDialog(
            ticker = pos.ticker,
            heldQty = pos.quantity,
            price = price,
            feeProfile = container.settings.activeFeeProfile(),
            onDismiss = { sellTarget = null },
            onConfirm = { qty ->
                scope.launch { container.paperRepository.sellShares(pos.ticker, price, qty) }
                sellTarget = null
            },
        )
    }
}

@Composable
private fun PaperPositionCard(p: PaperPositionEntity, current: Double, advice: ExitAdvice?, onSell: () -> Unit) {
    val plPct = if (p.avgPrice > 0) (current - p.avgPrice) / p.avgPrice * 100 else 0.0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(p.ticker, fontWeight = FontWeight.SemiBold)
                    Text("Qté ${"%.0f".format(p.quantity)} · PRU ${"%.2f €".format(p.avgPrice)}", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.2f €".format(current))
                    Text("%+.1f %%".format(plPct), color = if (plPct >= 0) BullGreen else BearRed)
                }
            }
            advice?.let { a ->
                val (label, color) = adviceLabel(a.action)
                Text("Conseil : $label", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onSell, modifier = Modifier.fillMaxWidth()) { Text("Vendre") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SellPaperDialog(
    ticker: String,
    heldQty: Double,
    price: Double,
    feeProfile: bzh.dargio.boursosignal.domain.signals.FeeProfile,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val held = kotlin.math.floor(heldQty).toInt().coerceAtLeast(1)
    var qtyText by remember { mutableStateOf(held.toString()) }
    val qty = (qtyText.toIntOrNull() ?: 0).coerceIn(0, held)
    val gross = qty * price
    val fee = feeProfile.feeFor(gross)
    val net = gross - fee

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { if (qty > 0) onConfirm(qty) }, enabled = qty > 0) { Text("Vendre") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        title = { Text("Vendre $ticker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Détenu : $held action(s) · cours ${"%.2f €".format(price)}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                    label = { Text("Quantité à vendre") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = false, onClick = { qtyText = (held / 2).coerceAtLeast(1).toString() }, label = { Text("Moitié") })
                    FilterChip(selected = false, onClick = { qtyText = held.toString() }, label = { Text("Tout") })
                }
                HorizontalDivider()
                LineItem("Montant brut", "%.2f €".format(gross))
                LineItem("Frais (${feeProfile.label})", "-%.2f €".format(fee))
                LineItem("Produit net estimé", "%.2f €".format(net))
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Ajout de transaction réelle (avec recherche de valeur)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
    onConfirm: (ticker: String, type: String, qty: Double, price: Double, fees: Double, date: String) -> Unit,
) {
    var ticker by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("BUY") }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var fees by remember { mutableStateOf("0") }
    var search by remember { mutableStateOf("") }
    val date = remember { LocalDate.now().toString() }

    val universe by produceState(initialValue = emptyList<UniverseEntry>()) {
        value = withContext(Dispatchers.IO) { container.universeProvider.load() }
    }
    val matches = remember(search, universe) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else universe.filter { it.name.lowercase().contains(q) || it.ticker.lowercase().contains(q) }.take(6)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(ticker, type, qty.toDoubleOrNull() ?: 0.0, price.toDoubleOrNull() ?: 0.0, fees.toDoubleOrNull() ?: 0.0, date)
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        title = { Text("Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "BUY", onClick = { type = "BUY" }, label = { Text("Achat") })
                    FilterChip(selected = type == "SELL", onClick = { type = "SELL" }, label = { Text("Vente") })
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Rechercher une valeur (nom ou code)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (matches.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                        matches.forEach { entry ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    ticker = entry.ticker
                                    search = entry.name
                                }.padding(vertical = 6.dp),
                            ) {
                                Column {
                                    Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(entry.ticker, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(ticker, { ticker = it }, label = { Text("Ticker (ex. AIR.PA)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(qty, { qty = it }, label = { Text("Quantité") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(price, { price = it }, label = { Text("Prix unitaire (€)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fees, { fees = it }, label = { Text("Frais (€)") }, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Helpers partagés
// ---------------------------------------------------------------------------

@Composable
private fun LineItem(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun priority(a: ExitAction?): Int = when (a) {
    ExitAction.SELL -> 0; ExitAction.TRIM -> 1; ExitAction.HOLD -> 2; null -> 3
}

private fun adviceLabel(a: ExitAction): Pair<String, Color> = when (a) {
    ExitAction.SELL -> "Vendre" to BearRed
    ExitAction.TRIM -> "Alléger" to AMBER
    ExitAction.HOLD -> "Conserver" to BullGreen
}
