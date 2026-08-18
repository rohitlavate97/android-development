package com.enterprise.financetracker.ui.viewmodels

import app.cash.turbine.test
import com.enterprise.financetracker.concurrency.TestDispatcherProvider
import com.enterprise.financetracker.domain.model.*
import com.enterprise.financetracker.domain.repository.PortfolioRepository
import com.enterprise.financetracker.domain.usecase.FakeExpenseRepository
import com.enterprise.financetracker.domain.usecase.GetPortfolioSummaryUseCase
import com.enterprise.financetracker.domain.usecase.GetTransactionsUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.Test

class FakePortfolioRepository(private val portfolio: Portfolio) : PortfolioRepository {
    override fun observePortfolio(): Flow<Portfolio> = flowOf(portfolio)
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private lateinit var testDispatcher: StandardTestDispatcher
    private lateinit var dispatchers: TestDispatcherProvider
    private lateinit var fakeExpenseRepository: FakeExpenseRepository
    private lateinit var fakePortfolioRepository: FakePortfolioRepository
    private lateinit var viewModel: DashboardViewModel

    private val sampleCategory = Category(CategoryId("cat_salary"), "Salary", "payments", "#4CAF50")
    private val sampleTransaction = Transaction(
        id = TransactionId("tx_dash_1"),
        accountId = AccountId("acc_checking"),
        title = "Bi-Weekly Salary",
        amount = 3500.00,
        type = TransactionType.Income,
        category = sampleCategory,
        timestamp = Instant.fromEpochMilliseconds(1738000000000L)
    )

    private val samplePortfolio = Portfolio(
        id = PortfolioId("port_1"),
        name = "Retirement Portfolio",
        holdings = listOf(
            InvestmentHolding(
                ticker = TickerSymbol("SPY"),
                name = "S&P 500 ETF",
                shares = 10.0,
                averageBuyPrice = 450.00,
                currentMarketPrice = 500.00,
                assetClass = AssetClass.EQUITY
            )
        )
    )

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        dispatchers = TestDispatcherProvider(testDispatcher)
        fakeExpenseRepository = FakeExpenseRepository()
        fakePortfolioRepository = FakePortfolioRepository(samplePortfolio)

        val getTransactionsUseCase = GetTransactionsUseCase(fakeExpenseRepository)
        val getPortfolioSummaryUseCase = GetPortfolioSummaryUseCase(fakePortfolioRepository)

        viewModel = DashboardViewModel(
            getTransactionsUseCase = getTransactionsUseCase,
            getPortfolioSummaryUseCase = getPortfolioSummaryUseCase,
            dispatchers = dispatchers
        )
    }

    @Test
    fun given_dashboard_view_model_when_initialized_then_emits_Loading_followed_by_Content_state() = runTest(testDispatcher) {
        fakeExpenseRepository.emit(listOf(sampleTransaction))

        viewModel.uiState.test {
            // 1. Initial Loading State
            assertThat(awaitItem()).isEqualTo(DashboardUiState.Loading)

            testDispatcher.scheduler.advanceUntilIdle()

            // 2. Transformed Content State
            val contentState = awaitItem() as DashboardUiState.Content
            assertThat(contentState.portfolio.name).isEqualTo("Retirement Portfolio")
            assertThat(contentState.portfolio.formattedNetWorth).isEqualTo("$17450.00") // 12450 cash + 5000 invested
            assertThat(contentState.recentTransactions).hasSize(1)
            assertThat(contentState.recentTransactions.first().title).isEqualTo("Bi-Weekly Salary")
            assertThat(contentState.recentTransactions.first().formattedAmount).isEqualTo("+$3500.00")
        }
    }
}
