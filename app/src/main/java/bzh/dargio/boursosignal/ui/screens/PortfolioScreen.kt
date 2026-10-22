package bzh.dargio.boursosignal.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.data.local.PositionEntity
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(container: AppContainer) {
    val positions by container.portfolioRepository.observePositions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }

    // Cours courant (dernière clôture en cache) par valeur, pour la +/- value latente.
    val prices by produceState(initialValue = emptyMap<String, Double>(), positions) {
        val map = mutableMapOf<String, Double>()
        positions.forEach { p ->
            container.marketRepository.cachedCandles(p.ticker).lastOrNull()?.let { map[p.ticker] = it.close }
        }
        value = map
    }

    val totalCost = positions.sumOf { it.quantity * it.avgPrice }
    val totalValue = positions.sumOf { it.quantity * (prices[it.ticker] ?: it.avgPrice) }
    val pnl = totalValue - totalCost

    Scaffold(
        topBar = { TopAppBar(title = { Text("Portefeuille") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Saisir une transaction")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Valeur estimée", style = MaterialTheme.typography.labelMedium)
                    Text("%.2f €".format(totalValue), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "P/L latent : %+.2f € (%+.1f %%)".format(pnl, if (totalCost > 0) pnl / totalCost * 100 else 0.0),
                        color = if (pnl >= 0) BullGreen else BearRed,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(positions, key = { it.ticker }) { p -> PositionCard(p, prices[p.ticker]) }
            }
        }
    }

    if (showAdd) {
        AddTransactionDialog(
            onDismiss = { showAdd = false },
            onConfirm = { ticker, type, qty, price, fees, date ->
                scope.launch {
                    container.portfolioRepository.recordTransaction(ticker.uppercase(), type, qty, price, fees, date)
                }
                showAdd = false
            },
        )
    }
}

@Composable
private fun PositionCard(p: PositionEntity, current: Double?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(p.ticker, fontWeight = FontWeight.SemiBold)
                Text("Qté ${"%.0f".format(p.quantity)} · PRU ${"%.2f €".format(p.avgPrice)}",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (current != null) {
                val pnlPct = if (p.avgPrice > 0) (current - p.avgPrice) / p.avgPrice * 100 else 0.0
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.2f €".format(current))
                    Text("%+.1f %%".format(pnlPct), color = if (pnlPct >= 0) BullGreen else BearRed)
                }
            }
        }
    }
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
                onConfirm(
                    ticker, type,
                    qty.toDoubleOrNull() ?: 0.0,
                    price.toDoubleOrNull() ?: 0.0,
                    fees.toDoubleOrNull() ?: 0.0,
                    date,
                )
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
