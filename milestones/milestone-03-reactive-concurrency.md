# Milestone 3: Reactive Concurrency

## Title, Goal & Phase Alignment
**Goal**: Manage asynchronous data streams and threading safely using Coroutines and Kotlin Flow.
**Phase**: Phase 3 (Expense Tracker v3)

## Architecture & Component Blueprint
- **Coroutines & Flow**: Use `Flow` for continuous data streams and suspend functions for one-shot operations.
- **StateFlow & Sharing**: Expose UI state using `StateFlow` with optimal lifecycle-aware caching.
- **Dispatcher Injection**: Abstract dispatchers behind a `DispatcherProvider` for testability.
- **Strict Cancellation**: Properly handle `CancellationException` to avoid breaking coroutine trees.

## Step-by-Step Implementation Instructions
1. Define a `DispatcherProvider` interface and its default implementation.
2. In your ViewModel, transform the repository's `Flow` of domain models into a `StateFlow` of `UiState`.
3. Apply `.stateIn` with `SharingStarted.WhileSubscribed(5000)` to survive configuration changes.
4. Implement an in-memory reactive cache using `MutableStateFlow` in the repository.
5. Audit all `try-catch` blocks to ensure `CancellationException` is rethrown.

## Code Snippets & Signatures
```kotlin
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

class TransactionViewModel(
    repository: TransactionRepository,
    dispatchers: DispatcherProvider
) : ViewModel() {

    val uiState: StateFlow<TransactionUiState> = repository.getTransactions()
        .map { TransactionUiState.Content(it) }
        .catch { emit(TransactionUiState.Error(it.message ?: "Unknown Error")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TransactionUiState.Loading
        )
}

// Correct Exception handling
suspend fun safeFetch() {
    try {
        api.fetchData()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        // Handle other exceptions
    }
}
```

## Deliberate Bugs to Catch & Debug
- **Bug**: Catching generic `Exception` inside a coroutine without rethrowing `CancellationException`, causing structured concurrency to fail.
- **Bug**: Hardcoding `Dispatchers.IO` inside repositories, making tests flaky or impossible to run properly.
- **Bug**: Using `SharingStarted.Lazily` or `Eagerly` instead of `WhileSubscribed(5000)`, causing active flows in the background when the app is backgrounded.

## Unit Testing Requirements (Given-When-Then)
- **Given** a mocked repository returning data **When** ViewModel initializes **Then** `uiState` transitions from Loading to Content.
- **Given** a failing suspend function **When** invoked **Then** `CancellationException` is propagated appropriately.

## Acceptance Criteria Checklist
- [ ] `StateFlow` used for all UI states.
- [ ] `SharingStarted.WhileSubscribed(5000)` implemented correctly.
- [ ] Dispatchers are injected via an interface.
- [ ] `CancellationException` is explicitly handled in all try-catch blocks.
