package com.enterprise.financetracker.data.repository

import com.enterprise.financetracker.core.concurrency.DispatcherProvider
import com.enterprise.financetracker.data.datasource.TransactionLocalDataSource
import com.enterprise.financetracker.data.mapper.toDomain
import com.enterprise.financetracker.data.mapper.toDto
import com.enterprise.financetracker.domain.model.*
import com.enterprise.financetracker.domain.repository.ExpenseRepository
import com.enterprise.financetracker.domain.repository.PortfolioRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Concrete implementation of ExpenseRepository (Data Layer).
 * Coordinates Local DataSource and applies boundary mappers.
 * (Phase 5 Concept 4 & Single Source of Truth)
 */
class ExpenseRepositoryImpl(
    private val localDataSource: TransactionLocalDataSource,
    private val dispatchers: DispatcherProvider
) : ExpenseRepository {

    override fun observeTransactions(): Flow<List<Transaction>> {
        return localDataSource.observeTransactions()
            .map { dtoList -> dtoList.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun getTransactionById(id: TransactionId): Transaction? = withContext(dispatchers.io) {
        localDataSource.getTransactionById(id.value)?.toDomain()
    }

    override suspend fun addTransaction(transaction: Transaction) = withContext(dispatchers.io) {
        localDataSource.insertTransaction(transaction.toDto())
    }

    override suspend fun deleteTransaction(id: TransactionId) = withContext(dispatchers.io) {
        localDataSource.deleteTransaction(id.value)
    }
}

class PortfolioRepositoryImpl(
    private val dispatchers: DispatcherProvider
) : PortfolioRepository {

    private val basePortfolio = Portfolio(
        id = PortfolioId("port_growth"),
        name = "Primary Tech & Crypto Portfolio",
        holdings = listOf(
            InvestmentHolding(
                ticker = TickerSymbol("AAPL"),
                name = "Apple Inc.",
                shares = 25.0,
                averageBuyPrice = 160.00,
                currentMarketPrice = 195.00,
                assetClass = AssetClass.EQUITY
            ),
            InvestmentHolding(
                ticker = TickerSymbol("NVDA"),
                name = "NVIDIA Corporation",
                shares = 15.0,
                averageBuyPrice = 90.00,
                currentMarketPrice = 135.00,
                assetClass = AssetClass.EQUITY
            ),
            InvestmentHolding(
                ticker = TickerSymbol("BTC"),
                name = "Bitcoin",
                shares = 0.45,
                averageBuyPrice = 58000.00,
                currentMarketPrice = 64000.00,
                assetClass = AssetClass.CRYPTO
            )
        )
    )

    override fun observePortfolio(): Flow<Portfolio> = flow {
        emit(basePortfolio)
        while (currentCoroutineContext().isActive) {
            delay(4000) // Periodic price updates
            val updatedHoldings = basePortfolio.holdings.map { holding ->
                val deltaPercent = Random.nextDouble(-0.015, 0.015)
                val newPrice = (holding.currentMarketPrice * (1.0 + deltaPercent)).coerceAtLeast(1.0)
                holding.copy(currentMarketPrice = newPrice)
            }
            emit(basePortfolio.copy(holdings = updatedHoldings))
        }
    }.flowOn(dispatchers.default)
}
