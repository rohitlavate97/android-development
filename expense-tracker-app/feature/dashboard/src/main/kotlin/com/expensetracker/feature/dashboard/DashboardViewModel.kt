package com.expensetracker.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.core.common.DispatcherProvider
import com.expensetracker.core.model.*
import com.expensetracker.feature.transactions.domain.GetTransactionsUseCase
import kotlinx.coroutines.flow.*

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Content(
        val totalBalance: Double,
        val monthlyIncome: Double,
        val monthlyExpense: Double,
        val recentTransactions: List<Transaction>,
        val topSpendingCategories: List<CategorySpending>
    ) : DashboardUiState
}

class DashboardViewModel(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = getTransactionsUseCase()
        .map { transactions ->
            val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val balance = income - expense

            // Top categories
            val categoryMap = transactions.filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.category }
                .mapValues { (_, txList) -> txList.sumOf { it.amount } }

            val topCategories = categoryMap.map { (cat, amount) ->
                CategorySpending(
                    category = cat,
                    totalAmount = amount,
                    percentage = if (expense > 0) (amount / expense).toFloat() else 0f
                )
            }.sortedByDescending { it.totalAmount }

            DashboardUiState.Content(
                totalBalance = balance,
                monthlyIncome = income,
                monthlyExpense = expense,
                recentTransactions = transactions.take(5),
                topSpendingCategories = topCategories
            )
        }
        .flowOn(dispatchers.default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState.Loading
        )
}
