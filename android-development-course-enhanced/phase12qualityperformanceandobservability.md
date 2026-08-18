# PHASE 12 — QUALITY, PERFORMANCE & OBSERVABILITY (Week 17)

**Objective:** Ship production Android code that is fast, leak-free, observable, and resilient in the field. Master profiling, frame budgets, memory leaks, and telemetry.
**Why this phase matters:** Writing features that work on a fast development machine is only half the battle. In production, users have budget devices, low RAM, and thermal throttling. Without performance discipline, apps drop frames (jank), suffer OutOfMemory crashes, get killed by the OS (ANRs), or burn battery, leading to 1-star reviews and uninstalls.
**Prerequisites:** Phase 1 (Kotlin), Phase 2 (Coroutines & Flow), Phase 4 (Jetpack Compose), Phase 5 (App Architecture), Phase 10 (Testing), Phase 11 (Gradle & Modularization).
**Project deliverable:** Expense Tracker Performance & Quality Suite — Custom detekt rules, LeakCanary leak diagnostics, Baseline Profile integration, and frame budget optimization.
**Concepts covered:** 10 total, each with the full 13-step teaching sequence.

---

## Concept 1: Static Analysis & Automated Code Quality (ktlint, detekt, Android Lint)

### 1. What is it
Automated static analysis tools parse your source code without executing it, checking against a set of predefined rules.
- **ktlint**: An anti-bikeshedding Kotlin linter that enforces the official Kotlin style guide.
- **detekt**: A static code analyzer for Kotlin looking for code smells, complexity, and performance issues.
- **Android Lint**: Google's official tool for Android-specific structural, accessibility, security, and performance issues.

### 2. Why does it exist
In your QA automation background, you know that finding bugs early (shift-left testing) is cheaper. Static analysis shifts quality enforcement to the moment code is written, preventing stylistic arguments in PRs, catching common bugs, and enforcing architectural boundaries automatically.

### 3. Mental model
Think of static analysis as a **CI/CD pre-flight checklist**. Just like SonarQube or Checkstyle in Java, but tailored for Kotlin's concise syntax and Android's specific SDK quirks.

### 4. How it works
These tools generate an Abstract Syntax Tree (AST) of your code. Rules traverse this tree. If a rule condition is met (e.g., "function length > 50 lines"), it flags a violation. Detekt allows you to write *custom rules* to enforce project-specific constraints (e.g., banning `java.util.Date` in favor of `java.time.LocalDate`).

### 5. Code
Configuring detekt in `build.gradle.kts` and a custom rule to ban `runCatching` with coroutines (because it catches `CancellationException`, breaking structured concurrency).

```kotlin
// build.gradle.kts
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.1"
}

detekt {
    buildUponDefaultConfig = true // pre-configure defaults
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

// Custom Detekt Rule: Ban runCatching in suspend functions
class NoRunCatchingInSuspend(config: Config) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Defect,
        "runCatching catches CancellationException, breaking coroutine cancellation.",
        Debt.FIVE_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text == "runCatching") {
            report(CodeSmell(issue, Entity.from(expression), issue.description))
        }
    }
}
```

### 6. Production usage
Every PR triggered on CI (GitHub Actions) runs `./gradlew lint detekt ktlintCheck`. If any tool reports a severity level of "error", the build fails, and the PR cannot be merged. Android Lint is configured to `warningsAsErrors = true`.

### 7. Common mistakes
❌ **Wrong:** Letting `runCatching` swallow coroutine cancellations.
```kotlin
suspend fun fetchExpenses() {
    runCatching {
        api.getExpenses() // If cancelled, runCatching swallows CancellationException
    }.onFailure { logError(it) }
}
```
✅ **Right:** Using a custom detekt rule to ban `runCatching`, or catching only `Exception` manually.

### 8. Debugging
When detekt fails on CI, read the HTML or XML report generated in `build/reports/detekt/`. It points to the exact file, line, and rule violated.

### 9. Testing
Custom detekt rules are unit tested using `detekt-test`. You pass a string of Kotlin code to your rule and assert that the exact number of findings are reported.

### 10. Exercise
Write a custom Detekt rule `BanJavaUtilDate` that flags any import of `java.util.Date` and suggests `java.time.Instant` or `LocalDate` instead.

### 11. Deliberate failure
Create an Activity with an `@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")` on a Compose Scaffold. Run Android Lint. See how suppressing hides real UI bugs (content drawing under the system bar). Remove the suppression and fix the padding.

### 12. Interview questions
- *Q: What is the difference between ktlint and detekt?* (ktlint is for formatting/style, detekt is for code smells/complexity).
- *Q: Why should we treat Android Lint warnings as errors?* (To prevent the "broken windows" theory where developers ignore warnings until the codebase is unmaintainable).
- *Q: How do you enforce custom architectural rules, like "ViewModels cannot import `android.content.Context`"?* (Using custom detekt rules).

### 13. Checkpoint
You can explain how static analysis tools generate an AST and how to integrate them into a Gradle CI pipeline to enforce project standards.

---

## Concept 2: App Startup Optimization & Baseline Profiles

### 1. What is it
Optimizing the time it takes from the user tapping the app icon to the app being usable.
- **Cold start:** App process is dead. OS creates it, initializes Application, creates Activity, inflates/composes UI.
- **Warm start:** App process exists, but Activity needs to be recreated (e.g., navigating back to it).
- **Hot start:** App process and Activity exist, just brought to the foreground.
**Baseline Profiles** are ahead-of-time (AOT) compiled lists of classes and methods that the app uses during critical paths (like startup), allowing Android's runtime (ART) to execute them faster without waiting for Just-In-Time (JIT) compilation.

### 2. Why does it exist
Slow startup kills retention. If an app takes >3 seconds to open, users abandon it. JIT compilation is slow on budget devices. By pre-compiling critical paths, we skip JIT overhead on the first run.

