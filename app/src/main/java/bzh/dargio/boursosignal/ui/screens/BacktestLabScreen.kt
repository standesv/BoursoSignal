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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.domain.signals.MarketRegimeEvaluator
import bzh.dargio.boursosignal.domain.signals.RealisticBacktestLab
import bzh.dargio.boursosignal.domain.signals.RealisticLabResult
import bzh.dargio.boursosignal.ui.theme.BearRed
import bzh.dargio.boursosignal.ui.theme.BullGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestLabScreen(container: AppContainer, onOpen: (String) -> Unit, onBack: () -> Unit) {
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RealisticLabResult?>(null) }
    var noFilter by remember { mutableStateOf<RealisticLabResult?>(null) }
    var feeLabel by remember { mutableStateOf("") }
    var hasIndex by remember { mutableStateOf(true) }
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backtest de la stratégie") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Ce backtest rejoue, jour après jour, les conseils que l'app t'aurait donnés : achat quand Idées " +
                    "dit « Achat », vente totale sur « Vendre », vente de la moitié sur « Alléger ». Comme dans la " +
                    "vraie vie, chaque ordre est passé à l'ouverture de la séance suivante et paie les frais de ton " +
                    "profil Boursorama. Il compare le résultat à un simple achat-conservation. Lance d'abord un scan " +
                    "depuis l'onglet Idées pour disposer des données.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = {
                    scope.launch {
                        running = true
                        val outcome = withContext(Dispatchers.Default) {
                            val cfg = container.strategyRepository.activeConfig()
                            val entries = container.universeProvider.load()
                            names = entries.associate { it.ticker to it.name }
                            val data = entries.associate { it.ticker to container.marketRepository.cachedCandles(it.ticker) }
                                .filterValues { it.isNotEmpty() }
                            // Rejoue la météo du marché jour par jour, puis compare avec / sans filtre.
                            val index = container.marketRepository.cachedCandles(MarketRegimeEvaluator.INDEX_TICKER)
                            val fee = container.settings.activeFeeProfile()
                            feeLabel = fee.label
                            Triple(
                                RealisticBacktestLab.run(data, cfg, index, fee),
                                RealisticBacktestLab.run(data, cfg.copy(useMarketFilter = false), index, fee),
                                index.size >= MarketRegimeEvaluator.MIN_HISTORY,
                            )
                        }
                        result = outcome.first
                        noFilter = outcome.second
                        hasIndex = outcome.third
                        running = false
                    }
                },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (running) "Analyse en cours…" else "Lancer le backtest sur l'univers") }

            if (running) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            }

            result?.let { r ->
                if (r.count == 0) {
                    Text("Aucune donnée en cache. Lance un scan depuis l'onglet Idées, puis réessaie.",
                        style = MaterialTheme.typography.bodyMedium)
                    return@let
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Synthèse sur ${r.count} valeurs", style = MaterialTheme.typography.labelMedium)
                        MetricRow("Stratégie (moyenne)", "%+.1f %%".format(r.avgStrategyPct), r.avgStrategyPct >= 0)
                        MetricRow("Achat-conservation (moyenne)", "%+.1f %%".format(r.avgBuyHoldPct), r.avgBuyHoldPct >= 0)
                        noFilter?.let { nf ->
                            MetricRow("Stratégie sans filtre marché", "%+.1f %%".format(nf.avgStrategyPct), nf.avgStrategyPct >= 0)
                            val gain = r.avgStrategyPct - nf.avgStrategyPct
                            Text(
                                when {
                                    !hasIndex -> "Filtre marché non mesuré : historique du CAC 40 absent du cache (lance un scan depuis Idées)."
                                    kotlin.math.abs(gain) < 0.05 -> "Le filtre « météo du marché » n'a rien changé sur la période : le CAC 40 n'y a pas connu de vraie phase baissière."
                                    gain > 0 -> "Le filtre « météo du marché » améliore la moyenne de %+.1f pts : il a évité des achats juste avant des baisses du marché.".format(gain)
                                    else -> "Sur cette période, le filtre « météo du marché » coûte %.1f pts : il a fait rater quelques rebonds. C'est le prix d'une protection contre les retournements.".format(gain)
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        HorizontalDivider()
                        Text("Résultat sur deux périodes (la stratégie tient-elle sur des marchés différents ?)",
                            style = MaterialTheme.typography.labelMedium)
                        MetricRow("1re moitié · stratégie / achat-cons.",
                            "%+.1f %% / %+.1f %%".format(r.avgFirstHalfPct, r.avgBhFirstHalfPct), r.avgFirstHalfPct >= r.avgBhFirstHalfPct)
                        MetricRow("2de moitié · stratégie / achat-cons.",
                            "%+.1f %% / %+.1f %%".format(r.avgSecondHalfPct, r.avgBhSecondHalfPct), r.avgSecondHalfPct >= r.avgBhSecondHalfPct)
                        Text(
                            "${r.totalTrades} achats simulés · frais payés : %.0f € (profil $feeLabel, 2 000 € par valeur).".format(r.totalFeesEuros),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        HorizontalDivider()
                        Text(
                            "La stratégie bat l'achat-conservation sur ${r.beatCount} / ${r.count} valeurs.",
                            fontWeight = FontWeight.SemiBold,
                            color = if (r.beatCount * 2 >= r.count) BullGreen else BearRed,
                        )
                        Text(
                            if (r.avgStrategyPct >= r.avgBuyHoldPct)
                                "En moyenne, ta stratégie gagne plus qu'un simple achat-conservation sur la période."
                            else
                                "En moyenne, ta stratégie gagne moins qu'un achat-conservation. C'est fréquent et pas forcément un défaut : sa force est surtout défensive — elle sort avant les fortes baisses. Le détail ci-dessous, trié par écart décroissant, montre en haut les valeurs où elle t'a évité le plus de pertes.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Comment lire : « Stratégie » = gain/perte en suivant les conseils de l'app, frais déduits ; " +
                                "« Achat-cons. » = gain/perte si tu avais seulement gardé la valeur ; " +
                                "« écart » = différence (positif = la stratégie a fait mieux).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Text("Détail par valeur — performances passées", fontWeight = FontWeight.SemiBold)
                Text(
                    "Ce n'est pas une liste d'achat : ce sont des résultats historiques. Touche une valeur " +
                        "pour ouvrir sa fiche et voir le conseil du jour. Les idées d'achat du moment sont " +
                        "dans l'onglet Idées.",
                    style = MaterialTheme.typography.bodySmall,
                )
                r.perTicker.forEach { t ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(t.ticker) }) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(names[t.ticker] ?: t.ticker, fontWeight = FontWeight.Medium)
                                Text("${t.ticker} · ${t.trades} trade(s)", style = MaterialTheme.typography.labelSmall)
                                Text("Stratégie ${"%+.1f %%".format(t.strategyPct)} · Achat-cons. ${"%+.1f %%".format(t.buyHoldPct)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "%+.1f pts".format(t.edgePct),
                                color = if (t.edgePct >= 0) BullGreen else BearRed,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Text(
                    "Écart = stratégie − achat-conservation. Hypothèses : 2 000 € par valeur, ordres à l'ouverture " +
                        "de la séance suivante, frais du profil actif ; météo du marché et stop suiveur rejoués jour " +
                        "par jour sans regarder le futur. Non simulés : actualité, force relative et secteurs, " +
                        "calendrier des résultats. Les performances passées ne préjugent pas des résultats futurs.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, positive: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium, color = if (positive) BullGreen else BearRed)
    }
}
