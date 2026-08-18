# Architecture Blueprint — Enterprise Finance Tracker

## 1. High-Level Target Architecture

The final architecture of **Enterprise Finance Tracker** follows Google's recommended **Clean Architecture with Unidirectional Data Flow (UDF)**:

```
┌────────────────────────────────────────────────────────┐
│                        UI Layer                        │
│   Jetpack Compose (Stateless Screens + Atomic Atoms)   │
│                          ↓                             │
│       ViewModel (StateFlow<UiState> + UI Intents)      │
└──────────────────────────┬─────────────────────────────┘
                           │ (Inward Dependency)
┌──────────────────────────▼─────────────────────────────┐
│                      Domain Layer                      │
│        Use Cases (operator fun invoke())               │
│   Domain Entities & Value Classes (Pure Kotlin)        │
│          Repository Interfaces (Contracts)             │
└──────────────────────────▲─────────────────────────────┘
                           │ (Dependency Inversion)
┌──────────────────────────┴─────────────────────────────┐
│                       Data Layer                       │
│        Repository Implementations (SSOT)               │
│      Local DataSource       │      Remote DataSource   │
│   (Room SQLite + DataStore) │    (Retrofit + OkHttp)   │
└────────────────────────────────────────────────────────┘
```

---

## 2. Evolution Plan by Stage

| Stage | Focus Area | Architectural Additions | What Is Intentionally Postponed |
|---|---|---|---|
| **Stage 1** | **Kotlin Foundation** | Pure Kotlin domain models, value classes, sealed types, invariant validations, unit tests. | No Android SDK, no UI, no networking, no database, no DI framework. |
| **Stage 2** | **Platform Fundamentals** | Android Gradle, AndroidManifest, Application class, MainActivity, lifecycle logging. | No Compose UI, no async networks. |
| **Stage 3** | **Jetpack Compose UI** | 3-Layer Screen architecture (`Route` → `Screen` → `Components`), Material 3 design tokens. | Static in-memory mock data (no coroutines/Room yet). |
| **Stage 4** | **Concurrency & Flow** | Coroutine scopes, `StateFlow`, `WhileSubscribed(5000)`, `DispatcherProvider` injection. | In-memory reactive repository. |
| **Stage 5** | **Clean Architecture** | Boundary mappers, Use Cases, MVVM & MVI patterns side-by-side. | No third-party network libraries yet. |
| **Stage 6** | **Dependency Injection** | DI container graph, constructor injection, interface binding, swappable test fakes. | Real database or remote server. |
| **Stage 7** | **Resilient Networking** | Retrofit, OkHttp, 401 token refresh mutex, 6-path failure resilience. | Room database (network-only data). |
| **Stage 8** | **Offline-First Persistence** | Room DB, reactive Flow queries, DataStore preferences, SSOT sync engine. | Multi-module splitting. |
| **Stage 9** | **Type-Safe Navigation** | Navigation-Compose with `@Serializable` routes, backstack management, deep links. | Modularization. |
| **Stage 10** | **Testing Pyramid** | Turbine Flow testing, Compose UI semantics, JVM screenshot diffing. | Build optimizations. |
| **Stage 11** | **Modularization** | Feature vs Core modules, `-api` / `-impl` split, Version Catalog. | Advanced APM profiling. |
| **Stage 12** | **Production Quality** | StrictMode, Baseline Profiles, LeakCanary, RUM telemetry. | Store release pipelines. |
| **Stage 13** | **Release & Incident Playbook** | Fastlane, Play Console tracks, Remote kill-switches, 4-level incident drill. | Final course graduation. |

---

## 3. Data Model Taxonomy & Boundary Discipline

To prevent architectural leaks, models are strictly separated:

1. **Domain Models (`DomainModels.kt`)**: Pure Kotlin representations containing core business logic and invariant validation. Uses `@JvmInline value class` for identifiers.
2. **Data Transfer Objects (`*Dto.kt`)**: Match remote backend JSON schemas. All fields nullable to defensively withstand malformed server payloads.
3. **Database Entities (`*Entity.kt`)**: Room SQLite table definitions optimized for relational storage and foreign keys.
4. **UI State Models (`*UiState.kt`)**: Immutable sealed hierarchies representing the 4 standard visual states (`Loading`, `Content`, `Empty`, `Error`).

---

## 4. Current Stage 1 Scope

In **Stage 1**, we strictly build the **Pure Kotlin Domain Model** without any Android dependencies. This ensures business rules (e.g., non-negative amounts, portfolio allocation sums, valid ticker symbols) are verified completely independently of the Android platform or UI frameworks.