### 3. Mental model
Think of JIT vs AOT like interpreting a speech live vs reading a translated script. Baseline Profiles give the Android OS the translated script of your app's startup sequence immediately upon installation.

### 4. How it works
You write a UI test using `Macrobenchmark` that starts your app and scrolls the main feed. The test generates a `baseline-prof.txt` file listing all invoked methods. You bundle this file in your APK/AAB. Google Play delivers it alongside the APK. Android OS AOT-compiles those methods during installation.

### 5. Code
Using AndroidX App Startup to defer non-critical initializations, and Macrobenchmark to generate a Baseline Profile.

```kotlin
// App Startup Initializer (runs before Application.onCreate)
class AnalyticsInitializer : Initializer<Analytics> {
    override fun create(context: Context): Analytics {
        // Init happens on background thread if requested, or lazy
        return Analytics.init(context)
    }
    override fun dependencies() = emptyList<Class<out Initializer<*>>>()
}

// Generating Baseline Profile (in a separate macrobenchmark module)
// BaselineProfileRule is stable — no @OptIn(ExperimentalBaselineProfilesApi) needed
// on current androidx.benchmark releases.
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.expense.tracker"
    ) {
        // Start the app
        pressHome()
        startActivityAndWait()
        // Simulate user journey (e.g., scroll the transaction list)
        device.findObject(By.res("transaction_list")).scroll(Direction.DOWN, 1f)
    }
}
```

> **[Extension] Note:** Since AGP 8.0, Google's recommended path is the **Baseline Profile Gradle plugin** (`androidx.baselineprofile`), which wires this `BaselineProfileRule` test into a single `./gradlew :app:generateBaselineProfile` task, automatically merges the profile into your release build, and (as of newer plugin versions) can also generate a **Startup Profile** for the classes needed before the first frame. Writing the raw `BaselineProfileRule` test above is still exactly how the profile is *collected* — the plugin just automates running it and wiring the output into your release variant, so you rarely invoke `rule.collect` "by hand" outside of the generator module.

### 6. Production usage
Production builds include a `baseline-prof.txt` file. You also use `reportFullyDrawn()` in your main Activity when the first network request completes and the UI is populated, telling the OS exactly when Time to Full Display (TTFD) is reached.

### 7. Common mistakes
❌ **Wrong:** Initializing 10 heavy SDKs (analytics, crash reporting, DB migrations) directly on the main thread in `Application.onCreate()`.
✅ **Right:** Using App Startup to lazy-load them or using Coroutines to initialize non-UI-blocking SDKs in the background.

### 8. Debugging
Profile app startup in Android Studio Profiler (CPU -> Trace System Calls). Look for large gaps or blocking calls in `bindApplication` or `activityStart`. Use `adb shell am start -W com.expense.tracker/.MainActivity` to measure raw startup time.

### 9. Testing
Use `androidx.benchmark:benchmark-macro-junit4` to write tests that measure startup time (`StartupTimingMetric`) with and without Baseline Profiles to quantify the improvement.

### 10. Exercise
Refactor an app that does `Thread.sleep(1000)` in `Application.onCreate()` to simulate a heavy SDK init. Move the initialization to a background coroutine using `ProcessLifecycleOwner.get().lifecycleScope`.

### 11. Deliberate failure
Put a network request inside an App Startup `Initializer` without using a background thread. Observe the ANR or crash on startup (NetworkOnMainThreadException).

### 12. Interview questions
- *Q: What is the difference between Time to Initial Display (TTID) and Time to Full Display (TTFD)?* (TTID is when the first frame is drawn, often just placeholders/spinners. TTFD is when actual data is loaded and usable).
- *Q: How do Baseline Profiles improve Compose performance?* (Compose libraries are heavily unbundled; without Baseline Profiles, ART has to JIT compile thousands of Compose methods on first start).

### 13. Checkpoint
You can set up a Macrobenchmark module to generate a Baseline Profile and verify the startup metrics improvement.

---

## Concept 3: The 16.6ms Frame Budget, Jank & Compose Performance

### 1. What is it
To achieve a smooth 60 Frames Per Second (FPS), the app must calculate, layout, and draw the screen in `1000ms / 60 = 16.6ms`. If it takes 20ms, the OS misses the display sync, drops the frame, and the user sees a stutter. This is called **Jank**.

### 2. Why does it exist
Compose is declarative; UI updates via **recomposition**. If your composables do heavy math, read from disk, or use unstable parameters, Compose recomposes them unnecessarily on every frame (e.g., during a scroll), easily blowing past the 16.6ms budget.

### 3. Mental model
Think of UI rendering as an assembly line running at a fixed speed (the refresh rate). If one station (a Composable) takes too long, the conveyor belt moves without the product (dropped frame).

### 4. How it works
Compose has three phases: **Composition** (what to show), **Layout** (where to put it), and **Draw** (how to render it). Performance optimization means skipping phases if inputs haven't changed. We use **Compose Compiler Metrics** to find functions that are not `skippable` because they take unstable parameters (like standard `List` or mutable `var`s in a class without `@Stable`).

### 5. Code
Using `derivedStateOf` to prevent excessive recomposition during scrolling.

```kotlin
// ❌ WRONG: Recomposes on EVERY pixel scrolled
@Composable
fun ScrollToTopButton(scrollState: LazyListState) {
    val showButton = scrollState.firstVisibleItemIndex > 0
    if (showButton) {
        Button(onClick = { /* ... */ }) { Text("Top") }
    }
}

// ✅ RIGHT: derivedStateOf throttles recomposition
@Composable
fun ScrollToTopButtonOptimized(scrollState: LazyListState) {
    // Only triggers recomposition when the boolean result CHANGES, not on every scroll step
    val showButton by remember {
        derivedStateOf { scrollState.firstVisibleItemIndex > 0 }
    }
    if (showButton) {
        Button(onClick = { /* ... */ }) { Text("Top") }
    }
}
```

