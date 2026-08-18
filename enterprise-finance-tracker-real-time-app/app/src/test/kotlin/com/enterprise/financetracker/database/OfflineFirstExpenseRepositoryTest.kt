package com.enterprise.financetracker.database

import app.cash.turbine.test
import com.enterprise.financetracker.concurrency.TestDispatcherProvider
import com.enterprise.financetracker.data.datasource.TransactionRemoteDataSource
import com.enterprise.financetracker.data.local.dao.CategoryDao
import com.enterprise.financetracker.data.local.dao.TransactionDao
import com.enterprise.financetracker.data.local.entity.CategoryEntity
import com.enterprise.financetracker.data.local.entity.TransactionEntity
import com.enterprise.financetracker.data.local.entity.TransactionWithCategory
import com.enterprise.financetracker.data.network.model.NetworkCategoryDto
import com.enterprise.financetracker.data.network.model.NetworkTransactionDto
import com.enterprise.financetracker.data.repository.OfflineFirstExpenseRepositoryImpl
import com.enterprise.financetracker.domain.model.FinancialResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FakeCategoryDao : CategoryDao {
    private val categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    override fun observeAll(): Flow<List<CategoryEntity>> = categories.asStateFlow()
    override suspend fun insertAll(categories: List<CategoryEntity>) {
        this.categories.update { it + categories }
    }
    override suspend fun insert(category: CategoryEntity) {
        this.categories.update { it + category }
    }
}

class FakeTransactionDao : TransactionDao {
    private val transactions = MutableStateFlow<List<TransactionWithCategory>>(emptyList())

    override fun observeAllWithCategory(): Flow<List<TransactionWithCategory>> = transactions.asStateFlow()

    override suspend fun getByIdWithCategory(id: String): TransactionWithCategory? =
        transactions.value.find { it.transaction.id == id }

    override suspend fun insertAll(transactions: List<TransactionEntity>) {
        val converted = transactions.map { tx ->
            TransactionWithCategory(
                transaction = tx,
                category = CategoryEntity(tx.categoryId, "General", "category", "#78909C", false)
            )
        }
        this.transactions.update { converted + it }
    }

    override suspend fun insert(transaction: TransactionEntity) {
        insertAll(listOf(transaction))
    }

    override suspend fun deleteById(id: String) {
        transactions.update { current -> current.filterNot { it.transaction.id == id } }
    }

    override suspend fun clearAll() {
        transactions.value = emptyList()
    }
}

class FakeRemoteDataSource : TransactionRemoteDataSource {
    var remoteList = listOf<NetworkTransactionDto>()

    override suspend fun fetchTransactions(): FinancialResult<List<NetworkTransactionDto>> {
        return FinancialResult.Success(remoteList)
    }

    override suspend fun fetchTransactionById(id: String): FinancialResult<NetworkTransactionDto> {
        return FinancialResult.Success(remoteList.first { it.id == id })
    }

    override suspend fun createTransaction(dto: NetworkTransactionDto): FinancialResult<NetworkTransactionDto> {
        remoteList = listOf(dto) + remoteList
        return FinancialResult.Success(dto)
    }

    override suspend fun deleteTransaction(id: String): FinancialResult<Unit> {
        remoteList = remoteList.filterNot { it.id == id }
        return FinancialResult.Success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstExpenseRepositoryTest {

    private lateinit var testDispatcher: StandardTestDispatcher
    private lateinit var dispatchers: TestDispatcherProvider
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var remoteDataSource: FakeRemoteDataSource
    private lateinit var repository: OfflineFirstExpenseRepositoryImpl

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        dispatchers = TestDispatcherProvider(testDispatcher)
        transactionDao = FakeTransactionDao()
        categoryDao = FakeCategoryDao()
        remoteDataSource = FakeRemoteDataSource()

        repository = OfflineFirstExpenseRepositoryImpl(
            transactionDao = transactionDao,
            categoryDao = categoryDao,
            remoteDataSource = remoteDataSource,
            dispatchers = dispatchers
        )
    }

    @Test
    fun given_empty_local_cache_when_syncTransactions_runs_then_flow_automatically_emits_new_data() = runTest(testDispatcher) {
        repository.observeTransactions().test {
            // 1. Initial State: Local Room cache is empty
            assertThat(awaitItem()).isEmpty()

            // 2. Mock Remote API payload
            remoteDataSource.remoteList = listOf(
                NetworkTransactionDto(
                    id = "tx_sync_1",
                    accountId = "acc_main",
                    title = "Salary Deposit",
                    amount = 5000.00,
                    type = "INCOME",
                    category = NetworkCategoryDto("cat_1", "Salary", "payments", "#4CAF50", true)
                )
            )

            // 3. Act: Run background sync
            repository.syncTransactions()
            testDispatcher.scheduler.advanceUntilIdle()

            // 4. Assert: Flow automatically emitted without polling
            val updated = awaitItem()
            assertThat(updated).hasSize(1)
            assertThat(updated.first().title).isEqualTo("Salary Deposit")
        }
    }
}
