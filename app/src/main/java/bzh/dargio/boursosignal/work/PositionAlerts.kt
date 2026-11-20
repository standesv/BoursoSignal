package bzh.dargio.boursosignal.work

import android.content.Context
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.domain.signals.Candle
import bzh.dargio.boursosignal.domain.signals.ExitAction
import bzh.dargio.boursosignal.domain.signals.ExitAdvice
import bzh.dargio.boursosignal.domain.signals.ExitAdvisor
import bzh.dargio.boursosignal.domain.signals.MarketRegimeEvaluator
import bzh.dargio.boursosignal.domain.signals.NewsAnalyzer
import bzh.dargio.boursosignal.domain.signals.NewsSentiment
import bzh.dargio.boursosignal.domain.signals.Regime
import bzh.dargio.boursosignal.domain.signals.StrategyConfig
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Conseils et alertes sur les positions détenues (réel + test), PARTAGÉS par les
 * écrans et les tâches de fond. Mêmes entrées partout — cours, stratégie, actualité,
 * météo du marché, ancre d'allègement — pour qu'un « Vendre » affiché soit toujours
 * un « Vendre » notifié. L'app ne vend jamais : elle prévient.
 */
object PositionAlerts {

    private const val NEWS_TTL_MS = 30 * 60 * 1000L
    private val newsCache = ConcurrentHashMap<String, Pair<Long, NewsSentiment>>()
    @Volatile private var universeNames: Map<String, String>? = null

    /** Conseil de sortie calculé avec les entrées de référence (met à jour l'ancre d'allègement). */
    suspend fun adviceFor(
        container: AppContainer,
        ticker: String,
        avgPrice: Double,
        candles: List<Candle>,
        cfg: StrategyConfig,
        regime: Regime?,
    ): ExitAdvice {
        val advice = ExitAdvisor.advise(
            ticker, candles, avgPrice, cfg, sentimentFor(container, ticker),
            lastTrimPrice = container.settings.trimAnchor(ticker), regime = regime,
        )
        container.settings.setTrimAnchor(ticker, advice.trimAnchor)
        return advice
    }

    /**
     * Notifie un conseil s'il est nouveau : une seule alerte par changement d'avis.
     * @param allowSell autorise l'alerte « Vendre » à cet instant
     * @param allowTrim autorise l'alerte « Alléger » à cet instant
     */
    fun notifyIfNew(
        context: Context,
        container: AppContainer,
        ticker: String,
        isTest: Boolean,
        advice: ExitAdvice,
        allowSell: Boolean,
        allowTrim: Boolean,
    ) {
        val settings = container.settings
        if (!settings.notificationsEnabled) return
        val dedupKey = if (isTest) "test:$ticker" else ticker
        val label = if (isTest) " (portefeuille test)" else ""
        val baseId = (ticker.hashCode() and 0xffff) + (if (isTest) 10 else 0)
        val reason = advice.reasons.firstOrNull() ?: "Conditions réunies"
        val already = settings.lastExitNotified(dedupKey)
        val key = advice.action.name
        when (advice.action) {
            ExitAction.SELL -> if (key != already && allowSell) {
                Notifications.notifyExit(context, baseId + 1, ticker, reason, label)
                settings.setLastExitNotified(dedupKey, key)
            }
            ExitAction.TRIM -> if (key != already && allowTrim) {
                Notifications.notifyTrim(context, baseId + 4, ticker, reason, label)
                settings.setLastExitNotified(dedupKey, key)
            }
            ExitAction.HOLD -> settings.setLastExitNotified(dedupKey, null)
        }
    }

