package com.enterprise.financetracker.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

sealed interface TransactionType {
    data object Income : TransactionType
    data object Expense : TransactionType
    data class Transfer(val targetAccountId: AccountId) : TransactionType
}

data class Category(
    val id: CategoryId,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val isDefault: Boolean = false
) {
    init {
        require(name.isNotBlank()) { "Category name cannot be blank" }
        require(colorHex.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$"))) {
            "Invalid color HEX format: $colorHex"
        }
    }
}

data class Transaction(
    val id: TransactionId,
    val accountId: AccountId,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: Category,
    val timestamp: Instant,
    val note: String? = null,
    val tags: Set<String> = emptySet(),
    val isRecurring: Boolean = false
) {
    init {
        require(title.isNotBlank()) { "Transaction title cannot be blank" }
        require(amount > 0.0) { "Transaction amount must be strictly positive: $amount" }
        require(!amount.isNaN() && !amount.isInfinite()) { "Amount must be a finite number" }
    }
}

data class Budget(
    val categoryId: CategoryId,
    val monthlyLimit: Double,
    val currentSpent: Double = 0.0
) {
    init {
        require(monthlyLimit > 0) { "Monthly limit must be positive" }
        require(currentSpent >= 0) { "Current spent cannot be negative" }
    }

    val percentageUsed: Float get() = ((currentSpent / monthlyLimit) * 100).toFloat().coerceIn(0f, 100f)
    val isExceeded: Boolean get() = currentSpent > monthlyLimit
}

enum class AccountType {
    CHECKING,
    SAVINGS,
    CREDIT_CARD,
    INVESTMENT,
    CRYPTO_WALLET
}

data class Account(
    val id: AccountId,
    val name: String,
    val type: AccountType,
    val balance: Double,
    val currency: String = "USD"
) {
    init {
        require(name.isNotBlank()) { "Account name cannot be blank" }
    }
}

enum class AssetClass {
    EQUITY,
    CRYPTO,
    COMMODITY,
    FIXED_INCOME,
    REAL_ESTATE
}

data class InvestmentHolding(
    val ticker: TickerSymbol,
    val name: String,
    val shares: Double,
    val averageBuyPrice: Double,
    val currentMarketPrice: Double,
    val assetClass: AssetClass = AssetClass.EQUITY
) {
    init {
        require(shares > 0) { "Shares must be positive: $shares" }
        require(averageBuyPrice >= 0) { "Buy price cannot be negative" }
        require(currentMarketPrice >= 0) { "Market price cannot be negative" }
    }

    val totalInvestedCost: Double get() = shares * averageBuyPrice
    val currentMarketValue: Double get() = shares * currentMarketPrice
    val unrealizedProfitLoss: Double get() = currentMarketValue - totalInvestedCost
    val returnPercentage: Double get() = if (totalInvestedCost == 0.0) 0.0 else ((unrealizedProfitLoss / totalInvestedCost) * 100.0)
}

data class Portfolio(
    val id: PortfolioId,
    val name: String,
    val holdings: List<InvestmentHolding> = emptyList()
) {
    val totalPortfolioValue: Double get() = holdings.sumOf { it.currentMarketValue }

    fun calculateAllocation(holding: InvestmentHolding): Float {
        val total = totalPortfolioValue
        return if (total == 0.0) 0f else (holding.currentMarketValue / total).toFloat().coerceIn(0f, 1f)
    }
}
