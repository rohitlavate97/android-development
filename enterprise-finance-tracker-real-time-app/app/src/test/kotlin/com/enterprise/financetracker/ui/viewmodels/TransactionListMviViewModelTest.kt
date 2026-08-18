package com.enterprise.financetracker.ui.viewmodels

import app.cash.turbine.test
import com.enterprise.financetracker.concurrency.TestDispatcherProvider
import com.enterprise.financetracker.domain.model.*
import com.enterprise.financetracker.domain.usecase.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionListMviViewModelTest {

    private lateinit var testDispatcher: StandardTestDispatcher
    private lateinit var dispatchers: TestDispatcherProvider
    private lateinit var fakeRepository: FakeExpenseRepository
    private lateinit var viewModel: TransactionListMviViewModel

    private val sampleCategory = Category(CategoryId("cat_food"), "Dining", "restaurant", "#FF5722")
    private val sampleTransaction = Transaction(
        id = TransactionId("tx_mvi_1"),
        accountId = AccountId("acc_checking"),
        title = "Sushi Dinner",
        amount = 85.00,
        type = TransactionType.Expense,
        category = sampleCategory,
        timestamp = Instant.fromEpochMilliseconds(1738000000000L)
    )

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        dispatchers = TestDispatcherProvider(testDispatcher)
        fakeRepository = FakeExpenseRepository()

        val getTransactionsUseCase = GetTransactionsUseCase(fakeRepository)
        val filterTransactionsUseCase = FilterTransactionsUseCase()
        val deleteTransactionUseCase = DeleteTransactionUseCase(fakeRepository)

        viewModel = TransactionListMviViewModel(
            getTransactionsUseCase = getTransactionsUseCase,
            filterTransactionsUseCase = filterTransactionsUseCase,
            deleteTransactionUseCase = deleteTransactionUseCase,
            dispatchers = dispatchers
        )
    }

    @Test
    fun given_empty_repository_when_observing_uiState_then_emits_Empty_state() = runTest(testDispatcher) {
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(TransactionListUiState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(TransactionListUiState.Empty)
        }
    }

    @Test
    fun given_intent_SearchQueryChanged_when_processed_then_emits_filtered_Content_state() = runTest(testDispatcher) {
        fakeRepository.emit(listOf(sampleTransaction))

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(TransactionListUiState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()

            val initialContent = awaitItem() as TransactionListUiState.Content
            assertThat(initialContent.filteredTransactions).hasSize(1)

            // Act: Dispatch Search intent with non-matching query
            viewModel.processIntent(TransactionListIntent.SearchQueryChanged("Pizza"))
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedContent = awaitItem() as TransactionListUiState.Content
            assertThat(updatedContent.filteredTransactions).isEmpty()
            assertThat(updatedContent.searchQuery).isEqualTo("Pizza")
        }
    }
}
