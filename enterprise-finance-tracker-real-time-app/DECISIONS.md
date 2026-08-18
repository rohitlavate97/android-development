# Architecture Decision Records (ADR)

---

## ADR 001: Use `@JvmInline value class` for Domain Identifiers

- **Status**: Accepted
- **Context**: Passing primitive strings for IDs creates catastrophic "primitive obsession" where parameters can be swapped in function calls without compile-time errors.
- **Decision**: Wrap all domain entity identifiers in `@JvmInline value class` (e.g., `TransactionId`, `AccountId`, `CategoryId`, `TickerSymbol`).
- **Consequences & Tradeoffs**:
  - *Pros*: Zero runtime heap allocation overhead on JVM, 100% compile-time type safety preventing ID transposition bugs.
  - *Cons*: Requires unboxing when serializing to SQLite or JSON primitives at boundary mappers.

---

## ADR 002: Enforce Business Invariants in Entity `init` Blocks

- **Status**: Accepted
- **Context**: Creating entities with invalid business states can propagate silently through repositories and crash the UI.
- **Decision**: Enforce non-negotiable invariants directly inside entity `init` blocks using `require()`.
- **Consequences & Tradeoffs**:
  - *Pros*: Impossible to create an invalid domain object anywhere in the application.
  - *Cons*: Boundary mappers must catch `IllegalArgumentException` and map to domain failure states.

---

## ADR 003: Pure Kotlin Domain Layer (Zero Android Framework Imports)

- **Status**: Accepted
- **Context**: Coupling domain logic with Android SDK classes makes unit tests slow and breaks clean architecture.
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
- **Context**: Accidental disk reads/writes or network calls on the Main thread cause frame drops and ANR dialogs.
- **Decision**: Initialize Android `StrictMode` inside `EnterpriseFinanceApp.onCreate()` when `BuildConfig.DEBUG` is true.
- **Consequences & Tradeoffs**:
  - *Pros*: Catches main-thread violations immediately in development with Logcat penalties.
  - *Cons*: Must be disabled in release builds to avoid crashing production users.

---

## ADR 006: State Preservation via `onSaveInstanceState` / `SavedStateHandle`

- **Status**: Accepted
- **Context**: Android kills background apps when RAM is low. Storing user state only in memory fields causes data loss upon process recreation.
- **Decision**: All critical transient navigation and form state must be saved to the saved state bundle / `SavedStateHandle`.
- **Consequences & Tradeoffs**:
  - *Pros*: 100% resilient across screen rotations and low-memory OS kills.
  - *Cons*: Saved bundles are limited to ~500KB.

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
- **Context**: Using `SharingStarted.Eagerly` or `Lazily` keeps upstream cold Flows active indefinitely even when the app is in the background, wasting CPU and battery.
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
- **Context**: Letting ViewModels interact with God repositories directly leads to duplicate business rules across multiple screens.
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
- **Context**: Complex screens with multiple concurrent user inputs suffer from race conditions when using multiple independent `MutableStateFlow` fields in ViewModels.
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

---

## ADR 018: Use `kotlinx.serialization` with `ignoreUnknownKeys = true`

- **Status**: Accepted
- **Context**: Gson and Jackson use slow reflection, lack Kotlin null-safety guarantees, and crash when backend services add new unexpected JSON fields.
- **Decision**: Standardize on `kotlinx.serialization` with `ignoreUnknownKeys = true` and `coerceInputValues = true`.
- **Consequences & Tradeoffs**:
  - *Pros*: Fast compile-time generated serializers, zero reflection, safe evolution of backend APIs.
  - *Cons*: Requires applying `@Serializable` and `@SerialName` annotations on all DTOs.

---

## ADR 019: Mutex-Protected Token Refresh in OkHttp `Authenticator`

- **Status**: Accepted
- **Context**: When a JWT token expires, 5 parallel requests receive 401s simultaneously. Without synchronization, the app dispatches 5 duplicate token refresh requests, causing race conditions and session revocation.
- **Decision**: Wrap the refresh operation in a Coroutine `Mutex.withLock` inside `TokenAuthenticator`.
- **Consequences & Tradeoffs**:
  - *Pros*: Exactly ONE refresh request is executed; subsequent pending requests reuse the refreshed token.
  - *Cons*: Requires thread blocking (`runBlocking`) inside OkHttp's synchronous `Authenticator` callback.

---

## ADR 020: Explicit 6-Path Network Error Taxonomy

