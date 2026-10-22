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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import bzh.dargio.boursosignal.data.local.PositionEntity
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.domain.signals.ExitAction
import bzh.dargio.boursosignal.domain.signals.ExitAdvice
import bzh.dargio.boursosignal.domain.signals.ExitAdvisor
import bzh.dargio.boursosignal.domain.signals.NewsAnalyzer
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen
import kotlinx.coroutines.launch
import java.time.LocalDate

private val AMBER = Color(0xFFF59E0B)

private data class PositionView(val position: PositionEntity, val price: Double?, val advice: ExitAdvice?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(container: AppContainer) {
    val positions by container.portfolioRepository.observePositions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Portefeuille") }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Saisir une transaction") } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                items(views, key = { it.position.ticker }) { v -> PositionCard(v) }
            }
        }
    }

    if (showAdd) {
        AddTransactionDialog(
            onDismiss = { showAdd = false },
            onConfirm = { ticker, type, qty, price, fees, date ->
                scope.launch { container.portfolioRepository.recordTransaction(ticker.uppercase(), type, qty, price, fees, date) }
                showAdd = false
            },
        )
    }
}

@Composable
private fun PositionCard(v: PositionView) {
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

private fun priority(a: ExitAction?): Int = when (a) {
    ExitAction.SELL -> 0; ExitAction.TRIM -> 1; ExitAction.HOLD -> 2; null -> 3
}

private fun adviceLabel(a: ExitAction): Pair<String, Color> = when (a) {
    ExitAction.SELL -> "Vendre" to BearRed
    ExitAction.TRIM -> "Alléger" to AMBER
    ExitAction.HOLD -> "Conserver" to BullGreen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (ticker: String, type: String, qty: Double, price: Double, fees: Double, date: String) -> Unit,
) {
    var ticker by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("BUY") }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var fees by remember { mutableStateOf("0") }
    val date = remember { LocalDate.now().toString() }

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
                OutlinedTextField(ticker, { ticker = it }, label = { Text("Ticker (ex. AIR.PA)") })
                OutlinedTextField(qty, { qty = it }, label = { Text("Quantité") })
                OutlinedTextField(price, { price = it }, label = { Text("Prix unitaire (€)") })
                OutlinedTextField(fees, { fees = it }, label = { Text("Frais (€)") })
            }
        },
    )
}
