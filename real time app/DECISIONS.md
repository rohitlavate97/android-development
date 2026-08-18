# Architecture Decision Records (ADR)

---

## ADR 001: Use `@JvmInline value class` for Domain Identifiers

- **Status**: Accepted
- **Context**: Passing primitive strings for IDs (e.g. `accountId: String`, `transactionId: String`, `categoryId: String`) creates catastrophic "primitive obsession" where parameters can be swapped in function calls without compile-time errors.
- **Decision**: Wrap all domain entity identifiers in `@JvmInline value class` (e.g., `TransactionId`, `AccountId`, `CategoryId`, `TickerSymbol`).
- **Consequences & Tradeoffs**:
  - *Pros*: Zero runtime heap allocation overhead on JVM, 100% compile-time type safety preventing ID transposition bugs.
  - *Cons*: Requires unboxing when serializing to SQLite or JSON primitives at boundary mappers.

---

## ADR 002: Enforce Business Invariants in Entity `init` Blocks

- **Status**: Accepted
- **Context**: Creating entities with invalid business states (e.g., negative expense amount, empty ticker symbol, progress > 100%) can propagate silently through repositories and crash the UI.
- **Decision**: Enforce non-negotiable invariants directly inside entity `init` blocks using `require()`.
- **Consequences & Tradeoffs**:
  - *Pros*: Impossible to create an invalid domain object anywhere in the application.
  - *Cons*: Boundary mappers must catch `IllegalArgumentException` and map to domain failure states.

---

## ADR 003: Pure Kotlin Domain Layer (Zero Android Framework Imports)

- **Status**: Accepted
- **Context**: Coupling domain logic with Android SDK classes (`android.content.Context`, `android.os.Bundle`, `android.text.TextUtils`) makes unit tests slow (requiring Robolectric/Emulators) and breaks clean architecture.
- **Decision**: The domain layer must remain pure Kotlin (`org.jetbrains.kotlin.*`, `kotlinx.datetime.*`, `kotlinx.coroutines.*`). Zero `android.*` imports.
- **Consequences & Tradeoffs**:
  - *Pros*: Lightning-fast JVM unit test execution (<50ms), portable to Kotlin Multiplatform (KMP).
  - *Cons*: Cannot use Android platform helpers directly; requires abstraction layers for platform features.

---

## ADR 004: Incremental Architectural Evolution over Upfront Framework Dumping

- **Status**: Accepted
- **Context**: Introducing 20 Gradle modules, Hilt DI, Retrofit, and Room simultaneously creates cognitive overload and hides the "why" behind modern Android engineering.
- **Decision**: Build the application stage-by-stage. Introduce libraries and architectural boundaries only when the roadmap specifically calls for them.
- **Consequences & Tradeoffs**:
  - *Pros*: Clear mastery of foundational mechanics before abstracting with frameworks.
  - *Cons*: Requires intentional refactoring steps between stages (which mirrors real-world enterprise codebase evolution).

---

## ADR 005: Enforce StrictMode Policy in Debug Builds

- **Status**: Accepted
- **Context**: Accidental disk reads/writes or network calls on the Main thread cause frame drops and Application Not Responding (ANR) dialogs.
- **Decision**: Initialize Android `StrictMode` inside `EnterpriseFinanceApp.onCreate()` when `BuildConfig.DEBUG` is true.
- **Consequences & Tradeoffs**:
  - *Pros*: Catches main-thread violations immediately in development with Logcat penalties.
  - *Cons*: Must be disabled in release builds to avoid crashing or penalizing production users.

---

## ADR 006: State Preservation via `onSaveInstanceState` / `SavedStateHandle`

- **Status**: Accepted
- **Context**: Android kills background apps when RAM is low. Storing user state only in memory fields causes data loss upon process recreation.
- **Decision**: All critical transient navigation and form state must be saved to the saved state bundle / `SavedStateHandle`.
- **Consequences & Tradeoffs**:
  - *Pros*: 100% resilient across screen rotations and low-memory OS kills.
  - *Cons*: Saved bundles are limited to ~500KB (TransactionTooLargeException if misuse).