### 6. Production usage
Using Immutable collections (`kotlinx.collections.immutable.ImmutableList`) instead of `List` in Compose state. `List` is an interface in Kotlin; Compose doesn't know if the underlying implementation is mutable (like `ArrayList`), so it assumes it is unstable and never skips recomposition.

### 7. Common mistakes
❌ **Wrong:** Passing unstable data classes to composables.
```kotlin
data class UnstableData(var name: String) // var makes it unstable
```
✅ **Right:** Using `val` and `@Immutable`.
```kotlin
@Immutable
data class StableData(val name: String)
```

### 8. Debugging
Enable Layout Inspector in Android Studio. Turn on "Show Recomposition Counts". Scroll your app. If you see a Composable recomposing hundreds of times unnecessarily, you have a performance bug. Run the Compose Compiler Metrics gradle task to identify unskippable functions.

> **[Extension] Note:** Since Kotlin 2.0, the Compose compiler moved out of AGP/Jetpack and into the Kotlin repository itself, so metrics/reports are now configured through the `org.jetbrains.kotlin.plugin.compose` Gradle plugin's `composeCompiler { }` DSL (e.g. `composeCompiler { metricsDestination.set(layout.buildDirectory.dir("compose_metrics")) }`) rather than the older pattern of passing raw `freeCompilerArgs` with `-P plugin:androidx.compose.compiler.plugins.kotlin:...`. If you see a project still setting `composeOptions { kotlinCompilerExtensionVersion = "..." }` in the `android { }` block, that's the pre-Kotlin-2.0 way of pinning a standalone Compose compiler version — with Kotlin 2.0+ the compiler version is tied to the Kotlin version instead, and that field is unused/ignored.

### 9. Testing
Write Macrobenchmark UI tests using `FrameTimingMetric` to measure the 50th, 90th, and 99th percentile frame times during a scroll animation.

### 10. Exercise
Create a `LazyColumn` with 1000 items. Put a `Thread.sleep(10)` inside the row item composable. Scroll it and experience horrendous jank. Use the Android Studio Profiler (System Trace) to visualize the frames missing the 16.6ms deadline.

### 11. Deliberate failure
Forget to use `key` in a `LazyColumn` for reorderable items. When the list order changes, Compose destroys and recreates every node instead of just moving them, causing massive UI jank.

### 12. Interview questions
- *Q: What are the three phases of Compose, and how do you optimize them?* (Composition, Layout, Draw. Optimize by deferring state reads to the lowest possible phase, e.g., reading offset in a lambda for the layout phase rather than composition phase).
- *Q: When should you use `derivedStateOf`?* (When your input state changes faster than you need your output state to change, like mapping scroll offset to a boolean visibility flag).

### 13. Checkpoint
You can explain the 16.6ms budget and use `derivedStateOf` to prevent excessive recomposition.

---

## Concept 4: Memory Management & Leak Detection with LeakCanary

### 1. What is it
A memory leak in Android occurs when an object that is no longer needed (like a destroyed `Activity`) is still referenced by a long-lived object (like a static variable or active background thread), preventing the Garbage Collector (GC) from reclaiming its memory. **LeakCanary** is a library that automatically detects these leaks in debug builds.

### 2. Why does it exist
Memory leaks lead to frequent GC pauses (which cause jank, as the app freezes while GC runs) and eventually `OutOfMemoryError` (OOM) crashes. In QA, catching OOMs is hard because they happen randomly. LeakCanary detects the leak the moment the Activity is destroyed.

### 3. Mental model
Imagine renting a hotel room (Activity). When you check out (destroy), you are supposed to give the key back. If you give the key to a friend who lives in the lobby permanently (a Static object), the hotel can never rent that room out again. The room is leaked.

### 4. How it works
LeakCanary hooks into the Android lifecycle. When an Activity/Fragment is destroyed, it creates a `WeakReference` to it. After a 5-second delay, it forces a Garbage Collection. If the `WeakReference` is not cleared by the GC, it means something is holding a strong reference to it. LeakCanary then dumps the JVM heap (`.hprof` file) and parses it using a library called Shark to find the exact chain of references (the shortest path to GC roots) causing the leak.

### 5. Code
A classic Coroutine leak (using the wrong scope) vs the correct approach.

```kotlin
// ❌ WRONG: Leaks the Activity if fetch takes a long time and Activity is rotated/destroyed
class ExpenseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // GlobalScope lives as long as the application process!
        GlobalScope.launch {
            val data = api.fetchData() // Takes 10 seconds
            updateUI(data) // Implicitly holds reference to ExpenseActivity
        }
    }
}

// ✅ RIGHT: lifecycleScope is automatically cancelled when Activity is destroyed
class ExpenseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            val data = api.fetchData()
            updateUI(data) 
        }
    }
}
```

### 6. Production usage
LeakCanary is **only** included in `debug` builds (using `debugImplementation`). In production, you use tools like Firebase Performance Monitoring or Bugsnag to track OOM rates, as heap dumping causes massive app freezes.

### 7. Common mistakes
❌ **Wrong:** Passing an `Activity` context to a Singleton or a ViewModel.
✅ **Right:** Passing the `Application` context, or avoiding passing contexts entirely by passing dependencies.

### 8. Debugging
When LeakCanary triggers, it posts a notification. Tapping it shows a stack trace of the leak path. Start from the bottom (the leaked Activity) and trace up to the top (the GC root, usually a static field, active thread, or JNI global). Look for the `Leaking: YES` tag in the hierarchy.

### 9. Testing
You can run automated tests with LeakCanary. If an instrumentation test finishes but leaks memory, the test will fail.

