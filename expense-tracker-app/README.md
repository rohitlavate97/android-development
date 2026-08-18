# Enterprise Expense & Investment Tracker

> A production-grade, modular Android application built using the architecture and patterns taught across all 13 phases of the **Modern Android Development Master Curriculum**.

---

## 🏛️ Module Architecture

```
expense-tracker-app/
├── gradle/
│   └── libs.versions.toml              # Version Catalog (Phase 11)
├── app/                                # Application Entry & Navigation (Phase 3 & 9)
│   ├── ExpenseTrackerApp.kt            # Koin DI, StrictMode, WorkManager init
│   ├── MainActivity.kt                 # Compose root, FLAG_SECURE
│   ├── di/AppModule.kt                 # Complete Koin dependency graph (Phase 6)
│   ├── navigation/ExpenseNavHost.kt    # Type-Safe Navigation-Compose (Phase 9)
│   └── worker/SyncWorker.kt            # Background WorkManager CoroutineWorker (Phase 3)
├── core/
│   ├── model/                          # Domain Entities & Value Classes (Phase 1 & 5)
│   ├── common/                         # DispatcherProvider, Resource<T>, SafeCall (Phase 2 & 7)
│   ├── database/                       # Room ORM, Reactive Flow DAOs (Phase 8)
│   ├── network/                        # Retrofit, DTOs, Remote DataSource (Phase 7)
│   ├── datastore/                      # Preferences DataStore for Settings (Phase 8)
│   └── designsystem/                   # Material 3 Theme, Typography, Reusable Atoms (Phase 4)
└── feature/
    ├── dashboard/                      # Dashboard Screen, Summary Card, Category Spending (Phase 4)
    ├── transactions/                   # Transaction List, Filter, Add & Detail Screens (Phase 4 & 5)
    └── analytics/                      # Spending Analytics & Budget Progress (Phase 4)
```

---

## 🚀 Key Patterns Implemented

1. **Phase 1 (Kotlin)**: `@JvmInline value class` for strongly-typed IDs (`TransactionId`), sealed interfaces for UI state, extension mappers.
2. **Phase 2 (Coroutines & Flow)**: `StateFlow<UiState>` with `SharingStarted.WhileSubscribed(5_000)`, injected `DispatcherProvider`, `safeSuspendCall` preserving `CancellationException`.
3. **Phase 3 (Platform & Background)**: `WorkManager` periodic sync, `StrictMode` main-thread violation detection.
4. **Phase 4 (Jetpack Compose)**: 3-Layer Screen Pattern (`Route` → `Screen` → `Components`), Material 3 theming (Light/Dark mode), `LazyColumn` with stable keys.
5. **Phase 5 (Clean Architecture)**: Inward dependency rule, Use Cases with `operator fun invoke()`, Single Source of Truth repository.
6. **Phase 6 (Dependency Injection)**: Modular Koin dependency graph.
7. **Phase 7 (Networking)**: Type-safe REST client, defensive nullability parsing, `Resource<T>` error modeling.
8. **Phase 8 (Local Persistence)**: Room database with auto-updating SQLite `Flow` queries, DataStore preferences.
9. **Phase 9 (Navigation)**: Type-Safe Navigation-Compose with `@Serializable` route destinations and deep link support (`expensetracker://transaction/{id}`).
10. **Phase 10 (Testing)**: Unit tests for ViewModels and Use Cases using **Turbine** and virtual time (`StandardTestDispatcher`).
11. **Phase 11 (Modularization)**: Gradle Version Catalog (`gradle/libs.versions.toml`), parallel task execution.
12. **Phase 12 (Quality)**: Release ProGuard/R8 rules, memory leak prevention.
13. **Phase 13 (Security)**: `FLAG_SECURE` window flags for sensitive financial data screens.

---

## 🛠️ How to Open & Run

1. Open **Android Studio** (Ladybug / Koala or newer).
2. Select **Open Project** and choose the `expense-tracker-app` directory.
3. Allow Gradle to sync dependencies via `gradle/libs.versions.toml`.
4. Select `app` run configuration and click **Run (Shift + F10)** on an emulator or device (API 26+).
5. Run unit tests via `./gradlew test` or inside Android Studio test runners.
