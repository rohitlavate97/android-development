package com.enterprise.financetracker

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.enterprise.financetracker.domain.model.*
import com.enterprise.financetracker.ui.screens.*
import com.enterprise.financetracker.ui.theme.EnterpriseFinanceTheme
import kotlinx.datetime.Instant

enum class AppScreen {
    LOGIN,
    DASHBOARD,
    TRANSACTION_LIST,
    TRANSACTION_DETAIL
}

/**
 * Launcher Activity rendering the declarative Jetpack Compose UI.
 * (Phase 4 & Stage 3)
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: Initializing Jetpack Compose UI content")

        // Mock domain data for Stage 3 Compose UI layer verification
        val sampleCategories = listOf(
            Category(CategoryId("cat_salary"), "Salary", "payments", "#4CAF50"),
            Category(CategoryId("cat_food"), "Food & Dining", "restaurant", "#FF5722"),
            Category(CategoryId("cat_shopping"), "Electronics", "shopping_cart", "#2196F3"),
            Category(CategoryId("cat_housing"), "Housing Rent", "home", "#9C27B0")
        )

        val sampleTransactions = listOf(
            Transaction(
                id = TransactionId("tx_1"),
                accountId = AccountId("acc_checking"),
                title = "Tech Corp Bi-weekly Paycheck",
                amount = 4250.00,
                type = TransactionType.Income,
                category = sampleCategories[0],
                timestamp = Instant.fromEpochMilliseconds(1738000000000L)
            ),
            Transaction(
                id = TransactionId("tx_2"),
                accountId = AccountId("acc_checking"),
                title = "Whole Foods Organic Market",
                amount = 142.80,
                type = TransactionType.Expense,
                category = sampleCategories[1],
                timestamp = Instant.fromEpochMilliseconds(1738100000000L)
            ),
            Transaction(
                id = TransactionId("tx_3"),
                accountId = AccountId("acc_checking"),
                title = "Dell UltraSharp 4K Monitor",
                amount = 499.99,
                type = TransactionType.Expense,
                category = sampleCategories[2],
                timestamp = Instant.fromEpochMilliseconds(1738200000000L)
            ),
            Transaction(
                id = TransactionId("tx_4"),
                accountId = AccountId("acc_checking"),
                title = "Monthly Apartment Rent",
                amount = 1800.00,
                type = TransactionType.Expense,
                category = sampleCategories[3],
                timestamp = Instant.fromEpochMilliseconds(1738300000000L),
                isRecurring = true
            )
        )

        val samplePortfolio = Portfolio(
            id = PortfolioId("port_growth"),
            name = "Primary Tech & Crypto Portfolio",
            holdings = listOf(
                InvestmentHolding(
                    ticker = TickerSymbol("AAPL"),
                    name = "Apple Inc.",
                    shares = 25.0,
                    averageBuyPrice = 160.00,
                    currentMarketPrice = 195.00,
                    assetClass = AssetClass.EQUITY
                ),
                InvestmentHolding(
                    ticker = TickerSymbol("NVDA"),
                    name = "NVIDIA Corporation",
                    shares = 15.0,
                    averageBuyPrice = 90.00,
                    currentMarketPrice = 135.00,
                    assetClass = AssetClass.EQUITY
                ),
                InvestmentHolding(
                    ticker = TickerSymbol("BTC"),
                    name = "Bitcoin",
                    shares = 0.45,
                    averageBuyPrice = 58000.00,
                    currentMarketPrice = 64000.00,
                    assetClass = AssetClass.CRYPTO
                )
            )
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
                            portfolio = samplePortfolio,
                            transactions = sampleTransactions,
                            onNavigateToTransactions = { currentScreen = AppScreen.TRANSACTION_LIST },
                            onNavigateToTransactionDetail = { id ->
                                selectedTransactionId = id.value
                                currentScreen = AppScreen.TRANSACTION_DETAIL
                            }
                        )
                    }
                    AppScreen.TRANSACTION_LIST -> {
                        TransactionListRoute(
                            transactions = sampleTransactions,
                            onNavigateBack = { currentScreen = AppScreen.DASHBOARD },
                            onNavigateToDetail = { id ->
                                selectedTransactionId = id.value
                                currentScreen = AppScreen.TRANSACTION_DETAIL
                            }
                        )
                    }
                    AppScreen.TRANSACTION_DETAIL -> {
                        val selectedTx = sampleTransactions.find { it.id.value == selectedTransactionId }
                        TransactionDetailRoute(
                            transaction = selectedTx,
                            onNavigateBack = { currentScreen = AppScreen.TRANSACTION_LIST },
                            onDeleteClick = { currentScreen = AppScreen.TRANSACTION_LIST }
                        )
                    }
                }
            }
        }
    }
}
