# Debugging Journal & Exercises — Enterprise Finance Tracker

## 🛠️ The Senior Android Debugging Protocol

When diagnosing defects in Android applications, follow the **4-Step Investigative Sequence**:

```
1. Observe Symptom (Crash log, unexpected UI state, failed assertion)
      ↓
2. Formulate Hypothesis (Trace state origin, inspect lifecycle / thread / boundary)
      ↓
3. Isolate & Reproduce (Write failing unit test or trigger via ADB / debugger)
      ↓
4. Fix & Prevent Regression (Apply surgical fix, verify test passes)
```

---

## 🔬 How to Read Android Heap Dumps & Fix Memory Leaks

When investigating OutOfMemoryError (OOM) or Activity retention in Android Studio Profiler:

1. **Capture Heap Dump**:
   - Open Android Studio Profiler -> Select Device & Process -> Memory Track.
   - Perform user action (e.g. rotate phone 5 times or enter/exit screen 10 times).
   - Click **Dump Java Heap**.
2. **Filter by Leaked Classes**:
   - Filter by your package: `com.enterprise.financetracker`.
   - Look for `MainActivity` or Screen classes. If there are >1 instances alive, you have an Activity leak.
3. **Inspect the Shortest Path to GC Root**:
   - Right-click the leaked instance -> **Go to Shortest Path to GC Roots (excluding weak references)**.
   - Look for the strong reference chain:
     - Is it a static companion object field?
     - Is it an active `CoroutineScope` (e.g. `GlobalScope.launch`) holding a lambda with an implicit `this` reference?
     - Is it an uncancelled `Flow.collect` or unregistered listener?
4. **Apply Surgical Fix**:
   - Use `viewLifecycleOwner.lifecycleScope` or `viewModelScope` instead of unbounded scopes.
   - Clear static references in `onDestroy()`.
   - Use WeakReference for non-lifecycle bound listeners.

---

## 🎯 Stage 1 Debugging Challenges

### Challenge 1: The Account ID Transposition Bug
* **Symptom**: User transfers $500 from Account A to Account B, but Account A is credited and Account B is debited.
* **Code Fragment**:
  ```kotlin
  fun transferFunds(sourceAccountId: String, targetAccountId: String, amount: Double)
  ```
* **Question for QA Engineer**: *Why does this compile cleanly even if a developer accidentally calls `transferFunds(targetAccount.id, sourceAccount.id, 500.0)`? How does Kotlin prevent this at compile time?*
* 💡 **Hint 1**: Look at the parameter types. Both are primitive `String`.
* 💡 **Hint 2**: Read ADR 001 on value classes.
* ✅ **Solution**: Use strongly typed value classes: `sourceAccountId: AccountId`, `targetAccountId: AccountId`. Transposing arguments will fail at compile time.

---

### Challenge 2: The Silent Invariant Violation
* **Symptom**: A user enters an expense of `-$50.00` on the transaction form. The net balance calculation gets corrupted and treats it as an income addition.
* **Question for QA Engineer**: *Where should domain validation live to prevent corrupted records from ever existing in memory or persistence?*
* 💡 **Hint 1**: Does validation belong only in the UI form, or in the entity itself?
* 💡 **Hint 2**: Check Kotlin's `init` block and `require()` function.
* ✅ **Solution**: In `Transaction.init`, call `require(amount > 0) { "Transaction amount must be positive" }`.

---

### Challenge 3: The Portfolio Allocation Math Anomaly
* **Symptom**: A user adds 3 stock holdings. The sum of allocation percentages shows `100.00000000000001%` or `99.999999999999%` due to IEEE-754 floating-point inaccuracies.
* **Question for QA Engineer**: *Why is floating-point arithmetic dangerous for financial calculations, and how do we handle it defensively?*
* 💡 **Hint 1**: Standard `Double` cannot accurately represent all decimal fractions in binary base-2.
* 💡 **Hint 2**: Use clamped percentage helpers or integer basis points (bps) / `BigDecimal`.
* ✅ **Solution**: Coerce allocation percentages using `.coerceIn(0f, 1f)` and round currency displays to 2 decimal places using formatters.