- **Status**: Accepted
- **Context**: Catching generic `IOException` and displaying "Network Error" prevents users and engineers from knowing the root failure path.
- **Decision**: Classify all network failures into 6 distinct paths: `401 Unauthorized`, `404 Not Found`, `500 Server Error`, `Timeout`, `No Internet`, and `Malformed JSON`.
- **Consequences & Tradeoffs**:
  - *Pros*: Clear UI error states (e.g. "Retry" vs "Log in again" vs "Check connection").
  - *Cons*: Requires comprehensive mapping logic in `safeApiCall`.

---

## ADR 021: Local Room Database as Single Source of Truth (SSOT)

- **Status**: Accepted
- **Context**: Displaying data directly from network responses causes blank screens when offline and inconsistent caching.
- **Decision**: The UI observes ONLY the local Room database via reactive `Flow`. Remote API responses write strictly into Room.
- **Consequences & Tradeoffs**:
  - *Pros*: 100% offline-first functionality, instant UI load times (<10ms), automatic UI updates when database changes.
  - *Cons*: Requires maintaining SQLite tables and database migration scripts.

---

## ADR 022: Adopt Jetpack Preferences DataStore over SharedPreferences

- **Status**: Accepted
- **Context**: `SharedPreferences` runs synchronous disk I/O on the UI thread causing frame drops/ANRs.
- **Decision**: Use `androidx.datastore:datastore-preferences` for lightweight key-value storage.
- **Consequences & Tradeoffs**:
  - *Pros*: 100% asynchronous Coroutines/Flow API, safe transactional writes, no UI thread blocking.
  - *Cons*: Cannot read values synchronously without suspending or collecting Flow.

---

## ADR 023: Explicit Schema Migrations over Destructive Fallbacks

- **Status**: Accepted
- **Context**: Calling `fallbackToDestructiveMigration()` in production deletes all user financial records and transactions during app updates.
- **Decision**: Provide explicit `Migration(from, to)` objects (e.g. `MIGRATION_1_2`) and export Room schemas to `/schemas`.
- **Consequences & Tradeoffs**:
  - *Pros*: Zero user data loss across production database upgrades.
  - *Cons*: Requires writing manual SQL `ALTER TABLE` scripts for schema alterations.

---

## ADR 024: Type-Safe `@Serializable` Navigation-Compose Destinations

- **Status**: Accepted
- **Context**: Legacy Navigation-Compose used raw strings causing runtime crashes from typos in argument keys.
- **Decision**: Use Kotlinx `@Serializable` objects and data classes.
- **Consequences & Tradeoffs**:
  - *Pros*: 100% compile-time type safety for arguments and routes; automated URL encoding/decoding.
  - *Cons*: Requires Kotlin Serialization plugin and Navigation-Compose 2.8+.

---

## ADR 025: Clear Authentication Backstack with `popUpTo<AuthGraph> { inclusive = true }`

- **Status**: Accepted
- **Context**: When a user logs in, pressing the device system Back button should exit the app rather than reopening the Login form.
- **Decision**: When navigating from `AuthGraph` to `MainGraph`, pop up to `AuthGraph` with `inclusive = true` and `launchSingleTop = true`.
- **Consequences & Tradeoffs**:
  - *Pros*: Clean backstack behavior adhering to Android navigation guidelines.
  - *Cons*: Requires structuring screens into nested navigation graphs (`AuthGraph`, `MainGraph`).

---

## ADR 026: Production Deep Linking with `navDeepLink<T>`

- **Status**: Accepted
- **Context**: Navigating to deep link destinations via manual Intent parsing in Activities scatters routing logic across multiple files.
- **Decision**: Declare deep links directly on composable destinations using `navDeepLink<Destination>(basePath = ...)`.
- **Consequences & Tradeoffs**:
  - *Pros*: Centralized routing, automated argument extraction from URL query/path parameters.
  - *Cons*: Requires matching URL path structure with destination properties.

---

## ADR 027: Prefer In-Memory Fakes over Mocking Frameworks in Architecture Tests

- **Status**: Accepted
- **Context**: Using Mockito/MockK to mock repositories creates brittle tests that break whenever internal method call order or signature changes.
- **Decision**: Write lightweight in-memory `Fake*Repository` implementations using `MutableStateFlow`.
- **Consequences & Tradeoffs**:
  - *Pros*: Tests execute realistic state mutations; refactor-proof; fast JVM execution without reflection.
  - *Cons*: Requires maintaining fake classes in test source sets.

---

## ADR 028: Use Cash App Turbine with Virtual Time for Flow Testing