### 10. Exercise
Create an Activity. Define a `companion object { var leakedContext: Context? = null }`. In `onCreate`, assign `leakedContext = this`. Rotate the device 5 times. Observe the LeakCanary notification and read the heap dump trace.

### 11. Deliberate failure
Register a `BroadcastReceiver` in an Activity but forget to unregister it in `onDestroy()`. See LeakCanary flag the Android OS system service holding onto your Activity.

### 12. Interview questions
- *Q: What is a memory leak in Android?* (When a GC root holds a strong reference to an object that has finished its lifecycle, preventing garbage collection).
- *Q: How does LeakCanary know an Activity is leaked?* (By watching destroyed activities with `WeakReference` and checking if they clear after a forced GC).
- *Q: Why should you never pass a Context into a ViewModel?* (ViewModels outlive Activities during configuration changes like rotation; holding the old Activity context leaks it).

### 13. Checkpoint
You understand the concept of a strong reference, GC roots, and how to read a LeakCanary trace to identify the offending object.

---

## Concept 5: ANRs (Application Not Responding) & Main Thread Discipline

### 1. What is it
An ANR occurs when the app's UI thread (Main Thread) is blocked for too long. If the main thread is blocked, the app cannot process user input or draw frames.
- Input dispatch timeout: 5 seconds.
- BroadcastReceiver timeout: 10 seconds (foreground).
**StrictMode** is a developer tool that detects accidental disk or network access on the main thread.

> **[Extension] The full ANR timeout table:** The two timeouts above are the most commonly cited, but interviewers often probe the rest of the table since it reveals how well you understand *why* each component has a different budget:
> | Component | Timeout |
> |---|---|
> | Input dispatch (touch/key events) | 5 seconds |
> | `BroadcastReceiver.onReceive()`, foreground | 10 seconds |
> | `BroadcastReceiver.onReceive()`, background | 60 seconds |
> | `Service` callbacks (e.g. `onStartCommand`), foreground | 20 seconds |
> | `Service` callbacks, background | 200 seconds |
> The pattern: anything the user can currently see gets a short leash (5–20s); anything running invisibly in the background gets a much longer one, because there's no frozen UI to complain about — the OS is just protecting overall system health, not user-perceived responsiveness.

### 2. Why does it exist
If an app freezes, the user thinks the phone is broken. The OS steps in and throws an ANR dialog ("App isn't responding - Close or Wait") to let the user escape the frozen app. ANRs severely impact Google Play Store rankings (Google Play Vitals).

### 3. Mental model
The Main Thread is a restaurant waiter. The waiter's job is to take orders (touch events) and serve food (draw UI). If the waiter goes to the kitchen and cooks a complex meal themselves (Network/Disk I/O), they ignore the customers. Customers get angry (ANR). The waiter must delegate cooking to the chefs (Background threads/Coroutines).

### 4. How it works
All UI operations and Android lifecycle callbacks (`onCreate`, `onResume`) run on a single thread (the Main Thread) via a message queue (`Looper`). If you put a heavy task in `onCreate`, the thread is blocked, and the next message in the queue (e.g., "user tapped button") cannot be processed until the heavy task finishes.

### 5. Code
Configuring StrictMode to detect main thread violations and fixing them with Coroutines.

```kotlin
// Application class
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .penaltyDeath() // Crash the app in debug so developers fix it!
                    .build()
            )
        }
    }
}

// ❌ WRONG: File I/O on Main Thread (StrictMode will crash this)
fun saveExpense(expense: String) {
    File(context.filesDir, "expenses.txt").appendText(expense) 
}

// ✅ RIGHT: Offloading to Dispatchers.IO
suspend fun saveExpenseSafe(expense: String) = withContext(Dispatchers.IO) {
    File(context.filesDir, "expenses.txt").appendText(expense)
}
```

### 6. Production usage
In production, Google Play Console and Crashlytics gather ANR traces. An ANR trace (`traces.txt`) shows the call stack of the Main Thread at the exact moment it was blocked, allowing you to see which method was hanging.

### 7. Common mistakes
❌ **Wrong:** Doing heavy JSON deserialization on the Main Thread (e.g., parsing a 5MB JSON string).
✅ **Right:** Using `Dispatchers.Default` for CPU-intensive tasks like JSON parsing or sorting large lists, and `Dispatchers.IO` for file/network access.

### 8. Debugging
If you get an ANR, look at the traces file. Search for `tid=1` (Thread ID 1, the main thread). Look at the top of the stack. Is it waiting on a lock (`Object.wait()`)? Is it stuck in `Gson.fromJson`? Is it stuck in a heavy database query?

### 9. Testing
You can write unit tests with `runTest` and swap the Main dispatcher using `Dispatchers.setMain(StandardTestDispatcher())` to verify that your view models correctly switch contexts for heavy work.

### 10. Exercise
Write a function that calculates the 50th Fibonacci number. Call it directly from a Button `onClick` listener. Observe the app freeze entirely. Wrap the call in `viewModelScope.launch(Dispatchers.Default)` and update the UI with a StateFlow.

### 11. Deliberate failure
Create a `CountDownLatch(1)` on the Main Thread and call `await()`. Watch the app completely freeze and trigger an ANR dialog after 5 seconds of tapping the screen.

### 12. Interview questions
- *Q: What is the difference between an ANR and a Crash?* (A crash is an unhandled exception that kills the app immediately. An ANR is a frozen state where the thread is blocked, and the OS kills it after a timeout).
- *Q: How do you read an ANR trace?* (Find the main thread stack trace, see what method is executing. If it's `nativePollOnce`, the main thread is idle and the ANR might be caused by CPU starvation from background threads. If it's a specific method, that method is blocking).

### 13. Checkpoint
You can set up StrictMode, identify main thread blockers, and correctly route CPU vs I/O bound tasks to their respective coroutine dispatchers.


