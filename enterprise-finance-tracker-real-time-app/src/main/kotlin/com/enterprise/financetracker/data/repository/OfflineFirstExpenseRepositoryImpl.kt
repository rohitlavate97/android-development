package com.enterprise.financetracker.data.repository

import com.enterprise.financetracker.core.concurrency.DispatcherProvider
import com.enterprise.financetracker.data.datasource.TransactionRemoteDataSource
import com.enterprise.financetracker.data.local.dao.CategoryDao
import com.enterprise.financetracker.data.local.dao.TransactionDao
import com.enterprise.financetracker.data.mapper.toDomain
import com.enterprise.financetracker.data.mapper.toEntity
import com.enterprise.financetracker.domain.model.FinancialResult
import com.enterprise.financetracker.domain.model.Transaction
import com.enterprise.financetracker.domain.model.TransactionId
import com.enterprise.financetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/**
 * Offline-First Single Source of Truth (SSOT) Expense Repository.
 *
 * Rules:
 * 1. UI observes ONLY the local Room database (observeTransactions).
 * 2. Remote API responses write strictly into Room.
 * 3. Flow emits updated values automatically to all UI collectors.
 *
 * (Phase 8 Concept 6 & ADR 021)
 */
class OfflineFirstExpenseRepositoryImpl(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val remoteDataSource: TransactionRemoteDataSource,
    private val dispatchers: DispatcherProvider
) : ExpenseRepository {

    override fun observeTransactions(): Flow<List<Transaction>> {
        return transactionDao.observeAllWithCategory()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun getTransactionById(id: TransactionId): Transaction? = withContext(dispatchers.io) {
        transactionDao.getByIdWithCategory(id.value)?.toDomain()
    }

    override suspend fun addTransaction(transaction: Transaction) = withContext(dispatchers.io) {
        categoryDao.insert(transaction.category.toEntity())
        transactionDao.insert(transaction.toEntity())
    }

    override suspend fun deleteTransaction(id: TransactionId) = withContext(dispatchers.io) {
        transactionDao.deleteById(id.value)
    }

    /**
     * Reconciles remote transactions with local Room cache.
     */
    suspend fun syncTransactions(): FinancialResult<Unit> = withContext(dispatchers.io) {
        when (val result = remoteDataSource.fetchTransactions()) {
            is FinancialResult.Success -> {
                val entityPairs = result.value.map { it.toEntity() }
                val categories = entityPairs.map { it.second }
                val transactions = entityPairs.map { it.first }

                categoryDao.insertAll(categories)
                transactionDao.insertAll(transactions)
                FinancialResult.Success(Unit)
            }
            is FinancialResult.Failure -> result
        }
    }
}
