# Milestone 4: Platform Resilience

## Title, Goal & Phase Alignment
**Goal**: Ensure the app gracefully handles Android system constraints like process death, configuration changes, permissions, and background processing.
**Phase**: Phase 4 (Platform Verification)

## Architecture & Component Blueprint
- **Process Death Survival**: Use `SavedStateHandle` to preserve essential ViewModel state (e.g., search queries, active tabs) across system-initiated process termination.
- **Permissions**: Adopt robust runtime permissions flows showing rationales before prompting.
- **Background Work**: Utilize `WorkManager` with `CoroutineWorker` for guaranteed execution of tasks like data syncing.

## Step-by-Step Implementation Instructions
1. Identify transient UI state in ViewModels (like a search filter) and back it with `SavedStateHandle`.
2. Implement an Accompanist or native Compose permission flow that displays a rationale dialog if permission is denied.
3. Create a `SyncWorker` extending `CoroutineWorker` to sync offline transactions.
4. Enqueue the worker in the `Application` class or main activity with network constraints.

## Code Snippets & Signatures
```kotlin
class TransactionViewModel(
    private val savedStateHandle: SavedStateHandle,
    repository: TransactionRepository
) : ViewModel() {

    // Survives process death
    val searchQuery = savedStateHandle.getStateFlow("search_query", "")

    fun updateSearch(query: String) {
        savedStateHandle["search_query"] = query
    }
}

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // perform sync
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.retry()
        }
    }
}
```

## Deliberate Bugs to Catch & Debug
- **Bug**: Storing complex domain models in `SavedStateHandle` exceeding the IPC transaction size limit, resulting in `TransactionTooLargeException`.
- **Bug**: Not respecting `CoroutineWorker` cancellation resulting in runaway background work.
- **Bug**: Prompting for permissions on launch without context/rationale, leading to high denial rates.

## Unit Testing Requirements (Given-When-Then)
- **Given** a process death simulation **When** the ViewModel is recreated **Then** `searchQuery` retains its previous value.
- **Given** failed network conditions **When** `SyncWorker` runs **Then** it returns `Result.retry()`.

## Acceptance Criteria Checklist
- [ ] Key UI states survive simulated process death (Logcat -> Terminate App).
- [ ] Runtime permissions show rationale before system prompts.
- [ ] `CoroutineWorker` handles syncs and respects constraints.
