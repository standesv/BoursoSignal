package bzh.dargio.boursosignal.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bzh.dargio.boursosignal.di.AppContainer
import bzh.dargio.boursosignal.data.remote.Quote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WatchRow(
    val ticker: String,
    val name: String,
    val priceLabel: String,
    val changeLabel: String,
    val signalLabel: String?,
)

data class WatchUiState(
    val rows: List<WatchRow> = emptyList(),
    val loading: Boolean = false,
)

class WatchlistViewModel(private val container: AppContainer) : ViewModel() {

    private val prices = MutableStateFlow<Map<String, Quote>>(emptyMap())
    private val loading = MutableStateFlow(false)

    val uiState = combine(
        container.watchlistRepository.observeWatchlist(),
        container.signalRepository.observeAll(),
        prices,
        loading,
    ) { instruments, signals, priceMap, isLoading ->
        val rows = instruments.map { ins ->
            val quote = priceMap[ins.ticker]
            val latest = signals.firstOrNull { it.ticker == ins.ticker }
            WatchRow(
                ticker = ins.ticker,
                name = ins.name,
                priceLabel = quote?.let { "%.2f €".format(it.price) } ?: "—",
                changeLabel = quote?.let { "%+.2f %%".format(it.changePct) } ?: "",
                signalLabel = latest?.let { "${if (it.type == "BUY") "▲ Achat" else "▼ Vente"} · ${it.date}" },
            )
        }
        WatchUiState(rows, isLoading)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatchUiState())

    init { refresh() }

    fun add(ticker: String, name: String) = viewModelScope.launch {
        if (ticker.isNotBlank()) {
            container.watchlistRepository.add(ticker, name.ifBlank { ticker })
            refresh()
        }
    }

    fun remove(ticker: String) = viewModelScope.launch {
        container.watchlistRepository.remove(ticker)
    }

    fun refresh() = viewModelScope.launch {
        loading.value = true
        runCatching { container.signalRefresher.refreshAll() }
        val map = mutableMapOf<String, Quote>()
        for (ins in container.watchlistRepository.watchlist()) {
            val candles = container.marketRepository.cachedCandles(ins.ticker)
            if (candles.size >= 2) {
                val last = candles.last().close
                val prev = candles[candles.size - 2].close
                val change = if (prev != 0.0) (last - prev) / prev * 100.0 else 0.0
                map[ins.ticker] = Quote(ins.ticker, last, change, candles.last().date)
            }
        }
        prices.value = map
        loading.value = false
    }
}
