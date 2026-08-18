package com.enterprise.financetracker.concurrency

import app.cash.turbine.test
import com.enterprise.financetracker.core.concurrency.DispatcherProvider
import com.enterprise.financetracker.core.concurrency.safeSuspendCall
import com.enterprise.financetracker.data.repository.InMemoryReactiveTransactionRepository
import com.enterprise.financetracker.domain.model.*
import com.enterprise.financetracker.ui.viewmodels.TransactionListUiState
import com.enterprise.financetracker.ui.viewmodels.TransactionListViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class TestDispatcherProvider(val testDispatcher: TestDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = testDispatcher
    override val mainImmediate: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val unconfined: CoroutineDispatcher = testDispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionFlowTest {

    private lateinit var testDispatcher: StandardTestDispatcher
    private lateinit var dispatchers: TestDispatcherProvider
    private lateinit var repository: InMemoryReactiveTransactionRepository
    private lateinit var viewModel: TransactionListViewModel

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        dispatchers = TestDispatcherProvider(testDispatcher)
        repository = InMemoryReactiveTransactionRepository(dispatchers)
        viewModel = TransactionListViewModel(repository, dispatchers)
    }

    @Test
    fun given_reactive_repository_when_observing_uiState_then_emits_initial_transactions() = runTest(testDispatcher) {
        viewModel.uiState.test {
            // 1. Initial State
            assertThat(awaitItem()).isEqualTo(TransactionListUiState.Loading)

            testDispatcher.scheduler.advanceUntilIdle()

            // 2. Success State with 4 transactions
            val state = awaitItem() as TransactionListUiState.Success
            assertThat(state.transactions).hasSize(4)
            assertThat(state.filteredTransactions).hasSize(4)
        }
    }

    @Test
    fun given_transactions_when_filtering_by_Income_then_uiState_updates_reactively() = runTest(testDispatcher) {
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(TransactionListUiState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()
            val initial = awaitItem() as TransactionListUiState.Success
            assertThat(initial.filteredTransactions).hasSize(4)

            // Act: Filter by INCOME
            viewModel.onFilterSelected("INCOME")
            testDispatcher.scheduler.advanceUntilIdle()

            // Assert: Emits filtered list with 1 income transaction
            val updated = awaitItem() as TransactionListUiState.Success
            assertThat(updated.filteredTransactions).hasSize(1)
            assertThat(updated.filteredTransactions.first().title).contains("Paycheck")
        }
    }

    @Test
    fun given_transaction_when_deleted_then_flow_automatically_updates_all_collectors() = runTest(testDispatcher) {
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(TransactionListUiState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()
            val initial = awaitItem() as TransactionListUiState.Success
            assertThat(initial.transactions).hasSize(4)

            // Act: Delete transaction 1
            viewModel.deleteTransaction(TransactionId("tx_1"))
            testDispatcher.scheduler.advanceUntilIdle()

            // Assert: Emits updated list with 3 transactions
            val updated = awaitItem() as TransactionListUiState.Success
            assertThat(updated.transactions).hasSize(3)
            assertThat(updated.transactions.none { it.id.value == "tx_1" }).isTrue()
        }
    }
}

class SafeSuspendCallTest {

    @Test
    fun given_cancellation_exception_when_safeSuspendCall_executes_then_rethrow_without_swallowing() = runTest {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                safeSuspendCall<Unit> {
                    throw CancellationException("Simulated job cancellation")
                }
            }
        }
    }

    @Test
    fun given_generic_exception_when_safeSuspendCall_executes_then_return_Result_failure() = runTest {
        val result = safeSuspendCall<String> {
            throw IllegalStateException("Business validation failed")
        }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }
}
