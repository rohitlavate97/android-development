package com.expensetracker.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class TransactionId(val value: String)

@JvmInline
@Serializable
value class CategoryId(val value: String)

@JvmInline
@Serializable
value class AccountId(val value: String)

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER
}

enum class CurrencyCode(val symbol: String) {
    USD("$"),
    EUR("€"),
    INR("₹"),
    GBP("£")
}

@Serializable
data class Category(
    val id: CategoryId,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val type: TransactionType
)

@Serializable
data class Transaction(
    val id: TransactionId,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: Category,
    val accountId: AccountId,
    val timestamp: Instant,
    val note: String? = null,
    val isTaxDeductible: Boolean = false
) {
    init {
        require(amount >= 0) { "Transaction amount must be non-negative: $amount" }
        require(title.isNotBlank()) { "Transaction title cannot be blank" }
    }
}

@Serializable
data class Account(
    val id: AccountId,
    val name: String,
    val balance: Double,
    val currency: CurrencyCode,
    val accountType: String
)

@Serializable
data class Budget(
    val categoryId: CategoryId,
    val monthlyLimit: Double,
    val currentSpent: Double
) {
    val progress: Float
        get() = if (monthlyLimit > 0) (currentSpent / monthlyLimit).toFloat().coerceIn(0f, 1f) else 0f

    val isOverBudget: Boolean
        get() = currentSpent > monthlyLimit
}

@Serializable
data class ExpenseSummary(
    val totalIncome: Double,
    val totalExpense: Double,
    val netBalance: Double,
    val topCategories: List<CategorySpending>,
    val recentTransactions: List<Transaction>
)

@Serializable
data class CategorySpending(
    val category: Category,
    val totalAmount: Double,
    val percentage: Float
)