    /**
     * Vérifie toutes les positions détenues (tâches de fond). « Vendre » part dès la
     * détection, même hors de la plage choisie, mais jamais la nuit (22h–7h : délivré
     * au premier passage du matin) ; « Alléger » respecte la plage horaire.
     */
    suspend fun checkHeld(context: Context, container: AppContainer, now: LocalDateTime) {
        val settings = container.settings
        val cfg = container.strategyRepository.activeConfig()
        val regime = container.marketScannerService.regimeFromCache()?.regime
        val inWindow = settings.notificationsEnabled && settings.isWithinNotifyWindow(now)
        val daytime = now.hour in 7..21

        val held = mutableListOf<Triple<String, Double, Boolean>>()
        container.portfolioRepository.observePositions().first().forEach { held += Triple(it.ticker, it.avgPrice, false) }
        if (container.paperRepository.isEnabled()) {
            container.paperRepository.observePositions().first().forEach { held += Triple(it.ticker, it.avgPrice, true) }
        }
        for ((ticker, avgPrice, isTest) in held) {
            val candles = container.marketRepository.refresh(ticker).getOrNull()
                ?: container.marketRepository.cachedCandles(ticker)
            if (candles.isEmpty()) continue
            val advice = adviceFor(container, ticker, avgPrice, candles, cfg, regime)
            notifyIfNew(context, container, ticker, isTest, advice, allowSell = inWindow || daytime, allowTrim = inWindow)
        }
    }

    /** Prévient quand la météo du marché (CAC 40) change — une alerte par changement. */
    suspend fun checkRegime(context: Context, container: AppContainer, now: LocalDateTime, refreshIndex: Boolean) {
        if (refreshIndex) container.marketRepository.refresh(MarketRegimeEvaluator.INDEX_TICKER)
        val reading = container.marketScannerService.regimeFromCache() ?: return
        val settings = container.settings
        val notifEnabled = settings.notificationsEnabled
        val inWindow = notifEnabled && settings.isWithinNotifyWindow(now)
        val daytime = now.hour in 7..21
        val prev = settings.lastRegime?.let { runCatching { Regime.valueOf(it) }.getOrNull() }
        val cur = reading.regime
        if (prev == null || !notifEnabled) {
            settings.lastRegime = cur.name // 1re lecture ou notifications coupées : mémoriser sans alerter
            return
        }
        if (cur == prev) return
        val why = reading.reasons.firstOrNull().orEmpty()
        val delivered = when {
            cur == Regime.RISK_OFF && (inWindow || daytime) -> {
                Notifications.notifyMarket(
                    context, "Marché en repli : achats suspendus",
                    "$why. Je ne propose plus de nouveaux achats et je surveille tes positions (alléger / vendre).",
                ); true
            }
            cur == Regime.CAUTION && inWindow -> {
                Notifications.notifyMarket(
                    context,
                    if (prev == Regime.RISK_OFF) "Marché en voie d'amélioration" else "Marché fragile : prudence",
                    "$why. Seuls les achats les plus solides sont proposés.",
                ); true
            }
            cur == Regime.RISK_ON && inWindow -> {
                Notifications.notifyMarket(
                    context, "Marché redevenu favorable",
                    "Le CAC 40 s'est repris : les signaux d'achat sont de nouveau actifs.",
                ); true
            }
            else -> false
        }
        if (delivered) settings.lastRegime = cur.name
    }

    /** Sentiment de l'actualité, mis en cache 30 min pour rester léger. */
    private suspend fun sentimentFor(container: AppContainer, ticker: String): NewsSentiment {
        val nowMs = System.currentTimeMillis()
        newsCache[ticker]?.let { (at, s) -> if (nowMs - at < NEWS_TTL_MS) return s }
        val news = container.newsProvider.forQuery(nameOf(container, ticker)).getOrDefault(emptyList())
        val s = NewsAnalyzer.sentimentOf(news.map { it.title }).first
        newsCache[ticker] = nowMs to s
        return s
    }

    private fun nameOf(container: AppContainer, ticker: String): String {
        val shown = container.marketScannerService.displayName(ticker)
        if (shown.isNotBlank() && shown != ticker) return shown
        val names = universeNames
            ?: runCatching { container.universeProvider.load().associate { it.ticker to it.name } }
                .getOrDefault(emptyMap()).also { universeNames = it }
        return names[ticker] ?: ticker
    }
}