---

## 6. Android Profiling Tools Mastery (Profiler, Perfetto, Benchmarks)

### 1. What is it?
The suite of tools used to analyze an Android application's runtime performance, including CPU usage, memory allocation, battery consumption, and rendering speed. Key tools include the Android Studio Profiler, Perfetto (system tracing), and Jetpack Macrobenchmark/Microbenchmark.

### 2. Why does it exist?
Unlike server-side Java where you can simply throw more RAM or CPU at a performance problem, mobile devices are severely constrained. Unoptimized code leads to dropped frames (jank), battery drain, out-of-memory (OOM) crashes, and high user churn. Profiling tools tell you exactly *where* the bottleneck is.

### 3. Mental model
If you've used YourKit or VisualVM in Java, the **Android Studio Profiler** is the equivalent for the Dalvik/ART VM. **Perfetto** is like a system-wide Wireshark for CPU threads and kernel events. **Jetpack Benchmarks** are like JMeter, but instead of measuring HTTP request throughput, they measure UI rendering speed and startup time.

### 4. How it works
- **Android Studio Profiler:** Attaches an agent to your app process to sample CPU stacks, track object allocations, and monitor energy APIs.
- **Perfetto/Systrace:** Collects high-frequency system events (vsync, thread scheduling, binder calls) directly from the Linux kernel.
- **Microbenchmark:** Runs small snippets of code (like sorting an array) in a loop to measure nanosecond-level performance.
- **Macrobenchmark:** Automates UI interactions (like scrolling) using a separate test process to measure frame timings and startup latency on real devices.

### 5. Code Example (Macrobenchmark for Startup)
```kotlin
// In your macrobenchmark module
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.enterprise.expensetracker",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }
}
```

### 6. Production Usage
Teams use Macrobenchmark in their CI pipeline running on physical devices in Firebase Test Lab to ensure that a new PR doesn't regress the app's cold startup time or cause scrolling jank in core flows like the main transaction feed.

### 7. Common Mistakes
**The QA Trap:** Trying to profile performance on a `debug` build or an emulator. Debug builds have overhead (assertions, no minification, slower Kotlin reflection).
**The Fix:** Always profile on a physical device using a `release` build configured with `<profileable android:shell="true"/>` in the manifest.

### 8. Debugging (Using CPU Flame Charts)
When investigating a frozen UI:
1. Open Android Studio Profiler -> CPU.
2. Hit Record. Reproduce the freeze. Stop.
3. Switch to the **Flame Chart** view. Look for wide orange bars on the `main` thread. A wide bar means a method took a long time to execute.

### 9. Testing
Performance testing *is* the test. You use Jetpack Benchmarks to write automated tests that fail if a metric (like `frameDurationCpuMs`) exceeds a defined threshold.

### 10. Exercise
1. Run the Android Studio CPU Profiler on the Expense Tracker.
2. Record a trace while navigating between screens.
3. Identify the method that consumes the most CPU time on the main thread.

### 11. Deliberate Failure
**The Broken Code:**
```kotlin
fun onCheckoutClicked() {
    // Blocking the main thread with heavy computation
    val sum = (1..100_000_000).sum() 
    navigateToSuccess()
}
```
**The Fix:** Use Coroutines to offload to `Dispatchers.Default` and observe the difference in the CPU Profiler (main thread is no longer blocked).

### 12. Interview Questions
*   **Q:** What is the difference between Microbenchmark and Macrobenchmark?
    *   **A:** Microbenchmark is for measuring CPU-bound logic (algorithms, data parsing) in isolation. Macrobenchmark measures end-to-end user flows (startup, scrolling) involving the entire app stack, rendering pipeline, and OS interactions.
*   **Q:** How do you track down the cause of dropped frames (jank)?
    *   **A:** I'd capture a system trace using Perfetto or Android Studio CPU Profiler, isolate the main thread, and look for operations taking longer than 16ms (for 60fps) between vsync signals.

### 13. Checkpoint
Can you explain why running a performance test on an emulator is generally invalid for Android?

---

## 7. Crash Reporting & Symbolication (Crashlytics & Sentry)

### 1. What is it?
Tools like Firebase Crashlytics or Sentry capture unhandled exceptions (crashes), native (C/C++) crashes, and ANRs (Application Not Responding) from users in production, aggregating them into a dashboard. "Symbolication" is the process of translating obscured, minified code back into readable Kotlin file names and line numbers.

### 2. Why does it exist?
Users rarely report crashes. When an app crashes, the OS just kills it. Without a crash reporter, you have zero visibility into production stability. Without symbolication, your stack traces look like `a.b.c() at a.java:12`, making debugging impossible.

### 3. Mental model
If you are used to checking Splunk, Datadog, or ELK for Spring Boot server logs and exceptions, Crashlytics is your Android equivalent. Symbolication is like applying a decryption key (the ProGuard/R8 mapping file) to a ciphertext stack trace.

### 4. How it works
- The SDK registers an `UncaughtExceptionHandler` at app startup.
- If an unhandled exception occurs, the SDK saves the stack trace, device state, and custom logs to disk.
- On the *next* app launch, it uploads this payload to the server.
- During your build process, the R8 compiler generates a `mapping.txt` file. A Gradle plugin automatically uploads this file to the crash reporting service so it can de-obfuscate the incoming traces.

### 5. Code Example
```kotlin
// Setting user context for easier debugging
FirebaseCrashlytics.getInstance().setUserId("user_12345")
FirebaseCrashlytics.getInstance().setCustomKey("user_tier", "premium")

// Logging a non-fatal exception (e.g., in a try-catch for a handled edge case)
try {
    processPayment(expense)
} catch (e: PaymentFailedException) {
    // Won't crash the app, but logs the stack trace in Crashlytics
    FirebaseCrashlytics.getInstance().recordException(e)
    showErrorUI()
}

// Adding breadcrumbs (helps retrace steps before a crash)
FirebaseCrashlytics.getInstance().log("User clicked 'Submit Expense'")
```

