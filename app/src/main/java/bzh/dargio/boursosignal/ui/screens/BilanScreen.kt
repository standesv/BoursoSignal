package bzh.dargio.boursosignal.ui.screens

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.data.repo.toRecord
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.domain.signals.AdviceEvaluator
import bzh.dargio.boursosignal.domain.signals.AdviceStats
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen
import bzh.dargio.boursosignal.work.PositionAlerts
import bzh.dargio.boursosignal.work.WeeklyDigest
import bzh.dargio.boursosignal.work.WeeklyDigestData
import java.util.Locale

/**
 * Écran Bilan (§23-1, §23-8) : la semaine écoulée, puis la réussite mesurée des
 * conseils de l'app avec les vrais cours (5 et 20 séances après chaque conseil).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilanScreen(container: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val entities by container.adviceRepository.observeAll().collectAsState(initial = emptyList())
    var evaluated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { container.adviceRepository.evaluatePending(container.marketRepository) }
        evaluated = true
    }
    val digest by produceState<WeeklyDigestData?>(initialValue = null, evaluated) {
        value = runCatching { WeeklyDigest.compute(container) }.getOrNull()
    }
    val records = remember(entities) { entities.map { it.toRecord() } }
    val byKind = remember(records) { AdviceEvaluator.statsByKind(records) }
    val byRegime = remember(records) { AdviceEvaluator.buyStatsByRegime(records) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bilan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- La semaine (§23-8) ----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cette semaine (5 dernières séances)", fontWeight = FontWeight.SemiBold)
                    val d = digest
                    if (d == null) Text("Calcul en cours…", style = MaterialTheme.typography.bodySmall)
                    else {
                        d.test?.let { WeekLine("Positions test", it.changeEuros, it.changePct) }
                        d.real?.let { WeekLine("Positions réelles", it.changeEuros, it.changePct) }
                        d.cacPct?.let { WeekLine("CAC 40", null, it) }
                        val counts = listOf("BUY", "SELL", "TRIM").mapNotNull { k ->
                            d.adviceCounts[k]?.let { "${AdviceEvaluator.kindLabel(k)} : $it" }
                        }
                        Text(
                            if (counts.isEmpty()) "Aucun conseil cette semaine." else "Conseils de la semaine — ${counts.joinToString(" · ")}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (d.attention.isNotEmpty()) {
                            Text("À surveiller : ${d.attention.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = BearRed)
                        }
                        Text(
                            "Variation des positions détenues aujourd'hui, sur 5 séances (hors achats et ventes de la semaine).",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            // ---- Réussite des conseils (§23-1) ----
            Text("Réussite des conseils", fontWeight = FontWeight.SemiBold)
            Text(
                "Mesurée avec les vrais cours : un achat est juste si le cours a monté 5 séances plus tard ; " +
                    "une vente ou un allègement est juste si le cours a baissé ensuite (perte évitée).",
                style = MaterialTheme.typography.bodySmall,
            )
            if (records.isEmpty()) {
                Text("Aucun conseil enregistré pour l'instant : le bilan se remplit à chaque scan.", style = MaterialTheme.typography.bodySmall)
            }
            byKind.forEach { StatsCard(it) }
            if (byRegime.size > 1) {
                Text("Achats selon la météo du marché", fontWeight = FontWeight.SemiBold)
                byRegime.forEach { StatsCard(it) }
            }

            // ---- Derniers conseils ----
            if (entities.isNotEmpty()) Text("Derniers conseils", fontWeight = FontWeight.SemiBold)
            entities.take(40).forEach { e ->
                val origin = when (e.portfolio) { "reel" -> "réel"; "test" -> "test"; else -> "idée" }
                Card(Modifier.fillMaxWidth().clickable { onOpen(e.ticker) }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${AdviceEvaluator.kindLabel(e.kind)} · ${PositionAlerts.displayName(container, e.ticker)}",
                            fontWeight = FontWeight.Medium,
                        )
                        Text("${frDate(e.date)} · ${eur(e.price)} · $origin", style = MaterialTheme.typography.labelSmall)
                        Text(
                            listOf(
                                e.ret5?.let { "5 séances : ${pctSigned(it)}" } ?: "5 séances : en attente",
                                e.ret20?.let { "20 séances : ${pctSigned(it)}" } ?: "20 séances : en attente",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = e.ret5?.let { if (AdviceEvaluator.isSuccess(e.kind, it)) BullGreen else BearRed } ?: Color.Unspecified,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekLine(label: String, euros: Double?, pct: Double?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        val value = listOfNotNull(euros?.let { WeeklyDigest.eurSigned(it) }, pct?.let { pctSigned(it) }).joinToString(" · ")
        Text(value, fontWeight = FontWeight.Medium, color = if ((pct ?: euros ?: 0.0) >= 0) BullGreen else BearRed)
    }
}

@Composable
private fun StatsCard(s: AdviceStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.label, fontWeight = FontWeight.Medium)
                Text(
                    s.hitRatePct?.let { String.format(Locale.FRANCE, "%.0f %% de réussite", it) } ?: "pas encore mesuré",
                    fontWeight = FontWeight.Bold,
                    color = s.hitRatePct?.let { if (it >= 50.0) BullGreen else BearRed } ?: Color.Unspecified,
                )
            }
            Text("${s.count} conseil${if (s.count > 1) "s" else ""} · ${s.evaluated} mesuré${if (s.evaluated > 1) "s" else ""} à 5 séances",
                style = MaterialTheme.typography.labelSmall)
            val moves = listOfNotNull(
                s.avgMove5Pct?.let { "cours moyen à 5 séances : ${pctSigned(it)}" },
                s.avgMove20Pct?.let { "à 20 séances : ${pctSigned(it)}" },
            )
            if (moves.isNotEmpty()) Text(moves.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}
