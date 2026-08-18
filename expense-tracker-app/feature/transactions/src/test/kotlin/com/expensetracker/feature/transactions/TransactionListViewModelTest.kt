package com.expensetracker.feature.transactions

import app.cash.turbine.test
import com.expensetracker.core.common.DispatcherProvider
import com.expensetracker.core.common.Resource
import com.expensetracker.core.model.*
import com.expensetracker.feature.transactions.domain.*
import com.expensetracker.feature.transactions.presentation.TransactionListIntent
import com.expensetracker.feature.transactions.presentation.TransactionListUiState
import com.expensetracker.feature.transactions.presentation.TransactionListViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.Test

class TestDispatcherProvider(val testDispatcher: TestDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = testDispatcher
    override val mainImmediate: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val unconfined: CoroutineDispatcher = testDispatcher
}

class FakeExpenseRepository : ExpenseRepository {
    private val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())

    fun setTransactions(list: List<Transaction>) {
        transactionsFlow.value = list
    }

    override fun observeTransactions(): Flow<List<Transaction>> = transactionsFlow

    override suspend fun getTransactionById(id: TransactionId): Transaction? {
        return transactionsFlow.value.find { it.id == id }
    }

    override suspend fun addTransaction(transaction: Transaction): Resource<Unit> {
        transactionsFlow.value = listOf(transaction) + transactionsFlow.value
        return Resource.Success(Unit)
    }

    override suspend fun deleteTransaction(id: TransactionId): Resource<Unit> {
        transactionsFlow.value = transactionsFlow.value.filterNot { it.id == id }
        return Resource.Success(Unit)
    }

    override suspend fun syncWithRemote(): Resource<Unit> = Resource.Success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionListViewModelTest {

    private lateinit var fakeRepository: FakeExpenseRepository
    private lateinit var testDispatcher: StandardTestDispatcher
    private lateinit var dispatchers: TestDispatcherProvider

    private val sampleCategory = Category(
        id = CategoryId("cat_test"),
        name = "Test Category",
        iconName = "restaurant",
        colorHex = "#FF5722",
        type = TransactionType.EXPENSE
    )

    private val sampleTransaction = Transaction(
        id = TransactionId("tx_test_1"),
        title = "Coffee",
        amount = 4.50,
        type = TransactionType.EXPENSE,
        category = sampleCategory,
        accountId = AccountId("acc_main"),
        timestamp = Instant.fromEpochMilliseconds(1700000000000L)
    )

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        dispatchers = TestDispatcherProvider(testDispatcher)
        fakeRepository = FakeExpenseRepository()
    }

    @Test
    fun `when repository is empty then uiState emits Empty`() = runTest(testDispatcher) {
        val viewModel = TransactionListViewModel(
            getTransactionsUseCase = GetTransactionsUseCase(fakeRepository),
            addTransactionUseCase = AddTransactionUseCase(fakeRepository),
            deleteTransactionUseCase = DeleteTransactionUseCase(fakeRepository),
            syncTransactionsUseCase = SyncTransactionsUseCase(fakeRepository),
            dispatchers = dispatchers
        )

        viewModel.uiState.test {
            // Initial loading state
            assertThat(awaitItem()).isEqualTo(TransactionListUiState.Loading)

            // Let coroutines run
            testDispatcher.scheduler.advanceUntilIdle()

            // State updates to Empty
            assertThat(awaitItem()).isEqualTo(TransactionListUiState.Empty)
        }
    }

    @Test
    fun `when transaction added then uiState updates to Content with calculated total`() = runTest(testDispatcher) {
        val viewModel = TransactionListViewModel(
            getTransactionsUseCase = GetTransactionsUseCase(fakeRepository),
            addTransactionUseCase = AddTransactionUseCase(fakeRepository),
            deleteTransactionUseCase = DeleteTransactionUseCase(fakeRepository),
            syncTransactionsUseCase = SyncTransactionsUseCase(fakeRepository),
            dispatchers = dispatchers
        )

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(TransactionListUiState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(TransactionListUiState.Empty)

            // Act: Add transaction
            viewModel.processIntent(TransactionListIntent.AddNewTransaction(sampleTransaction))
            testDispatcher.scheduler.advanceUntilIdle()

            // Assert: Emits Content state
            val contentState = awaitItem() as TransactionListUiState.Content
            assertThat(contentState.transactions).hasSize(1)
            assertThat(contentState.transactions.first().title).isEqualTo("Coffee")
            assertThat(contentState.totalAmount).isEqualTo(-4.50)
        }
    }

    @Test
    fun `when filtering by Income then only income transactions are in filtered list`() = runTest(testDispatcher) {
        val incomeTransaction = sampleTransaction.copy(
            id = TransactionId("tx_income"),
            title = "Freelance",
            amount = 500.0,
            type = TransactionType.INCOME
        )
        fakeRepository.setTransactions(listOf(sampleTransaction, incomeTransaction))

        val viewModel = TransactionListViewModel(
            getTransactionsUseCase = GetTransactionsUseCase(fakeRepository),
            addTransactionUseCase = AddTransactionUseCase(fakeRepository),
            deleteTransactionUseCase = DeleteTransactionUseCase(fakeRepository),
            syncTransactionsUseCase = SyncTransactionsUseCase(fakeRepository),
            dispatchers = dispatchers
        )

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(TransactionListUiState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()

            val initialContent = awaitItem() as TransactionListUiState.Content
            assertThat(initialContent.filteredTransactions).hasSize(2)

            // Act: Filter by Income
            viewModel.processIntent(TransactionListIntent.FilterByType(TransactionType.INCOME))
            testDispatcher.scheduler.advanceUntilIdle()

            val filteredContent = awaitItem() as TransactionListUiState.Content
            assertThat(filteredContent.filteredTransactions).hasSize(1)
            assertThat(filteredContent.filteredTransactions.first().title).isEqualTo("Freelance")
        }
    }
}
