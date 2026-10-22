package bzh.dargio.boursosignal.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.ui.screens.AlertsScreen
import bzh.dargio.boursosignal.ui.screens.BacktestScreen
import bzh.dargio.boursosignal.ui.screens.DashboardScreen
import bzh.dargio.boursosignal.ui.screens.InstrumentDetailScreen
import bzh.dargio.boursosignal.ui.screens.PortfolioScreen
import bzh.dargio.boursosignal.ui.screens.SettingsScreen
import bzh.dargio.boursosignal.ui.screens.WatchlistScreen

private sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Dest("dashboard", "Tableau", Icons.Filled.Dashboard)
    data object Watchlist : Dest("watchlist", "Valeurs", Icons.AutoMirrored.Filled.List)
    data object Portfolio : Dest("portfolio", "Portefeuille", Icons.Filled.AccountBalanceWallet)
    data object Alerts : Dest("alerts", "Alertes", Icons.Filled.Notifications)
    data object Settings : Dest("settings", "Réglages", Icons.Filled.Settings)
}

private val bottomDestinations = listOf(
    Dest.Dashboard, Dest.Watchlist, Dest.Portfolio, Dest.Alerts, Dest.Settings,
)

@Composable
fun AppRoot(container: AppContainer) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDest = backStackEntry?.destination
            NavigationBar {
                bottomDestinations.forEach { dest ->
                    NavigationBarItem(
                        selected = currentDest?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.Dashboard.route) {
                DashboardScreen(container, onOpen = { navController.navigate("detail/$it") })
            }
            composable(Dest.Watchlist.route) {
                WatchlistScreen(container, onOpen = { navController.navigate("detail/$it") })
            }
            composable(Dest.Portfolio.route) { PortfolioScreen(container) }
            composable(Dest.Alerts.route) {
                AlertsScreen(container, onOpen = { navController.navigate("detail/$it") })
            }
            composable(Dest.Settings.route) { SettingsScreen(container) }
            composable("detail/{ticker}") { entry ->
                val ticker = entry.arguments?.getString("ticker").orEmpty()
                InstrumentDetailScreen(
                    container = container,
                    ticker = ticker,
                    onBack = { navController.popBackStack() },
                    onBacktest = { navController.navigate("backtest/$ticker") },
                )
            }
            composable("backtest/{ticker}") { entry ->
                val ticker = entry.arguments?.getString("ticker").orEmpty()
                BacktestScreen(container, ticker, onBack = { navController.popBackStack() })
            }
        }
    }
}