### 6. Production Usage
Every production app uses this. Teams monitor "Crash-Free Users" percentage daily. A sudden drop below 99% triggers a rollback or a hotfix.

### 7. Common Mistakes
**The QA Trap:** Forgetting to upload the mapping file in CI/CD.
**The Fix:** Ensure your release pipeline executes the mapping upload task.
```bash
# Example for Crashlytics
./gradlew app:assembleRelease app:uploadCrashlyticsMappingFileRelease
```

### 8. Debugging
When investigating a crash in Crashlytics:
1. Look at the OS version and Device models. Is it specific to Samsung Android 12?
2. Read the "Breadcrumbs" (logs leading up to the crash).
3. Check custom keys (e.g., was the user offline?).

### 9. Testing
To test crash reporting integration, you must intentionally crash the app, then restart it.
```kotlin
// Add a hidden debug button
Button(onClick = { throw RuntimeException("Test Crash") }) {
    Text("Crash App")
}
```

### 10. Exercise
1. Integrate Firebase Crashlytics into the Expense Tracker.
2. Add a `Timber` tree that automatically forwards `Timber.e(exception)` calls to `Crashlytics.recordException()`.

### 11. Deliberate Failure
**The Broken Code:**
Swallowing exceptions without reporting them:
```kotlin
try {
    val data = parseJson(response)
} catch (e: Exception) {
    Log.e("Error", "Failed to parse") // Invisible in production!
}
```
**The Fix:** Call `FirebaseCrashlytics.getInstance().recordException(e)`.

### 12. Interview Questions
*   **Q:** What is a mapping file and why is it crucial for production builds?
    *   **A:** A mapping file is generated by R8/ProGuard during code shrinking/obfuscation. It maps the obfuscated class/method names (like `a.b.c`) back to their original Kotlin names. Without it, production crash stack traces are unreadable.
*   **Q:** What is the difference between a fatal and non-fatal crash in Crashlytics?
    *   **A:** A fatal crash kills the app process (unhandled exception). A non-fatal crash is explicitly caught by the developer and logged via `recordException`, allowing the app to continue running while still alerting the team to an issue.

### 13. Checkpoint
Explain the sequence of events that occurs when an Android app throws an `UncaughtExceptionHandler` and how Crashlytics ensures the payload reaches the server.

---

## 8. Real User Monitoring (RUM) & Performance Telemetry

### 1. What is it?
Real User Monitoring (RUM) tracks performance metrics directly from your users' devices in the wild. This includes screen rendering times, HTTP request latency, and custom code execution durations. Tools include Datadog RUM and Firebase Performance Monitoring.

### 2. Why does it exist?
Profiling in Android Studio only tests *your* specific device on *your* fast Wi-Fi. Users have diverse hardware (cheap 5-year-old phones) and network conditions (flaky 3G). RUM aggregates this real-world data to show you the 50th, 90th, and 99th percentiles (p50, p90, p99) of performance.

### 3. Mental model
It's exactly like APM (Application Performance Monitoring) tools like New Relic, Dynatrace, or Datadog for Spring Boot, but applied to the client side. Instead of tracking database query latency, you track SQLite query latency and UI render times.

### 4. How it works
- **Auto-Instrumentation:** A Gradle plugin injects bytecode at compile time to automatically wrap `OkHttp` calls and Activity lifecycle methods.
- **Custom Traces:** You manually wrap specific code blocks with start/stop API calls to measure specific business logic (e.g., "compress_image_duration").

### 5. Code Example (Custom Trace)
```kotlin
fun synchronizeData() {
    // Start custom trace
    val trace = FirebasePerformance.getInstance().newTrace("sync_expenses_trace")
    trace.start()
    
    try {
        val expenses = api.fetchExpenses()
        database.insert(expenses)
        // Add custom metric to the trace
        trace.putMetric("expenses_processed", expenses.size.toLong())
    } finally {
        // Always stop in a finally block!
        trace.stop() 
    }
}

// Or using an annotation (requires Gradle plugin)
@AddTrace(name = "process_image", enabled = true)
fun processImage(bitmap: Bitmap) { ... }
```

### 6. Production Usage
If an API endpoint slows down from 200ms to 2000ms, Firebase Performance Monitoring will flag a regression in network response time. You monitor the p90 latency of your "App Start" trace to ensure the 10% of users with the slowest phones still get an acceptable experience.

### 7. Common Mistakes
**The QA Trap:** Forgetting to stop a custom trace when an exception is thrown.
**The Fix:** Always use `try-finally` blocks for start/stop traces to ensure they complete, or use higher-order functions to wrap the logic safely.

### 8. Debugging
If HTTP requests aren't showing up in Datadog/Firebase:
1. Check if you added the required Interceptor to your OkHttpClient.
2. Verify the auto-instrumentation Gradle plugin is applied.

### 9. Testing
Telemetry tools usually provide a debug mode to log trace payloads to logcat so you can verify they are being captured correctly during manual QA.

### 10. Exercise
Add an OkHttp Interceptor to your Expense Tracker network module to automatically log the URL, HTTP method, response code, and latency of every API call to a custom logging interface.

### 11. Deliberate Failure
**The Broken Code:**
```kotlin
val trace = Firebase.performance.newTrace("load_data")
trace.start()
if (cache != null) return // Early return leaks the trace!
loadFromNetwork()
trace.stop()
```
**The Fix:** The trace is never stopped if the cache is hit. Move `trace.stop()` to a `finally` block or use a scoping function.