---

## 🎯 Stage 2 Debugging Challenges (Android Platform)

### Challenge 4: The Vanishing Form State on Rotation
* **Symptom**: A user types a 50-character transaction note into an input field. When they rotate the phone to landscape mode, the text disappears.
* **Question for QA Engineer**: *What Android lifecycle event occurs during device rotation, and why does in-memory Activity state get wiped?*
* 💡 **Hint 1**: Configuration changes destroy and recreate the Activity by default.
* 💡 **Hint 2**: Where is temporary UI state saved across teardowns?
* ✅ **Solution**: The OS calls `onSaveInstanceState(outState: Bundle)`. Store user inputs in the bundle or in a ViewModel's `SavedStateHandle`.

---

### Challenge 5: The Android 12+ Unexported Activity Crash
* **Symptom**: App crashes on install/launch on Android 12 (API 31+) with `IllegalArgumentException: Targeting S+ (version 31 and above) requires that an explicit value for android:exported be defined when intent filters are present`.
* **Question for QA Engineer**: *Why did Google make `android:exported` mandatory in Android 12, and how do you fix it?*
* 💡 **Hint 1**: Look at `<activity>` tags in `AndroidManifest.xml` that contain `<intent-filter>`.
* 💡 **Hint 2**: Security: If an activity has an intent-filter, should other apps be able to launch it?
* ✅ **Solution**: Explicitly set `android:exported="true"` on the launcher activity, and `android:exported="false"` on internal activities.

---

## 🎯 Stage 3 Debugging Challenges (Jetpack Compose)

### Challenge 6: The Unstable LazyColumn Jitter
* **Symptom**: A user scrolls down a list of 100 transactions. Whenever a new transaction is prepended, the scroll position jumps erratically and every visible item flashes/recomposes.
* **Code Fragment**:
  ```kotlin
  LazyColumn {
      items(transactions) { tx -> TransactionCard(tx) }
  }
  ```
* **Question for QA Engineer**: *Why does Compose recompose every row when a new item is added at index 0 without a key?*
* 💡 **Hint 1**: What is the default identity key used by `LazyColumn` if none is provided?
* 💡 **Hint 2**: Index-based identity vs domain-based identity.
* ✅ **Solution**: Supply stable domain key: `items(items = transactions, key = { it.id.value })`.

---

### Challenge 7: `remember` vs `rememberSaveable` on Rotation
* **Symptom**: A user types a search query in `TransactionListScreen`. When rotating the device, the search query resets to empty string.
* **Code Fragment**:
  ```kotlin
  var searchQuery by remember { mutableStateOf("") }
  ```
* **Question for QA Engineer**: *Why does `remember` survive recomposition but fail across Activity destruction / rotation?*
* 💡 **Hint 1**: `remember` stores values in the Composition slot table in memory.
* 💡 **Hint 2**: Read ADR 006 on saved state.
* ✅ **Solution**: Use `rememberSaveable` which writes the state to the Android `SavedStateRegistry`.

---

## 🎯 Stage 4 Debugging Challenges (Coroutines & Concurrency)

### Challenge 8: The Swallowed Cancellation Bug
* **Symptom**: A user cancels a long-running sync job or navigates away from a screen, but the background HTTP download continues running in Logcat, burning battery and data.
* **Code Fragment**:
  ```kotlin
  suspend fun syncData() {
      try {
          fetchLargeFile()
      } catch (e: Exception) {
          Log.e(TAG, "Failed: ${e.message}")
      }
  }
  ```
* **Question for QA Engineer**: *Why does catching `Exception` break coroutine cancellation?*
* 💡 **Hint 1**: What is the inheritance hierarchy of `CancellationException` in Kotlin?
* 💡 **Hint 2**: Structured concurrency relies on `CancellationException` bubbling up to cancel the parent Job.
* ✅ **Solution**: Re-throw `CancellationException`: `if (e is CancellationException) throw e` or use `safeSuspendCall`.

