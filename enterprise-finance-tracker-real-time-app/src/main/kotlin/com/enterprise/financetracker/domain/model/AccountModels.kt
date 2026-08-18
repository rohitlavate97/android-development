package com.enterprise.financetracker.domain.model

enum class CurrencyCode(val symbol: String, val displayName: String) {
    USD("$", "US Dollar"),
    EUR("€", "Euro"),
    INR("₹", "Indian Rupee"),
    GBP("£", "British Pound"),
    JPY("¥", "Japanese Yen")
}

sealed interface AccountType {
    data object Checking : AccountType
    data object Savings : AccountType
    data class CreditCard(val creditLimit: Double) : AccountType
    data object Investment : AccountType
}

data class Account(
    val id: AccountId,
    val name: String,
    val balance: Double,
    val currency: CurrencyCode,
    val type: AccountType,
    val isArchived: Boolean = false
) {
    init {
        require(name.isNotBlank()) { "Account name cannot be blank" }
        if (type is AccountType.CreditCard) {
            require(type.creditLimit > 0.0) { "Credit limit must be positive: ${type.creditLimit}" }
        }
    }
}
