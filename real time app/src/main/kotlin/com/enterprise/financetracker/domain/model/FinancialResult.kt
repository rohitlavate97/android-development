package com.enterprise.financetracker.domain.model

/**
 * Idiomatic Kotlin Result hierarchy for Domain and Business operations.
 * (Phase 1 Concept 10 & Phase 7)
 */
sealed interface FinancialResult<out T> {
    data class Success<out T>(val value: T) : FinancialResult<T>
    
    sealed interface Failure : FinancialResult<Nothing> {
        val message: String
        
        data class ValidationError(override val message: String, val fieldName: String? = null) : Failure
        data class InsufficientFunds(override val message: String, val available: Double, val required: Double) : Failure
        data class AccountNotFound(override val message: String, val accountId: AccountId) : Failure
        data class NetworkUnavailable(override val message: String) : Failure
        data class UnexpectedError(override val message: String, val cause: Throwable? = null) : Failure
    }
}

inline fun <T, R> FinancialResult<T>.map(transform: (T) -> R): FinancialResult<R> {
    return when (this) {
        is FinancialResult.Success -> FinancialResult.Success(transform(value))
        is FinancialResult.Failure -> this
    }
}

inline fun <T> FinancialResult<T>.onSuccess(action: (T) -> Unit): FinancialResult<T> {
    if (this is FinancialResult.Success) action(value)
    return this
}

inline fun <T> FinancialResult<T>.onFailure(action: (FinancialResult.Failure) -> Unit): FinancialResult<T> {
    if (this is FinancialResult.Failure) action(this)
    return this
}
