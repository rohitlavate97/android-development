# Architecture Blueprint — Enterprise Finance Tracker

## 1. Multi-Module Topology & Responsibilities

The application is structured into **9 decoupled Gradle subprojects** adhering to clean architectural boundaries:

| Module | Type | Responsibilities | Key Dependencies |
|---|---|---|---|
| **`:app`** | Application | Aggregates all feature & core modules. Hosts `EnterpriseFinanceApp`, `MainActivity`, `FinanceNavHost`, and Koin DI graph bootstrapping. | `:feature:*`, `:core:*` |
| **`:feature:dashboard`** | Feature | Net Worth summary, liquid vs investment allocation cards, portfolio holdings list. | `:core:model`, `:core:designsystem`, `:core:database` |
| **`:feature:transactions`**| Feature | Transaction search list, category filters, detail screen, MVI state reducer. | `:core:model`, `:core:designsystem`, `:core:database` |
| **`:feature:analytics`** | Feature | Spending trend analysis, monthly budget adherence progress bars. | `:core:model`, `:core:designsystem`, `:core:database` |
| **`:core:designsystem`** | Core | Material 3 Theme tokens (`Color`, `Type`, `Theme`), atomic UI atoms (`CategoryBadge`, `AmountDisplay`, `EmptyStateWidget`). | `:core:model` |
| **`:core:database`** | Core | Room SQLite Database (`FinanceDatabase`), DAOs, Entities, TypeConverters, `UserPreferencesDataStore`. | `:core:model`, `:core:common` |
| **`:core:network`** | Core | Retrofit `FinanceApiService`, OkHttp Client, 401 Mutex `TokenAuthenticator`, 6-path `safeApiCall`. | `:core:model`, `:core:common` |
| **`:core:model`** | Core | Pure Kotlin Domain Entities (`Transaction`, `Account`, `Budget`, `Portfolio`), value classes, `FinancialResult`. | *None (Zero Android SDK dependencies)* |
| **`:core:common`** | Core | `DispatcherProvider`, `safeSuspendCall` with `CancellationException` preservation. | `kotlinx-coroutines-core` |

---

## 2. Compile Avoidance & `api` vs `implementation` Rules

1. **`implementation` (Default)**:
   - Dependencies are **internal** to the module and NOT exposed to consumers on the compile classpath.
   - *Advantage*: When an implementation dependency changes, Gradle ONLY recompiles that single module, avoiding downstream cascade recompilations.
2. **`api` (Public Contract)**:
   - Used sparingly for shared foundational types (e.g. `:core:model` inside `:core:database` and `:core:network`).
   - Consumers automatically inherit the public contract without manually declaring it.
3. **Feature Module Isolation (No Horizontal Dependencies)**:
   - `:feature:dashboard` NEVER depends on `:feature:transactions`.
   - Feature communication is orchestrated strictly via `:app` type-safe navigation routes and deep links.