---

### Challenge 9: The Flaky Test Thread.sleep() Anti-Pattern
* **Symptom**: A unit test asserting `StateFlow` emission passes on a fast local MacBook, but fails 20% of the time on a slower CI runner.
* **Code Fragment**:
  ```kotlin
  viewModel.loadData()
  Thread.sleep(500) // Waiting for IO thread
  assertEquals(expectedState, viewModel.uiState.value)
  ```
* **Question for QA Engineer**: *Why is `Thread.sleep()` an antipattern in asynchronous testing, and how does `runTest` solve it?*
* 💡 **Hint 1**: What does `StandardTestDispatcher` do to coroutine virtual time?
* 💡 **Hint 2**: Read ADR 010 on `DispatcherProvider` and Turbine `test {}`.
* ✅ **Solution**: Inject `TestDispatcherProvider` and use `testDispatcher.scheduler.advanceUntilIdle()` or Turbine's `awaitItem()`.

---

## 🎯 Stage 5 Debugging Challenges (Clean Architecture)

### Challenge 10: The Inward Dependency Rule Violation
* **Symptom**: A developer imports `android.content.Context` inside a domain UseCase to format a localized currency string: `context.getString(R.string.currency_format, amount)`. Running the JVM unit test fails with `RuntimeException: Method getString not mocked`.
* **Question for QA Engineer**: *Why did this unit test fail on JVM, and how does Clean Architecture prevent this?*
* 💡 **Hint 1**: JVM unit tests do not run the Android OS runtime.
* 💡 **Hint 2**: Where does formatting belong: Domain or UI Presentation Layer?
* ✅ **Solution**: Move formatting to the UI Layer (`ui/mapper/UiMappers.kt`). The Domain layer must remain pure Kotlin with ZERO `android.*` imports.

---

### Challenge 11: Leaking DTO Nullability into Compose UI
* **Symptom**: A backend API occasionally returns `null` for `category.icon_name`. The Compose UI crashes with `NullPointerException` when attempting to render `Icons.Default`.
* **Question for QA Engineer**: *Why did a backend nullability bug crash the UI layer, and how do boundary mappers act as a firewall?*
* 💡 **Hint 1**: Were DTOs passed directly into the Composable?
* 💡 **Hint 2**: Read ADR 014 on defensive boundary mappers.
* ✅ **Solution**: Never pass DTOs directly to UI. Convert DTOs in `data/mapper/` with defensive fallbacks (e.g. `iconName ?: "category"`).

---

## 🎯 Stage 6 Debugging Challenges (Dependency Injection)

### Challenge 12: The `NoDefinitionFoundException` Runtime Crash
* **Symptom**: App crashes on startup with `NoDefinitionFoundException: No definition found for class 'com.enterprise.financetracker.domain.repository.ExpenseRepository'`.
* **Question for QA Engineer**: *Why does Koin throw this exception at runtime instead of failing at compile time, and how do we prevent it before shipping?*
* 💡 **Hint 1**: Koin is a runtime dependency injection DSL, not a compile-time code generator like Hilt.
* 💡 **Hint 2**: Check if `singleOf(::ExpenseRepositoryImpl)` is bound to its interface with `bind ExpenseRepository::class`.
* ✅ **Solution**: Use `singleOf(::ExpenseRepositoryImpl) bind ExpenseRepository::class` and write a CI unit test calling `AppModuleCheckTest` (`checkModules()`).

---

### Challenge 13: Scope Lifetime Mismatch (State Leaking Singleton)
* **Symptom**: User A logs out and User B logs in on the same device. User B sees User A's active filter query in the search bar.
* **Code Fragment**:
  ```kotlin
  val uiModule = module {
      single { TransactionListMviViewModel(...) } // Declared as Singleton!
  }
  ```
