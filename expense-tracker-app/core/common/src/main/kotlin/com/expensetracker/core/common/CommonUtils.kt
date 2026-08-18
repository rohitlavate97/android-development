package com.expensetracker.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.cancellation.CancellationException

/**
 * Interface to inject Coroutine dispatchers for main-safety and deterministic testing.
 * (Phase 2 & Phase 10 best practice)
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val mainImmediate: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

class StandardDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val mainImmediate: CoroutineDispatcher = Dispatchers.Main.immediate
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

/**
 * Sealed result hierarchy for domain and network operations.
 * (Phase 1 & Phase 7)
 */
sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>
    data object Loading : Resource<Nothing>
}

/**
 * Safe API wrapper that NEVER swallows CancellationException.
 * (Phase 2 & Phase 7 golden rule)
 */
inline fun <T> safeCall(action: () -> T): Resource<T> {
    return try {
        Resource.Success(action())
    } catch (e: CancellationException) {
        throw e // Re-throw cancellation to let structured concurrency work!
    } catch (e: Exception) {
        Resource.Error(message = e.message ?: "An unexpected error occurred", cause = e)
    }
}

/**
 * Suspended version of safeCall for async executions.
 */
suspend inline fun <T> safeSuspendCall(crossinline action: suspend () -> T): Resource<T> {
    return try {
        Resource.Success(action())
    } catch (e: CancellationException) {
        throw e // NEVER swallow CancellationException
    } catch (e: Exception) {
        Resource.Error(message = e.message ?: "An unexpected error occurred", cause = e)
    }
}
