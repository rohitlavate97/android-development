# Architecture Blueprint — Enterprise Finance Tracker

## 1. Multi-Module Topology & Responsibilities

The application is structured into **9 decoupled Gradle subprojects** adhering to clean architectural boundaries:

| Module | Type | Responsibilities | Key Dependencies |
|---|---|---|---|
| **`:app`** | Application | Aggregates all feature & core modules. Hosts `EnterpriseFinanceApp`, `MainActivity`, `FinanceNavHost`, and Koin DI graph bootstrapping. | `:feature:*`, `:core:*` |
| **`:feature:dashboard`** | Feature | Net Worth summary, liquid vs investment allocation cards, portfolio holdings list. | `:core:model`, `:core:designsystem`, `:core:database` |
| **`:feature:transactions`**| Feature | Transaction search list, category filters, detail screen, MVI state reducer. | `:core:model`, `:core:designsystem`, `:core:database` |
| **`:feature:analytics`** | Feature | Spending trend analysis, monthly budget adherence progress bars. | `:core:model`, `:core:designsystem`, `:core:database` |
| **`:core:designsystem`** | Core | Material 3 Theme tokens (`Color`, `Type`, `Theme`), atomic UI atoms (`CategoryBadge`, `AmountDisplay`, `EmptyStateWidget`), `@Immutable` UI models. | `:core:model` |
| **`:core:database`** | Core | Room SQLite Database (`FinanceDatabase`), DAOs, Entities, TypeConverters, `UserPreferencesDataStore`. | `:core:model`, `:core:common` |
| **`:core:network`** | Core | Retrofit `FinanceApiService`, OkHttp Client, 401 Mutex `TokenAuthenticator`, 6-path `safeApiCall`. | `:core:model`, `:core:common` |
| **`:core:model`** | Core | Pure Kotlin Domain Entities (`Transaction`, `Account`, `Budget`, `Portfolio`), value classes, `FinancialResult`. | *None (Zero Android SDK dependencies)* |
| **`:core:common`** | Core | `DispatcherProvider`, `safeSuspendCall` with `CancellationException` preservation. | `kotlinx-coroutines-core` |

---

## 2. Performance Engineering & Compilation Pipeline

```
┌────────────────────────────────────────────────────────┐
│               Android Runtime (ART) Execution          │
│                                                        │
│   ┌─────────────────────┐       ┌──────────────────┐   │
│   │ Baseline Profiles   │──────►│ DEX Pre-Compiles │   │
│   │ (Critical Journeys) │ (AOT) │ (Ahead-Of-Time)  │   │
│   └─────────────────────┘       └─────────┬────────┘   │
│                                           │            │
│   ┌─────────────────────┐                 ▼            │
│   │ Compose Compiler    │       ┌──────────────────┐   │
│   │ Stability Metrics   │──────►│ 0ms JIT Delay    │   │
│   │ (@Immutable Models) │(Skip) │ 60/120 FPS Scroll│   │
│   └─────────────────────┘       └──────────────────┘   │
└────────────────────────────────────────────────────────┘
```

---

## 3. StrictMode Guardrails in Debug Builds

To eliminate Main Thread bottlenecks before code hits production, `StrictModeInitializer` enforces:
1. **ThreadPolicy**:
   - `detectDiskReads()` / `detectDiskWrites()`: Flags synchronous file/SharedPreferences access.
   - `detectNetwork()`: Throws immediately if HTTP sockets open on Main.
   - `detectCustomSlowCalls()`: Logs long-running computation blocks.
2. **VmPolicy**:
   - `detectLeakedClosableObjects()`: Flags unclosed Streams or SQLite cursors.
   - `detectActivityLeaks()`: Catches static Activity references upon screen rotation.