* **Question for QA Engineer**: *Why is declaring a ViewModel as `single` catastrophic for multi-user security and screen state?*
* 💡 **Hint 1**: What is the lifespan of a `single` definition vs a `viewModelOf` definition?
* 💡 **Hint 2**: Read ADR 017 on scope lifetimes.
* ✅ **Solution**: Change `single` to `viewModelOf(::TransactionListMviViewModel)` so the ViewModel lifecycle is scoped to the screen composable and destroyed when dismissed.

---

## 🎯 Stage 7 Debugging Challenges (Networking & Interceptors)

### Challenge 14: The 401 Token Refresh Storm (Thundering Herd)
* **Symptom**: When a user's access token expires, the dashboard makes 4 concurrent API calls (`/user`, `/transactions`, `/portfolio`, `/notifications`). The backend receives 4 refresh requests simultaneously with the same refresh token, triggers its "Token Reuse Detection" security rule, and logs the user out.
* **Question for QA Engineer**: *Why did 4 parallel requests trigger 4 token refresh calls, and how does a Coroutines Mutex in OkHttp Authenticator fix this?*
* 💡 **Hint 1**: OkHttp `Authenticator` is called on multiple background threads concurrently.
* 💡 **Hint 2**: Read ADR 019 on Mutex-protected token refresh.
* ✅ **Solution**: Use `Mutex.withLock` inside `TokenAuthenticator` and verify if `response.request.header("Authorization") != tokenManager.accessToken` before dispatching a new refresh call.

---

### Challenge 15: Serialization Mismatch on Unexpected Server Fields
* **Symptom**: A backend team releases an update adding `"geo_location": {"lat": 37.77, "lng": -122.41}` to the transaction response JSON. All Android clients immediately crash with `SerializationException: Field 'geo_location' is not known`.
* **Question for QA Engineer**: *Why does Kotlinx Serialization reject unknown fields by default, and how do you configure it for backward-compatible evolution?*
* 💡 **Hint 1**: Strict schema validation vs lenient parsing.
* 💡 **Hint 2**: Read ADR 018.
* ✅ **Solution**: Configure `Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }`.

---

## 🎯 Stage 8 Debugging Challenges (Local Persistence & Room)

### Challenge 16: The `IllegalStateException: Room cannot verify data integrity`
* **Symptom**: A developer adds a new column `@ColumnInfo(name = "notes") val notes: String` to `TransactionEntity` and increments the `@Database(version = 2)`. App crashes immediately on launch with `IllegalStateException: Room cannot verify the data integrity. Looks like you've changed schema but forgot to update the build identity hash`.
* **Question for QA Engineer**: *Why does Room crash on schema changes without migrations, and how do we resolve it safely?*
* 💡 **Hint 1**: Room generates an identity hash in the `room_master_table` SQLite table.
* 💡 **Hint 2**: Read ADR 023 on explicit migrations.
* ✅ **Solution**: Provide an explicit `Migration(1, 2)` executing `ALTER TABLE transactions ADD COLUMN notes TEXT DEFAULT ''` and attach it via `.addMigrations(MIGRATION_1_2)`.

---

### Challenge 17: Main Thread Database Query Violation
* **Symptom**: A developer calls `val tx = database.transactionDao().getByIdSync("tx_1")` directly from a Composable onClick lambda. Debug builds immediately crash with `IllegalStateException: Cannot access database on the main thread since it may potentially lock the UI for a long period of time`.
* **Question for QA Engineer**: *Why does Room strictly forbid main thread queries, and how do Kotlin Coroutines `suspend` and `Flow` solve it automatically?*
* 💡 **Hint 1**: Disk I/O latency ranges from 5ms to 500ms; Android frames must render in 16.6ms.
* 💡 **Hint 2**: Suspend functions and Flow in Room offload queries to background threads automatically.
* ✅ **Solution**: Mark DAO functions as `suspend` or return `Flow<T>`, and call them inside `viewModelScope.launch(dispatchers.io)`.

---

## 🎯 Stage 9 Debugging Challenges (Navigation & Deep Links)

