package com.enterprise.financetracker.domain.repository

import com.enterprise.financetracker.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Domain Repository Interface.
 * Pure contract defined in Domain, implemented in Data.
 * (Phase 5 Concept 4 & Inward Dependency Rule)
 */
interface ExpenseRepository {
    fun observeTransactions(): Flow<List<Transaction>>
    suspend fun getTransactionById(id: TransactionId): Transaction?
    suspend fun addTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: TransactionId)
}

interface PortfolioRepository {
    fun observePortfolio(): Flow<Portfolio>
}
