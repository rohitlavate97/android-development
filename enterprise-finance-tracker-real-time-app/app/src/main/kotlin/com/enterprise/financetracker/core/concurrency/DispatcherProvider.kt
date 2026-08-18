package com.enterprise.financetracker.core.concurrency

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.cancellation.CancellationException

/**
 * Injected dispatcher provider ensuring main-safety and testability without Thread.sleep().
 * (Phase 2 Concept 6 & ADR 010)
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
 * Safe suspend invocation that strictly preserves CancellationException.
 * Swallowing CancellationException breaks structured concurrency, parent job cancellation,
 * and causes coroutine leaks. (Phase 2 Concept 4 & ADR 012)
 */
suspend inline fun <T> safeSuspendCall(crossinline action: suspend () -> T): Result<T> {
    return try {
        Result.success(action())
    } catch (e: CancellationException) {
        throw e // ALWAYS rethrow CancellationException!
    } catch (e: Exception) {
        Result.failure(e)
    }
}
