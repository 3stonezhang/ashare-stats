package com.ashare.stats.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashare.stats.data.EastMoneyApi
import com.ashare.stats.data.MarketStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MarketUiState(
    val stats: MarketStats = MarketStats(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

class MarketStatsViewModel(
    private val api: EastMoneyApi = EastMoneyApi(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketUiState())
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        startAutoRefresh()
    }

    fun startAutoRefresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            while (isActive) {
                refreshInternal(showFullLoading = _uiState.value.stats.updatedAtMillis == 0L)
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            refreshInternal(showFullLoading = false)
        }
    }

    private suspend fun refreshInternal(showFullLoading: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = showFullLoading,
                isRefreshing = !showFullLoading,
                errorMessage = null,
            )
        }
        try {
            val stats = api.fetchMarketStats()
            _uiState.update {
                MarketUiState(
                    stats = stats,
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null,
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = e.message ?: "刷新失败",
                )
            }
        }
    }

    companion object {
        const val REFRESH_INTERVAL_MS = 10_000L
    }
}
