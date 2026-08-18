# Testing Strategy & Test Pyramid — Enterprise Finance Tracker

## 1. The 70/20/10 Test Pyramid

We enforce Google's industry-standard test distribution:

```
        ▲
       / \      10% End-to-End UI & Deep Link Tests
      /   \     (Compose UI Semantics, Navigation flow)
     /     \
    /───────\   20% Integration & Repository Tests
   /         \  (MockWebServer 6 failure paths, Room DAO queries, SSOT invalidation)
  /           \
 /─────────────\ 70% Fast JVM Unit Tests
/               \ (Domain Entities, Invariants, UseCases with Fakes, ViewModels with Turbine)
─────────────────
```

---

## 2. Why Fakes are Preferred over Mocks

| Dimension | In-Memory Fakes (`FakeExpenseRepository`) | Mocking Frameworks (`Mockito.mock()`) |
|---|---|---|
| **State Consistency** | Implements real state mutations via `MutableStateFlow`. Adding an item updates subsequent queries. | Brittle stubbing (`whenever(repo.get()).thenReturn(...)`). Must manually coordinate return values. |
| **Refactoring Resilience** | Tests survive internal refactors without changing verification assertions. | Tests break whenever method signatures or call orders change (`verify(repo, times(1))`). |
| **Execution Speed** | Ultra-fast JVM execution (<10ms). | Slower due to runtime bytecode reflection/generation. |
| **Kotlin Coroutines** | First-class Flow and suspend support. | Requires complex `runBlocking` or `doAnswer` adapters. |

---

## 3. Test Suites Implemented Across All 10 Stages

### Level 1: Domain Entities & Invariants (70% Unit Tests)
- `TransactionValidationTest.kt` — Asserts positive amounts, non-empty IDs, boundary values.
- `InvestmentPortfolioTest.kt` — Asserts weighted allocation percentages and unrealized profit/loss math.
- `UseCasesTest.kt` — Tests `GetTransactionsUseCase`, `AddTransactionUseCase`, and `FilterTransactionsUseCase` with `FakeExpenseRepository`.

### Level 2: Boundary Mappers & DTO Validation
- `DataMapperTest.kt` — Tests defensive fallbacks against malformed or missing backend JSON fields.
- `EntityMapperTest.kt` — Tests Room SQLite Entity <-> Domain Model bidirectional conversion.

### Level 3: Concurrency & ViewModel State Machines (Turbine + Virtual Time)
- `TransactionFlowTest.kt` — Tests reactive state streams without `Thread.sleep()` using `StandardTestDispatcher.scheduler.advanceUntilIdle()`.
- `SafeSuspendCallTest.kt` — Verifies that `CancellationException` is strictly rethrown and never swallowed.
- `DashboardViewModelTest.kt` — Tests `DashboardUiState.Loading` ➔ `DashboardUiState.Content` cold flow combination with Turbine.
- `TransactionListMviViewModelTest.kt` — Tests MVI intents (`SearchQueryChanged`, `FilterSelected`, `DeleteTransaction`) and state reducer transitions.

### Level 4: Networking & Failure Paths (20% Integration Tests)
- `MockWebServerFailurePathsTest.kt` — Tests all 6 failure paths:
  1. `200 OK` (JSON Deserialization)
  2. `401 Unauthorized` (Token refresh trigger)
  3. `404 Not Found` (Validation error)
  4. `500 Server Error` (Unexpected error)
  5. `Socket Timeout` (Network timeout)
  6. `Malformed JSON` (Schema mismatch)

### Level 5: Persistence & SSOT Tests
- `OfflineFirstExpenseRepositoryTest.kt` — Verifies that syncing remote API data writes into Room and automatically invalidates the UI Flow without polling.

### Level 6: Navigation, DI & UI Presentation (10% UI Tests)
- `NavigationDestinationSerializationTest.kt` — Verifies type-safe `@Serializable` destination encoding and deep link URL matching.
- `AppModuleCheckTest.kt` — Automated Koin DI graph validation testing singletons, factories, and ViewModel resolutions.
- `StatelessScreenLogicTest.kt` — Form input validation and search query filtering logic.
