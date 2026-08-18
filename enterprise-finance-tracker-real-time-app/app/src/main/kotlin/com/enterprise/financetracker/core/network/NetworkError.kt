package com.enterprise.financetracker.core.network

import com.enterprise.financetracker.domain.model.FinancialResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The 6 Canonical Network Failure Paths.
 * (Phase 7 Concept 6 & Stage 7 Requirements)
 */
sealed interface NetworkError {
    /** Failure Path 1: 401 Unauthorized / Token Expired */
    data class Unauthorized(val message: String = "Session expired. Please log in again.") : NetworkError

    /** Failure Path 2: 404 Resource Not Found */
    data class NotFound(val message: String = "The requested resource was not found.") : NetworkError

    /** Failure Path 3: 500+ Internal Server Error */
    data class ServerError(val code: Int, val message: String = "Internal server error occurred ($code).") : NetworkError

    /** Failure Path 4: Connection / Read / Write Socket Timeout */
    data class Timeout(val message: String = "Connection timed out. Please check your network and retry.") : NetworkError

    /** Failure Path 5: No Internet Connection / DNS Lookup Failure */
    data class NoInternet(val message: String = "No internet connection detected.") : NetworkError

    /** Failure Path 6: Malformed JSON / Schema Serialization Mismatch */
    data class MalformedJson(val message: String = "Failed to parse server response schema.", val cause: Throwable) : NetworkError
}

/**
 * Maps Retrofit/OkHttp exceptions to typed FinancialResult failures while strictly
 * preserving Coroutine CancellationException. (ADR 012 & ADR 020)
 */
suspend fun <T> safeApiCall(apiCall: suspend () -> T): FinancialResult<T> {
    return try {
        val result = apiCall()
        FinancialResult.Success(result)
    } catch (e: CancellationException) {
        throw e // Mandatory coroutine cancellation preservation
    } catch (e: HttpException) {
        when (e.code()) {
            401 -> FinancialResult.Failure.Unauthorized(e.message())
            404 -> FinancialResult.Failure.ValidationError("Resource not found: ${e.message()}")
            in 500..599 -> FinancialResult.Failure.UnexpectedError("Server error (${e.code()}): ${e.message()}", e)
            else -> FinancialResult.Failure.UnexpectedError("HTTP ${e.code()}: ${e.message()}", e)
        }
    } catch (e: SocketTimeoutException) {
        FinancialResult.Failure.NetworkError("Network request timed out", e)
    } catch (e: UnknownHostException) {
        FinancialResult.Failure.NetworkError("No internet connection available", e)
    } catch (e: SerializationException) {
        FinancialResult.Failure.UnexpectedError("Malformed JSON response schema: ${e.message}", e)
    } catch (e: IOException) {
        FinancialResult.Failure.NetworkError("I/O network communication failure: ${e.message}", e)
    } catch (e: Exception) {
        FinancialResult.Failure.UnexpectedError("Unexpected network failure: ${e.message}", e)
    }
}