### Challenge 18: The Double-Click Navigation Crash / Duplicate Destinations
* **Symptom**: A user rapidly taps the "Transactions" card twice on the Dashboard. The Transaction List screen opens twice, and pressing Back opens the same list screen again.
* **Code Fragment**:
  ```kotlin
  navController.navigate(TransactionListDestination)
  ```
* **Question for QA Engineer**: *Why does Navigation-Compose push two identical destinations onto the backstack, and how do you prevent it?*
* 💡 **Hint 1**: What does `launchSingleTop = true` do?
* 💡 **Hint 2**: Read ADR 025.
* ✅ **Solution**: Pass `{ launchSingleTop = true }` in the `navigate` lambda builder: `navController.navigate(TransactionListDestination) { launchSingleTop = true }`.

---

### Challenge 19: Unserializable Custom Complex Object in Route Argument
* **Symptom**: A developer passes a complete domain object into a destination class: `@Serializable data class DetailRoute(val transaction: Transaction)`. Running the app crashes with `IllegalArgumentException: Navigation destination route classes should only contain primitive or simple parcelable arguments`.
* **Question for QA Engineer**: *Why is passing entire data models between screens an architectural anti-pattern in Android?*
* 💡 **Hint 1**: What happens during process recreation if a large object was serialized into the backstack bundle?
* 💡 **Hint 2**: Read ADR 001 and Single Source of Truth (SSOT).
* ✅ **Solution**: Pass ONLY the identifier `@Serializable data class TransactionDetailDestination(val transactionId: String)`. Let the target screen query the repository / SSOT database using the ID.

---

## 🎯 Stage 10 Debugging Challenges (Testing & Turbine)

### Challenge 20: The Brittle Mock Verification Test Failure
* **Symptom**: A developer refactors `GetTransactionsUseCase` to add an in-memory cache check before querying the repository. Unit tests immediately fail with `Wanted but not invoked: repository.observeTransactions()`.
* **Question for QA Engineer**: *Why did a performance improvement break the unit tests, and how do In-Memory Fakes prevent brittle test failures?*
* 💡 **Hint 1**: Mocks test *implementation details* (method calls). Fakes test *observable behavior* (state outputs).
* 💡 **Hint 2**: Read ADR 027 on Fakes over Mocks.
* ✅ **Solution**: Replace `mock(ExpenseRepository::class.java)` with `FakeExpenseRepository()`. Test that `useCase()` emits the expected data regardless of internal caching mechanics.

---

### Challenge 21: Flaky Virtual Time with `UnconfinedTestDispatcher`
* **Symptom**: A developer uses `UnconfinedTestDispatcher` for testing a debounce operator (`debounce(300)`). The test sometimes finishes before virtual time advances, causing race conditions in CI.
* **Question for QA Engineer**: *What is the difference between `StandardTestDispatcher` and `UnconfinedTestDispatcher` regarding scheduled delays?*
* 💡 **Hint 1**: `StandardTestDispatcher` queues coroutines on a virtual clock scheduler.
* 💡 **Hint 2**: Read ADR 010 and ADR 028.
* ✅ **Solution**: Use `StandardTestDispatcher()` and explicitly advance time with `testDispatcher.scheduler.advanceUntilIdle()`.

---

## 🎯 Stage 11 Debugging Challenges (Multi-Module Architecture)

### Challenge 22: Circular Module Dependency Cycle
* **Symptom**: A developer in `:feature:transactions` adds a direct dependency `implementation(project(":feature:dashboard"))`, and `:feature:dashboard` adds `implementation(project(":feature:transactions"))`. Build fails with circular dependency.
* **Question for QA Engineer**: *Why are circular dependencies forbidden in Gradle, and how does the mediator pattern in `:app` solve it?*
* 💡 **Hint 1**: Gradle builds DAG (Directed Acyclic Graphs). Cycles cannot be resolved topologically.
* 💡 **Hint 2**: Read ADR 032 on feature module isolation.
* ✅ **Solution**: Remove horizontal dependencies between feature modules. Keep feature modules isolated and orchestrate cross-feature navigation centrally inside `:app`.

