package com.enterprise.financetracker.domain.model

/**
 * Strongly-typed domain identifiers using Kotlin value classes.
 * Compiles to primitive String under the hood with zero heap allocation overhead.
 * (Phase 1 Concept 14 & ADR 001)
 */
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
        require(value.isNotBlank() && value.length in 1..10) { 
            "TickerSymbol must be between 1 and 10 alphanumeric characters: '$value'" 
        }
    }
}

@JvmInline
value class PortfolioId(val value: String) {
    init {
        require(value.isNotBlank()) { "PortfolioId cannot be blank" }
    }
}
