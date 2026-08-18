package com.enterprise.financetracker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.financetracker.core.concurrency.DispatcherProvider
import com.enterprise.financetracker.data.repository.PortfolioRepository
import com.enterprise.financetracker.data.repository.TransactionRepository
import com.enterprise.financetracker.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val totalNetWorth: Double,
        val cashBalance: Double,
        val portfolio: Portfolio,
        val recentTransactions: List<Transaction>
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val portfolioRepository: PortfolioRepository,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        transactionRepository.observeTransactions(),
        portfolioRepository.observePortfolio()
    ) { transactions, portfolio ->
        val liquidCash = 12450.00
        val netWorth = liquidCash + portfolio.totalPortfolioValue
        DashboardUiState.Success(
            totalNetWorth = netWorth,
            cashBalance = liquidCash,
            portfolio = portfolio,
            recentTransactions = transactions.take(5)
        )
    }
        .flowOn(dispatchers.default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000), // Phase 2 Concept 16 & ADR 011
            initialValue = DashboardUiState.Loading
        )
}

sealed interface TransactionListUiState {
    data object Loading : TransactionListUiState
    data class Success(
        val transactions: List<Transaction>,
        val filteredTransactions: List<Transaction>,
        val searchQuery: String = "",
        val selectedFilter: String = "ALL"
    ) : TransactionListUiState
    data object Empty : TransactionListUiState
}

class TransactionListViewModel(
    private val transactionRepository: TransactionRepository,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow("ALL")

    val uiState: StateFlow<TransactionListUiState> = combine(
        transactionRepository.observeTransactions(),
        _searchQuery,
        _selectedFilter
    ) { transactions, query, filter ->
        if (transactions.isEmpty()) {
            TransactionListUiState.Empty
        } else {
            val filtered = transactions.filter { tx ->
                val matchesQuery = tx.title.contains(query, ignoreCase = true) ||
                        tx.category.name.contains(query, ignoreCase = true)
                val matchesFilter = when (filter) {
                    "EXPENSE" -> tx.type is TransactionType.Expense
                    "INCOME" -> tx.type is TransactionType.Income
                    "TRANSFER" -> tx.type is TransactionType.Transfer
                    else -> true
                }
                matchesQuery && matchesFilter
            }
            TransactionListUiState.Success(
                transactions = transactions,
                filteredTransactions = filtered,
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

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelected(filter: String) {
        _selectedFilter.value = filter
    }

    fun deleteTransaction(id: TransactionId) {
        viewModelScope.launch(dispatchers.io) {
            transactionRepository.deleteTransaction(id)
        }
    }
}