---

### Challenge 23: Transitive Classpath Leakage via `api`
* **Symptom**: Module `:core:database` exposes Room runtime via `api(libs.androidx.room.runtime)`. A developer in `:feature:dashboard` starts writing raw SQLite queries directly in the UI layer.
* **Question for QA Engineer**: *Why is leaking low-level persistence libraries into UI feature modules dangerous for clean architecture?*
* 💡 **Hint 1**: `api` puts dependencies on the compile classpath of all consumer modules.
* 💡 **Hint 2**: Read ADR 031 on `api` vs `implementation`.
* ✅ **Solution**: Change `api` to `implementation(libs.androidx.room.runtime)` in `:core:database`.

---

## 🎯 Stage 12 Debugging Challenges (Production Performance & Profiling)

### Challenge 24: Unstable Collection Recomposition Storm
* **Symptom**: In `DashboardScreen`, whenever a stock ticker price updates in `HoldingCard`, all 50 transaction rows in `TransactionCard` recompose simultaneously, causing frame drops down to 35 FPS.
* **Question for QA Engineer**: *Why does the Compose compiler treat `List<TransactionUiModel>` as unstable by default, and how does `@Immutable` fix this?*
* 💡 **Hint 1**: Standard Kotlin `List<T>` is an interface; the underlying runtime instance could be a mutable `ArrayList`.
* 💡 **Hint 2**: Read ADR 034 on Compose stability annotations.
* ✅ **Solution**: Annotate `TransactionUiModel` and `HoldingUiModel` with `@Immutable`.

---

### Challenge 25: Static Activity Leak in Singleton
* **Symptom**: After rotating the device 10 times, the app crashes with `OutOfMemoryError`. Heap dump shows 10 instances of `MainActivity` retained in memory.
* **Code Fragment**:
  ```kotlin
  object SecurityNotificationHelper {
      var activityContext: Context? = null
  }
  ```
* **Question for QA Engineer**: *Why does holding an Activity reference in a Singleton cause a massive memory leak upon screen rotation?*
* 💡 **Hint 1**: The OS creates a new Activity on rotation and calls `onDestroy()` on the previous one.
* 💡 **Hint 2**: GC Roots: A static field is never garbage collected during the process lifetime.
* ✅ **Solution**: Never store Activity context in singletons. Pass `applicationContext` or inject dependencies via Koin.

---

## 🎯 Stage 13 Debugging Challenges (Release Engineering & ProGuard/R8)

### Challenge 26: R8 Stripped Kotlinx Serializer Crash in Release Builds
* **Symptom**: Debug build works perfectly. Release build crashes on login API response with `SerializationException: Serializer for class 'NetworkTransactionDto' is not found. Mark the class as @Serializable or provide serializer explicitly`.
* **Question for QA Engineer**: *Why does R8 full mode strip companion serializers if not explicitly preserved in `proguard-rules.pro`?*
* 💡 **Hint 1**: Kotlinx Serialization generates companion `serializer()` synthetic methods at compile-time.
* 💡 **Hint 2**: Read ADR 036 on ProGuard keep rules.
* ✅ **Solution**: Add `-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }` and `-keepclassmembers class * { *** Companion; }` in `proguard-rules.pro`.

---

### Challenge 27: Missing Room Database Implementation in Minified APK
* **Symptom**: Release APK crashes on launch with `RuntimeException: Cannot find implementation for com.enterprise.financetracker.data.local.FinanceDatabase. FinanceDatabase_Impl does not exist`.
* **Question for QA Engineer**: *Why does Room use reflection to instantiate `_Impl` classes, and how do we prevent R8 from obfuscating/stripping them?*
* 💡 **Hint 1**: Room generates `FinanceDatabase_Impl` at compile-time and instantiates it via `Room.databaseBuilder`.
* 💡 **Hint 2**: Read ADR 036.
* ✅ **Solution**: Add `-keep class **_Impl { *; }` and `-keep class * extends androidx.room.RoomDatabase` in `proguard-rules.pro`.
