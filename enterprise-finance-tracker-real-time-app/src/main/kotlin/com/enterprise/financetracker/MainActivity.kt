package com.enterprise.financetracker

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.enterprise.financetracker.core.concurrency.StandardDispatcherProvider
import com.enterprise.financetracker.data.datasource.InMemoryTransactionLocalDataSource
import com.enterprise.financetracker.data.repository.ExpenseRepositoryImpl
import com.enterprise.financetracker.data.repository.PortfolioRepositoryImpl
import com.enterprise.financetracker.domain.model.TransactionId
import com.enterprise.financetracker.domain.usecase.*
import com.enterprise.financetracker.ui.mapper.toUiModel
import com.enterprise.financetracker.ui.model.TransactionUiModel
import com.enterprise.financetracker.ui.screens.*
import com.enterprise.financetracker.ui.theme.EnterpriseFinanceTheme
import com.enterprise.financetracker.ui.viewmodels.DashboardViewModel
import com.enterprise.financetracker.ui.viewmodels.TransactionListIntent
import com.enterprise.financetracker.ui.viewmodels.TransactionListMviViewModel

/**
 * Launcher Activity rendering the Clean Architecture + UDF UI layer.
 * (Phase 5 & Stage 5)
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: Initializing Clean Architecture pipeline (DataSource -> Repository -> UseCases -> ViewModels)")

        // 1. Data Layer
        val dispatchers = StandardDispatcherProvider()
        val localDataSource = InMemoryTransactionLocalDataSource()
        val expenseRepository = ExpenseRepositoryImpl(localDataSource, dispatchers)
        val portfolioRepository = PortfolioRepositoryImpl(dispatchers)

        // 2. Domain Layer (Use Cases)
        val getTransactionsUseCase = GetTransactionsUseCase(expenseRepository)
        val getTransactionDetailUseCase = GetTransactionDetailUseCase(expenseRepository)
        val addTransactionUseCase = AddTransactionUseCase(expenseRepository)
        val deleteTransactionUseCase = DeleteTransactionUseCase(expenseRepository)
        val getPortfolioSummaryUseCase = GetPortfolioSummaryUseCase(portfolioRepository)
        val filterTransactionsUseCase = FilterTransactionsUseCase()

        // 3. Presentation Layer (ViewModels)
        val dashboardViewModel = DashboardViewModel(
            getTransactionsUseCase = getTransactionsUseCase,
            getPortfolioSummaryUseCase = getPortfolioSummaryUseCase,
            dispatchers = dispatchers
        )

        val transactionListMviViewModel = TransactionListMviViewModel(
            getTransactionsUseCase = getTransactionsUseCase,
            filterTransactionsUseCase = filterTransactionsUseCase,
            deleteTransactionUseCase = deleteTransactionUseCase,
            dispatchers = dispatchers
        )

        setContent {
            EnterpriseFinanceTheme {
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.LOGIN) }
                var selectedTransactionId by rememberSaveable { mutableStateOf<String?>(null) }

                when (currentScreen) {
                    AppScreen.LOGIN -> {
                        LoginRoute(
                            onLoginSuccess = { currentScreen = AppScreen.DASHBOARD }
                        )
                    }
                    AppScreen.DASHBOARD -> {
                        DashboardRoute(
                            viewModel = dashboardViewModel,
                            onNavigateToTransactions = { currentScreen = AppScreen.TRANSACTION_LIST },
                            onNavigateToTransactionDetail = { id ->
                                selectedTransactionId = id
                                currentScreen = AppScreen.TRANSACTION_DETAIL
                            }
                        )
                    }
                    AppScreen.TRANSACTION_LIST -> {
                        TransactionListRoute(
                            viewModel = transactionListMviViewModel,
                            onNavigateBack = { currentScreen = AppScreen.DASHBOARD },
                            onNavigateToDetail = { id ->
                                selectedTransactionId = id
                                currentScreen = AppScreen.TRANSACTION_DETAIL
                            }
                        )
                    }
                    AppScreen.TRANSACTION_DETAIL -> {
                        var transactionUiModel by remember { mutableStateOf<TransactionUiModel?>(null) }
                        LaunchedEffect(selectedTransactionId) {
                            if (selectedTransactionId != null) {
                                val result = getTransactionDetailUseCase(TransactionId(selectedTransactionId!!))
                                if (result is com.enterprise.financetracker.domain.model.FinancialResult.Success) {
                                    transactionUiModel = result.value.toUiModel()
                                }
                            }
                        }

                        TransactionDetailRoute(
                            transaction = transactionUiModel,
                            onNavigateBack = { currentScreen = AppScreen.TRANSACTION_LIST },
                            onDeleteClick = { id ->
                                transactionListMviViewModel.processIntent(TransactionListIntent.DeleteTransaction(id))
                                currentScreen = AppScreen.TRANSACTION_LIST
                            }
                        )
                    }
                }
            }
        }
    }
}
