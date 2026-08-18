# Milestone 5: Clean Architecture

## Title, Goal & Phase Alignment
**Goal**: Enforce strict separation of concerns using Clean Architecture principles, establishing clear boundaries between UI, Domain, and Data.
**Phase**: Phase 5 (Expense Tracker v4)

## Architecture & Component Blueprint
- **Inward Dependency Rule**: UI and Data layers depend on Domain. Domain depends on nothing.
- **SSOT Repository**: Single Source of Truth repositories coordinating multiple data sources (local database, remote API).
- **Use Cases**: Encapsulate single pieces of business logic using `operator fun invoke()`.
- **Orchestrator**: Use an `ExpenseSummaryOrchestrator` to combine multiple Use Cases into complex flows.
- **MVVM vs MVI**: Side-by-side implementation comparison for state management.

## Step-by-Step Implementation Instructions
1. Refactor packages into `ui`, `domain`, and `data` boundaries.
2. Move Repository interfaces to `domain` and their implementations to `data`.
3. Create `GetTransactionsUseCase` and `CalculateBudgetUseCase` with `invoke` operators.
4. Implement `ExpenseSummaryOrchestrator` to aggregate data from multiple use cases.
5. Create two ViewModels for the same screen: one using standard MVVM and one using an MVI Action/Intent architecture.

## Code Snippets & Signatures
```kotlin
// Domain Layer
class GetTransactionsUseCase(
    private val repository: TransactionRepository
) {
    operator fun invoke(): Flow<List<Transaction>> {
        return repository.getTransactions()
            .map { list -> list.sortedByDescending { it.timestamp } }
    }
}

// Orchestrator combining Use Cases
class ExpenseSummaryOrchestrator(
    private val getTransactions: GetTransactionsUseCase,
    private val getBudget: GetBudgetUseCase
) {
    operator fun invoke(): Flow<ExpenseSummary> = combine(
        getTransactions(),
        getBudget()
    ) { transactions, budget ->
        ExpenseSummary(transactions, budget)
    }
}
```

## Deliberate Bugs to Catch & Debug
- **Bug**: Returning DTOs (Data Transfer Objects) directly to the UI layer instead of mapping them to Domain Entities.
- **Bug**: Domain layer importing classes from `androidx.*` or `retrofit2.*`.
- **Bug**: Over-engineering by creating pass-through Use Cases that do nothing but call repository methods without any business logic.

## Unit Testing Requirements (Given-When-Then)
- **Given** multiple Use Cases **When** the Orchestrator runs **Then** it correctly combines the outputs into an `ExpenseSummary`.
- **Given** DTOs from data **When** mapped **Then** they accurately translate to Domain Entities.

## Acceptance Criteria Checklist
- [ ] Strict Inward Dependency Rule is verified.
- [ ] Use Cases use the `operator fun invoke()` syntax.
- [ ] Repository interfaces reside in Domain, implementations in Data.
- [ ] MVVM and MVI variants of the screen are functional.
