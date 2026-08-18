package com.expensetracker.feature.transactions.domain

import com.expensetracker.core.common.DispatcherProvider
import com.expensetracker.core.common.Resource
import com.expensetracker.core.database.ExpenseDatabase
import com.expensetracker.core.database.toDomain
import com.expensetracker.core.database.toEntity
import com.expensetracker.core.model.*
import com.expensetracker.core.network.ExpenseRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Single Source of Truth (SSOT) Repository (Phase 5 & Phase 8).
 * UI only observes local Room database; network writes to Room database.
 */
interface ExpenseRepository {
    fun observeTransactions(): Flow<List<Transaction>>
    suspend fun getTransactionById(id: TransactionId): Transaction?
    suspend fun addTransaction(transaction: Transaction): Resource<Unit>
    suspend fun deleteTransaction(id: TransactionId): Resource<Unit>
    suspend fun syncWithRemote(): Resource<Unit>
}

class ExpenseRepositoryImpl(
    private val database: ExpenseDatabase,
    private val remoteDataSource: ExpenseRemoteDataSource,
    private val dispatchers: DispatcherProvider
) : ExpenseRepository {

    override fun observeTransactions(): Flow<List<Transaction>> {
        return database.transactionDao().observeAllTransactions()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun getTransactionById(id: TransactionId): Transaction? = withContext(dispatchers.io) {
        database.transactionDao().getTransactionById(id.value)?.toDomain()
    }

    override suspend fun addTransaction(transaction: Transaction): Resource<Unit> = withContext(dispatchers.io) {
        try {
            database.categoryDao().upsertCategories(listOf(transaction.category.toEntity()))
            database.transactionDao().upsertTransaction(transaction.toEntity())
            // Sync async to remote
            remoteDataSource.syncTransaction(transaction)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to save transaction", e)
        }
    }

    override suspend fun deleteTransaction(id: TransactionId): Resource<Unit> = withContext(dispatchers.io) {
        try {
            database.transactionDao().deleteTransactionById(id.value)
            remoteDataSource.deleteTransaction(id)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete transaction", e)
        }
    }

    override suspend fun syncWithRemote(): Resource<Unit> = withContext(dispatchers.io) {
        when (val remoteResult = remoteDataSource.fetchTransactions()) {
            is Resource.Success -> {
                val categories = remoteResult.data.map { it.category.toEntity() }.distinctBy { it.id }
                val transactions = remoteResult.data.map { it.toEntity() }
                database.categoryDao().upsertCategories(categories)
                database.transactionDao().upsertTransactions(transactions)
                Resource.Success(Unit)
            }
            is Resource.Error -> Resource.Error(remoteResult.message, remoteResult.cause)
            Resource.Loading -> Resource.Loading
        }
    }
}

// Use Cases with operator fun invoke() (Phase 1 Concept 10 & Phase 5)

class GetTransactionsUseCase(private val repository: ExpenseRepository) {
    operator fun invoke(): Flow<List<Transaction>> = repository.observeTransactions()
}

class AddTransactionUseCase(private val repository: ExpenseRepository) {
    suspend operator fun invoke(transaction: Transaction): Resource<Unit> {
        return repository.addTransaction(transaction)
    }
}

class DeleteTransactionUseCase(private val repository: ExpenseRepository) {
    suspend operator fun invoke(id: TransactionId): Resource<Unit> {
        return repository.deleteTransaction(id)
    }
}

class SyncTransactionsUseCase(private val repository: ExpenseRepository) {
    suspend operator fun invoke(): Resource<Unit> {
        return repository.syncWithRemote()
    }
}
