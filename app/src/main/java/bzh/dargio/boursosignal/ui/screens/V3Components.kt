package bzh.dargio.boursosignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.domain.signals.CompanyEvents
import bzh.dargio.boursosignal.domain.signals.Diversification
import bzh.dargio.boursosignal.domain.signals.Opportunity
import bzh.dargio.boursosignal.domain.signals.PositionSize
import bzh.dargio.boursosignal.domain.signals.Stance
import bzh.dargio.boursosignal.domain.signals.StrategyConfig
import bzh.dargio.boursosignal.domain.signals.TradePlan
import bzh.dargio.boursosignal.domain.signals.TradePlanner
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen
import bzh.dargio.boursosignal.work.PortfolioSnapshot
import bzh.dargio.boursosignal.work.PortfolioSnapshots
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

private val WARN = Color(0xFFB45309)

internal fun eur(v: Double): String = String.format(Locale.FRANCE, "%.2f €", v)
internal fun pctSigned(v: Double): String = String.format(Locale.FRANCE, "%+.1f %%", v)
internal fun frDate(iso: String): String =
    runCatching { LocalDate.parse(iso).let { "%02d/%02d/%d".format(it.dayOfMonth, it.monthValue, it.year) } }.getOrDefault(iso)

/** « Éligible PEA · Secteur X » (§23-7, §23-11). */
internal fun peaSectorText(pea: Boolean?, sector: String?): String = listOfNotNull(
    when (pea) {
        true -> "Éligible PEA"
        false -> "Hors PEA (foncière SIIC)"
        null -> null
    },
    sector?.let { "Secteur : $it" },
).joinToString(" · ")

@Composable
private fun V3Line(label: String, value: String, color: Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (color != null) Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = color)
        else Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

/** Plan chiffré d'un achat (§23-3) + quantité conseillée + avertissements de diversification. */
@Composable
internal fun TradePlanBlock(
    plan: TradePlan,
    stance: Stance,
    size: PositionSize?,
    portfolioLabel: String?,
    warnings: List<String>,
    config: StrategyConfig,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (stance == Stance.BUY) "Plan si tu achètes" else "Plan indicatif (pas d'achat conseillé pour l'instant)",
            fontWeight = FontWeight.SemiBold,
        )
        V3Line("Entrée (dernier cours)", eur(plan.entry))
        V3Line("Stop : vente conseillée sous", "${eur(plan.stop)} (${pctSigned((plan.stop / plan.entry - 1) * 100)})", BearRed)
        V3Line(
            if (plan.resistance != null && plan.target == plan.resistance) "Objectif (résistance)" else "Objectif",
            "${eur(plan.target)} (${pctSigned((plan.target / plan.entry - 1) * 100)})",
            BullGreen,
        )
        V3Line("Rapport gain / risque", String.format(Locale.FRANCE, "%.1f", plan.rewardRisk))
        if (size != null && portfolioLabel != null) {
            val limited = size.limitedBy?.let { " — limitée par : $it" } ?: ""
            Text(
                if (size.quantity > 0)
                    "Quantité conseillée ($portfolioLabel) : ${size.quantity} action${if (size.quantity > 1) "s" else ""} ≈ ${eur(size.amount)} ; " +
                        "perte si le stop est touché ≈ ${eur(size.riskEuros)} (${String.format(Locale.FRANCE, "%.0f", config.riskPerTradePct)} % du capital)$limited."
                else "Quantité conseillée ($portfolioLabel) : aucune action$limited.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                "Active le portefeuille test ou renseigne tes liquidités réelles pour obtenir une quantité conseillée.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = WARN) }
    }
}