### 12. Interview Questions
*   **Q:** Why is monitoring the p90 or p99 latency more important than the average (mean) latency?
    *   **A:** Averages hide outliers. If 9 users load the app in 1 second, and 1 user takes 20 seconds, the average is ~3 seconds, which looks fine. Monitoring p90 ensures you are tracking the experience of your slowest/worst-case scenarios.
*   **Q:** How do you correlate a network failure on the Android client with the corresponding backend server log?
    *   **A:** Distributed tracing. The Android RUM SDK injects specific HTTP headers (like `x-datadog-trace-id`) into the outbound OkHttp request. The backend APM reads this header, linking the client trace to the server trace.

### 13. Checkpoint
What is the difference between auto-instrumented network traces and custom code traces?

---

## 9. Structured Analytics & Contract Validation

### 1. What is it?
Structured Analytics means defining user behavior events (like clicks, screen views, conversions) using strongly typed classes instead of arbitrary strings. Contract Validation ensures these events meet the required schema before they are sent to tools like Mixpanel, Amplitude, or Google Analytics.

### 2. Why does it exist?
"Stringly-typed" analytics (`logEvent("btn_clk", mapOf("scrn" to "home"))`) are a nightmare. Typos silently ruin data dashboards. Marketing/Data Science relies on specific parameter names. Strongly typed analytics enforce the contract at compile-time.

### 3. Mental model
Think of string-based logging like using an unstructured NoSQL document. Structured analytics is like defining a strict SQL Schema or Protobuf message. You enforce the shape of the data before it leaves the app.

### 4. How it works
You create an abstract `AnalyticsEvent` interface. Specific events implement this interface. An `AnalyticsTracker` facade handles the actual dispatching, mapping the typed objects down to the string-based SDKs (like FirebaseAnalytics).

### 5. Code Example
```kotlin
// 1. The structured event contract
sealed class AppEvent(val name: String, val params: Map<String, Any> = emptyMap()) {
    class ScreenView(screenName: String) : AppEvent("screen_view", mapOf("screen_name" to screenName))
    class ExpenseCreated(amount: Double, category: String) : 
        AppEvent("expense_created", mapOf("amount" to amount, "category" to category))
}

// 2. The facade
interface AnalyticsTracker {
    fun track(event: AppEvent)
}

// 3. The implementation (wraps the 3rd party SDK)
class FirebaseAnalyticsTracker(private val firebase: FirebaseAnalytics) : AnalyticsTracker {
    override fun track(event: AppEvent) {
        val bundle = Bundle().apply {
            event.params.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Double -> putDouble(key, value)
                    // etc...
                }
            }
        }
        firebase.logEvent(event.name, bundle)
    }
}

// Usage
tracker.track(AppEvent.ExpenseCreated(50.0, "Food"))
```

### 6. Production Usage
Large companies often generate these Kotlin sealed classes automatically from a centralized JSON schema or Protobuf definition maintained by the Data Science team.

### 7. Common Mistakes
**The QA Trap:** Emitting Personally Identifiable Information (PII) like email addresses or plain-text passwords in analytics parameters. This violates GDPR and app store policies.
**The Fix:** Write unit tests that introspect your analytics events to assert no keys containing "email", "password", or "token" are emitted.

### 8. Debugging
Use the Firebase DebugView in Android Studio or logcat interceptors to verify exactly what events and parameters are being dispatched in real-time.

### 9. Testing
Because we created a facade (`AnalyticsTracker`), we can inject a `FakeAnalyticsTracker` in our unit tests and assert that business logic triggers the correct events.
```kotlin
@Test
fun `creating expense tracks ExpenseCreated event`() {
    val fakeTracker = FakeAnalyticsTracker()
    val viewModel = ExpenseViewModel(fakeTracker)
    
    viewModel.saveExpense(50.0, "Food")
    
    val event = fakeTracker.events.last() as AppEvent.ExpenseCreated
    assertEquals(50.0, event.params["amount"])
}
```

### 10. Exercise
Refactor a legacy ViewModel that calls `FirebaseAnalytics.getInstance().logEvent(...)` directly to use a strongly typed `AnalyticsTracker` injected via Hilt.

### 11. Deliberate Failure
**The Broken Code:**
```kotlin
// Typo in parameter name breaks the downstream dashboard
firebase.logEvent("purchase", bundleOf("amout" to 50.0)) 
```
**The Fix:** Use a sealed class `AppEvent.Purchase(val amount: Double)` where the key is safely hardcoded inside the class.

### 12. Interview Questions
*   **Q:** Why should you wrap 3rd-party SDKs like Firebase Analytics in your own interface (Facade pattern)?
    *   **A:** 1) Testability: You can mock/fake the interface in unit tests. 2) Flexibility: If the company decides to switch from Firebase to Amplitude, you only change the implementation class, not the hundreds of call sites in your ViewModels. 3) Type Safety: You can enforce structured contracts.

### 13. Checkpoint
How does using a Facade and sealed classes prevent analytics typos?

---

## 10. CI/CD Quality Gates & Release Health Monitoring

### 1. What is it?
CI/CD Quality Gates are automated checks (lint, tests, build) that prevent bad code from merging. Release Health Monitoring is the process of observing app stability (crashes, ANRs) during a staged rollout on the Google Play Store to decide whether to halt or continue the release.

### 2. Why does it exist?
Manual QA cannot catch every regression. Humans forget to run formatting checks. Releasing an app to 100% of users immediately is dangerous; a critical crash could break the app for everyone before you can push an update.

### 3. Mental model
CI/CD is the automated bouncer at the club (your `main` branch). Release Health Monitoring is the canary in the coal mine: you release to 1% of users, wait, observe, and if the canary dies (crashes spike), you abort.

