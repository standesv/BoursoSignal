package bzh.dargio.boursosignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.domain.signals.FeeProfile
import bzh.dargio.boursosignal.domain.signals.Fees
import bzh.dargio.boursosignal.work.RefreshWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val settings = container.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var interval by remember { mutableStateOf(settings.refreshIntervalHours.toString()) }
    var notifications by remember { mutableStateOf(settings.notificationsEnabled) }
    var saved by remember { mutableStateOf(false) }

    // Mode test (paper trading).
    val paperAccount by container.paperRepository.observeAccount().collectAsState(initial = null)
    var capital by remember(paperAccount?.initialCapital) {
        mutableStateOf("%.0f".format(paperAccount?.initialCapital ?: 10_000.0))
    }
    var confirmReset by remember { mutableStateOf(false) }

    // Profils de frais (éditables).
    val profiles = remember { settings.allFeeProfiles() }
    var activeKey by remember { mutableStateOf(settings.paperFeeProfileKey) }
    val pctState = remember { mutableStateMapOf<String, String>().apply { profiles.forEach { put(it.key, trimNum(it.percent)) } } }
    val minState = remember { mutableStateMapOf<String, String>().apply { profiles.forEach { put(it.key, trimNum(it.minFee)) } } }
    var feesSaved by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Réglages") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Source de cours", fontWeight = FontWeight.SemiBold)
            Text("Yahoo Finance (gratuit, couvre Euronext Paris). Aucune clé requise.", style = MaterialTheme.typography.bodyMedium)

            HorizontalDivider()
            Text("Rafraîchissement", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = interval,
                onValueChange = { interval = it.filter { c -> c.isDigit() } },
                label = { Text("Intervalle (heures)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications")
                Switch(checked = notifications, onCheckedChange = { notifications = it })
            }
            Button(
                onClick = {
                    settings.refreshIntervalHours = interval.toIntOrNull()?.coerceIn(1, 168) ?: 12
                    settings.notificationsEnabled = notifications
                    RefreshWorker.schedule(context, settings.refreshIntervalHours)
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer") }
            if (saved) Text("Réglages enregistrés.", style = MaterialTheme.typography.bodySmall)

            // ---- Mode test ----
            HorizontalDivider()
            Text("Mode test (portefeuille fictif)", fontWeight = FontWeight.SemiBold)
            Text(
                "Teste l'efficacité des signaux avec de l'argent fictif : suivi automatique des " +
                    "recommandations, achats/ventes à la main, sans aucun risque. Les positions test " +
                    "apparaissent dans l'onglet Portefeuille.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Activer le portefeuille test")
                Switch(
                    checked = paperAccount?.enabled == true,
                    onCheckedChange = { on ->
                        scope.launch {
                            if (on) {
                                if (paperAccount == null) {
                                    container.paperRepository.activate(capital.toDoubleOrNull()?.coerceAtLeast(100.0) ?: 10_000.0)
                                } else {
                                    container.paperRepository.setEnabled(true)
                                }
                            } else {
                                container.paperRepository.setEnabled(false)
                            }
                        }
                    },
                )
            }
            OutlinedTextField(
                value = capital,
                onValueChange = { capital = it.filter { c -> c.isDigit() } },
                label = { Text("Capital de départ (€)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Réinitialiser le portefeuille test")
            }
            paperAccount?.let { acc ->
                Text(
                    if (acc.enabled) "Actif depuis le ${acc.startDate}." else "Portefeuille test désactivé.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // ---- Frais de courtage simulés ----
            HorizontalDivider()
            Text("Frais de courtage (simulation)", fontWeight = FontWeight.SemiBold)
            Text(
                "Appliqués aux ordres fictifs (achat et vente) pour refléter la tarification " +
                    "Boursorama. Modèle simplifié : frais = max(minimum, pourcentage × montant). " +
                    "Modifiable ci-dessous.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                profiles.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = activeKey == p.key,
                        onClick = { activeKey = p.key },
                        shape = SegmentedButtonDefaults.itemShape(index, profiles.size),
                    ) { Text(Fees.byKey(p.key).label) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pctState[activeKey].orEmpty(),
                    onValueChange = { pctState[activeKey] = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Pourcentage (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = minState[activeKey].orEmpty(),
                    onValueChange = { minState[activeKey] = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Frais min. (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = {
                    profiles.forEach { p ->
                        val pct = pctState[p.key]?.replace(',', '.')?.toDoubleOrNull() ?: p.percent
                        val min = minState[p.key]?.replace(',', '.')?.toDoubleOrNull() ?: p.minFee
                        settings.saveFeeProfile(FeeProfile(p.key, Fees.byKey(p.key).label, pct.coerceAtLeast(0.0), min.coerceAtLeast(0.0)))
                    }
                    settings.paperFeeProfileKey = activeKey
                    feesSaved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer les frais") }
            if (feesSaved) Text("Profil actif : ${Fees.byKey(activeKey).label}. Frais enregistrés.", style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Text("Avertissement", fontWeight = FontWeight.SemiBold)
            Text(
                "Outil personnel d'aide à la décision. Les signaux reposent sur des indicateurs " +
                    "techniques et ne constituent pas un conseil en investissement. Investir comporte un " +
                    "risque de perte en capital. Les ordres restent à passer manuellement dans Boursorama.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.paperRepository.reset(capital.toDoubleOrNull()?.coerceAtLeast(100.0) ?: 10_000.0) }
                    confirmReset = false
                }) { Text("Réinitialiser") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Annuler") } },
            title = { Text("Réinitialiser le portefeuille test ?") },
            text = { Text("Les positions et le journal fictifs seront effacés, et le portefeuille repartira du capital indiqué.") },
        )
    }
}

/** Formate un nombre sans décimales superflues (0.60 → "0.6", 1.99 → "1.99"). */
private fun trimNum(v: Double): String {
    val s = "%.2f".format(v).replace(',', '.')
    return s.trimEnd('0').trimEnd('.')
}
