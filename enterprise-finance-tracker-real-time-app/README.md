# Enterprise Finance Tracker

> A production-style Android application built incrementally to master the entire Android Development Learning Roadmap.

---

## 🎯 Project Overview & Philosophy

**Enterprise Finance Tracker** is a comprehensive personal finance and wealth management application. It covers:
- **Authentication**: Biometrics, secure session storage, JWT refresh.
- **Accounts & Balances**: Multi-currency bank, credit, and investment accounts.
- **Transactions**: Expense/Income tracking, recurring entries, categorization, receipt attachment.
- **Investments & Portfolio**: Real-time stock/crypto holdings, asset allocation, unrealized P&L, watchlists.
- **Analytics & Budgets**: Spending trends, category limits, predictive cash-flow.
- **Offline-First Resilience**: Local database as Single Source of Truth, background synchronization via WorkManager, conflict resolution.

---

## 🗺️ Architectural Evolution Roadmap (13 Stages)

```mermaid
graph TD
    S01["Stage 1: Pure Kotlin Domain Foundation ✅"] --> S02["Stage 2: Android Platform & Build Fundamentals ✅"]
    S02 --> S03["Stage 3: Jetpack Compose UI (3-Layer Screens) ✅"]
    S03 --> S04["Stage 4: Coroutines, Flow & Concurrency Engine ✅"]
    S04 --> S05["Stage 5: Clean Architecture (UDF, UseCases, SSOT) ✅"]
    S05 --> S06["Stage 6: Dependency Injection (Koin & Hilt) ✅"]
    S06 --> S07["Stage 7: Resilient Networking (Retrofit, 401 Mutex, 6-path errors) ✅"]
    S07 --> S08["Stage 8: Offline-First Persistence (Room, DataStore)"]
    S08 --> S09["Stage 9: Type-Safe Navigation & Deep Links"]
    S09 --> S10["Stage 10: Complete Test Pyramid (Turbine, Fakes, Compose UI)"]
    S10 --> S11["Stage 11: Modularization (:app, :core:*, :feature:*-api/-impl)"]
    S11 --> S12["Stage 12: Production Quality (StrictMode, Profiler, Baseline Profiles)"]
    S12 --> S13["Stage 13: Release Engineering & Incident Response Playbook"]

    style S01 fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px
    style S02 fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px
    style S03 fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px
    style S04 fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px
    style S05 fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px
    style S06 fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px
    style S07 fill:#c8e6c9,stroke:#2e7d32,stroke-width:2px
```

---

## 📚 Living Project Documentation

- **[ARCHITECTURE.md](ARCHITECTURE.md)** — Architectural blueprint, layer definitions, data flow, and evolution plan.
- **[DECISIONS.md](DECISIONS.md)** — Architecture Decision Records (ADRs) with rationale, alternatives, and tradeoffs.
- **[DEBUGGING.md](DEBUGGING.md)** — Bug investigation logs, root cause analyses, and debugging challenges.
- **[TESTING.md](TESTING.md)** — Testing strategy, test pyramid breakdown, and execution guidelines.

---

## 📍 Current Status

- **Current Stage**: **Stage 7 Completed — Resilient Networking (Retrofit, 401 Mutex Authenticator, 6-Path Error Taxonomy)**
- **Artifacts Built**:
  - **Retrofit & OkHttp Engine**: `FinanceApiService`, `NetworkClientFactory` with custom timeouts, logging, and Kotlinx Serialization.
  - **Security & Token Management**: `AuthInterceptor` (attaches Bearer token) and `TokenAuthenticator` with Coroutines `Mutex` (prevents 401 refresh storms).
  - **6 Canonical Failure Paths**: Explicit error mapping for `401 Unauthorized`, `404 Not Found`, `500 Server Error`, `SocketTimeout`, `NoInternet`, and `MalformedJson`.
  - **Remote DataSource**: `RetrofitTransactionRemoteDataSource` executing calls via `safeApiCall`.
  - **Automated MockWebServer Test Suite**: `MockWebServerFailurePathsTest` verifying all 6 failure paths in isolation.
