package com.enterprise.financetracker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.financetracker.core.concurrency.DispatcherProvider
import com.enterprise.financetracker.domain.model.TransactionId
import com.enterprise.financetracker.domain.usecase.*
import com.enterprise.financetracker.ui.mapper.toUiModel
import com.enterprise.financetracker.ui.model.PortfolioUiModel
import com.enterprise.financetracker.ui.model.TransactionUiModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ==========================================
// 1. Dashboard Feature: Clean MVVM Pattern
// ==========================================

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Content(
        val portfolio: PortfolioUiModel,
        val recentTransactions: List<TransactionUiModel>
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

class DashboardViewModel(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val getPortfolioSummaryUseCase: GetPortfolioSummaryUseCase,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        getTransactionsUseCase(),
        getPortfolioSummaryUseCase()
    ) { transactions, portfolioSummary ->
        DashboardUiState.Content(
            portfolio = portfolioSummary.toUiModel(),
            recentTransactions = transactions.take(5).map { it.toUiModel() }
        )
    }
        .flowOn(dispatchers.default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState.Loading
        )
}

// ==========================================
// 2. Transactions Feature: MVI Pattern
// ==========================================

sealed interface TransactionListIntent {
    data class SearchQueryChanged(val query: String) : TransactionListIntent
    data class FilterSelected(val filter: String) : TransactionListIntent
    data class DeleteTransaction(val id: String) : TransactionListIntent
}

sealed interface TransactionListUiState {
    data object Loading : TransactionListUiState
    data class Content(
        val transactions: List<TransactionUiModel>,
        val filteredTransactions: List<TransactionUiModel>,
        val searchQuery: String = "",
        val selectedFilter: String = "ALL"
    ) : TransactionListUiState
    data object Empty : TransactionListUiState
    data class Error(val message: String) : TransactionListUiState
}

class TransactionListMviViewModel(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val filterTransactionsUseCase: FilterTransactionsUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow("ALL")

    val uiState: StateFlow<TransactionListUiState> = combine(
        getTransactionsUseCase(),
        _searchQuery,
        _selectedFilter
    ) { transactions, query, filter ->
        if (transactions.isEmpty()) {
            TransactionListUiState.Empty
        } else {
            val filtered = filterTransactionsUseCase(transactions, query, filter)
            TransactionListUiState.Content(
                transactions = transactions.map { it.toUiModel() },
                filteredTransactions = filtered.map { it.toUiModel() },
                searchQuery = query,
                selectedFilter = filter
            )
        }
    }
        .flowOn(dispatchers.default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionListUiState.Loading
        )

    fun processIntent(intent: TransactionListIntent) {
        when (intent) {
            is TransactionListIntent.SearchQueryChanged -> _searchQuery.value = intent.query
            is TransactionListIntent.FilterSelected -> _selectedFilter.value = intent.filter
            is TransactionListIntent.DeleteTransaction -> delete(intent.id)
        }
    }

    private fun delete(idString: String) {
        viewModelScope.launch(dispatchers.io) {
            deleteTransactionUseCase(TransactionId(idString))
        }
    }
}
