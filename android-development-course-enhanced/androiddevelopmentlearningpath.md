# Modern Android Development — A General Learning Path

Vendor-neutral. Everything here applies to **any** modern Android app, not one codebase. Where the
industry has more than one accepted answer, the alternatives are listed side by side so you can read
*any* repo you're dropped into.

The main body (Phases 1–13) is stack-agnostic and applies to any app. The **appendix at the end**
maps the same material onto our own `Mistplay/mistplay-android` codebase — which option our team
picked on each axis, and the exact files to open. Learn the concept from the body; use the appendix
only to locate it locally.

---

## Table of Contents

1. [The landscape: what "modern Android" means in 2026](#1-the-landscape)
2. [The options map — competing choices you'll meet](#2-the-options-map)
3. [Phase 1 — Kotlin](#phase-1--kotlin)
4. [Phase 2 — Coroutines & Flow](#phase-2--coroutines--flow)
5. [Phase 3 — Android platform fundamentals](#phase-3--android-platform-fundamentals)
6. [Phase 4 — Jetpack Compose](#phase-4--jetpack-compose)
7. [Phase 5 — App architecture](#phase-5--app-architecture)
8. [Phase 6 — Dependency injection](#phase-6--dependency-injection)
9. [Phase 7 — Networking](#phase-7--networking)
10. [Phase 8 — Local persistence](#phase-8--local-persistence)
11. [Phase 9 — Navigation](#phase-9--navigation)
12. [Phase 10 — Testing](#phase-10--testing)
13. [Phase 11 — Gradle & modularization](#phase-11--gradle--modularization)
14. [Phase 12 — Quality, performance & observability](#phase-12--quality-performance--observability)
15. [Phase 13 — Release engineering, flags & experiments](#phase-13--release-engineering-flags--experiments)
16. [Suggested schedule](#suggested-schedule)
17. [Universal anti-patterns](#universal-anti-patterns)
18. [Glossary](#glossary)
19. [How to read an unfamiliar Android codebase in 90 minutes](#how-to-read-an-unfamiliar-android-codebase-in-90-minutes)
20. [Canonical resources](#canonical-resources)
21. [Appendix — applying this to `Mistplay/mistplay-android`](#appendix--applying-this-to-mistplaymistplay-android)
    — our stack choices, module topology, house rules, and a file-by-file trace of one real feature

---

## 1. The landscape

What a professional Android app looks like today, and what it replaced:

| Concern | Current standard | What it replaced |
|---|---|---|
| Language | **Kotlin** | Java |
| UI | **Jetpack Compose** (declarative) | XML layouts + `findViewById` / ViewBinding |
| Async | **Coroutines + Flow** | AsyncTask, Handler, RxJava, callbacks |
| Screen container | One `Activity` + many composables | One Activity or Fragment **per screen** |
| State holder | **`ViewModel`** + immutable UI state | Presenter (MVP), Activity-as-controller |
| DI | **Hilt or Koin** | manual factories, service locators |
| HTTP | **Retrofit / Ktor** over OkHttp | HttpURLConnection, Volley, AsyncTask |
| DB | **Room** | raw SQLiteOpenHelper |
| Key-value | **DataStore** | SharedPreferences |
| Navigation | **Navigation-Compose** (type-safe routes) | `startActivity(Intent)`, FragmentManager |
| Background work | **WorkManager** | Services, AlarmManager, JobScheduler |
| Build | Gradle + **version catalog**, convention plugins | hardcoded versions per module |
| Structure | **Multi-module** by feature | one giant `:app` module |
| Config | **Remote flags / experiments** (Firebase Remote Config, Statsig, LaunchDarkly) | ship-and-pray |

**Two ideas underpin all of it:**

1. **Declarative UI** — you describe what the screen looks like *for a given state*; the framework
   re-renders when state changes. You never imperatively mutate views.
2. **Unidirectional Data Flow (UDF)** — state flows **down** (data layer → ViewModel → UI), events
   flow **up** (UI → ViewModel → data layer). One direction each. Never a two-way loop.

If you learn nothing else structural, learn UDF. Every architecture below is a variation on it.

---

## 2. The options map

Real repos differ. These are the axes, so nothing surprises you:

| Axis | Option A | Option B | Option C |
|---|---|---|---|
| DI | **Hilt** (Dagger, compile-time, annotation-heavy, Google-blessed) | **Koin** (runtime, Kotlin DSL, no codegen, fails at runtime not compile time) | manual / `koin-annotations` |
| Presentation pattern | **MVVM** — ViewModel exposes state, UI calls methods directly | **MVI** — a single `State`, a sealed `Intent`/`Action` type, and a pure reducer | MVI variants: Orbit, Mobius, MVIKotlin, Circuit |
| HTTP client | **Retrofit** (annotated interfaces) | **Ktor Client** (Kotlin-first, KMP-ready) | raw OkHttp |
| JSON | **kotlinx.serialization** (compile-time, KMP) | **Moshi** (codegen, null-safe) | **Gson** (reflection, older, lenient) |
| Immutable collections | `kotlinx.collections.immutable` (`PersistentList`, `ImmutableList`) | plain `List` + `@Immutable`/`@Stable` annotations | — |
| Image loading | **Coil** (Kotlin/coroutines) | Glide | Fresco |
| Navigation | **Navigation-Compose** with `@Serializable` type-safe routes | Compose Destinations (KSP) | custom nav state machine; Voyager; Decompose |
| Multiplatform | Android-only | **KMP** shared domain/data | KMP + Compose Multiplatform UI |
| Testing doubles | **Fakes** (hand-written) | **Mocks** (MockK / Mockito) | mixed |
| UI tests | Compose test rule + Robolectric (JVM, fast) | instrumented on device/emulator | Appium/Maestro/QAWolf (black box, E2E) |
| Modularization | by **feature**, with `api`/`impl` split | by **layer** (`:domain`, `:data`, `:ui`) | single module |

**How to tell which a repo uses in 60 seconds:** open `gradle/libs.versions.toml` (or the root
`build.gradle`) and read the dependency list. It tells you the whole stack before you read a line of
Kotlin.

---

## Phase 1 — Kotlin

**Goal:** read and write idiomatic Kotlin without translating from Java in your head.

### Concepts, in dependency order

1. `val` vs `var`; immutability as the default
2. **Null safety** — `?`, `?.`, `?:`, `!!`, `lateinit`, platform types from Java
3. `data class` — `copy()`, destructuring, `equals`/`hashCode` for free
4. `sealed class` / `sealed interface` + **exhaustive `when`** — the backbone of state modelling
5. `object`, `data object`, `companion object`
6. **Extension functions & properties** — how Kotlin codebases add behaviour without inheritance
7. Default & named arguments (they kill the Builder pattern and most overloads)
8. **Higher-order functions, lambdas, trailing-lambda syntax**, `inline`, function types
9. Scope functions: `let`, `run`, `with`, `apply`, `also`, `takeIf`, `takeUnless`
10. `operator fun invoke()` — makes a class callable like a function (why use cases look like `getUser()`)
11. Visibility: `public` (default), `internal` (module-scoped), `private`, `protected`
12. Collections API: `map`, `filter`, `mapNotNull`, `flatMap`, `groupBy`, `associateBy`, `fold`,
    `first/firstOrNull`, `any/all/none`, `sortedBy`, `partition`; **sequences** for large/lazy chains
13. `Result<T>`, `runCatching`, and sealed-class result types — the three ways Kotlin models failure
14. Generics: variance (`in`/`out`), `reified` type parameters
15. Delegation: `by lazy`, `by`, delegated properties
16. `typealias`, `value class` (inline classes), enums vs sealed
17. `kotlinx.datetime` / `java.time`; `Duration`
18. Interop: `@JvmStatic`, `@JvmOverloads`, `@Throws` (only matters in mixed codebases)

### Java → Kotlin translation table

| Java | Kotlin |
|---|---|
| `getX()` / `setX()` | properties (`val x` / `var x`) |
| Builder pattern | default + named arguments |
| `static` utils class | top-level functions or extension functions |
| `Optional<T>` | `T?` |
| abstract class + subclasses for a closed set | `sealed interface` |
| POJO + Lombok | `data class` |
| interface with one method + anon class | lambda / function type |
| checked exceptions | none — model failure in the return type |
| `for` loops with index bookkeeping | collection operators |
| `switch` fallthrough | `when` (no fallthrough, is an expression) |

### Practice

- Convert a small Java class you already own to Kotlin, then simplify it (a page object, a util bag,
  a POJO). Android Studio's converter gives you Java-flavoured Kotlin — the *learning* is in the
  cleanup pass afterwards.
- Model a real domain as a `sealed interface` and handle it with an exhaustive `when` with **no `else`**.
- Do the [Kotlin Koans](https://play.kotlinlang.org/koans) — a few hours, high payoff.

### Checkpoint

Explain, unaided:
```kotlin
internal class GetUser(private val repo: UserRepo) {
    suspend operator fun invoke(id: String, refresh: Boolean = false): Result<User> = repo.get(id, refresh)
}
```
…and say why `else ->` in a `when` over a sealed type is usually a latent bug.

---

## Phase 2 — Coroutines & Flow

**Goal:** the single highest-leverage phase. Almost every modern Android bug you will debug is a
coroutine, lifecycle, or flow-collection bug.

### Coroutine concepts

1. `suspend` — what it actually compiles to (a state machine, not a thread)
2. **Main-safety** — a `suspend` function must be safe to call from the main thread
3. `CoroutineScope`, `Job`, `SupervisorJob`, `CoroutineContext`
4. **Structured concurrency** — children are tied to a parent; the parent doesn't finish until they do
5. `launch` (fire and forget) vs `async`/`await` (parallel results) vs `withContext` (switch thread, return value)
6. **Cancellation is cooperative** — `isActive`, `ensureActive()`, `NonCancellable`,
   and the golden rule: **never swallow `CancellationException`** (this is why `runCatching` and
   `catch (e: Exception)` are dangerous in coroutine code — catch it and re-throw it)
7. Dispatchers: `Main`, `Main.immediate`, `IO` (blocking IO), `Default` (CPU), `Unconfined`.
   **Inject dispatchers, never hardcode them** — that's what makes code testable
8. Android scopes: `viewModelScope`, `lifecycleScope`, `repeatOnLifecycle`, and an
   application-lifetime scope for work that must outlive a screen
9. `coroutineScope { }` vs `supervisorScope { }`; `withTimeout`, `retry`
10. Exception handling: how it propagates, `CoroutineExceptionHandler`, why `try/catch` around
    `launch` doesn't do what you expect

### Flow concepts

11. **Cold `Flow`** — nothing runs until collected; a new run per collector
12. **Hot `StateFlow`** (always has a current value, conflated) and **`SharedFlow`** (events, replay
    cache) — and `MutableStateFlow` for in-memory reactive caches
13. `flow { }`, `flowOf`, `asFlow`, `channelFlow`, `callbackFlow` (wrapping a listener API)
14. Operators: `map`, `mapLatest`, `filter`, `transform`, `combine`, `zip`, `flatMapLatest`,
    `onStart`, `onEach`, `catch`, `retryWhen`, `debounce`, `distinctUntilChanged`, `sample`, `emitAll`
15. **Context preservation** — use `flowOn` inside a `Flow`; `withContext` around emissions is illegal
16. `stateIn` / `shareIn` and the **start strategies**: `Eagerly`, `Lazily`,
    **`WhileSubscribed(5_000)`** — the last is the standard for UI state (survives rotation, stops
    when nothing is watching)
17. Never return a `StateFlow` from a function or a custom `get()` — each call builds a new one and
    leaks. Store it as a property.
18. Collecting in UI: **`collectAsStateWithLifecycle()`** in Compose;
    `repeatOnLifecycle(STARTED)` in Views. Plain `collect` in a `lifecycleScope` keeps running
    while the screen is backgrounded — a classic leak.
19. Back-pressure & buffering: `buffer`, `conflate`, `collectLatest`

### Practice

- Wrap a callback-based API in `callbackFlow` and make cancellation work (`awaitClose`).
- Write a `Flow` chain: fake network emission → `map` to UI model → `catch` → `stateIn`. Assert the
  emissions with **Turbine**.
- Deliberately break structured concurrency: `runCatching` around a cancelled call, watch a
  cancelled job continue, then fix it.
- Write a test with `runTest`, `TestDispatcher`, `advanceTimeBy`, and a `debounce` operator.

### Checkpoint

Answer without looking: why is `fun state(): StateFlow<X> = flow.stateIn(scope, …)` a bug? What
breaks if you use `withContext(Default)` around `emit()`? What's the practical difference between
`collectAsState()` and `collectAsStateWithLifecycle()`?

---

## Phase 3 — Android platform fundamentals

**Goal:** know the platform under the framework. Compose doesn't remove it, it hides it.

1. **Process & app lifecycle** — `Application`, cold/warm/hot start, process death, low-memory kills
2. **Activity lifecycle** — `onCreate/Start/Resume/Pause/Stop/Destroy`; **configuration changes**
   and why they recreate everything; `SavedStateHandle` / `rememberSaveable` for process-death survival
3. Why the modern shape is **one Activity, many composables** — and when a second Activity is justified
4. **Intents** — explicit vs implicit, extras, `PendingIntent`, deep links, exported vs non-exported
   components (a non-exported activity **cannot** be launched by another app or by `adb am start`
   without permission — this is a common automation gotcha)
5. `AndroidManifest.xml` — permissions, activities, services, receivers, providers, `queries`
6. **Resources & qualifiers** — `res/values`, densities (`hdpi`…`xxxhdpi`), locales, night mode,
   `strings.xml` with plurals and format args; **localization**; RTL
7. **Runtime permissions** — request flow, rationale, "don't ask again", `POST_NOTIFICATIONS`,
   location (foreground/background), scoped storage
8. Background execution limits — Doze, App Standby, foreground-service restrictions,
   **WorkManager** for deferrable guaranteed work
9. Notifications & channels; FCM push basics
10. `Context` — Application vs Activity context, and how holding the wrong one leaks
11. Storage: app-private files, cache, MediaStore, `SharedPreferences` → **DataStore**
12. **API levels** — `minSdk`/`targetSdk`/`compileSdk`, behaviour changes per release, desugaring
13. APK/AAB, R8/ProGuard shrinking & obfuscation, `multiDex`, native ABIs, app signing
14. **ADB** — `install`, `logcat`, `shell am start`, `shell pm clear`, `dumpsys`, `bugreport`

### Practice

Build a throwaway app that: requests one runtime permission, survives rotation with state intact,
handles a deep link, and enqueues a WorkManager job. Then kill the process from Studio's
"Terminate Application" and prove your state restores.

### Checkpoint

Explain what survives: rotation, backgrounding, process death, force-stop — and which mechanism
covers each (`ViewModel`, `SavedStateHandle`, disk).

---

## Phase 4 — Jetpack Compose

**Goal:** build screens declaratively and understand recomposition well enough not to write janky UI.

### Core

1. `@Composable` functions — no return value, they *emit* UI. Idempotent, side-effect free.
2. **Recomposition** — the framework re-invokes composables whose inputs changed. Assume it runs
   often, in any order, and can be skipped or cancelled.
3. **State**: `mutableStateOf`, `remember`, `rememberSaveable`, `derivedStateOf`,
   `produceState`, `snapshotFlow`
4. **State hoisting** — push state up, pass data down, pass events up as lambdas.
   **Stateless composables are the default**; stateful wrappers are the exception.
5. `Modifier` — order matters (`padding().background()` ≠ `background().padding()`); chaining,
   custom modifiers, `Modifier` as the first optional parameter by convention
6. Layout: `Column`, `Row`, `Box`, `Spacer`, weights, `ConstraintLayout`, intrinsic sizes,
   custom `Layout`; the **measure → layout → draw** phases
7. Lists: `LazyColumn`/`LazyRow`/`LazyVerticalGrid`, `key = { }` (essential for correctness *and*
   performance), `contentType`, sticky headers, paging
8. Theming: `MaterialTheme`, color/typography/shape systems, **design tokens**, dark mode,
   dynamic color, custom design systems on top of Material 3
9. **Side effects** — the ones you must know: `LaunchedEffect(key)`, `rememberCoroutineScope`,
   `DisposableEffect`, `SideEffect`, `rememberUpdatedState`. Never launch work in the composable body.
10. `@Preview`, preview parameters, light/dark previews, `@PreviewScreenSizes`; Compose previews are
    your inner development loop
11. **Stability & skipping** — `@Stable`, `@Immutable`, why `List<T>` params defeat skipping and
    `ImmutableList`/`PersistentList` fix it; the Compose compiler metrics/reports
12. Animation: `animate*AsState`, `AnimatedVisibility`, `AnimatedContent`, `Transition`, `Modifier.animateItem`
13. Accessibility: `contentDescription`, semantics, merge/clear semantics, touch target sizes,
    `testTag` (**this is what UI tests and some automation frameworks hook onto — a real bridge from
    your QA background**)
14. Interop: `AndroidView`, `ComposeView` — how apps migrate incrementally from XML
15. `CompositionLocal` — and why it should be rare (implicit dependencies)

### Common structure you'll see

A three-layer screen split, used in most mature Compose codebases:

```kotlin
// 1. Route/entry — owns the ViewModel, collects state, wires navigation
@Composable
fun ProfileRoute(viewModel: ProfileViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(state = state, onRefresh = viewModel::refresh, onBack = onBack)
}

// 2. Screen — stateless, takes state + lambdas. Fully previewable, fully testable.
@Composable
internal fun ProfileScreen(state: ProfileUiState, onRefresh: () -> Unit, onBack: () -> Unit) { … }

// 3. Components — small reusable pieces
@Composable private fun ProfileHeader(name: String, modifier: Modifier = Modifier) { … }
```

Why it matters: layer 2 has no ViewModel, no DI, no navigation — so it previews instantly and tests
without a device.

### Practice

Build a two-screen app: a list fetched from a fake repository (loading / content / empty / error
states) and a detail screen. Requirements: no `ViewModel` reference below the route layer, every
composable previewable, dark mode correct, `key` set on the lazy list.

### Checkpoint

Explain why passing `List<Item>` into a composable can cause needless recomposition, and three ways
to fix it. Explain what `LaunchedEffect(Unit)` vs `LaunchedEffect(id)` do differently.

---

## Phase 5 — App architecture

**Goal:** know where a given piece of code belongs, and be able to defend it.

### Layers (Google's recommended architecture)

```
   UI layer            Compose screens + state holders (ViewModel)
       ↓ ↑
   Domain layer        use cases / interactors — optional but common at scale
       ↓ ↑
   Data layer          repositories → data sources (remote, local, memory)
```

Rules that hold in essentially every serious codebase:

- **Dependencies point inward.** UI knows domain; domain knows nothing about UI. Data implements
  what domain declares.
- **The domain layer has no Android imports.** No `Context`, `Intent`, `Bundle`, `WorkManager`,
  `Notification`. If domain needs platform behaviour, it *declares an interface* and something
  outward implements it (**dependency inversion**; often called an *interactor* or *gateway*).
- **The repository is the single source of truth** for its data, and owns the cache policy.
  Callers never know whether an answer came from network, disk or memory.
- **Mappers at every boundary.** A network DTO must not leak into the domain, and a domain model must
  not leak into the UI. Each layer owns its own model. It feels like boilerplate; it's what lets you
  change an API response without touching a screen.
- **The ViewModel is a state holder, not a controller.** It exposes immutable UI state and accepts
  events. No `Context`, no `View`, no navigation logic beyond emitting an event.

### Roles, precisely

| Class | Responsibility | Never |
|---|---|---|
| **Composable** | render state, emit events | fetch data, hold business logic |
| **ViewModel** | hold UI state, orchestrate calls, survive config change | touch Android UI/`Context`, do IO itself |
| **Use case** | one business operation, reusable across ViewModels | know about UI or the network client |
| **Repository** | single source of truth, cache policy, combine sources | contain UI logic; be shared across unrelated features |
| **Data source** | talk to exactly one backing store (API / DB / prefs) | contain business rules |
| **Mapper** | translate between two layers' models | have side effects |

### MVVM vs MVI

**MVVM (simpler, most common):**
```kotlin
class ProfileViewModel(private val getUser: GetUser) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        getUser().fold(
            onSuccess = { u -> _state.update { it.copy(user = u, isLoading = false) } },
            onFailure = { e -> _state.update { it.copy(error = e.message, isLoading = false) } },
        )
    }
}
```

**MVI (more ceremony, stricter guarantees):** one `State`, a sealed `Intent` per possible action, and
a **pure reducer** `(State, Intent) -> State`. All async work happens in *effects* that emit new
Intents back into the reducer. Benefits: every state transition is a single testable pure function,
the state history is replayable, and impossible states are easy to design out. Cost: more types and
indirection per screen.

Many teams also split **internal state** (rich domain data) from **UI state** (exactly what the
screen needs), with a mapper between — so the UI can't accidentally depend on business internals.

### One-off events

State is *current*; events (show a snackbar, navigate once) must fire exactly once. Options, best
first: **model it in the state** and let the UI clear it; a `Channel`/`SharedFlow` consumed with
lifecycle awareness; (avoid) `SingleLiveEvent`-style hacks. Getting this wrong causes duplicate
navigation on rotation — a bug you've almost certainly *seen* as a tester.

### Practice

Take the Phase 4 app and refactor it into layers: `ui/`, `domain/`, `data/` with a repository, two
data sources (fake remote + in-memory cache), a use case, DTO→domain→UI models and mappers. Then
write the same screen twice, once MVVM and once MVI, and compare the test files.

### Checkpoint

Given any class in a repo, say which layer it belongs to and name one thing it must **not** import.

---

## Phase 6 — Dependency injection

**Goal:** understand construction, scope, and lifetime — the source of most "why is this state
wrong?" bugs.

1. Why DI at all: constructor injection makes classes testable and lifetimes explicit
2. **Scopes/lifetimes:** singleton (app), scoped (screen/navigation graph/session), factory (new
   each time). Choosing wrongly = leaks or lost state.
3. Binding an interface to an implementation — and how test builds swap in fakes
4. **ViewModel injection**, including passing route/navigation arguments in
5. Module organization — one DI module per layer per feature, composed into an app graph
6. **Hilt** specifics: `@HiltAndroidApp`, `@AndroidEntryPoint`, `@Module`/`@InstallIn`,
   `@Provides`/`@Binds`, `@Singleton`/`@ViewModelScoped`, `hiltViewModel()`.
   Compile-time verified — a missing binding fails the **build**.
7. **Koin** specifics: `startKoin`, `module { }`, `single`/`factory`/`viewModel`,
   `singleOf(::X) bind Y::class`, `get()`, `koinViewModel()`, `checkModules()` test.
   Runtime resolution — a missing binding fails at **runtime**, so `checkModules()`/verify tests
   matter more.
8. Anti-patterns: service-locator calls scattered through classes, injecting a DI container,
   singletons holding `Activity` context, hidden global mutable state

### Practice

Wire your Phase 5 app with Hilt **and** Koin, one after the other. It's a couple of hours and you'll
be able to read either codebase forever after.

### Checkpoint

Explain what breaks if a screen-scoped cache is registered as a singleton, and what breaks if an
app-wide session store is registered as a factory.

---

## Phase 7 — Networking

**Goal:** call an API correctly, including the unhappy paths.

1. **HTTP fundamentals** first: methods, status codes, headers, content types, caching,
   TLS, timeouts, idempotency
2. **OkHttp** — the layer under everything: connection pooling, **interceptors** (auth headers,
   logging, retries), `Authenticator` for token refresh, `CookieJar`, certificate pinning
3. **Retrofit** — annotated interfaces, `suspend` functions returning `T` or `Response<T>`,
   `@Path`/`@Query`/`@Body`/`@Field`/`@Header`, converters, call adapters
4. **Ktor Client** as the alternative — same job, Kotlin DSL, KMP-friendly
5. **Serialization** — `kotlinx.serialization` / Moshi / Gson. Nullability discipline: server-optional
   fields are nullable with defaults, because **the server will eventually send you something you
   didn't expect**
6. **DTO ≠ domain model.** Map at the boundary. Never let `@SerializedName` classes reach your UI.
7. **Error handling** — a uniform result type (`Result<T>` or a sealed `ApiResult`) covering:
   HTTP error with a parsed error body, network failure, timeout, serialization failure, and
   cancellation (which is **not** an error). Wrap every call in one helper so this is consistent.
8. **Resilience:** timeouts, retry with exponential backoff + jitter, offline behaviour,
   `mapNotNull` over lists so one malformed item doesn't nuke a whole response, and
   log-and-fallback for unknown enum values instead of throwing
9. Auth: token storage, refresh-on-401, logout-on-refresh-failure, race conditions between parallel refreshes
10. Realtime: WebSockets / SSE / socket.io, and polling as the boring alternative
11. Debugging: OkHttp logging interceptor, Charles/Proxyman/mitmproxy, Studio's Network Inspector

### Practice

Consume a public API. Then deliberately handle: 500 with a JSON error body, malformed JSON, a
timeout, airplane mode, an unknown enum value, and a cancelled request mid-flight. Six paths — most
production bugs live in these six.

### Checkpoint

Show your single call-wrapping helper and explain how `CancellationException` passes through it
untouched.

---

## Phase 8 — Local persistence

**Goal:** cache correctly and reactively.

1. Pick the right tool: **DataStore** (Preferences or Proto) for key-value and settings;
   **Room** for structured/queryable/relational data; files for blobs; in-memory
   `MutableStateFlow` for session-lifetime reactive cache; `EncryptedSharedPreferences`/Keystore
   for secrets. **SharedPreferences is legacy** — its `apply()` can block, DataStore is
   async and transactional.
2. Room: `@Entity`, `@Dao`, `@Database`, relations, `@Transaction`, type converters,
   **`Flow`-returning queries** (the DB pushes updates to the UI), **migrations** and schema export,
   in-memory DB for tests
3. **Single source of truth pattern:** UI observes the *database*; the network only *writes* to it.
   The UI never waits on the network to render.
4. Cache invalidation: TTL/staleness, `NetworkBoundResource`-style flows, pull-to-refresh,
   optimistic updates + rollback
5. Consistency across process death; what must be persisted vs recomputed
6. Migrations are a **production** concern — an unhandled migration crashes on launch for existing
   users only, which is exactly the case your emulator tests miss

### Practice

Add Room to your app: observe a `Flow` query in the UI, refresh from the fake network into the DB,
verify the screen updates without touching the ViewModel. Then write a real migration and test it.

### Checkpoint

Draw the data flow for "user opens screen offline, then connectivity returns" with no UI flicker and
no duplicate loading spinners.

---

## Phase 9 — Navigation

**Goal:** move between screens without leaking state or breaking the back stack.

1. **Back stack** semantics: push, pop, `popUpTo`, inclusive, `launchSingleTop`, `saveState`/`restoreState`
2. **Type-safe routes** — `@Serializable` route classes (the current standard) instead of string
   routes with manually parsed arguments
3. Passing arguments: small, serializable identifiers only. **Never pass whole objects** — pass an
   ID and re-fetch. Read args via `SavedStateHandle`/`toRoute()`.
4. Nested graphs, per-graph scoping, bottom-nav with multiple back stacks
5. Dialogs and bottom sheets **as destinations** (so back/predictive-back behave correctly)
6. **Navigation must not live in composables' business logic.** Screens emit callbacks
   (`onItemClick: (String) -> Unit`); a route/coordinator layer decides where that goes. Larger apps
   centralize this into a navigator/state machine so features never touch `NavController` and never
   depend on each other.
7. Deep links and app links; conditional/authenticated routing; **flag-driven routing** (which
   screen you land on can depend on an experiment — a frequent source of "it worked on my device")
8. Transitions, shared-element transitions, predictive back
9. Result passing between screens (the back-stack-entry pattern), not a shared mutable singleton

### Practice

Three screens: list → detail → edit. Edit returns a result to detail. Add a deep link straight to
detail. Verify the back stack behaves after rotation and after process death.

### Checkpoint

Explain why passing a `Parcelable` domain object between destinations is a bad default, and what to
do instead.

---

## Phase 10 — Testing

**Goal:** as a QA automation engineer this is your unfair advantage — you already think in test
pyramids. What's new is *unit*-level testing of Kotlin/coroutine code.

### The pyramid, Android edition

| Level | Runs on | Tools | What to cover |
|---|---|---|---|
| **Unit (JVM)** | your machine, milliseconds | JUnit4/5, Truth/Kotest assertions, **Turbine**, `kotlinx-coroutines-test`, fakes, MockK | use cases, mappers, reducers, ViewModels, repositories |
| **JVM + Android stubs** | your machine | **Robolectric** | code needing `Context`, resources, `SharedPreferences` |
| **Compose UI** | JVM (Robolectric) or device | `createComposeRule`, semantics matchers, screenshot tests (Paparazzi/Roborazzi) | one screen's states in isolation |
| **Instrumented** | emulator/device | Espresso, UIAutomator, `createAndroidComposeRule` | integration across real Android components |
| **E2E / black box** | device farm | Appium, Maestro, QAWolf, Firebase Test Lab | critical user journeys only |

Cost rises and stability falls as you go up. **Most value lives in the bottom two rows** — which is
exactly the layer that's invisible from an Appium seat, and the fastest way for you to add value.

### Coroutine & flow testing

- `runTest { }`, virtual time, `advanceTimeBy`, `advanceUntilIdle`, `runCurrent`
- `TestDispatcher` (`StandardTestDispatcher` vs `UnconfinedTestDispatcher`), `Dispatchers.setMain`
  (or a `CoroutineTestRule`), and **injected dispatchers** so you never need to touch globals
- **Turbine** for flows: `flow.test { assertEquals(X, awaitItem()); awaitComplete() }`
- Testing `WhileSubscribed` state flows: remember they don't emit unless collected

### Fakes vs mocks (know the argument, it comes up in every review)

**Fakes** are hand-written working implementations of an interface. **Mocks** are generated
stubs configured per test.

Fakes win on: reusability (one fake serves many tests), maintainability (refactor once),
stability (no reflection, no breakage on library upgrades), and looser coupling (tests assert
behaviour, not "method X was called"). Mocks win on: zero boilerplate for a one-off.

Practical order: **real object → fake → mock**. Reserve mocks for types you don't own
(`Context`, framework classes). Overusing mocks produces tests that pass while the app is broken,
because they assert your assumptions rather than real behaviour.

### Hygiene

- Name tests as behaviour: `` `when_offline_and_cache_empty_then_error_state_is_emitted` ``
- Structure every test **given / when / then** (= arrange / act / assert)
- Test doubles in `src/test/.../fake/`, data builders in `.../factory/` — build objects via factories
  with sensible defaults, not 12-argument constructors repeated 40 times
- One behaviour per test; no `Thread.sleep`; no dependence on test ordering; no real network/clock —
  inject a `Clock`
- Flakiness is a bug in the test, not an excuse for a retry

### Practice

For your Phase 5 app write, in this order: a mapper test (pure), a use case test with a fake repo, a
ViewModel test with Turbine + `TestDispatcher`, a Compose test asserting the four UI states, and one
screenshot test. Then break the production code on purpose and confirm each test fails for the right
reason. **A test you've never seen fail is not yet a test.**

### Checkpoint

Explain what `runTest`'s virtual time does to a `delay(30_000)`, and why a fake is usually better
than `every { repo.get() } returns …`.

---

## Phase 11 — Gradle & modularization

**Goal:** stop being blocked by the build.

1. Gradle model: projects, tasks, configurations, phases (initialization → configuration →
   execution); Groovy DSL vs **Kotlin DSL** (`.gradle` vs `.gradle.kts`)
2. **Version catalog** (`gradle/libs.versions.toml`) — one place for every version and dependency
   alias. **Reading this file first tells you a codebase's entire stack.**
3. `implementation` vs `api` vs `compileOnly` vs `runtimeOnly` vs `testImplementation` — and why
   `api` leaks transitively and slows builds
4. Android build config: `compileSdk`/`minSdk`/`targetSdk`, **build types** (debug/release),
   **product flavors** and dimensions (white-labelling, environments), variants
   (`assembleFlavorDebug`), `buildConfigField`, signing configs, `manifestPlaceholders`
5. R8/ProGuard rules, resource shrinking, non-transitive R classes
6. Speed: build cache (local + remote), configuration cache, parallel execution, KSP over kapt,
   `--scan` to find what's slow
7. Convention plugins / `buildSrc` — how mature repos avoid copy-pasted build files
8. **Multi-module design:**
   - split **by feature** (a vertical slice each), not only by layer
   - the **api/impl split**: a tiny contracts-only module (interfaces + models) that peers depend on,
     with the implementation module depended on **only by the app module**, which wires them in DI.
     This is dependency inversion at module level — it prevents dependency cycles between features
     and keeps incremental builds fast, because changing an implementation recompiles nothing else.
   - `internal` visibility as the module's wall: everything internal by default, only the contract public
   - one DI module per layer, composed upward into the app graph
   - trade-off: more modules = faster incremental builds and enforced boundaries, but more
     configuration overhead and more indirection to read

### Practice

Split your app into `:app`, `:feature:x`, `:feature:x-api`, `:core:ui`, `:core:network`. Move all
versions into a version catalog. Add one product flavor and one `buildConfigField` that changes
behaviour. Measure build time before and after with `--scan`.

### Checkpoint

Explain what recompiles when you change a function body inside an `impl` module vs inside an `api`
module, and why that difference drives the whole modularization strategy.

---

## Phase 12 — Quality, performance & observability

**Goal:** ship code that stays healthy in the field.

**Static analysis & style**
- **ktlint** (formatting) and **detekt** (smells, complexity) — plus **custom rules**: mature teams
  encode their own conventions as lint/detekt rules so review comments become build failures
- Android Lint for platform-specific issues; treat warnings as errors on CI

**Performance**
- Startup time (cold/warm/hot), **Baseline Profiles** & Startup Profiles, App Startup library
- Jank: the 16 ms frame budget, `Modifier.drawWithCache`, avoiding work in composition,
  Compose compiler metrics for skippability, `LazyColumn` keys
- Memory: leaks (**LeakCanary**), bitmap sizing, `Context` leaks; ANRs and strict mode
- APK/AAB size: shrinking, WebP/vector assets, dynamic feature modules
- Tools: Macrobenchmark, Microbenchmark, Perfetto/systrace, Android Studio Profiler

**Observability** (how you find out you broke something before Twitter does)
- Crash reporting (Crashlytics/Sentry) — symbol uploads, non-fatals, breadcrumbs
- Performance/RUM (Firebase Performance, Datadog), custom traces
- Structured **analytics** — event naming schemas, typed event keys instead of stringly-typed calls,
  and validation of the event contract (a natural place for a QA-minded engineer to own tooling)
- Logging discipline: levels, no PII, strip verbose logs in release
- Alerting on crash-free-users %, ANR rate, key funnel drop-off

**CI**
- Per-PR: compile, unit tests, lint/ktlint/detekt, and ideally only for **changed modules**
- Nightly: full test suite, instrumented tests on a device farm, screenshot diffs
- Merge queues, required checks, PR templates, `CODEOWNERS`
- Artifacts: signed QA/prod builds published automatically

### Practice

Add ktlint + detekt to your project and make CI fail on a violation. Write one custom detekt rule
(e.g. "no `runCatching` in a `suspend` function"). Add LeakCanary and deliberately create a leak.
Generate a baseline profile and measure startup before/after.

### Checkpoint

Name the four signals you'd watch after a release, and what value would make you roll back.

---

## Phase 13 — Release engineering, flags & experiments

**Goal:** understand how code becomes a user-visible change — and why the same build behaves
differently for two users.

1. Branching: trunk-based vs GitFlow; release branches, cherry-picks, hotfix and patch flows, backports
2. Versioning: `versionCode`/`versionName`, semantic versioning, build numbers from CI
3. Play Console: internal/closed/open testing tracks, **staged rollout**, halting a rollout,
   in-app updates, pre-launch reports
4. Signing: upload key vs app signing key, Play App Signing, keystore hygiene
5. **Feature flags** (kill switches, gradual rollout) vs **experiments** (A/B with metrics) vs
   **remote config** (values you can tune without shipping). Tools: Firebase Remote Config,
   Statsig, LaunchDarkly, Optimizely, Amplitude.
6. How flag systems actually work, and why they cause "unreproducible" bugs:
   - a **unit id** (user/device) is hashed into a bucket → deterministic per user, arbitrary across users
   - **targeting rules / segments** (country, app version, cohort) gate eligibility
   - **default values compiled into the app** apply until the SDK resolves — so behaviour differs on
     first launch, offline, or before login
   - values are **cached** locally, so a change may need a restart or a fresh install
   - **layers/mutual exclusion** prevent overlapping experiments from confounding each other
   - conditional navigation means a *flag can change the screen order itself*
7. **Testing flagged code:** verify both variants explicitly; pin/override flags in QA builds
   (debug menus exist for this); never let a test's outcome depend on an unpinned remote value.
   Treat "which variant did this run get?" as required test metadata.
8. Kill-switch discipline: every risky feature ships behind a flag defaulted **off**
9. Migration & rollback: flags for behaviour, DB migrations for data (only one of those is reversible)

### Practice

Add Firebase Remote Config (free) to your app: one boolean flag with a compiled-in default, one
config value, and one screen whose *destination* changes with the flag. Then test it: both variants,
offline first launch, and a value changed mid-session.

### Checkpoint

Explain to a teammate why a test passed on your laptop and failed in CI when neither the code nor the
test changed — using bucketing, targeting, defaults and caching in the explanation.

---

## Suggested schedule

Assuming a working engineer studying part-time (~6–8 h/week):

| Weeks | Focus | Deliverable |
|---|---|---|
| 1–2 | Phase 1 Kotlin | Koans done; one Java class idiomatically converted |
| 3–4 | Phase 2 Coroutines & Flow | a tested Flow pipeline with Turbine |
| 5 | Phase 3 platform | throwaway app: permission + rotation + deep link + WorkManager |
| 6–8 | Phase 4 Compose | 2-screen app, 4 UI states, fully previewable |
| 9–10 | Phase 5 architecture + Phase 6 DI | same app layered, wired with Hilt *and* Koin |
| 11 | Phase 7 networking | real API + all six failure paths |
| 12 | Phase 8 persistence | Room as single source of truth, offline-first |
| 13 | Phase 9 navigation | 3 screens, result passing, deep link, process death |
| 14–15 | Phase 10 testing | full pyramid on your own app; every test seen failing once |
| 16 | Phase 11 Gradle/modules | app split into 5 modules with an api/impl pair |
| 17 | Phase 12 quality/perf | CI failing on lint; baseline profile; leak found |
| 18 | Phase 13 release/flags | flag-driven screen, both variants tested |

Order matters more than pace: **1 → 2 → 4 → 5** is the spine. 3, 6–13 can be re-ordered to match
whatever work lands on your plate.

If you only have four weeks: Phase 1, Phase 2, Phase 4, Phase 5. That's enough to read almost any
screen in almost any modern Android codebase.

---

## Universal anti-patterns

Applies in any Android repo, regardless of stack choices:

| Anti-pattern | Why it's wrong | Do instead |
|---|---|---|
| Android imports (`Context`, `Intent`) in domain/business code | untestable, couples logic to the OS | declare an interface, implement it outward |
| `Context`/`View`/`Activity` reference in a `ViewModel` | leaks across config change | expose state; let the UI do platform work |
| Business logic in a composable or Activity | unpreviewable, untestable, duplicated | push into ViewModel/domain |
| Swallowing `CancellationException` (`runCatching`, bare `catch (e: Exception)`) in coroutines | breaks structured concurrency; work keeps running | catch and **re-throw** it |
| Hardcoding `Dispatchers.IO`/`Main` | can't be tested deterministically | inject a dispatcher provider |
| `GlobalScope` | unscoped, uncancellable, leaks | scoped or app-lifetime injected scope |
| Returning a new `StateFlow` from a function or `get()` | new instance per call; leaks; state resets | store it as a property |
| Plain `collect` in `lifecycleScope` for UI | keeps collecting while backgrounded | `collectAsStateWithLifecycle()` / `repeatOnLifecycle` |
| `else ->` in a `when` over a sealed type | new cases silently fall through | list every branch, let the compiler tell you |
| Mutable shared state without a single owner | race conditions, "impossible" states | one source of truth, immutable state objects |
| Exposing DTOs (or DB entities) to the UI | an API/schema change ripples into screens | map at each boundary |
| Passing whole objects as navigation args | size limits, stale data, serialization pain | pass an ID and re-fetch |
| Everything `public` | no boundaries; anything can depend on anything | `internal` by default |
| God repository / util class / 800-line ViewModel | untestable, merge-conflict magnet | split by responsibility |
| Throwing on unexpected server data (unknown enum, null field) | one bad row crashes a screen | log, drop, fall back |
| Hardcoded strings, colors, dimensions | breaks localization, theming, dark mode | resources + design tokens |
| Blocking the main thread (disk, JSON, `apply()`) | jank and ANRs | suspend + proper dispatcher |
| Mock-heavy tests asserting call counts | pass while the app is broken | fakes asserting behaviour |
| `Thread.sleep` in tests | flaky and slow | virtual time / idling / Turbine |
| Secrets or keys committed in the repo | irreversible once pushed | CI secrets, injected at build time |
| Comments explaining *what* the code does | rot immediately | make the code obvious; comment only non-obvious *why* |
| Big-bang PRs | unreviewable; risky | small, single-purpose, behind a flag |

---

## Glossary

| Term | Meaning |
|---|---|
| **UDF** | Unidirectional Data Flow — state down, events up |
| **State holder** | Object owning UI state across recomposition/config change (usually a ViewModel) |
| **Recomposition** | Compose re-running composables whose inputs changed |
| **State hoisting** | Moving state up to the caller so the composable is stateless |
| **Side effect** | Work in a composable that escapes composition (`LaunchedEffect`, etc.) |
| **Stability / skippability** | Whether Compose can prove a parameter unchanged and skip re-running |
| **Structured concurrency** | Coroutines form a parent/child tree; cancellation and completion propagate |
| **Main-safe** | A `suspend` function callable from the main thread without blocking |
| **Cold / hot flow** | Cold restarts per collector; hot exists independently (`StateFlow`, `SharedFlow`) |
| **Conflation** | Dropping intermediate values when the collector is slower than the producer |
| **Back-pressure** | Handling a producer faster than its consumer |
| **DTO** | Data Transfer Object — the wire shape of an API response |
| **Entity** | The database row shape |
| **Domain model** | Business shape, framework-free |
| **UI state / ViewState** | Exactly what one screen needs to render |
| **Mapper** | Converts between two layers' models |
| **Use case / interactor** | One business operation, reusable across state holders |
| **Repository** | Single source of truth for a data type; owns cache policy |
| **Data source** | Talks to exactly one backing store |
| **Dependency inversion** | High-level code declares the interface; low-level code implements it |
| **api/impl split** | Contracts in a tiny module peers can depend on; implementation depended on only by the app |
| **Reducer** | Pure `(State, Intent) -> State` function (MVI) |
| **Intent / Action** | A modelled event fed into a reducer |
| **Effect** | Async work triggered by state logic, feeding results back as events |
| **Fake / Mock / Stub / Spy** | Test doubles: working impl / configured stub / canned answers / wrapper recording calls |
| **Turbine** | Library for asserting `Flow` emissions |
| **Robolectric** | Runs Android-dependent code on the JVM |
| **Baseline Profile** | Ahead-of-time compilation hints that speed up startup and scrolling |
| **R8** | Shrinker/obfuscator applied to release builds |
| **Build variant** | build type × product flavor (e.g. `freeRelease`) |
| **Version catalog** | Centralized dependency/version declarations (`libs.versions.toml`) |
| **Feature flag** | Remote boolean gating a code path |
| **Experiment** | A/B test assigning users to variants and measuring metrics |
| **Bucketing / unit id** | Deterministic hash of a user identifier into a variant |
| **Staged rollout** | Releasing to a growing percentage of users |
| **ANR** | Application Not Responding — main thread blocked too long |

---

## How to read an unfamiliar Android codebase in 90 minutes

A repeatable procedure. Works on any repo, including one you join next year.

1. **`gradle/libs.versions.toml`** (or root `build.gradle`) → the entire stack: DI, HTTP, JSON, UI,
   test libs. *10 min*
2. **`settings.gradle(.kts)`** → the module map. Feature-based or layer-based? Are there `-api`
   modules? *5 min*
3. **`README` / `CONTRIBUTING` / `docs/` / `.ai/` / `AGENTS.md` / `CLAUDE.md`** → the team's own
   conventions. Mature repos document their architecture; read it before the code and trust it over
   neighbouring files. *15 min*
4. **`AndroidManifest.xml`** in the app module → launcher activity, permissions, deep links,
   what's exported. *5 min*
5. **The `Application` class + the DI graph setup** → what's a singleton, what's initialized on
   startup, in what order. *10 min*
6. **The app-module build file** → flavors, build types, `buildConfigField`s, SDK levels.
   These explain environment differences. *5 min*
7. **Pick one screen you know as a user and trace it end to end**: route → screen composable →
   state holder → use case → repository → data source → API definition. Write down each file.
   *30 min — this is the phase that actually teaches you the codebase.*
8. **Read its tests** → reveals the intended seams, the fake/mock convention, and what the team
   considers worth testing. *10 min*
9. **`.github/workflows/`** → what must pass before merge; run those commands locally once. *5 min*

Then, and only then, start changing things.

---

## Canonical resources

**Official (treat as source of truth)**
- Android Developers — *Guide to app architecture* (layers, UDF, state holders, the whole vocabulary)
- Android Developers — *Jetpack Compose* pathways & *Compose performance*
- Kotlin docs — *Coding Conventions*, *Coroutines guide*, *Flow*
- AndroidX — *Compose component API guidelines* (how to design composable APIs properly)
- Android Developers — *Testing* section, and *Now in Android* (Google's reference multi-module app;
  the single best full-app sample to read)

**Deep dives worth the time**
- Android Developers Medium: "Things to know about Flow's shareIn/stateIn", "Consuming flows safely
  in Jetpack Compose", "ViewModel: one-off event antipatterns", "Compose stability explained"
- Jake Wharton's and Chris Banes' blogs (OkHttp/Retrofit internals; Compose internals)
- *Compose Internals* (Jorge Castillo) — for when recomposition stops being magic
- Martin Fowler, *Test Double*; Cash App's *Mocking* post — the fakes-over-mocks argument
- Google I/O + Android Dev Summit architecture and Compose talks (current year)

**Practice repos to read**
- `android/nowinandroid` — multi-module, Compose, Hilt, offline-first. The reference.
- `android/compose-samples` — idiomatic Compose UI patterns
- `google/iosched` — a large real app
- `chrisbanes/tivi` — opinionated modularization + Compose

**Keeping current:** Android Developers Blog, *Now in Android* podcast/newsletter, Kotlin Weekly,
Android Weekly, release notes for AGP/Kotlin/Compose BOM (breaking changes live there).

---

## A note on your specific starting point

Coming from Appium/TestNG automation, you already own things most junior Android devs don't:
`adb`, emulator lifecycle, APK install/signing basics, app package/activity structure, flaky-test
discipline, and a real behavioural model of the product. What's genuinely new is:

1. **Kotlin idioms** (Phase 1) — syntax only, a few weeks
2. **Coroutines/Flow** (Phase 2) — the actual conceptual jump; don't rush it
3. **Declarative UI** (Phase 4) — unlearning imperative view manipulation
4. **Layered architecture** (Phase 5) — knowing where code belongs

And your fastest route to being useful on a dev team is **Phase 10 from the inside**: unit and
Compose tests for use cases, mappers, reducers and ViewModels. That work is high-value, low-risk,
reviewed quickly, and it forces you to read production code closely — which is how you learn the
rest anyway.

---

# Appendix — Applying This to `Mistplay/mistplay-android`

Everything above is transferable. This appendix maps it onto our own codebase: which option our team
picked on each axis, and the exact files to open. Read it *after* the phase it relates to, so you
learn the concept first and the local spelling second.

> **Ground rule from the repo itself:** the codebase is **mid-migration**. The docs in
> `.ai/conventions/android/` are *authoritative*; neighbouring code may not match them yet.
> Never infer a convention by copying the file next door — check the doc.

### Part A — The Stack

| Layer | What the repo uses |
|---|---|
| **Language** | **Kotlin 2.3.21 only** — 10,088 `.kt` files, **zero** `.java` files |
| **UI** | **Jetpack Compose** (BOM 2026.03) — 1,661 files contain `@Composable`; only 62 XML layouts remain |
| **DI** | **Koin 4.2.2** (`factoryOf` / `singleOf` / `viewModelOf` / `bind`) — *not* Hilt/Dagger |
| **Async** | Coroutines 1.10 + `Flow`/`StateFlow`; injected `DispatcherProvider`; app-wide `AppCoroutineScope` |
| **Networking** | **Retrofit 3.0 + OkHttp 5.4 + Gson** — one shared `Retrofit` singleton in the app's `NetworkModule` |
| **Persistence** | **Room 2.8**, **DataStore 1.2**, in-memory reactive caches (`MutableStateFlow`) |
| **Navigation** | Navigation-Compose 2.9 wrapped by a custom **`NavigationStateMachine`** |
| **Images / anim** | Coil 2.7, Landscapist, Glide 5 (legacy), Lottie 6.7 |
| **Feature flags / A-B** | **Statsig 4.45** behind a `RemoteConfig` façade (`library/remote-config`, `core/experiments`) |
| **Analytics & obs.** | Segment, Braze, AppsFlyer, Firebase (Crashlytics + Performance), **Datadog RUM/Trace**, Sentry, AWS Kinesis, LeakCanary |
| **Anti-fraud / geo** | Verisoul, IPQS, RootBeer, Play Integrity, reCAPTCHA, GeoComply (`library/geocomplylib`) |
| **Build** | Gradle 8.14.3, **AGP 9.2.1**, KSP, version catalog, `TYPESAFE_PROJECT_ACCESSORS`, remote build cache, 10 GB JVM heap |
| **SDK levels** | `compileSdk 36`, `targetSdk 36`, `minSdk 24` (libraries 23); Robolectric pinned to `testOptions.targetSdk 35` |
| **Code quality** | ktlint, **detekt + custom `detekt-rules` module**, custom `lint-rules` module, jacoco, baseline profiles |
| **Testing** | JUnit4, MockK *(discouraged)*, Robolectric 4.16, **Turbine** (Flow assertions), Espresso/UIAutomator, nightly Firebase Test Lab — **808 unit test classes** |
| **Flavors** | `flavorDimensions "whitelabel"` → **`mistplay`** and **`cashquest`** (`applicationIdSuffix .cashquest`) |
| **App id / launcher** | `com.mistplay.mistplay` / `com.mistplay.mistplay/.launch.LaunchActivity` |

> **[Extension] [Flag — do not silently correct]** As instructed in the master prompt's Source of Truth
> section, four entries in the table above are known to be stale or inconsistent with reality
> as of this document's stated timeframe. They are preserved verbatim above (this is someone's
> factual snapshot of their own repo, not a typo) and flagged here instead of edited:
> - **`Kotlin 2.3.21`** — trails the current stable Kotlin line by one minor version.
> - **`Coil 2.7`** — labeled implicitly as the current default, but Coil 3.x has been the
>   actively developed line since late 2024; a repo pinned to 2.7 is intentionally behind,
>   not on the "modern default."
> - **`Glide 5 (legacy)`** — does not correspond to any real shipped Glide release. Glide's
>   latest stable line is 4.x, so "Glide 5" is most likely a documentation error, not a
>   library version to look up.
> - **`Gradle 8.14.3` / `AGP 9.2.1`** — cited as current here, but Gradle 9.x had already
>   shipped before this document's stated date, so treat the Gradle number specifically as
>   dated (the AGP number is plausible on its own and not part of this flag).
>
> If you're using this course with an AI instructor, it should raise each of these the first
> time it becomes relevant (Kotlin version discussions, image-loading libraries, Gradle/build
> tooling) rather than quietly renumbering them. See also the matching tooling note in
> `phase11gradleandmodularization.md`, which models the same flag-don't-fix pattern for Gradle
> 8.x vs 9.x.

**What this means for you:** there is no Java, no XML-layout work, no Dagger, and no
`Activity`-per-screen. The whole mental model is *Kotlin + Compose + coroutines + Koin*.

---

### Part B — Module Topology

~170 Gradle modules (see `settings.gradle`) in six buckets:

| Bucket | Purpose | Examples |
|---|---|---|
| `mistplay` | **The app module.** The only module that sees every other module; wires impls to interfaces via Koin. | `mistplay/build.gradle` |
| `feature/*` | One user-facing feature each, self-contained vertical slice. | `feature/onboarding2`, `feature/loyalty`, `feature/geocheck`, `feature/shop/**`, `feature/checkpoint` |
| `core/*` | Cross-cutting app infrastructure. | `core/experiments`, `core/navigation-api`, `core/user`, `core/localization`, `core/authentication` |
| `library/*` | Reusable, product-agnostic libraries. | `library/design`, `library/analytics`, `library/remote-config`, `library/mistplayTheme` |
| `common/*` | Shared plumbing. | `common/ui`, `common/network`, `common/util`, `common/coroutines`, `common/test`, `common/observability` |
| Tooling | Non-shipping modules. | `playground` (component gallery), `baselineprofile`, `detekt-rules`, `lint-rules`, `mixlist` |

#### The single most important structural idea: the `-api` split

```
feature/loyalty          ← impl: ViewModels, screens, repositories, Koin modules (all `internal`)
feature/loyalty-api      ← contract: use-case interfaces, public models, @Composable contracts
```

The rule, from `.ai/conventions/android/module-api-pattern.md`:

> **Feature impl modules depend on peer `-api` modules, NEVER on peer impl modules.
> Only the app module (`mistplay`) depends on both api + impl and wires them via Koin.**

```
mistplay (app)
  ├── depends on ALL feature impl modules + ALL api modules
  └── wires impls → interfaces in Koin (InteractorModule, ProviderModule)

feature/{name} (impl)
  ├── own feature/{name}-api
  ├── core/*-api modules
  ├── OTHER feature/*-api modules  ✅
  └── other feature impl modules   ❌ never

feature/{name}-api
  └── Kotlin stdlib, Compose UI, coroutines, kotlinx.serialization — nothing else
```

**Why it exists:** without it, `gamedetails → loyalty → gamedetails` would be a dependency cycle.
The `-api` module applies Dependency Inversion *at the module level*. Side benefit: incremental
builds stay fast because changing an impl never recompiles its peers.

There are ~34 `-api` modules (29 feature-level, 5 core-level).

**Never goes in an `-api` module:** `*Impl` classes, ViewModels, screens, repositories, data
sources, Koin modules, Android framework deps.

---

### Part C — Architecture & Core Patterns

**Clean Architecture + MVI + Unidirectional Data Flow.**
Layers, innermost → outermost: `Domain → Repository → Data → Presentation → UI`

#### C.1 Directory layout inside one feature

```
feature/{name}/src/main/kotlin/com/mistplay/{name}/
├── di/{Name}Module.kt              # root Koin module — composes the rest via includes()
├── domain/
│   ├── di/DomainModule.kt
│   ├── entity/                     # domain models (data classes)
│   ├── repository/                 # concrete internal class — NO interface
│   ├── usecase/                    # concrete; impl/ only for -api interfaces
│   │   └── impl/
│   ├── orchestrator/               # multi-use-case combination / transformation
│   ├── mapper/
│   └── interactor/                 # cross-cutting interfaces, implemented elsewhere
├── data/
│   ├── remote/{api,di,mapper,model}/  + {Name}RemoteDataSource.kt
│   ├── local/{di,mapper,model,provider}/ + {Name}LocalDataSource.kt
│   ├── memory/                     # in-memory reactive cache (optional)
│   └── repository/{di,mapper,model}/
├── presentation/ (or feature/)
│   ├── di/PresentationModule.kt
│   ├── {Name}ViewModel.kt
│   ├── model/{Name}State.kt, {Name}Intent.kt, {Name}ViewState.kt
│   ├── mapper/                     # State → ViewState
│   ├── presentation/screen/, components/
│   └── analytics/{Name}AnalyticsKeys.kt
└── navigation/{Name}Navigation.kt  # GraphProvider impl + di/NavigationModule.kt
```

Real example to open side by side: `feature/onboarding2/src/main/java/com/mistplay/onboarding2/`
(has `di/`, `domain/`, `data/local/`, `presentation/`, `navigation/`, `library/analytics/`).

#### C.2 `ViewModelFlow<State, Intent>` — the homegrown MVI engine

Lives in **`common/ui/src/main/java/com/mistplay/ui/extension/ViewModelFlow.kt`**
(with a test at `common/ui/src/test/java/com/mistplay/ui/extension/ViewModelFlowTest.kt` — read both).

```kotlin
class ViewModelFlow<State, Intent>(
    initialIntent: Intent,
    initialState: State,
    private val sendInitialIntentOnRestart: Boolean = true,
    private val reduceState: suspend (State, Intent) -> State,
    private val viewModelScope: CoroutineScope,
) : Flow<State>
```

- `sendIntent(intent)` — dispatch an action
- `setEffect { … }` — one-shot async op that returns a **new Intent**
- `setEffectFlow { … }` — long-running `Flow<Intent>` (keyed; duplicates are cancelled)

**Three state types, deliberately separate:**

| Type | Role | Notes |
|---|---|---|
| `State` | Rich internal business data | uses `PersistentList`; **never** exposed to UI |
| `Intent` | Every possible action (user *and* system-generated) | `sealed` |
| `ViewState` | UI-facing shape | produced by a mapper; `data class` or `sealed interface` |

**Rules:** `reduceState` must be **pure** — no side effects. All async goes through effects.
Expose with `stateInViewModelLifecycle(...)` (= `stateIn` with `WhileSubscribed(5_000)`), never
a hand-rolled `stateIn`.

Real examples: `onboarding2/presentation/mistai/` has the complete set —
`MistAiViewModel.kt`, `model/MistAiIntent.kt`, `model/ViewModelState.kt`,
`model/MistAiUiState.kt`, `mapper/MistAiViewMapper.kt`. Also
`onboarding2/presentation/permission/` (`PermissionState`/`PermissionIntent`/`PermissionViewState`).

A simple screen that only observes one `Flow` may skip `ViewModelFlow` entirely — see the
`LoadingViewModel` example in `state-management.md`.

#### C.3 Orchestrators — the rule newcomers break

> If a ViewModel needs data from **2+ use cases**, *or* **transforms** a single use case's result
> before use, that logic **must** live in an Orchestrator in `domain/orchestrator/`.
> The docs call this a **violation**, not a preference.

Orchestrators are concrete `internal` classes, **no interface**, registered with
`factoryOf(::MyOrchestrator)` and **no `bind`**. `Flow`-returning orchestrators always
`flowOn(dispatchers.default)`. They get their own dedicated `*OrchestratorTest`.

What legitimately stays in the ViewModel: domain → UI mapping, and sequential action workflows.

#### C.4 Navigation — features never touch `NavController`

```
UI callback → stateMachine.sendEvent(NavEvent) → NavigationStateMachine → NavController.navigate()
```

Key files: `core/navigation-api/src/main/kotlin/com/mistplay/navigationapi/` →
`NavigationStateMachine.kt`, `GraphProvider.kt`, `TopBarRegistration.kt`,
`destination/*Destination.kt` (e.g. `OnboardingDestination.kt`, `LoyaltyDestination.kt`),
`destination/ConditionalDestination.kt`.

Adding navigation for a feature:

1. `@Serializable` `Destination` objects in `core/navigation-api` (`DestinationType` =
   `FullScreen` / `BottomSheet` / `Dialog` / `Action`)
2. register the list in `feature/navigation/.../AllDestinations.kt`
3. define `NavEvent`s with `nextDestination()`
4. implement `GraphProvider` in the feature (e.g. `onboarding2/navigation/OnboardingNavigation.kt`)
5. Koin: `factoryOf(::MyNavigation) bind GraphProvider::class`
6. include the navigation module in the feature's root Koin module

Built-ins: `NavEvent.OnBack`, `OnFinish`, `OnError(errorModel)`, `OnOpenUrl`, `OnWebActivity`.
`ConditionalDestination` routes by experiment flag — **this is how A/B'd screens change flow**.

#### C.5 Networking

One Retrofit singleton; features do `get<Retrofit>().create(XxxApiDefinition::class.java)`.

```kotlin
internal interface CheckpointApiDefinition {
    @GET("v1/checkpoint/tasks")
    suspend fun fetchCheckpointTasks(
        @Query("pid") packageId: String,
        @Query("uid") userId: String,
    ): Response<CheckpointCard>
}
```

Every call is wrapped by `processApiResultFromResponse` (from `common/network`) and converted with
`.toResult("SourceName")`:

```kotlin
suspend fun fetchCheckpointTasks(info: CheckpointGameInfo): Result<CheckpointCard> =
    processApiResultFromResponse(
        networkCall = { api.fetchCheckpointTasks(info.pid, info.uid) },
        modelAdapter = { response -> mapper.toRepo(response) },
    ).toResult("CheckpointRemoteDataSource")
```

- DTOs: `internal data class`, `@SerializedName`, nullable + defaults for server-optional fields,
  live in `data/remote/model/`, newer modules use a `*Dto` suffix. Use `retrofit2.Response<T>`
  (the old `DataResponse<T>` is deprecated).
- Mappers: `mapNotNull` + try/catch + log-and-drop. **One bad item must never fail the whole
  response**, and unknown enum values are logged and mapped to `null`, never thrown.
- All errors converge on `ErrorModel(msg, code, domain, msgRef, internalMsg) : Exception(), Parcelable`.

#### C.6 Threading model

Every `suspend` function is **main-safe** — the layer doing the real work owns the dispatch;
everything above it switches nothing. Always inject `DispatcherProvider`, never hardcode
`Dispatchers.X` (so tests can substitute `TestDispatcherProvider`).

| Work | Mechanism | Where |
|---|---|---|
| Blocking IO (only if not already main-safe) | `withContext(dispatchers.io)` | data source |
| CPU work in a `suspend` fun | `withContext(dispatchers.default)` | use case / (rarely) repository |
| CPU work in a `Flow` | `flowOn(dispatchers.default)` | orchestrator (always), Flow-returning use case |
| Pass-through glue | nothing | any layer |

Retrofit / Room / DataStore are **already main-safe** — do not wrap them in `withContext(io)`.
`flowOn` and `withContext` are **not** interchangeable.

#### C.7 DI conventions (Koin)

```kotlin
internal val domainModule = module {
    factoryOf(::FetchCheckpointSectionUseCaseImpl) bind FetchCheckpointSectionUseCase::class
}
internal val presentationModule = module {
    factoryOf(::CheckpointCardContentImpl) bind CheckpointCardContent::class
    viewModelOf(::CheckpointCardViewModel)
}
internal val remoteModule = module {
    single { get<Retrofit>().create(CheckpointApiDefinition::class.java) }
    factoryOf(::CheckpointRemoteDataSource)      // concrete → no bind
}
val checkpointModule = module {
    includes(presentationModule, domainModule, repositoryModule, remoteModule, memoryModule, localModule, navigationModule)
}
```

- `factoryOf` = stateless, `singleOf` = stateful, `viewModelOf` = ViewModels
- `bind` to the interface — **except orchestrators, repositories and data sources**, which are
  concrete and get no `bind`
- Retrofit API interfaces are `single`

#### C.8 Experimentation (Statsig)

All experiments go through the `RemoteConfig` interface (`library/remote-config`); definitions live
in `core/experiments/`.

| Primitive | Statsig call | Interface |
|---|---|---|
| Feature gate | `checkGate()` | `GateDefinition` |
| Experiment | `getExperiment()` | `ExperimentDefinition` + `ConfigParam<T>` |
| Layer | `getLayer()` | `LayerDefinition` |

```kotlin
data object MyGate : GateDefinition { override val name = "my_feature_gate" }
val isEnabled = remoteConfig.isEnabled(MyGate)

data object MyExperiment : ExperimentDefinition {
    override val name = "2025_03_my_experiment"
    data object Enabled : ConfigParam<Boolean> { override val name = "is_enabled"; override val defaultValue = false }
}
// conventional access via extension functions in core/experiments/.../MyExperimentExt.kt
fun RemoteConfig.isMyFeatureEnabled(): Boolean = getConfig(MyExperiment).getBoolean(MyExperiment.Enabled)
```

Note the **flavor-specific source sets**: `core/experiments/src/mistplay/...` and
`core/experiments/src/cashquest/...` hold different defaults providers
(`MistplayOnboardingDefaultsProvider` vs `CashQuestOnboardingDefaultsProvider`).

---

### Part D — House Rules (what gets flagged in review)

From `.ai/conventions/android/README.md` ("Never Do") and `docs/codebase-guidelines.md`:

| Anti-pattern | Correct approach |
|---|---|
| Android imports (`Context`, `Intent`, `WorkManager`) in domain | abstract behind repository / interactor interfaces |
| Android code in the ViewModel | delegate to the UI layer |
| `public` by default | **`internal` by default** — "let the compiler remind you something must be public" |
| `else` in an exhaustive `when` | list every branch, so new sealed cases fail to compile |
| Side effects inside `reduceState()` | `state.setEffect { }` / `setEffectFlow { }` |
| `List<T>` in `State` | `PersistentList<T>` (and `ImmutableList<T>` for Composable params) |
| `collectAsState()` | `collectAsStateWithLifecycle()` |
| returning `StateFlow` from a function, or via `get()` | store it as a class field |
| `SharingStarted.Lazily/Eagerly` for UI state | `WhileSubscribed(5_000)` via `stateInViewModelLifecycle()` |
| `runCatching` in coroutine code | try/catch that **re-throws `CancellationException`** |
| `context.getString` / `resources.getString` / `getQuantityString` | inject `com.mistplay.core.localization.StringManager` (enforced by detekt rule `mistplay:UseStringManagerForStrings`); Compose `stringResource(...)` is allowed |
| hardcoded colors | design tokens (`TextColorToken`, `SurfaceColorToken`, …) |
| plain `Text()` | `MistplayBodyText`, `MistplayH5Text`, … |
| bare `R.string.x` from another module | non-transitive R: `import com.mistplay.core.resources.R as CoreResR` |
| direct `navController` calls | `stateMachine.sendEvent(NavEvent)` |
| depending on a peer feature's impl | depend on its `-api` |
| 2+ use cases (or any transformation) in a ViewModel | **Orchestrator** |
| long comments narrating *why* | ≤2 lines inline / 1-line KDoc on private helpers; reasoning goes in the PR or Jira |
| `viewModelScope` for work that must outlive the screen | inject `AppCoroutineScope` |
| uncommented empty body `{}` | add `/* no-op */` or a `// TODO` |
| constants | Composables: top of file, `PascalCase`. Non-Composable: `companion object` at the bottom, `SCREAMING_SNAKE_CASE` |
| `if (failure) … else …` | put the **happy path first** |

#### Testing rules

- **Prefer `Fake` over `Mock`.** Reasons given: reusability, maintainability, stability (mocks broke
  during the User refactor / Robolectric / coroutine upgrades), and fewer implementation details.
  Decision order: **real object → fake → mock**. Mocks only for things you don't own
  (`Context`, `SharedPreferences`, `Intent`).
- Structure every test `given / when / then` (a.k.a. arrange/act/assert), including in the name:
  `` fun `when_gift_card_is_valid_but_no_internet_then_error_is_returned`() ``
- Fakes live in `src/test/.../fake/`, data builders in `src/test/.../factory/`
  (see `feature/geocheck/src/test/.../fake/FakeReportVerisoulSessionUseCase.kt`).
- Flows are asserted with **Turbine**; coroutines with `CoroutineTestRule`.

---

### Part E — Build, Tooling & CI

- **Default branch: `develop`.** Releases branch off it; `scripts/release.sh`, `patch.sh`,
  `hotfix.sh`, `backport.sh` automate the rest.
- Dependency versions come **only** from `gradle/libs.versions.toml`. Never hardcode a version.
- Shared Gradle config is in `config/`: `android_common_dependencies.gradle`,
  `mistplay_dependencies.gradle`, `compose_dependencies.gradle`, `quality_dependencies.gradle`,
  `shared_dependencies.gradle`, `module_flavors.gradle`. A feature's `build.gradle.kts` just
  `apply(from = …)` those and lists project deps via typesafe accessors
  (`implementation(projects.feature.loyaltyApi)`).
- **26 GitHub workflows** (`.github/workflows/`), notably:
  `pull-request-check.yml` / `-grouped` / `-smart` / `-cashquest`, `nightly-full-unit-tests.yml`,
  `nightly-mistplay-firebase.yml`, `nightly-playground-firebase.yml`, `generate-qa-apk*.yml`,
  `generate-prod-apk.yml`, `mistplay-deploy-google-play.yml`, `cashquest-deploy-google-play.yml`,
  `release.yml`, `patch.yml`, `hotfix.yml`, `backport.yml`, `validate-ai.yml`,
  `publish-dev-apk-to-qawolf.yml`, `analytics-monitor-ci.yml`.
- `scripts/pre-push` + `git-hooks.gradle` install a local pre-push gate.
- The repo ships an AI knowledge base: `.ai/README.md`, `.ai/MAP.md`,
  `.ai/conventions/**`, `.ai/skills/**`, `.ai/workflows/**` (112 markdown files), plus
  `.claude/`, `.cursor/`, `.gemini/` harness configs and a `.codegraph/` index. Use
  `codegraph explore "<symbol or question>"` instead of grep when it's available.

---

### Part F — Local practice track (7 steps)

This is the general Phases 1–13 executed *inside our repo* — the same skills, with our files as the
exercise material. Run it in parallel with the main body: general Phase 2 (coroutines) pairs with
local Step 2, general Phases 4–5 with local Steps 3–4, general Phase 10 with local Step 5.

Each step: **goal → read → build → checkpoint**. Do the *build* step; reading alone won't stick.
Realistic pace at a few hours a week: phases 0–2 in ~2 weeks, 3–4 in ~3 weeks, 5–6 ongoing.

---

#### Phase 0 — Get the app running from source

**Goal:** compile, install and launch the debug app yourself.

**Do:**
```bash
git clone git@github.com:Mistplay/mistplay-android.git && cd mistplay-android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
# create local.properties with sdk.dir=/Users/<you>/Library/Android/sdk
./gradlew assembleMistplayDebug
./gradlew :mistplay:installMistplayDebug -x test
adb shell am start -n com.mistplay.mistplay/.launch.LaunchActivity
```
Also read `.ai/conventions/android/run-and-verify.md`.

**Checkpoint:** you can produce a debug APK locally and explain what
`assembleMistplayDebug` vs `assembleCashquestDebug` builds.

> **Note for you specifically:** you already own the emulator + `adb` + APK-install pipeline from
> `AppiumUtils`. This phase is mostly a Gradle exercise, not an Android one.

---

#### Phase 1 — Kotlin for a Java developer

**Goal:** read any file in the repo without stumbling on syntax.

**Read/learn (in this order):**
1. [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) — the repo's base
2. `data class`, `sealed interface` / `sealed class`, `object` / `data object`
3. `val`/`var`, nullability (`?`, `?.`, `?:`, `!!` — and why `!!` is rare here)
4. Extension functions (`fun RemoteConfig.isMyFeatureEnabled()`) — used *everywhere*
5. Scope functions (`let`, `also`, `apply`, `run`, `takeUnless`)
6. `operator fun invoke()` — why use cases are *called like functions*
7. `internal` vs `private` vs `public`
8. Trailing lambdas, named args, default args, trailing commas
9. `Result<T>`, `runCatching` (and why it's banned in coroutine code here)
10. `mapNotNull`, `map`, `filter`, destructuring

**Build:** take one Appium page object you wrote in Java and rewrite it in Kotlin as an exercise
(nullable locators, extension function for a common wait, `sealed interface` for screen state).

**Checkpoint:** you can explain, unaided, what this means:
```kotlin
internal class GetTopPicksGamesUseCase(private val repository: TopPicksRepository) {
    suspend operator fun invoke(force: Boolean = false): Result<List<Game>> = repository.get(force)
}
```

---

#### Phase 2 — Coroutines & Flow

**Goal:** understand the async model the whole app is built on. This is the highest-leverage phase.

**Learn:**
- `suspend`, structured concurrency, `CoroutineScope`, `Job`, cancellation
- `CancellationException` — why swallowing it (via `runCatching`) breaks cancellation
- `withContext` vs `launch` vs `async`
- Cold `Flow` vs hot `StateFlow`/`SharedFlow`
- Operators you'll meet constantly: `map`, `mapLatest`, `combine`, `onStart`, `catch`, `debounce`,
  `emitAll`, `flowOn`, `stateIn`
- `SharingStarted.WhileSubscribed(5_000)` — what it actually does and why it's the default here
- `collectAsStateWithLifecycle()`

**Read in repo:** the "Coroutines" section of `docs/codebase-guidelines.md` (the ❌/✅ pairs are the
best 10 minutes you can spend), then `common/coroutines/` and the Threading table in
`.ai/conventions/android/architecture.md`.

**Build:** write a small Kotlin scratch program: a `Flow` that emits a fake API result, `map` it to
a UI model, `catch` errors, and `stateIn` it in a scope; assert the emissions with Turbine.

**Checkpoint:** explain why
`fun getState(): StateFlow<X> = flow.stateIn(...)` is a bug, and why `flowOn` can't be replaced by
`withContext` in a `Flow`.

---

#### Phase 3 — Compose & the design system

**Goal:** build and preview UI.

**Learn:** composable functions, state hoisting, `remember` / `mutableStateOf`, recomposition and
why immutability matters, `Modifier` ordering, `LazyColumn`, `@Preview` / `@PreviewLightDark`.

**Read in repo:**
- `.ai/conventions/android/compose-and-ui.md` — the **3-layer screen structure**, design tokens,
  text components, spacing, previews, navigation callbacks
- `library/design/`, `library/mistplayTheme/`
- `feature/onboarding2/.../presentation/main/component/OnboardingScreen.kt` and
  `presentation/mistai/MistAiScreen.kt` as real, current examples
- `.ai/conventions/android/activity-to-compose.md` — shows the direction the last 62 XML layouts
  and remaining Activities are heading

**Build:** run **`:playground`** (the component gallery). Add a small component there following
`.ai/skills/android/component-gallery/SKILL.md`. This is the *lowest-risk* real contribution in the
whole repo.

**Checkpoint:** your component renders in the gallery with `@PreviewLightDark`, uses design tokens
and `Mistplay*Text` only, and `./gradlew ktlintCheck detekt` passes.

---

#### Phase 4 — Trace one vertical slice end to end

**Goal:** internalize Clean Architecture by following data through every layer *once*.

Pick a feature you already test. Two good candidates:

**Option A — `feature/onboarding2`** (recommended; you know this flow best)
Trace, in this order:
1. `navigation/OnboardingNavigation.kt` → which `Destination`s it declares
2. `core/navigation-api/.../destination/OnboardingDestination.kt`
3. `presentation/main/OnboardingActivity.kt` → `presentation/main/component/OnboardingScreen.kt`
4. `presentation/main/OnboardingViewModel.kt` + `presentation/main/model/OnboardingScreenState.kt`
5. `presentation/mistai/` — the full MVI set (`MistAiViewModel`, `MistAiIntent`, `ViewModelState`,
   `MistAiUiState`, `mapper/MistAiViewMapper`)
6. `domain/usecase/GetStartDestinationUseCase.kt` and `domain/usecase/impl/`
7. `domain/repository/OnboardingStatusRepository.kt`
8. `data/local/OnboardingStatusLocalDataSource.kt`
9. Wiring: `di/OnboardingModule.kt`, `domain/di/DomainModule.kt`,
   `presentation/di/PresentationModule.kt`, `data/local/di/LocalModule.kt`
10. Analytics: `library/analytics/OnboardingAnalyticsKeys.kt`

**Option B — `feature/geocheck`** (smaller, no UI, pure domain — good if the above feels heavy)
`api/GeoCheckImpl.kt`, `domain/usecase/*` (14 tiny single-purpose use cases:
`IsGeoCheckRequiredUseCase`, `ShouldEnforceGeoCheckUseCase`, `UserIsInAllowedPositionUseCase`, …),
`domain/repository/GeoPositionRepository.kt`, `domain/interactor/GeoCheckInteractor.kt`,
`di/GeoCheckDI.kt`, plus `feature/geocheck-api/.../api/GeoCheck.kt`.

**Read alongside:** `architecture.md`, `usecase.md`, `repository.md`, `datasource.md`,
`orchestrator.md`, `state-management.md`, `networking.md`.

**Build:** draw the slice as a diagram (boxes = files, arrows = calls). Then write it up in 15 lines.

**Checkpoint:** you can answer, for your chosen feature — where does a network response first
become a domain model? Which class owns the dispatcher switch? Which classes are `internal`, and
which are public and why? What lives in the `-api` module and who consumes it?

---

#### Phase 5 — Write tests (your fastest route to real contributions)

**Goal:** ship merged PRs while still learning. Unit tests are low-blast-radius and there are
already 808 of them to copy from.

**Read:** `.ai/conventions/android/testing.md`, `.ai/skills/android/write-unit-tests/SKILL.md`, the
"Unit testing" section of `docs/codebase-guidelines.md`, and `common/test/`.

**Build, in order:**
1. A **mapper test** — pure function, no coroutines. Cover the log-and-drop path with a malformed DTO.
2. A **use case test** with a hand-written `Fake` repository (not MockK).
3. A **`ViewModel` test** — `CoroutineTestRule` + Turbine on `screenState`; assert the
   State → ViewState mapping and one effect-driven transition.
4. An **orchestrator test** with its use cases as fakes (`orchestrator.md` §5).

```bash
./gradlew :feature:loyalty:test
./gradlew testDebugUnitTestChangedModules -Pbranch=develop
```

**Checkpoint:** a PR containing only tests, passing `pull-request-check`, using fakes and
`given_when_then` names.

---

#### Phase 6 — Own a small feature change

**Goal:** ship product code.

Progression, easiest → hardest:
1. A copy/string change through `StringManager` + `strings.xml` (learn the localization path;
   `/translate` skill exists for this)
2. A new field on a `ViewState` + its mapper + the Composable that renders it
3. A new `ConfigParam` on an existing experiment in `core/experiments/` and gate a UI element on it
   — closest to your current Statsig work
4. A new `Destination` + `NavEvent` + `GraphProvider` route for an existing screen
5. A **new feature module**, following the 7-phase checklist at the bottom of
   `.ai/conventions/android/architecture.md` (`-api` module → domain → data → presentation →
   navigation → integration → tests)

**Read:** `.ai/conventions/android/experimentation.md`, `ktlint.md`, `code-comments.md`, and the
git/PR conventions in `.ai/conventions/git-and-prs/` + `.ai/skills/git-and-prs/pre-push-gate/`.

**Before every push:**
```bash
./gradlew ktlintFormatChangedModules -Pbranch=develop
./gradlew ktlintCheckChangedModules  -Pbranch=develop
./gradlew detektChangedModules       -Pbranch=develop
./gradlew testDebugUnitTestChangedModules -Pbranch=develop
```

**Checkpoint:** a merged PR that touched domain + presentation and required no
architecture-related review comments.

---

### Part G — Your QA knowledge → where it lives in code

You already understand this app's *behaviour* better than most engineers do. Convert it:

| What you've debugged in test automation | Where it lives in the app |
|---|---|
| Statsig segment seeding, gates, variant assignment | `core/experiments/` (`GateDefinition`, `ExperimentDefinition`, `ConfigParam` **defaults** — these tell you exactly what the app does before Statsig resolves), `library/remote-config/`, flavor source sets `src/mistplay/` vs `src/cashquest/` |
| Onboarding screen order, the conditional "how did you hear" screen, milestones | `feature/onboarding2/`, `core/experiments/.../dynamicConfig/onboarding/*` (`OnboardingStepsConfigParams`, `OnboardingMilestone`, `IntroScreenConfigParams`, `AgeSelectionScreenConfigParams`, …), `OnboardingLayer.kt` |
| A screen appearing in one run and not the next | `ConditionalDestination` + `NextDestinationParams.remoteConfig` in `core/navigation-api` — the flow itself is experiment-driven |
| VPN / IP flagged errors (537), geo blocks | `feature/geocheck/`, `feature/verisoul/`, `library/geocomplylib/`, IPQS |
| Error codes / "Error 544" style dialogs | `ErrorModel` + `common/network` `processApiResultFromResponse`, `NavEvent.OnError`, `core/error/` |
| Mixlist variants, carousels | `mixlist/`, `feature/mixlist-api`, `feature/mixlistgamestab` |
| Debug Menu (uid lookup, overrides) | `feature/debugmenu/`, `feature/debugmenu-api`, `MixlistDebugOverridesImpl` |
| Login / consent flow | `core/authentication`, `feature/authentication`, `feature/consent` |
| Non-exported activities (why `startActivity` fails for you) | `AndroidManifest.xml` per module + `LaunchActivity` |
| Analytics events you validate | `library/analytics/`, per-feature `library/analytics/*AnalyticsKeys.kt`, `tools/analytics-monitor`, `.ai/references/analytics/REF.md` |

Two immediate payoffs: reading `ConfigParam` default values tells you the app's fallback behaviour
when Statsig hasn't resolved, and reading `*AnalyticsKeys.kt` gives you the exact event names to
assert on.

---

### Part H — Glossary

| Term | Meaning here |
|---|---|
| **`-api` module** | Contracts-only module that breaks cross-feature dependency cycles |
| **`ViewModelFlow`** | Mistplay's MVI engine in `common/ui`: state + intents + effects as one `Flow<State>` |
| **Intent** | A sealed action (user- or system-generated) fed into the reducer |
| **`reduceState`** | Pure `(State, Intent) -> State` function. No side effects, ever |
| **Effect** | Async work launched from the reducer that returns a new Intent (`setEffect` / `setEffectFlow`) |
| **ViewState** | UI-facing projection of State, produced by a mapper |
| **Orchestrator** | Domain class combining/transforming 2+ use cases so the ViewModel gets ready-to-use data |
| **Interactor** | Interface declared by a feature, implemented by the app module or another feature (bridge pattern) |
| **Provider** | Same idea, usually exposing a `@Composable` or read-only data across modules |
| **`GraphProvider`** | Per-feature contributor of Compose navigation routes |
| **`NavigationStateMachine`** | The only thing allowed to call `NavController` |
| **`Destination` / `NavEvent`** | `@Serializable` route type / event that resolves to a destination |
| **`ConditionalDestination`** | Destination chosen at runtime from a remote-config flag |
| **`RemoteConfig`** | Façade over Statsig: `isEnabled(Gate)`, `getConfig(Experiment)`, layers |
| **`StringManager`** | The only sanctioned way to read `R.string`/`R.plurals` outside Compose |
| **`processApiResultFromResponse`** | `common/network` helper wrapping every Retrofit call into `ApiResult` |
| **`ErrorModel`** | The single error type all failures converge to |
| **`stateInViewModelLifecycle`** | `stateIn` + `WhileSubscribed(5_000)`, the mandated form |
| **`DispatcherProvider`** | Injected dispatchers so tests can swap in `TestDispatcherProvider` |
| **`AppCoroutineScope`** | App-lifetime scope for work that must outlive a ViewModel |
| **Fake** | Hand-written test double implementing the real interface — preferred over MockK |
| **Turbine** | Library for asserting `Flow` emissions in tests |
| **whitelabel flavor** | `mistplay` vs `cashquest` product flavors from one codebase |
| **playground** | The in-repo Compose component gallery module |
| **`.ai/`** | The repo's authoritative convention/skill knowledge base |

---

### Part I — Command cheat sheet

```bash
# Build
./gradlew assembleMistplayDebug
./gradlew :mistplay:installMistplayDebug -x test
adb shell am start -n com.mistplay.mistplay/.launch.LaunchActivity

# Test
./gradlew :feature:loyalty:test
./gradlew test
./gradlew testDebugUnitTestChangedModules       -Pbranch=develop
./gradlew testMistplayDebugUnitTestChangedModules -Pbranch=develop

# Quality
./gradlew ktlintFormatChangedModules -Pbranch=develop
./gradlew ktlintCheckChangedModules  -Pbranch=develop
./gradlew detektChangedModules       -Pbranch=develop
./gradlew ktlintCheck

# In CI / cloud: always add --no-daemon
```

Notes: Gradle uses a 10 GB heap and parallel builds. The Robolectric warning
`Android SDK 36 requires Java 21 (have Java 17)` is expected and non-blocking. Use the Android
Studio bundled JDK (`JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`).

---

### Part J — Reading list, in order

**In-repo (highest value first):**

1. `.ai/conventions/android/README.md` — 95 lines; the quick-reference + "Never Do" tables
2. `docs/codebase-guidelines.md` — the ❌/✅ pairs, written by the team for the team
3. `.ai/conventions/android/architecture.md` — full layout + the new-feature checklist
4. `.ai/conventions/android/module-api-pattern.md` — why 170 modules
5. `.ai/conventions/android/state-management.md` — `ViewModelFlow` MVI in depth
6. `.ai/conventions/android/orchestrator.md` — the rule most easily broken
7. `.ai/conventions/android/compose-and-ui.md` — 3-layer screens, tokens, previews
8. `.ai/conventions/android/networking.md` + `datasource.md` + `repository.md` + `usecase.md`
9. `.ai/conventions/android/navigation.md`
10. `.ai/conventions/android/experimentation.md` — directly relevant to your current work
11. `.ai/conventions/android/testing.md` + `.ai/skills/android/write-unit-tests/SKILL.md`
12. `.ai/conventions/android/ktlint.md`, `code-comments.md`, `activity-to-compose.md`
13. `.ai/README.md` and `.ai/MAP.md` for everything else (112 md files, incl. Jira/release/CI workflows)
14. `README.md` → Confluence links: **Android Engineer Onboarding**, **Android Architecture
    Guideline**, Dev Specs, Telemetry/Event Sink, release process
15. Slack: `#android-guild`, `#android-code-review`, `#release`, `#frontend-crashes`

**External, mapped to phases:**

| Phase | Resource |
|---|---|
| 1 | Kotlin Coding Conventions; Google Kotlin Style Guide |
| 2 | Google's "Things to know about Flows: shareIn/stateIn"; "Consuming flows safely in Jetpack Compose" |
| 3 | Jetpack Compose pathway (Android Developers); AndroidX **Compose component API guidelines** (the repo follows it) |
| 4 | Google's *Guide to app architecture* + *Now in Android* sample (closest public analogue to this structure) |
| 5 | Martin Fowler, *Test Double*; Cash App's *Mocking* post (both cited in the repo's guidelines) |

---

### First-week concrete plan

- [ ] Clone, build `assembleMistplayDebug`, install and launch the debug app
- [ ] Read `.ai/conventions/android/README.md` and `docs/codebase-guidelines.md` end to end
- [ ] Skim `settings.gradle` — get a feel for the module count and naming
- [ ] Open `common/ui/.../ViewModelFlow.kt` **and** its test; read them together
- [ ] Trace `feature/onboarding2` top to bottom (Phase 4, Option A) and sketch the diagram
- [ ] Open `core/experiments/.../onboarding/` and note the default values of every `ConfigParam`
      that affects a screen you test
- [ ] Run `./gradlew :feature:onboarding2:test` and read the two most complex tests it runs
- [ ] Pick one mapper with no test and write one (Phase 5, step 1)