### 4. How it works
- **CI Pipeline (GitHub Actions / Bitrise):** On every Pull Request, a server runs `./gradlew lint detekt ktlint testDebugUnitTest`. If any task fails, the PR cannot be merged.
- **Staged Rollout:** In Google Play Console, you release the app to 1%, then 10%, then 50% of users over several days.
- **Health Metrics:** You monitor the "Crash-Free User" rate and the "ANR rate" in the Play Console during the rollout.

### 5. Code Example (GitHub Actions CI Workflow)
```yaml
name: Android CI
on: [pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Run Detekt (Static Analysis)
      run: ./gradlew detekt
      
    - name: Run Unit Tests
      run: ./gradlew testDebugUnitTest
      
    - name: Assemble Debug APK
      run: ./gradlew assembleDebug
```

### 6. Production Usage
Google Play has strict vitals thresholds. If your app exceeds a 1.09% crash rate or a 0.47% ANR rate, Google will penalize your app's search ranking in the Play Store. Release managers actively monitor these numbers during staged rollouts.

### 7. Common Mistakes
**The QA Trap:** Running *all* UI tests (Espresso) on every PR. They are slow and flaky, frustrating developers.
**The Fix:** Run unit tests and static analysis on PRs. Run heavy/flaky UI tests nightly or on merged commits only.

### 8. Debugging
If a PR fails the CI check, developers click the CI logs, find the exact failing task (e.g., `> Task :app:detekt FAILED`), read the generated HTML/TXT report, fix the issue locally, and push a new commit.

### 9. Testing
You test your CI/CD pipeline by intentionally creating a PR that breaks a lint rule or fails a unit test to ensure the system correctly blocks the merge.

### 10. Exercise
Add a basic GitHub Actions `.yaml` file to your Expense Tracker repository that builds the project and runs `ktlintCheck` and `testDebugUnitTest` on every PR.

### 11. Deliberate Failure
**The Broken Code:**
A PR introduces a commit that violates a `detekt` rule (e.g., a method is 150 lines long).
**The Fix:** The CI pipeline catches this and fails. The developer must refactor the method into smaller pieces to pass the gate.

### 12. Interview Questions
*   **Q:** What is the difference between `lint`, `ktlint`, and `detekt`?
    *   **A:** `ktlint` enforces Kotlin formatting/style rules (spacing, indents). `detekt` analyzes Kotlin code smells (complexity, magic numbers). Android `lint` checks for Android-specific bugs (missing XML translations, using APIs newer than `minSdkVersion`).
*   **Q:** What is a staged rollout and why is it standard practice?
    *   **A:** Releasing an update incrementally (e.g., 5% -> 20% -> 100%). It limits the blast radius of critical bugs that escaped QA, allowing you to halt the release and fix the issue before it impacts the entire user base.

### 13. Checkpoint
Name three automated checks that should run on a Pull Request before it is allowed to merge.

---

## Phase 12 Project — Expense Tracker Quality & Performance Suite

**Goal:** Implement a comprehensive quality, profiling, and observability infrastructure for the Expense Tracker.

**Requirements:**
1. **Static Analysis & Custom Rules:**
   - Configure detekt and ktlint.
   - Write a custom detekt rule `NoHardcodedDispatchers` that fails the build if `Dispatchers.IO` or `Dispatchers.Default` is referenced directly instead of using an injected `DispatcherProvider`.
2. **Leak Detection & Debugging:**
   - Integrate LeakCanary.
   - Deliberately introduce an Activity leak (e.g. passing Activity to a Singleton listener), observe the LeakCanary notification and heap dump, and refactor the code to fix the leak.
3. **Startup & Baseline Profiles:**
   - Setup `androidx.benchmark:benchmark-macro` module and write a Baseline Profile generator targeting app startup and scrolling the transactions `LazyColumn`.
4. **Telemetry & Crashlytics Facade:**
   - Build a type-safe `AnalyticsTracker` with strongly typed events: `ExpenseCreatedEvent(category, amount)`, `ScreenViewedEvent(screenName)`.
   - Unit test the analytics facade to ensure no PII (e.g., passwords, emails) is emitted in telemetry parameters.

---

## Phase 12 Checkpoint

Answer without looking:
1. What are the 4 primary production health signals you would monitor on the Google Play Console / Datadog immediately following a release, and at what thresholds would you halt rollout?
2. How does LeakCanary detect that an Activity instance has been leaked without requiring you to write custom memory assertion tests?
3. What is a Baseline Profile, and how does ahead-of-time (AOT) compilation reduce cold start latency compared to Just-In-Time (JIT) compilation?
4. What causes an ANR in an Android application, and how does `StrictMode` help catch potential ANRs during development?
5. Why is logging stringly-typed analytics events (e.g. `tracker.log("click", "btn_1")`) an antipattern, and how do strongly-typed event contracts fix it?

---

## Complete APM / QA Performance Testing → Android Production Observability Translation Table

| QA / Backend APM Concept (Dynatrace / JMeter / New Relic) | Android Production Observability Equivalent | Notes |
|---|---|---|
| APM Server Traces (Distributed Tracing) | Datadog RUM / Firebase Performance Custom Traces | End-to-end client latency tracking |
| Server CPU / Memory utilization graphs | Android Studio Profiler (CPU Flame Chart, Heap Dump) | On-device hardware resource profiling |
| JMeter Load Testing Response Times | Macrobenchmark / Microbenchmark | Automated startup & UI frame rate testing |
| Web Vitals (LCP, FID, CLS) | Android Vitals (Cold Start TTID, Frozen Frames, ANRs) | Google Play Store ranking metrics |
| Sentry Server Exception Logging | Firebase Crashlytics with De-obfuscation Mapping | Stack trace symbolication via R8 mappings |
| Synthetic Monitoring (Selenium cron check) | Nightly Firebase Test Lab instrumentation runs | Automated regression verification |
| SonarQube Code Quality Gates | detekt + ktlint + Android Lint on PR CI | Automated static analysis build failure gates |
