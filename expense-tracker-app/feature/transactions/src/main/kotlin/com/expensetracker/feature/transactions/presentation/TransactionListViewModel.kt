package com.expensetracker.feature.transactions.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.core.common.DispatcherProvider
import com.expensetracker.core.model.*
import com.expensetracker.feature.transactions.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface TransactionListUiState {
    data object Loading : TransactionListUiState
    data class Content(
        val transactions: List<Transaction>,
        val filteredTransactions: List<Transaction>,
        val selectedFilter: TransactionType? = null,
        val totalAmount: Double = 0.0,
        val isRefreshing: Boolean = false
    ) : TransactionListUiState
    data object Empty : TransactionListUiState
    data class Error(val message: String) : TransactionListUiState
}

sealed interface TransactionListIntent {
    data object Refresh : TransactionListIntent
    data class FilterByType(val type: TransactionType?) : TransactionListIntent
    data class DeleteTransaction(val id: TransactionId) : TransactionListIntent
    data class AddNewTransaction(val transaction: Transaction) : TransactionListIntent
}

class TransactionListViewModel(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val addTransactionUseCase: AddTransactionUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val syncTransactionsUseCase: SyncTransactionsUseCase,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<TransactionType?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<TransactionListUiState> = combine(
        getTransactionsUseCase(),
        _selectedFilter,
        _isRefreshing
    ) { transactions, filter, refreshing ->
        if (transactions.isEmpty()) {
            TransactionListUiState.Empty
        } else {
            val filtered = if (filter != null) {
                transactions.filter { it.type == filter }
            } else {
                transactions
            }
            val total = filtered.sumOf {
                when (it.type) {
                    TransactionType.INCOME -> it.amount
                    TransactionType.EXPENSE -> -it.amount
                    TransactionType.TRANSFER -> 0.0
                }
            }
            TransactionListUiState.Content(
                transactions = transactions,
                filteredTransactions = filtered,
                selectedFilter = filter,
                totalAmount = total,
                isRefreshing = refreshing
            )
        }
    }
        .flowOn(dispatchers.default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000), // Phase 2 Concept 16 standard
            initialValue = TransactionListUiState.Loading
        )

    init {
        refresh()
    }

    fun processIntent(intent: TransactionListIntent) {
        when (intent) {
            is TransactionListIntent.Refresh -> refresh()
            is TransactionListIntent.FilterByType -> _selectedFilter.value = intent.type
            is TransactionListIntent.DeleteTransaction -> delete(intent.id)
            is TransactionListIntent.AddNewTransaction -> add(intent.transaction)
        }
    }

    private fun refresh() {
        viewModelScope.launch(dispatchers.io) {
            _isRefreshing.value = true
            syncTransactionsUseCase()
            _isRefreshing.value = false
        }
    }

    private fun delete(id: TransactionId) {
        viewModelScope.launch(dispatchers.io) {
            deleteTransactionUseCase(id)
        }
    }

    private fun add(transaction: Transaction) {
        viewModelScope.launch(dispatchers.io) {
            addTransactionUseCase(transaction)
        }
    }
}
