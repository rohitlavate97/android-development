package com.enterprise.financetracker.domain.usecase

import com.enterprise.financetracker.domain.model.*
import com.enterprise.financetracker.domain.repository.ExpenseRepository
import com.enterprise.financetracker.domain.repository.PortfolioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Domain Use Cases with operator fun invoke().
 * Represents a single executable business operation with zero framework dependencies.
 * (Phase 5 Concept 6 & ADR 013)
 */

class GetTransactionsUseCase(
    private val repository: ExpenseRepository
) {
    operator fun invoke(): Flow<List<Transaction>> {
        return repository.observeTransactions()
    }
}

class GetTransactionDetailUseCase(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(id: TransactionId): FinancialResult<Transaction> {
        val transaction = repository.getTransactionById(id)
        return if (transaction != null) {
            FinancialResult.Success(transaction)
        } else {
            FinancialResult.Failure.ValidationError("Transaction with id '${id.value}' not found")
        }
    }
}

class AddTransactionUseCase(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(transaction: Transaction): FinancialResult<Transaction> {
        return try {
            repository.addTransaction(transaction)
            FinancialResult.Success(transaction)
        } catch (e: Exception) {
            FinancialResult.Failure.UnexpectedError("Failed to save transaction: ${e.message}", e)
        }
    }
}

class DeleteTransactionUseCase(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(id: TransactionId): FinancialResult<Unit> {
        return try {
            repository.deleteTransaction(id)
            FinancialResult.Success(Unit)
        } catch (e: Exception) {
            FinancialResult.Failure.UnexpectedError("Failed to delete transaction: ${e.message}", e)
        }
    }
}

data class PortfolioSummary(
    val netWorth: Double,
    val liquidCash: Double,
    val investedValue: Double,
    val portfolio: Portfolio
)

class GetPortfolioSummaryUseCase(
    private val portfolioRepository: PortfolioRepository
) {
    operator fun invoke(liquidCash: Double = 12450.00): Flow<PortfolioSummary> {
        return portfolioRepository.observePortfolio().map { portfolio ->
            PortfolioSummary(
                netWorth = liquidCash + portfolio.totalPortfolioValue,
                liquidCash = liquidCash,
                investedValue = portfolio.totalPortfolioValue,
                portfolio = portfolio
            )
        }
    }
}

class FilterTransactionsUseCase {
    operator fun invoke(
        transactions: List<Transaction>,
        query: String,
        filter: String
    ): List<Transaction> {
        return transactions.filter { tx ->
            val matchesQuery = tx.title.contains(query, ignoreCase = true) ||
                    tx.category.name.contains(query, ignoreCase = true)
            val matchesFilter = when (filter.uppercase()) {
                "EXPENSE" -> tx.type is TransactionType.Expense
                "INCOME" -> tx.type is TransactionType.Income
                "TRANSFER" -> tx.type is TransactionType.Transfer
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }
}
