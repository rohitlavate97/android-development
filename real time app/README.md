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

Unlike traditional tutorials that dump 20 modules and full frameworks upfront, this application evolves organically across 13 distinct stages:

```mermaid
graph TD
    S01["Stage 1: Pure Kotlin Domain Foundation"] --> S02["Stage 2: Android Platform & Build Fundamentals"]
    S02 --> S03["Stage 3: Jetpack Compose UI (3-Layer Screens)"]
    S03 --> S04["Stage 4: Coroutines, Flow & Concurrency Engine"]
    S04 --> S05["Stage 5: Clean Architecture (UDF, UseCases, SSOT)"]
    S05 --> S06["Stage 6: Dependency Injection (Koin & Hilt)"]
    S06 --> S07["Stage 7: Resilient Networking (Retrofit, 401 Mutex, 6-path errors)"]
    S07 --> S08["Stage 8: Offline-First Persistence (Room, DataStore)"]
    S08 --> S09["Stage 9: Type-Safe Navigation & Deep Links"]
    S09 --> S10["Stage 10: Complete Test Pyramid (Turbine, Fakes, Compose UI)"]
    S10 --> S11["Stage 11: Modularization (:app, :core:*, :feature:*-api/-impl)"]
    S11 --> S12["Stage 12: Production Quality (StrictMode, Profiler, Baseline Profiles)"]
    S12 --> S13["Stage 13: Release Engineering & Incident Response Playbook"]
```

---

## 📚 Living Project Documentation

- **[ARCHITECTURE.md](ARCHITECTURE.md)** — Architectural blueprint, layer definitions, data flow, and evolution plan.
- **[DECISIONS.md](DECISIONS.md)** — Architecture Decision Records (ADRs) with rationale, alternatives, and tradeoffs.
- **[DEBUGGING.md](DEBUGGING.md)** — Bug investigation logs, root cause analyses, and debugging challenges.
- **[TESTING.md](TESTING.md)** — Testing strategy, test pyramid breakdown, and execution guidelines.

---

## 📍 Current Status

- **Current Stage**: **Stage 1 — Kotlin Domain Foundation**
- **Artifacts Built**: Pure Kotlin Domain Entities, Value Classes, Sealed Types, Business Invariant Validation, Domain Unit Test Suite.
