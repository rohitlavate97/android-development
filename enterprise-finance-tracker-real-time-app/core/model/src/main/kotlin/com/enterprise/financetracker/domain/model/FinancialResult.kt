package com.enterprise.financetracker.domain.model

sealed interface FinancialResult<out T> {

    data class Success<out T>(val value: T) : FinancialResult<T>

    sealed interface Failure : FinancialResult<Nothing> {
        data class NetworkError(val message: String, val cause: Throwable? = null) : Failure
        data class ValidationError(val message: String, val field: String? = null) : Failure
        data class Unauthorized(val message: String = "Session expired. Please log in.") : Failure
        data class InsufficientFunds(val available: Double, val required: Double) : Failure
        data class UnexpectedError(val message: String, val cause: Throwable? = null) : Failure
    }
}
