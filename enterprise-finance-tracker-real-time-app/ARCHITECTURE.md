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

## 2. Release & Build Pipeline (R8 / ProGuard / Signing)

```
┌────────────────────────────────────────────────────────┐
│             Continuous Integration & Release           │
│                                                        │
│   ┌─────────────────────┐       ┌──────────────────┐   │
│   │ Kotlin Sources &    │──────►│ R8 Full Mode     │   │
│   │ Compose Bytecode    │       │ • Shrinking      │   │
│   └─────────────────────┘       │ • Obfuscation    │   │
│                                 │ • Log Stripping  │   │
│                                 └─────────┬────────┘   │
│                                           │            │
│   ┌─────────────────────┐                 ▼            │
│   │ Google Play App     │◄──────┌──────────────────┐   │
│   │ Bundle (.aab)       │ (Sign)│ Release DEX &    │   │
│   │ + mapping.txt       │       │ Baseline Profiles│   │
│   └─────────────────────┘       └──────────────────┘   │
└────────────────────────────────────────────────────────┘
```

---

## 3. StrictMode & Incident Guardrails

To eliminate Main Thread bottlenecks and memory leaks before production:
1. **StrictModeInitializer**: Enforces strict ThreadPolicy (zero disk/network on Main) and VmPolicy (cursor and Activity leak detection) in debug builds.
2. **Production Incident Runbook (`docs/INCIDENT_PLAYBOOK.md`)**: P0 crash loop escalation, hotfix branching strategy, and staged rollout monitoring.
