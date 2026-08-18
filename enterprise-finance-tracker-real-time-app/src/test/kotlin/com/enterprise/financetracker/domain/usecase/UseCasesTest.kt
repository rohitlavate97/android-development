package com.enterprise.financetracker.domain.usecase

import app.cash.turbine.test
import com.enterprise.financetracker.domain.model.*
import com.enterprise.financetracker.domain.repository.ExpenseRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.Test

class FakeExpenseRepository : ExpenseRepository {
    private val flow = MutableStateFlow<List<Transaction>>(emptyList())

    fun emit(list: List<Transaction>) {
        flow.value = list
    }

    override fun observeTransactions(): Flow<List<Transaction>> = flow

    override suspend fun getTransactionById(id: TransactionId): Transaction? {
        return flow.value.find { it.id == id }
    }

    override suspend fun addTransaction(transaction: Transaction) {
        flow.value = listOf(transaction) + flow.value
    }

    override suspend fun deleteTransaction(id: TransactionId) {
        flow.value = flow.value.filterNot { it.id == id }
    }
}

class UseCasesTest {

    private lateinit var fakeRepository: FakeExpenseRepository
    private lateinit var getTransactionsUseCase: GetTransactionsUseCase
    private lateinit var addTransactionUseCase: AddTransactionUseCase
    private lateinit var deleteTransactionUseCase: DeleteTransactionUseCase
    private lateinit var filterTransactionsUseCase: FilterTransactionsUseCase

    private val sampleCategory = Category(CategoryId("cat_1"), "Tech", "devices", "#2196F3")
    private val sampleTransaction = Transaction(
        id = TransactionId("tx_100"),
        accountId = AccountId("acc_main"),
        title = "SSD Drive",
        amount = 120.00,
        type = TransactionType.Expense,
        category = sampleCategory,
        timestamp = Instant.fromEpochMilliseconds(1738000000000L)
    )

    @Before
    fun setUp() {
        fakeRepository = FakeExpenseRepository()
        getTransactionsUseCase = GetTransactionsUseCase(fakeRepository)
        addTransactionUseCase = AddTransactionUseCase(fakeRepository)
        deleteTransactionUseCase = DeleteTransactionUseCase(fakeRepository)
        filterTransactionsUseCase = FilterTransactionsUseCase()
    }

    @Test
    fun given_use_case_when_addTransaction_called_then_repository_emits_updated_list() = runTest {
        getTransactionsUseCase().test {
            assertThat(awaitItem()).isEmpty()

            // Act: Add transaction via UseCase
            val result = addTransactionUseCase(sampleTransaction)
            assertThat(result).isInstanceOf(FinancialResult.Success::class.java)

            // Assert: Flow emits updated list
            val emitted = awaitItem()
            assertThat(emitted).hasSize(1)
            assertThat(emitted.first().title).isEqualTo("SSD Drive")
        }
    }

    @Test
    fun given_transactions_when_filterUseCase_filters_by_query_then_return_matching_items() {
        val incomeTx = sampleTransaction.copy(
            id = TransactionId("tx_101"),
            title = "Freelance Consulting",
            type = TransactionType.Income
        )
        val list = listOf(sampleTransaction, incomeTx)

        val filtered = filterTransactionsUseCase(list, query = "Freelance", filter = "ALL")
        assertThat(filtered).hasSize(1)
        assertThat(filtered.first().title).isEqualTo("Freelance Consulting")
    }
}
