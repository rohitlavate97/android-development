package com.enterprise.financetracker.domain.model

@JvmInline
value class TransactionId(val value: String) {
    init {
        require(value.isNotBlank()) { "TransactionId cannot be blank" }
    }
}

@JvmInline
value class AccountId(val value: String) {
    init {
        require(value.isNotBlank()) { "AccountId cannot be blank" }
    }
}

@JvmInline
value class CategoryId(val value: String) {
    init {
        require(value.isNotBlank()) { "CategoryId cannot be blank" }
    }
}

@JvmInline
value class TickerSymbol(val value: String) {
    init {
        require(value.isNotBlank()) { "TickerSymbol cannot be blank" }
        require(value.length in 1..10) { "TickerSymbol length must be between 1 and 10 characters" }
    }
}

@JvmInline
value class PortfolioId(val value: String) {
    init {
        require(value.isNotBlank()) { "PortfolioId cannot be blank" }
    }
}
