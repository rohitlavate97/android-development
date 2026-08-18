package com.enterprise.financetracker.data.repository

import com.enterprise.financetracker.core.concurrency.DispatcherProvider
import com.enterprise.financetracker.domain.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.random.Random

interface TransactionRepository {
    fun observeTransactions(): Flow<List<Transaction>>
    suspend fun getTransactionById(id: TransactionId): Transaction?
    suspend fun addTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: TransactionId)
}

class InMemoryReactiveTransactionRepository(
    private val dispatchers: DispatcherProvider
) : TransactionRepository {

    private val sampleCategories = listOf(
        Category(CategoryId("cat_salary"), "Salary", "payments", "#4CAF50"),
        Category(CategoryId("cat_food"), "Food & Dining", "restaurant", "#FF5722"),
        Category(CategoryId("cat_shopping"), "Electronics", "shopping_cart", "#2196F3"),
        Category(CategoryId("cat_housing"), "Housing Rent", "home", "#9C27B0")
    )

    private val _transactionsFlow = MutableStateFlow<List<Transaction>>(
        listOf(
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
    )

    override fun observeTransactions(): Flow<List<Transaction>> =
        _transactionsFlow.asStateFlow().flowOn(dispatchers.io)

    override suspend fun getTransactionById(id: TransactionId): Transaction? = withContext(dispatchers.io) {
        _transactionsFlow.value.find { it.id == id }
    }

    override suspend fun addTransaction(transaction: Transaction) = withContext(dispatchers.io) {
        _transactionsFlow.update { current ->
            listOf(transaction) + current
        }
    }

    override suspend fun deleteTransaction(id: TransactionId) = withContext(dispatchers.io) {
        _transactionsFlow.update { current ->
            current.filterNot { it.id == id }
        }
    }
}

interface PortfolioRepository {
    fun observePortfolio(): Flow<Portfolio>
}

class LiveTickerPortfolioRepository(
    private val dispatchers: DispatcherProvider
) : PortfolioRepository {

    private val basePortfolio = Portfolio(
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

    override fun observePortfolio(): Flow<Portfolio> = flow {
        emit(basePortfolio)
        while (currentCoroutineContext().isActive) {
            delay(4000) // Simulated live stock price ticker interval
            val updatedHoldings = basePortfolio.holdings.map { holding ->
                val deltaPercent = Random.nextDouble(-0.015, 0.015) // ±1.5% fluctuation
                val newPrice = (holding.currentMarketPrice * (1.0 + deltaPercent)).coerceAtLeast(1.0)
                holding.copy(currentMarketPrice = newPrice)
            }
            emit(basePortfolio.copy(holdings = updatedHoldings))
        }
    }.flowOn(dispatchers.default)
}