- **Status**: Accepted
- **Context**: Calling `first()` on a `StateFlow` only captures the initial item, missing subsequent transitions or causing tests to hang on cold flows.
- **Decision**: Use `flow.test { awaitItem() }` combined with `StandardTestDispatcher.scheduler.advanceUntilIdle()`.
- **Consequences & Tradeoffs**:
  - *Pros*: Complete inspection of state stream emission sequences with zero timing flakiness.
  - *Cons*: Requires learning Turbine syntax.

---

## ADR 029: Test Compose UI via Stateless Screen Contracts & Semantics

- **Status**: Accepted
- **Context**: Testing Compose screens with tight ViewModel coupling requires spinning up Android instrumentation runners.
- **Decision**: Because screens follow the 3-Layer Pattern (`*Route` -> `*Screen`), unit test the stateless `*Screen` functions and UI logic using pure JVM tests.
- **Consequences & Tradeoffs**:
  - *Pros*: Sub-second test execution on JVM; tests pure UI rendering logic and form validation.
  - *Cons*: Does not verify actual OpenGL pixels (reserved for screenshot testing).

---

## ADR 030: Multi-Module Layered Topology (`:app`, `:core:*`, `:feature:*`)

- **Status**: Accepted
- **Context**: A single monolithic `:app` module causes long incremental build times, allows unrestricted coupling between features, and prevents parallel compilation.
- **Decision**: Split codebase into 9 decoupled modules: `:app`, `:core:common`, `:core:model`, `:core:database`, `:core:network`, `:core:designsystem`, `:feature:dashboard`, `:feature:transactions`, `:feature:analytics`.
- **Consequences & Tradeoffs**:
  - *Pros*: Blazing fast parallel build times, enforced architectural boundaries, high team scalability.
  - *Cons*: Requires managing multiple `build.gradle.kts` files and Version Catalog dependencies.

---

## ADR 031: Strict `api` vs `implementation` Encapsulation

- **Status**: Accepted
- **Context**: Using `api` indiscriminately leaks transitive dependencies and forces downstream modules to recompile whenever an internal dependency changes.
- **Decision**: Default to `implementation`. Use `api` exclusively for core domain models (`:core:model`) that must cross module boundaries.
- **Consequences & Tradeoffs**:
  - *Pros*: Maximum compile avoidance and minimal recompilation times across Gradle builds.
  - *Cons*: Requires intentional declaration of dependencies per module.

---

## ADR 032: Strict Feature Module Isolation (Zero Horizontal Dependencies)

- **Status**: Accepted
- **Context**: Having `:feature:dashboard` depend on `:feature:transactions` creates spaghetti dependency graphs and circular build cycles.
- **Decision**: Feature modules must NEVER depend on each other. All feature navigation is orchestrated centrally in `:app` via type-safe routes.
- **Consequences & Tradeoffs**:
  - *Pros*: Zero circular dependency risks; features can be built, tested, and deleted independently.
  - *Cons*: Shared feature components must be hoisted to `:core:designsystem` or `:core:model`.

---

## ADR 033: Baseline Profiles for Ahead-Of-Time (AOT) DEX Pre-Compilation

- **Status**: Accepted
- **Context**: JIT compilation during app launch and list scrolling causes frame drops (jank) and slow startup times on low-end and mid-range devices.
- **Decision**: Generate Baseline Profiles covering Critical User Journeys (CUJ) (Startup, Login, Dashboard, Transaction List fling) using `androidx.profileinstaller`.
- **Consequences & Tradeoffs**:
  - *Pros*: Up to 30-40% faster cold startup time and smoother 60/120fps scrolling out of the box.
  - *Cons*: Adds a baseline profile generation step in CI before Google Play release.

---

## ADR 034: Compose Compiler Stability via `@Immutable` UI Models

- **Status**: Accepted
- **Context**: The Compose Compiler treats standard `List<T>` parameters as *unstable*, forcing composable rows in `LazyColumn` to recompose even when items haven't changed.
- **Decision**: Annotate all UI presentation state data classes with `@Immutable` or `@Stable`.
- **Consequences & Tradeoffs**:
  - *Pros*: Enables smart recomposition skipping across the entire UI tree.
  - *Cons*: Developers must ensure annotated classes do not contain mutable properties (`var`).

---

## ADR 035: StrictMode VM & Thread Policy Enforcement in Debug Builds

- **Status**: Accepted
- **Context**: Main thread disk reads (e.g. Room queries, file access) and unclosed SQLite cursors slip into production undetected, causing ANRs and memory leaks.
- **Decision**: Initialize `StrictMode` in `EnterpriseFinanceApp.onCreate()` during debug builds.
- **Consequences & Tradeoffs**:
  - *Pros*: Immediately detects regressions during daily development with Logcat penalties.
  - *Cons*: Must be conditionally compiled out in release builds.
