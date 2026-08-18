package com.enterprise.financetracker.domain.model

import kotlinx.datetime.Instant

sealed interface TransactionType {
    data object Expense : TransactionType
    data object Income : TransactionType
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
        require(colorHex.startsWith("#") && (colorHex.length == 7 || colorHex.length == 9)) {
            "Invalid hex color format: $colorHex"
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
        require(amount > 0.0) { "Transaction amount must be strictly positive: $amount" }
        require(title.isNotBlank()) { "Transaction title cannot be blank" }
    }

    val netBalanceImpact: Double
        get() = when (type) {
            is TransactionType.Income -> amount
            is TransactionType.Expense -> -amount
            is TransactionType.Transfer -> -amount // Debits source account
        }
}

data class Budget(
    val categoryId: CategoryId,
    val monthlyLimit: Double,
    val currentSpent: Double
) {
    init {
        require(monthlyLimit > 0.0) { "Monthly budget limit must be positive: $monthlyLimit" }
        require(currentSpent >= 0.0) { "Current spent cannot be negative: $currentSpent" }
    }

    val progress: Float
        get() = (currentSpent / monthlyLimit).toFloat().coerceIn(0f, 1f)

    val isOverBudget: Boolean
        get() = currentSpent > monthlyLimit

    val remainingBalance: Double
        get() = (monthlyLimit - currentSpent).coerceAtLeast(0.0)
}
