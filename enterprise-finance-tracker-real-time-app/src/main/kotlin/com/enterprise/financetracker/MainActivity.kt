package com.enterprise.financetracker

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.enterprise.financetracker.domain.model.TransactionId
import com.enterprise.financetracker.domain.usecase.GetTransactionDetailUseCase
import com.enterprise.financetracker.ui.mapper.toUiModel
import com.enterprise.financetracker.ui.model.TransactionUiModel
import com.enterprise.financetracker.ui.screens.*
import com.enterprise.financetracker.ui.theme.EnterpriseFinanceTheme
import com.enterprise.financetracker.ui.viewmodels.DashboardViewModel
import com.enterprise.financetracker.ui.viewmodels.TransactionListIntent
import com.enterprise.financetracker.ui.viewmodels.TransactionListMviViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

/**
 * Launcher Activity rendering the Clean Architecture + UDF UI layer powered by Koin DI.
 * (Phase 6 & Stage 6)
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val getTransactionDetailUseCase: GetTransactionDetailUseCase by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: Activity initialized with Koin Dependency Injection")

        setContent {
            EnterpriseFinanceTheme {
                val dashboardViewModel: DashboardViewModel = koinViewModel()
                val transactionListMviViewModel: TransactionListMviViewModel = koinViewModel()

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
