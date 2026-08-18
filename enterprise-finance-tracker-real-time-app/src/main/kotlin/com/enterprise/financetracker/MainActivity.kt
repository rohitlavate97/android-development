package com.enterprise.financetracker

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.enterprise.financetracker.core.concurrency.StandardDispatcherProvider
import com.enterprise.financetracker.data.repository.InMemoryReactiveTransactionRepository
import com.enterprise.financetracker.data.repository.LiveTickerPortfolioRepository
import com.enterprise.financetracker.domain.model.*
import com.enterprise.financetracker.ui.screens.*
import com.enterprise.financetracker.ui.theme.EnterpriseFinanceTheme
import com.enterprise.financetracker.ui.viewmodels.DashboardViewModel
import com.enterprise.financetracker.ui.viewmodels.TransactionListViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Launcher Activity rendering the reactive Jetpack Compose UI powered by Coroutines & Flow.
 * (Phase 2, Phase 4 & Stage 4)
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val dispatchers = StandardDispatcherProvider()
    private val transactionRepository = InMemoryReactiveTransactionRepository(dispatchers)
    private val portfolioRepository = LiveTickerPortfolioRepository(dispatchers)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: Initializing reactive Coroutines & Flow UI engine")

        val dashboardViewModel = DashboardViewModel(transactionRepository, portfolioRepository, dispatchers)
        val transactionListViewModel = TransactionListViewModel(transactionRepository, dispatchers)

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
                                selectedTransactionId = id.value
                                currentScreen = AppScreen.TRANSACTION_DETAIL
                            }
                        )
                    }
                    AppScreen.TRANSACTION_LIST -> {
                        TransactionListRoute(
                            viewModel = transactionListViewModel,
                            onNavigateBack = { currentScreen = AppScreen.DASHBOARD },
                            onNavigateToDetail = { id ->
                                selectedTransactionId = id.value
                                currentScreen = AppScreen.TRANSACTION_DETAIL
                            }
                        )
                    }
                    AppScreen.TRANSACTION_DETAIL -> {
                        var transaction by remember { mutableStateOf<Transaction?>(null) }
                        LaunchedEffect(selectedTransactionId) {
                            if (selectedTransactionId != null) {
                                transaction = transactionRepository.getTransactionById(TransactionId(selectedTransactionId!!))
                            }
                        }

                        TransactionDetailRoute(
                            transaction = transaction,
                            onNavigateBack = { currentScreen = AppScreen.TRANSACTION_LIST },
                            onDeleteClick = { tx ->
                                transactionListViewModel.deleteTransaction(tx.id)
                                currentScreen = AppScreen.TRANSACTION_LIST
                            }
                        )
                    }
                }
            }
        }
    }
}
