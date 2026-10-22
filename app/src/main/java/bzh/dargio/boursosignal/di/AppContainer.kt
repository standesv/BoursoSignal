package bzh.dargio.boursosignal.di

import android.content.Context
import bzh.dargio.boursosignal.data.local.AppDatabase
import bzh.dargio.boursosignal.data.prefs.SettingsStore
import bzh.dargio.boursosignal.data.repo.MarketRepository
import bzh.dargio.boursosignal.data.repo.PaperRepository
import bzh.dargio.boursosignal.data.repo.PortfolioRepository
import bzh.dargio.boursosignal.data.repo.SignalRepository
import bzh.dargio.boursosignal.data.repo.StrategyRepository
import bzh.dargio.boursosignal.data.repo.WatchlistRepository
import bzh.dargio.boursosignal.data.universe.UniverseProvider
import bzh.dargio.boursosignal.domain.MarketScannerService
import bzh.dargio.boursosignal.domain.PaperTradingService
import bzh.dargio.boursosignal.domain.SignalRefresher

/**
 * Conteneur d'injection de dépendances manuel (sans framework) : instancie la
 * base, les réglages, les repositories et les services, partagés dans toute
 * l'application. Simple et suffisant pour une app mono-utilisateur.
 */
class AppContainer(context: Context) {

    private val db = AppDatabase.build(context)

    val settings = SettingsStore(context)

    val watchlistRepository = WatchlistRepository(db.instrumentDao())
    val marketRepository = MarketRepository(db.candleDao(), settings)
    val portfolioRepository = PortfolioRepository(db.portfolioDao())
    val signalRepository = SignalRepository(db.signalDao())
    val strategyRepository = StrategyRepository(db.strategyDao())
    val paperRepository = PaperRepository(db.paperDao())
    val universeProvider = UniverseProvider(context)

    val signalRefresher = SignalRefresher(
        watchlist = watchlistRepository,
        market = marketRepository,
        strategies = strategyRepository,
        portfolio = portfolioRepository,
        signals = signalRepository,
    )

    val paperTradingService = PaperTradingService(paperRepository)

    val marketScannerService = MarketScannerService(
        universe = universeProvider,
        market = marketRepository,
        strategies = strategyRepository,
    )
}