/** Avertissements de diversification d'un portefeuille (§23-6). Rien n'est affiché s'il est équilibré. */
@Composable
internal fun DiversificationCard(warnings: List<String>) {
    if (warnings.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Diversification", fontWeight = FontWeight.SemiBold, color = WARN)
            warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/**
 * Compléments de la fiche valeur (v3) : PEA et secteur, plan d'achat chiffré,
 * calendrier des résultats et dividendes, alertes de prix.
 */
@Composable
internal fun InstrumentExtras(
    container: AppContainer,
    ticker: String,
    name: String,
    opp: Opportunity?,
    config: StrategyConfig,
    onSuggestedQuantity: (Int?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scanner = container.marketScannerService
    val sector = remember(ticker) { scanner.sectorOf(ticker) }
    val pea = remember(ticker) { scanner.isPea(ticker) }

    val tags = peaSectorText(pea, sector)
    if (tags.isNotBlank()) Text(tags, style = MaterialTheme.typography.bodySmall, color = if (pea == false) WARN else Color.Unspecified)

    // ---- Plan d'achat (§23-3, §23-6) ----
    val snapshot by produceState<PortfolioSnapshot?>(initialValue = null, ticker) {
        value = PortfolioSnapshots.test(container) ?: PortfolioSnapshots.real(container)
    }
    val plan = opp?.plan
    if (opp != null && plan != null && (opp.stance == Stance.BUY || opp.stance == Stance.WATCH)) {
        val snap = snapshot
        val size = snap?.let { TradePlanner.size(plan, it.total, it.cash, config) }
        LaunchedEffect(size?.quantity) { onSuggestedQuantity(size?.quantity?.takeIf { it > 0 }) }
        val warnings = if (snap != null && size != null && size.quantity > 0) {
            val totalAfter = snap.total + (if (snap.cash == null) size.amount else 0.0)
            Diversification.warningsForBuy(snap.holdings, totalAfter, ticker, name, sector, size.amount, config)
        } else emptyList()
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                TradePlanBlock(
                    plan = plan, stance = opp.stance, size = size,
                    portfolioLabel = snap?.let { if (it.initialCapital != null) "portefeuille test" else "portefeuille réel" },
                    warnings = warnings, config = config,
                )
            }
        }
    }

    // ---- Calendrier (§23-4) ----
    val events by produceState<Pair<Boolean, CompanyEvents?>>(initialValue = false to null, ticker) {
        value = true to container.calendarRepository.events(ticker)
    }
    val (loaded, ev) = events
    val today = remember { LocalDate.now().toString() }
    Text(
        when {
            !loaded -> "Calendrier : chargement…"
            ev == null -> "Calendrier des résultats indisponible pour le moment (source Yahoo)."
            else -> listOf(
                ev.earningsDate?.let { "Prochains résultats : ${frDate(it)}" } ?: "Pas de date de résultats annoncée",
                ev.exDividendDate?.let { d ->
                    if (d >= today) "Détachement du dividende : ${frDate(d)}" else "Dernier détachement du dividende : ${frDate(d)}"
                },
            ).filterNotNull().joinToString(" · ")
        },
        style = MaterialTheme.typography.bodySmall,
    )

    // ---- Alertes de prix (§23-5) ----
    val alerts by container.priceAlertRepository.observeFor(ticker).collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    Text("Alertes de prix", fontWeight = FontWeight.SemiBold)
    if (alerts.isEmpty()) Text("Aucune alerte sur cette valeur.", style = MaterialTheme.typography.bodySmall)
    alerts.forEach { a ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${if (a.above) "Au-dessus de" else "En dessous de"} ${eur(a.level)}" +
                    if (a.active) " · active" else " · déclenchée${a.triggeredPrice?.let { " à ${eur(it)}" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { container.priceAlertRepository.delete(a.id) } }) {
                Icon(Icons.Filled.Delete, contentDescription = "Supprimer l'alerte")
            }
        }
    }
    OutlinedButton(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Créer une alerte de prix") }

    if (showDialog) {
        val lastPrice = opp?.price ?: 0.0
        var above by remember { mutableStateOf(true) }
        var level by remember { mutableStateOf(if (lastPrice > 0) String.format(Locale.US, "%.2f", lastPrice) else "") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Alerte de prix · $name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = above, onClick = { above = true }, label = { Text("Au-dessus de") })
                        FilterChip(selected = !above, onClick = { above = false }, label = { Text("En dessous de") })
                    }
                    OutlinedTextField(
                        value = level,
                        onValueChange = { v -> level = v.filter { it.isDigit() || it == '.' || it == ',' } },
                        label = { Text("Prix (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Text("Une seule notification au franchissement, pendant la journée ; l'alerte se désactive ensuite.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = level.replace(',', '.').toDoubleOrNull()
                    if (value != null && value > 0.0) scope.launch { container.priceAlertRepository.add(ticker, above, value) }
                    showDialog = false
                }) { Text("Créer") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Annuler") } },
        )
    }
}
