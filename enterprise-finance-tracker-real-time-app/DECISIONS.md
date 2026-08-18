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
  - *Cons*: Saved bundles are limited to ~500KB (TransactionTooLargeException if misused).

---

## ADR 007: The 3-Layer Screen Architecture Pattern

- **Status**: Accepted
- **Context**: Mixing ViewModel state observation, navigation lambdas, and UI layout inside a single composable function creates tightly coupled, untestable, and un-previewable screens.
- **Decision**: Structure every screen into 3 distinct layers:
  1. **`*Route`**: Stateful, collects ViewModel/StateFlow, passes callbacks.
  2. **`*Screen`**: 100% Stateless, accepts state data class and lambda event handlers.
  3. **`*Components`**: Pure atomic reusable UI widgets.
- **Consequences & Tradeoffs**:
  - *Pros*: 100% previewable in Android Studio Previews, easy screenshot testing without mocking ViewModels.
  - *Cons*: Requires creating two composable functions per screen file (`*Route` and `*Screen`).

---

## ADR 008: Mandatory Stable Keys for `LazyColumn` / `LazyRow`

- **Status**: Accepted
- **Context**: Omitting item keys in `LazyColumn` forces Compose to use item index as the identity key. When an item is inserted at position 0, every single row is recomposed and animated state is reset.
- **Decision**: Always provide a unique, stable domain key using `items(items = list, key = { it.id.value })`.
- **Consequences & Tradeoffs**:
  - *Pros*: Smooth animations, minimal recomposition overhead, preserved item scroll states.
  - *Cons*: Requires unique ID fields on all domain entities.

---

## ADR 010: Inject Coroutine Dispatchers via `DispatcherProvider`

- **Status**: Accepted
- **Context**: Hardcoding `Dispatchers.IO` or `Dispatchers.Default` inside repositories or ViewModels breaks unit tests by forcing multi-threaded concurrency and `Thread.sleep()` hacks.
- **Decision**: Always inject a `DispatcherProvider` interface. Use `StandardDispatcherProvider` in production and `TestDispatcherProvider` (`StandardTestDispatcher`) in unit tests.
- **Consequences & Tradeoffs**:
  - *Pros*: Deterministic virtual-time execution with `advanceUntilIdle()` and zero flaky tests.
  - *Cons*: Requires passing `DispatcherProvider` into constructors.

---

## ADR 011: Expose UI State with `SharingStarted.WhileSubscribed(5_000)`

- **Status**: Accepted
- **Context**: Using `SharingStarted.Eagerly` or `Lazily` keeps upstream cold Flows (e.g. database/location/live stock tickers) active indefinitely even when the app is in the background, wasting CPU and battery.
- **Decision**: Always convert cold flows to `StateFlow` using `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InitialState)`.
- **Consequences & Tradeoffs**:
  - *Pros*: Cancels upstream subscriptions 5 seconds after all UI collectors detach.
  - *Cons*: Requires understanding lifecycle-aware collection (`collectAsStateWithLifecycle()`).

---

## ADR 012: Strictly Preserve `CancellationException` in Catch Blocks

- **Status**: Accepted
- **Context**: Catching `java.lang.Exception` in suspend functions silently swallows `CancellationException`, which prevents Coroutines from cancelling parent jobs and causes memory leaks.
- **Decision**: In all try-catch blocks in suspend functions, explicitly rethrow `CancellationException` before handling generic exceptions.
- **Consequences & Tradeoffs**:
  - *Pros*: Clean structured concurrency and instant coroutine cancellation.
  - *Cons*: Requires developer discipline to avoid `try { ... } catch (e: Exception)` without rethrowing.

---

## ADR 013: Domain Use Cases with `operator fun invoke()`

- **Status**: Accepted
- **Context**: Letting ViewModels interact with God repositories directly leads to duplicate business rules (e.g. filtering logic, validation, currency conversions) across multiple screens.
- **Decision**: Encapsulate distinct business actions into Single Responsibility Use Cases implementing `operator fun invoke()`.
- **Consequences & Tradeoffs**:
  - *Pros*: 100% testable in isolation, callable as functions `getTransactionsUseCase()`, clean reuse.
  - *Cons*: Increases class count in the domain package.

---

## ADR 014: Explicit Boundary Mappers at Every Layer

- **Status**: Accepted
- **Context**: Leaking database entity annotations (`@Entity`) or network annotations (`@SerialName`) into Domain or UI models tightly couples the UI to backend schemas.
- **Decision**: Maintain dedicated DTOs, Entities, Domain Models, and UI Models with explicit mapping extension functions at every layer boundary.
- **Consequences & Tradeoffs**:
  - *Pros*: Complete decoupling. Backend API changes never break Compose UI rendering.
  - *Cons*: Requires writing mapping extension functions.

---

## ADR 015: Model-View-Intent (MVI) for Complex Interactive Screens

- **Status**: Accepted
- **Context**: Complex screens with multiple concurrent user inputs (search, filter chips, delete gestures) suffer from race conditions when using multiple independent `MutableStateFlow` fields in ViewModels.
- **Decision**: Use the MVI pattern (`TransactionListIntent` → State Reducer → `TransactionListUiState`) for transactional screens, and Clean MVVM for read-heavy screens.
- **Consequences & Tradeoffs**:
  - *Pros*: Single Source of Truth for screen state, 100% deterministic state transitions.
  - *Cons*: Requires defining Intent sealed hierarchies.

---

## ADR 016: Adopt Koin Pure Kotlin DSL for Dependency Injection

- **Status**: Accepted
- **Context**: Manual constructor injection in `MainActivity` becomes unmaintainable as dependencies scale across multi-layered architecture. Using Dagger/Hilt requires heavy KSP / annotation processor build steps.
- **Decision**: Adopt Koin with Kotlin DSL constructor injection (`singleOf`, `factoryOf`, `viewModelOf`).
- **Consequences & Tradeoffs**:
  - *Pros*: Instant build times (0 codegen overhead), clean Kotlin DSL, portable to Kotlin Multiplatform (KMP).
  - *Cons*: Graph resolution happens at runtime; mitigated by running automated `AppModuleCheckTest` in CI.

---

## ADR 017: Explicit Scope Lifetimes: Singleton vs Factory vs ViewModelScoped

- **Status**: Accepted
- **Context**: Creating singletons for stateful UseCases can retain stale memory, while creating new repository instances per screen breaks in-memory caching.
- **Decision**: Enforce strict scoping:
  1. `single` / `@Singleton`: Repositories, DataSources, DispatcherProvider, Database.
  2. `factory`: Stateless Domain UseCases.
  3. `viewModelOf`: Screen state ViewModels tied to Composable lifecycle.
- **Consequences & Tradeoffs**:
  - *Pros*: Optimal memory usage and deterministic lifecycles.
  - *Cons*: Requires deliberate choice of `singleOf` vs `factoryOf` during module registration.
