# PHASE 2 — COROUTINES & FLOW (Weeks 3–4)

**Objective:** The single highest-leverage phase. Almost every modern Android bug you will debug is a coroutine, lifecycle, or flow-collection bug.
**Why this phase matters:** Coroutines replace Java's threads, RxJava, AsyncTask, and callbacks with sequential-looking code that is actually asynchronous. Flow replaces RxJava's Observable streams. Understanding these deeply is what separates a junior Android developer from a production-ready one.
**Prerequisites:** Phase 1 Kotlin complete. Understanding of lambdas, sealed classes, extension functions.
**Project deliverable:** Expense Tracker v3 — Coroutines + Flow wired into existing screens.
**Concepts covered:** 19 total (10 coroutine + 9 Flow), each with the full 13-step teaching sequence.

---

## Concept 1: `suspend` — The State Machine

### 1. What is it
A `suspend` function is a function that can be paused and resumed at a later time. It does not block the thread it is running on.

### 2. Why does it exist
In Java, making network or database calls blocks the current thread until the response arrives. In Android, blocking the Main (UI) thread causes an ANR (Application Not Responding). `suspend` solves this by pausing the function, freeing up the thread to do other work, and resuming when the result is ready—without nested callbacks.

### 3. Mental model
Imagine a chef (thread) making a complex meal (function). In Java blocking threading, the chef puts bread in the toaster and just stands there watching it for 2 minutes (thread blocked). In Kotlin Coroutines, the chef puts bread in the toaster, sets a timer (`suspend`), and goes to chop vegetables (frees the thread). When the toaster pops, the chef resumes handling the toast.

### 4. How it works
Under the hood, the Kotlin compiler transforms a `suspend` function into a state machine using Continuation-Passing Style (CPS). It adds a hidden `Continuation` parameter to the function. When the function suspends, it saves its state (local variables, where it paused). When resumed, it restores the state and jumps to the next label.

### 5. Code
```kotlin
// Represents an expense fetched from network
data class Expense(val id: String, val amount: Double)

class ExpenseRepository(private val api: ExpenseApi) {
    // Looks sequential, but it's asynchronous
    suspend fun fetchLatestExpense(userId: String): Expense {
        // Pauses here until network returns, thread is free!
        val response = api.getExpense(userId) 
        return Expense(response.id, response.amount)
    }
}
```

### 6. Production usage
Every database call (Room DAO), Network call (Retrofit), or SharedPreferences read/write (DataStore) in a modern Android app is a `suspend` function.

### 7. Common mistakes
❌ **Calling suspend from non-suspend context:**
```kotlin
fun loadData() {
    // Compiler Error: Suspend function 'fetchLatestExpense' should be called only from a coroutine or another suspend function
    val expense = repository.fetchLatestExpense("u1") 
}
```

✅ **The right way:** (Using scopes, covered in Concept 3)

### 8. Debugging
If a `suspend` function seems to "hang" forever, the underlying asynchronous API might not be resuming the `Continuation` (e.g., a forgotten callback). In Android Studio, the "Coroutines" debugger tab shows exactly where each coroutine is suspended.

### 9. Testing
Use `runTest { ... }` from `kotlinx.coroutines.test`. It runs coroutines in a controlled environment, immediately skipping delays.
```kotlin
@Test
fun testFetch() = runTest {
    val result = repository.fetchLatestExpense("u1")
    assertEquals(100.0, result.amount)
}
```

### 10. Exercise
Convert a hypothetical Java `Callback` based function (`fetchData(Callback callback)`) into a `suspend` function using `suspendCoroutine`.

### 11. Deliberate failure
Write a `suspend` function that just calls `Thread.sleep(5000)`. Notice that it *still* blocks the thread, proving `suspend` alone doesn't change threads.

### 12. Interview questions
- What does the `suspend` keyword actually do at compile time?
- Does a `suspend` function automatically run on a background thread? (A: No).

### 13. Checkpoint
Can you explain the difference between blocking a thread and suspending a coroutine?

---

## Concept 2: Main-Safety

### 1. What is it
Main-safety is a design contract: any `suspend` function should be safe to call from the Main (UI) thread, regardless of what heavy lifting it does internally.

### 2. Why does it exist
In Java/Android, you constantly juggle threads: "Am I on the background thread for DB? Did I switch to Main to update UI?" Main-safety shifts the burden of thread-switching to the repository/domain layer, leaving the UI layer clean and sequential.

### 3. Mental model
A self-contained washing machine. You can plug it into any outlet in your house (call from any thread), and it knows internally how to draw the right amount of power and water (switch to the right dispatcher) without blowing the fuses.

### 4. How it works
A `suspend` function achieves main-safety by using `withContext(Dispatchers.IO)` or similar internally to shift its own execution off the main thread before doing heavy work.

### 5. Code
```kotlin
class ExpenseRepository(private val db: ExpenseDatabase) {
    // It's a suspend function, but is it main-safe?
    // YES, because we internally use withContext to shift to IO.
    suspend fun saveExpense(expense: Expense) {
        withContext(Dispatchers.IO) {
            db.expenseDao().insert(expense) // heavy DB write
        }
    }
}

// In ViewModel (Main thread):
suspend fun onSaveClicked() {
    // Safe to call directly! The repository handles the threads.
    repository.saveExpense(expense)
}
```

### 6. Production usage
Architecturally, ViewModels should never specify `Dispatchers.IO` when calling UseCases or Repositories. The lower layers must guarantee main-safety. Room and Retrofit already do this automatically!

### 7. Common mistakes
❌ **Leaking thread logic to the caller:**
```kotlin
class ViewModel {
    fun onSave() {
        // UI layer has to know about threading. Bad!
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveExpenseBlocking(expense)
        }
    }
}
```

✅ **The right way:**
```kotlin
class ViewModel {
    fun onSave() {
        viewModelScope.launch { // Default is Main
            repository.saveExpense(expense) // Main-safe!
        }
    }
}
```

### 8. Debugging
Enable StrictMode in Android. If you get `NetworkOnMainThreadException` or `BlockGuard` warnings, you have a `suspend` function that violates main-safety.

### 9. Testing
Because main-safe functions dictate their own dispatchers, you must inject dispatchers (see Concept 7) to replace `Dispatchers.IO` with a test dispatcher during unit tests.

### 10. Exercise
Take a blocking file-read operation, wrap it in a `suspend` function, and make it main-safe using `withContext(Dispatchers.IO)`.

### 11. Deliberate failure
Create a `suspend fun parseLargeJson()` that uses `Gson` synchronously. Call it from a `viewModelScope.launch { }`. Watch the app drop frames (stutter) while parsing.

### 12. Interview questions
- Who is responsible for switching threads: the caller (ViewModel) or the callee (Repository)?
- Why do Room and Retrofit `suspend` functions not need `withContext(Dispatchers.IO)`?

### 13. Checkpoint
If a function is marked `suspend`, is it automatically main-safe?

---

## Concept 3: `CoroutineScope`, `Job`, `SupervisorJob`, `CoroutineContext`

### 1. What is it
- **CoroutineContext**: A map of elements (like a `Job`, `Dispatcher`, `Name`) defining coroutine behavior.
- **Job**: A handle to a coroutine's lifecycle (active, cancelled, completed).
- **SupervisorJob**: A special Job where if one child fails, it doesn't cancel its siblings.
- **CoroutineScope**: A wrapper around a `CoroutineContext`. It provides the lifecycle bounds for launching coroutines.

### 2. Why does it exist
In Java, you fire a Thread and it runs forever, easily causing memory leaks when the user leaves a screen. Coroutines MUST be launched in a `CoroutineScope`. When the scope is cancelled, all coroutines in it are cancelled, preventing memory leaks automatically.

### 3. Mental model
`CoroutineContext` is a configuration file. `Job` is an employee badge (tracks status). `CoroutineScope` is the company department. When the department (`Scope`) shuts down, all employees (`Jobs`) are fired automatically. A `SupervisorJob` means if one employee burns the kitchen down, the others keep working; a normal `Job` means everyone is fired.

### 4. How it works
When you call `scope.launch {}`, it creates a new `Job` that is a child of the scope's `Job`. The new coroutine inherits the `CoroutineContext` from the scope, overriding elements if specified.

### 5. Code
```kotlin
// A typical custom scope (in Android, usually handled by ViewModel)
val myJob = SupervisorJob()
val myScope = CoroutineScope(Dispatchers.Main + myJob)

fun startSync() {
    val jobHandle = myScope.launch {
        repository.syncExpenses()
    }
    
    // You can check status
    println(jobHandle.isActive) 
}

fun cleanup() {
    // Cancels everything launched in this scope
    myScope.cancel() 
}
```

### 6. Production usage
You rarely create your own `CoroutineScope` in Android. You use the framework-provided `viewModelScope` and `lifecycleScope`, which automatically cancel themselves when the ViewModel/Activity dies.

### 7. Common mistakes
❌ **Using `GlobalScope`:**
```kotlin
// This keeps running even if the user leaves the screen! Memory leak!
GlobalScope.launch { repository.heavyWork() }
```

✅ **Using scoped coroutines:**
```kotlin
viewModelScope.launch { repository.heavyWork() }
```

### 8. Debugging
If a screen is closed but logcats still show network requests happening, you leaked a coroutine by using `GlobalScope` or creating a custom scope you forgot to `cancel()`.

### 9. Testing
In tests, you often inject a `TestScope` which allows you to manually advance time and verify all jobs completed.

### 10. Exercise
Create a custom `CoroutineScope` with a normal `Job`. Launch two failing coroutines. See them both die. Change to `SupervisorJob` and see the second survive the first's crash.

### 11. Deliberate failure
Create a custom scope but forget to pass a `Job` (e.g., `CoroutineScope(Dispatchers.Main)`). Attempt to cancel it. Notice it creates a new Job under the hood, making lifecycle management tricky if you don't hold a reference.

### 12. Interview questions
- What is the difference between `Job` and `SupervisorJob`?
- Why is `GlobalScope` considered an anti-pattern in Android?

### 13. Checkpoint
Can you explain the relationship between a Scope, a Context, and a Job?

---

## Concept 4: Structured Concurrency

### 1. What is it
Structured concurrency is the principle that coroutines live in a strict hierarchy (parent-child). A parent coroutine cannot complete until all its child coroutines have completed. If a child fails, it notifies the parent, which cancels itself and all other children.

### 2. Why does it exist
In Java threaded code, if you start Thread A, which starts Thread B, and Thread A throws an exception, Thread B keeps running as an orphaned zombie. Structured concurrency guarantees zero orphaned background tasks. You always know exactly what is running.

### 3. Mental model
A military chain of command. If the general (parent) says "retreat" (cancel), all soldiers (children) retreat. If a soldier dies (throws exception), the squad is compromised, and the general aborts the mission for everyone. The general doesn't go home until every soldier is accounted for (completed).

### 4. How it works
Every coroutine builder (`launch`, `async`) adds its new `Job` as a child to the `Job` in the context it was called from. The parent job enters a "completing" state and waits for children before moving to "completed".

### 5. Code
```kotlin
suspend fun processMonthlyReport() = coroutineScope {
    // PARENT COROUTINE
    println("Starting report")
    
    launch { 
        // CHILD 1
        delay(1000)
        println("Fetched income") 
    }
    
    launch { 
        // CHILD 2
        delay(2000)
        println("Fetched expenses") 
    }
    
    // The parent coroutine implicitly waits here!
    // It will NOT return until both 1 and 2 finish.
}
// Calling processMonthlyReport() takes 2 seconds total.
```

### 6. Production usage
When syncing user data, you might launch 5 parallel requests (Children) from a repository (Parent). If the user logs out, you cancel the Parent, and instantly all 5 network requests are aborted, freeing resources.

### 7. Common mistakes
❌ **Breaking the hierarchy:**
```kotlin
suspend fun badSync() = coroutineScope {
    // Breaking hierarchy by passing a NEW Job!
    launch(Job()) { 
        delay(5000)
        println("This is orphaned!") 
    }
    // Parent returns immediately, leaving the child running.
}
```

✅ **Respecting hierarchy:**
```kotlin
suspend fun goodSync() = coroutineScope {
    launch { /* implicit child job */ } 
}
```

### 8. Debugging
If a `suspend` function takes wildly longer to return than expected, check if you accidentally launched a long-running child coroutine inside it. The parent is dutifully waiting for it.

### 9. Testing
Structured concurrency makes testing easy. When you call a `suspend` function in a test, the test is guaranteed not to finish until all inner concurrent work is completely done. No `Thread.sleep()` needed in tests!

### 10. Exercise
Write a function that launches 3 coroutines that delay for 1, 2, and 3 seconds. Measure the total time the parent function takes to execute (it should be ~3 seconds).

### 11. Deliberate failure
In a structured block with 3 children, make the first child `throw Exception("DB Error")`. Observe that the other two children are immediately cancelled and never finish their work.

### 12. Interview questions
- How does structured concurrency prevent memory leaks?
- If a parent coroutine is cancelled, what happens to its children? What if a child is cancelled?

### 13. Checkpoint
Why does a parent `coroutineScope` wait for its children before returning?

---

## Concept 5: `launch` vs `async` vs `withContext`

### 1. What is it
- **`launch`**: Fire-and-forget. Starts a coroutine and returns a `Job` (no result).
- **`async`**: Parallel result. Starts a coroutine and returns a `Deferred<T>` (a promise). You call `.await()` to get the value.
- **`withContext`**: Thread switch + return value. Suspends the *current* coroutine until the block completes, returning its result.

### 2. Why does it exist
Different async needs: Sometimes you just want to trigger a save (`launch`). Sometimes you need to fetch from two APIs at the same time to combine results (`async`). Sometimes you just need to read a file sequentially on an IO thread (`withContext`).

### 3. Mental model
- `launch`: Mailing a letter. You send it, you don't wait for a reply.
- `async`: Ordering pizza while you clean the house. You start the order, do other work, then `.await()` the delivery guy.
- `withContext`: Going to the basement to get a tool. You stop what you are doing upstairs, go down, get the tool, and return upstairs to continue.

### 4. How it works
`launch` and `async` create *new* child coroutines. `withContext` does *not* create a new coroutine; it shifts the existing coroutine to a different context/dispatcher, executes the block, and shifts back.

### 5. Code
```kotlin
class ExpenseViewModel : ViewModel() {

    fun trackClick() {
        // 1. launch: Fire and forget
        viewModelScope.launch { 
            analyticsRepo.logEvent("click") 
        }
    }

    suspend fun loadDashboardData(): DashboardState = coroutineScope {
        // 2. async: Run in parallel
        val incomeDeferred = async { api.getIncome() }
        val expenseDeferred = async { api.getExpenses() }
        
        // Wait for both parallel requests to finish
        val income = incomeDeferred.await()
        val expense = expenseDeferred.await()
        
        DashboardState(income, expense)
    }

    suspend fun saveLocally(data: String) {
        // 3. withContext: Sequential thread shift
        withContext(Dispatchers.IO) {
            file.writeText(data) // Wait for this to finish on IO thread
        }
    }
}
```

### 6. Production usage
`launch` is your main entry point from UI to ViewModels. `async` is heavily used in Repositories/UseCases to aggregate data from multiple microservices concurrently to reduce loading times.

### 7. Common mistakes
❌ **Using async but immediately awaiting (defeats parallelism):**
```kotlin
val income = async { api.getIncome() }.await()
val expense = async { api.getExpenses() }.await()
// This is sequential! It takes twice as long.
```

✅ **The right way:**
```kotlin
val incomeDef = async { api.getIncome() }
val expDef = async { api.getExpenses() }
val income = incomeDef.await()
val expense = expDef.await()
```

### 8. Debugging
If network calls are slow, check if you accidentally sequentialized your `async` calls (as shown in common mistakes).

### 9. Testing
Mock the API calls to have delays. Assert that `loadDashboardData` takes only as long as the *longest* individual call (proving they ran in parallel), not the sum of both.

### 10. Exercise
Write a function that fetches a User profile and User settings from fake APIs. Write it sequentially with `withContext`, then convert it to parallel with `async`, and log the time difference.

### 11. Deliberate failure
Try to return a value from a `launch` block to the outside function. You'll quickly realize `launch` doesn't return data, only a `Job`.

### 12. Interview questions
- Can you explain when to use `withContext` vs `async` followed by `await`?
- Does `withContext` create a new coroutine?

### 13. Checkpoint
If you need to fetch three independent lists from a database and merge them, which builder do you use?

---

## Concept 6: Cancellation is Cooperative (CRITICAL)

### 1. What is it
When a coroutine is cancelled, it doesn't instantly die like a killed process. Instead, cancellation sets a flag (`isActive = false`). The coroutine must actively check this flag (or call a suspending function that checks it) and voluntarily stop itself by throwing a `CancellationException`.

### 2. Why does it exist
If a thread/coroutine is killed instantly while writing to a file or database, you get corrupted data. Cooperative cancellation allows the coroutine to finish its current atomic operation and clean up resources safely.

### 3. Mental model
A polite referee. When the game is over, the referee blows the whistle (cancels). The player who has the ball in mid-air gets to finish their shot, hears the whistle, and then walks off the court. The player isn't vaporized instantly. But if the player is wearing noise-canceling headphones (ignoring the flag), they will keep playing an empty game forever.

### 4. How it works
Built-in `suspend` functions (`delay`, Room DB calls, Retrofit calls) automatically check for cancellation before and after they run. If cancelled, they throw `CancellationException`. If you write a long `while` loop doing CPU work (no `suspend` calls), it will *never* cancel unless you manually check `ensureActive()`.

### 5. Code
```kotlin
suspend fun processLargeList(items: List<Expense>) {
    // Example of CPU-heavy work that ignores cancellation initially
    for (item in items) {
        // GOLDEN RULE: Manually check if cancelled during tight loops!
        ensureActive() // Throws CancellationException if cancelled
        
        expensiveHashCalculation(item)
    }
}

// Cleaning up strictly requires NonCancellable
suspend fun writeWithCleanup() {
    try {
        writeFile()
    } finally {
        // If we were cancelled, we can't call suspend functions here normally!
        // We must shift to NonCancellable context.
        withContext(NonCancellable) {
            closeFileStream() // suspend func
        }
    }
}
```

### 6. Production usage
When scrolling a RecyclerView, you launch image downloads. As views recycle, you cancel the old jobs. If cancellation wasn't cooperative, you'd corrupt bitmaps. But because it is, you must ensure your CPU-heavy image processors check `ensureActive()`.

> [Extension] `ensureActive()` vs `yield()`: both throw `CancellationException` if the coroutine was cancelled, but `yield()` does more — it also *gives up the thread* so other coroutines waiting on the same dispatcher get a chance to run, then resumes. Use `ensureActive()` when you just need a cheap cancellation checkpoint in a tight CPU loop; use `yield()` when you also want to be a "good citizen" and let other coroutines on a limited thread pool (like `Dispatchers.Default`) make progress between chunks of work.

### 7. Common mistakes (The most common production bug)
❌ **Swallowing CancellationException:**
```kotlin
viewModelScope.launch {
    try {
        api.syncData()
    } catch (e: Exception) {
        // DISASTER: We caught CancellationException! 
        // The framework thinks the coroutine succeeded and ignores the cancel request.
        Log.e("Error", e.message)
    }
}
```

✅ **The right way:**
```kotlin
viewModelScope.launch {
    try {
        api.syncData()
    } catch (e: CancellationException) {
        throw e // ALWAYS rethrow CancellationException
    } catch (e: Exception) {
        Log.e("Error", e.message)
    }
}
```

### 8. Debugging
If closing a screen doesn't stop the background work, or if navigating away causes weird UI state updates later, you either swallowed a `CancellationException` or you have a tight CPU loop without `ensureActive()`.

### 9. Testing
Launch a coroutine containing an infinite loop, wait 100ms, then cancel the job. Assert that the job actually reaches the `.isCancelled` state. If you forgot `ensureActive()`, the test will hang forever.

### 10. Exercise
Write a `while(true)` loop that calculates random numbers. Launch it, wait 1 second, and cancel the job. Watch it keep running. Fix it by adding `yield()` or `ensureActive()`.

### 11. Deliberate failure
Write a `try { delay(1000) } catch(e: Exception) {}`. Cancel the job after 10ms. Notice how the catch block eats the cancellation, and any code after the try-catch continues executing!

### 12. Interview questions
- Why is it dangerous to catch generic `Exception` inside a coroutine?
- What does `withContext(NonCancellable)` do and when do you use it?

### 13. Checkpoint
If a coroutine is cancelled while executing `Thread.sleep(5000)`, when will it actually stop?

---

## Concept 7: Dispatchers

### 1. What is it
Dispatchers determine *which thread or thread pool* the coroutine uses for its execution.
- `Main`: UI thread (Android). Fast UI updates only.
- `Main.immediate`: Same as Main, but skips the queue if already on Main.
- `IO`: Thread pool optimized for blocking I/O (Network, DB, Files). Can create many threads.
- `Default`: Thread pool optimized for CPU-intensive work (sorting, JSON parsing). Threads = CPU cores.
- `Unconfined`: Starts on caller thread, but resumes on whatever thread the suspending function used. Rarely used.

### 2. Why does it exist
Android UI must run on the Main thread. Database/Network *must not* run on the Main thread. Dispatchers are the routing mechanism to ensure work happens in the right place.

### 3. Mental model
Dispatchers are like transport vehicles. `Main` is a bicycle courier (can only carry light UI updates, but fast in the city). `IO` is a fleet of delivery trucks (great for waiting at loading docks for network data). `Default` is a bullet train (heavy computational power, limited to the number of tracks/cores).

### 4. How it works
When a coroutine resumes after suspension, the Dispatcher schedules it onto an available thread in its pool.

### 5. Code
```kotlin
class ExpenseRepository(
    private val api: ExpenseApi,
    // INJECT dispatchers! Never hardcode Dispatchers.IO
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO 
) {
    suspend fun processLargeFile(fileBytes: ByteArray) {
        // Shift to CPU-optimized pool for heavy math
        withContext(Dispatchers.Default) {
            val parsed = parseCustomFormat(fileBytes)
            
            // Shift to IO-optimized pool to save
            withContext(ioDispatcher) {
                saveToDisk(parsed)
            }
        }
    }
}
```

### 6. Production usage
JSON serialization (Moshi/Gson) should ideally run on `Dispatchers.Default` (it's CPU bound). File reading/writing runs on `Dispatchers.IO`.

> [Extension] `Dispatchers.IO` is a large shared thread pool (it can grow past the number of CPU cores because most of its threads are blocked waiting on I/O, not computing). That's fine for occasional disk/network calls, but if you have a resource that must only ever be touched by one thread at a time (e.g. a single-writer file handle, or to avoid hammering a resource-constrained third-party SDK), use `Dispatchers.IO.limitedParallelism(1)` (available since kotlinx.coroutines 1.6) to carve out a constrained view of the IO dispatcher instead of inventing your own single-thread executor.

### 7. Common mistakes
❌ **Hardcoding dispatchers:**
```kotlin
suspend fun fetch() = withContext(Dispatchers.IO) { ... }
```

✅ **Injecting dispatchers for testability:**
```kotlin
class MyRepo(private val io: CoroutineDispatcher) {
    suspend fun fetch() = withContext(io) { ... }
}
```

### 8. Debugging
If the UI stutters, you are likely doing `Default` or `IO` work on the `Main` dispatcher. Check the block running on `Main` for heavy list sorting or parsing.

### 9. Testing
Because you injected the dispatcher, in your tests you pass `StandardTestDispatcher()` or `UnconfinedTestDispatcher()`. This forces all test code to run on a single predictable test thread, making async code testable synchronously.

### 10. Exercise
Create an API that simulates a 2-second delay. Call it from a ViewModel using `Dispatchers.Main`. Watch the app crash (NetworkOnMainThread). Fix it by shifting to `Dispatchers.IO`.

### 11. Deliberate failure
Try to run a massive `for` loop (100 million iterations) on `Dispatchers.IO`. Notice it hogs an IO thread. Then run 64 of these concurrently. You'll exhaust the IO pool, blocking actual network calls!

### 12. Interview questions
- What is the difference between `Dispatchers.IO` and `Dispatchers.Default`?
- Why should you inject dispatchers instead of using the singletons directly?

### 13. Checkpoint
If you need to sort a list of 50,000 items, which dispatcher should you use?

---

## Concept 8: Android Scopes (`viewModelScope`, `lifecycleScope`)

### 1. What is it
Android-provided `CoroutineScope` extensions tied to component lifecycles.
- `viewModelScope`: Lives as long as the ViewModel. Cancelled in `onCleared()`.
- `lifecycleScope`: Lives as long as the Activity/Fragment. Cancelled in `onDestroy()`.
- `repeatOnLifecycle`: Runs a coroutine when Activity is started/resumed, and cancels it when stopped/paused.

### 2. Why does it exist
Manually overriding `onDestroy` to call `myScope.cancel()` is tedious and prone to human error. These scopes automate memory management.

### 3. Mental model
Hotel room keycards. `viewModelScope` is a keycard that works for your entire stay, even if you briefly leave the room (rotate screen). `lifecycleScope` is a keycard that stops working the second you step into the hallway (Activity destroyed).

### 4. How it works
They use Android's `LifecycleObserver` under the hood. When the lifecycle owner emits an `ON_DESTROY` event, the observer catches it and calls `cancel()` on the scope's `CoroutineContext`.

### 5. Code
```kotlin
class ExpenseViewModel(private val repo: ExpenseRepository) : ViewModel() {
    fun loadData() {
        // Automatically cancelled if ViewModel is cleared!
        viewModelScope.launch {
            val data = repo.fetchData()
            // update state
        }
    }
}

// In Activity/Fragment:
lifecycleScope.launch {
    // Suspends until Activity is in STARTED state.
    // Cancels the block if Activity drops below STARTED.
    // Restarts the block if Activity comes back to STARTED.
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect { state ->
            // Update UI safely
        }
    }
}
```

### 6. Production usage
100% of ViewModel coroutines start in `viewModelScope`. 100% of UI flow collection starts inside `repeatOnLifecycle`. Never deviate from this.

### 7. Common mistakes
❌ **Using `lifecycleScope.launch` for Flow collection:**
```kotlin
// DANGEROUS: Collects even when app is in background, wasting battery/CPU!
lifecycleScope.launch {
    viewModel.flow.collect { }
}
```

✅ **Using `repeatOnLifecycle`:**
```kotlin
// Safe: pauses collection in background
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.flow.collect { }
    }
}
```

### 8. Debugging
If your app crashes with "Fragment not attached to a context" during a UI update, you likely updated the UI from a coroutine that wasn't scoped properly or didn't use `repeatOnLifecycle`.

### 9. Testing
To test `viewModelScope`, you must swap the Android Main dispatcher with a Test dispatcher using `Dispatchers.setMain(testDispatcher)` before your tests run, because `viewModelScope` uses `Dispatchers.Main` by default.

### 10. Exercise
Create an Activity that launches a `while(true)` logging loop in `lifecycleScope.launch` and another in `repeatOnLifecycle(STARTED)`. Background the app. Observe Logcat to see which one stops and which one keeps spamming.

### 11. Deliberate failure
Launch a 10-second delay inside `viewModelScope`, rotate the device, and log when it finishes. You'll see it survives rotation! Do the same in `lifecycleScope` and rotate. You'll see it gets cancelled.

### 12. Interview questions
- Why do we use `repeatOnLifecycle` instead of just `lifecycleScope.launch`?
- Does `viewModelScope` survive a configuration change (screen rotation)?

### 13. Checkpoint
Where is the correct place to launch a network request that should survive a screen rotation?

---

## Concept 9: `coroutineScope {}` vs `supervisorScope {}` & Timeouts

### 1. What is it
Scoped builders that define boundaries for failure.
- `coroutineScope {}`: If one child fails, it cancels all siblings and throws the exception up.
- `supervisorScope {}`: If one child fails, siblings continue.
- `withTimeout`: Wraps a block and cancels it if it takes too long.

### 2. Why does it exist
When downloading 5 images, if 1 fails, you still want the other 4 (`supervisorScope`). When verifying a bank transaction, if the fraud-check fails, you want to abort the money-transfer immediately (`coroutineScope`).

### 3. Mental model
- `coroutineScope`: A synchronized swimming team. If one person drowns, the routine is ruined, everyone stops.
- `supervisorScope`: A call center. If one agent's phone breaks, the other agents keep taking calls.

### 4. How it works
`supervisorScope` overrides the parent's `Job` context with a `SupervisorJob`, breaking the bidirectional cancellation. Children can be cancelled, but their failure doesn't propagate up to kill the supervisor itself.

### 5. Code
```kotlin
suspend fun fetchDashboard() = supervisorScope {
    // If getting ads fails, we don't care, still show content!
    val adsDeferred = async { api.getAds() }
    val contentDeferred = async { api.getContent() }
    
    val content = try { 
        contentDeferred.await() 
    } catch (e: Exception) { 
        // If content fails, the whole dashboard is useless
        throw e 
    }
    
    val ads = try {
        adsDeferred.await()
    } catch(e: Exception) {
        null // Silently fail ads, supervisorScope keeps content alive
    }
    
    Dashboard(content, ads)
}

suspend fun fetchWithRetry() {
    // Cancels the block if it takes > 5 seconds
    val result = withTimeoutOrNull(5000L) {
        api.flakyCall()
    }
    if (result == null) { /* handle timeout */ }
}
```

### 6. Production usage
`supervisorScope` is essential when hydrating an app on startup where non-critical requests (analytics config, user avatars) shouldn't crash the core data load.

### 7. Common mistakes
❌ **Using `SupervisorJob` in a builder incorrectly:**
```kotlin
// MISTAKE: Passing a Job/SupervisorJob instance directly into launch() does NOT
// turn that one launch into a "supervised child" of the enclosing scope.
// Instead it REPLACES the parent-child link entirely: the new coroutine's Job
// becomes a child of this fresh SupervisorJob() instead of the enclosing scope's Job.
// Net effect: this coroutine is now ORPHANED from structured concurrency, exactly
// like the `launch(Job())` mistake in Concept 4. It will NOT be cancelled when the
// enclosing scope is cancelled (a leak), and if it throws, the exception has no
// parent to report to, so it goes straight to the uncaught exception handler
// (crashing the app) instead of being contained the way supervisorScope intends.
launch(SupervisorJob()) { ... }
```

✅ **The right way:**
```kotlin
launch { 
    supervisorScope { ... } 
}
```

### 8. Debugging
If an entire screen fails to load because one minor image request timed out, you accidentally used `coroutineScope` instead of `supervisorScope`.

### 9. Testing
Write a test that launches two `async` blocks inside `supervisorScope`. Make the first throw an exception. `await()` the second one and assert it returns correctly.

### 10. Exercise
Write a function that downloads 3 files concurrently using `async`. Use `coroutineScope`. Make file 2 throw an error. Observe file 1 and 3 getting cancelled. Switch to `supervisorScope` and observe 1 and 3 completing.

### 11. Deliberate failure
Wrap a slow 10-second `suspend` function in `withTimeout(2000L)`. Forget to catch `TimeoutCancellationException`. Watch your app crash. (This is why `withTimeoutOrNull` is often preferred).

### 12. Interview questions
- How do you prevent one failing parallel request from cancelling another?
- What is the difference between `withTimeout` and `withTimeoutOrNull`?

### 13. Checkpoint
If you launch two child coroutines inside a `coroutineScope`, and child A throws an exception, what happens to child B?

---

## Concept 10: Exception Handling

### 1. What is it
How errors propagate in coroutines. Exceptions thrown in `launch` bubble up to the global uncaught exception handler (crash) unless caught by a `CoroutineExceptionHandler`. Exceptions in `async` are encapsulated in the `Deferred` and thrown when you call `.await()`.

### 2. Why does it exist
Structured concurrency means errors don't just disappear into the void; they follow the tree up to the root. We need specific tools to catch them at the root without wrapping every single line in try/catch.

### 3. Mental model
- `launch` exception: A bomb going off. It blows up the coroutine and destroys the parent scope (crashing the app) unless the parent has a blast shield (`CoroutineExceptionHandler`).
- `async` exception: A bomb in a briefcase. It doesn't blow up immediately. It only blows up when you open the briefcase (call `.await()`).

### 4. How it works
When a child throws an exception (other than `CancellationException`), it cancels itself and passes the exception to its parent. The parent cancels all other children, then cancels itself, passing the exception up. If it reaches the root of the hierarchy without being handled, it crashes.

### 5. Code
```kotlin
// The blast shield for root coroutines
val handler = CoroutineExceptionHandler { _, exception ->
    Log.e("Error", "Caught $exception")
}

class ViewModel : ViewModel() {
    fun loadSafely() {
        // Pass handler to root coroutine
        viewModelScope.launch(handler) {
            // If this throws, app won't crash. Handler catches it.
            api.dangerousCall() 
        }
    }
    
    fun asyncError() {
        viewModelScope.launch {
            val deferred = async { throw Exception("Boom") }
            try {
                // Throws HERE. Try-catch works here.
                deferred.await() 
            } catch (e: Exception) {
                // Handled!
            }
        }
    }
}
```

### 6. Production usage
Most Android apps use a BaseViewModel that injects a `CoroutineExceptionHandler` into the default scope to globally catch network errors and show a generic "Something went wrong" Toast.

### 7. Common mistakes (The second most common production bug)
❌ **Try/Catch outside the launch:**
```kotlin
try {
    // launch returns immediately! Try-catch block ends.
    viewModelScope.launch {
        throw Exception("App Crashes!")
    }
} catch (e: Exception) {
    // NEVER CALLED!
}
```

✅ **The right way:**
```kotlin
viewModelScope.launch {
    try {
        throw Exception("Handled")
    } catch (e: Exception) {
        // CALLED!
    }
}
```

### 8. Debugging
If your app crashes with an exception coming from a Coroutine, check where you placed your try/catch. If it's wrapping the `launch` builder instead of *inside* it, it's doing nothing.

### 9. Testing
Inject a custom `CoroutineExceptionHandler` in your tests. Assert that when the repository throws an exception, the handler intercepts it and updates an error state LiveData/StateFlow.

### 10. Exercise
Create a `launch` block that throws an exception. Try to catch it by putting a try/catch *around* the launch. Watch it crash. Move the try/catch *inside* the launch and watch it succeed.

### 11. Deliberate failure
Use `async` to throw an exception, but *don't* call `.await()`. Observe whether the app crashes. (Hint: In a standard scope, the exception still bubbles up to the parent and crashes the app, even if you never call await!)

### 12. Interview questions
- Why does putting a try/catch block around a `viewModelScope.launch` call not catch exceptions?
- What is `CoroutineExceptionHandler` and where must it be installed to work?

### 13. Checkpoint
If you throw an exception inside an `async` block, where and when is it caught?


---

## 11. Cold `Flow` — nothing runs until collected

### 1. What is it
A `Flow` is a cold asynchronous data stream that emits multiple values sequentially. "Cold" means the code inside the flow builder does not execute until someone subscribes to (collects) the flow.

### 2. Why does it exist
A standard `suspend` function can only return a *single* value asynchronously. To return *multiple* asynchronously computed values over time (e.g., downloading progress, database table updates), we need a stream.

### 3. Mental model
Think of a cold `Flow` like a CD (blueprint) of a movie. The CD itself doesn't play anything. Every time someone presses Play (`collect()`), the movie starts from the very beginning. Two people pressing Play on their own DVD players get two independent showings.

### 4. How it works
When you create a `flow { }`, you define a block of suspendable code. Calling `collect()` on the flow executes that block. If `collect()` is called 5 times, the block executes 5 separate times from scratch.

### 5. Code
```kotlin
fun getSyncProgress(): Flow<Int> = flow {
    println("Sync started...")
    for (i in 1..3) {
        delay(1000) // simulate work
        emit(i * 33)
    }
    emit(100)
}

suspend fun runSync() {
    // The block inside flow {} only runs now
    getSyncProgress().collect { progress ->
        println("Progress: $progress%")
    }
}
```

### 6. Production usage
Room DAO queries returning `Flow<List<Transaction>>`. Room creates a cold flow that executes the SQL query when collected, and then re-runs the query whenever the underlying tables change.

### 7. Common mistakes
❌ **Assuming a cold flow shares its state:**
```kotlin
val expensiveFlow = flow { emit(heavyApiCall()) }
// Two collectors will trigger the heavyApiCall TWICE!
expensiveFlow.collect { ... } 
expensiveFlow.collect { ... }
```
✅ **Right way:** Use `shareIn` or `stateIn` to make it hot (see Concept 16).

### 8. Debugging
Use the `onEach` operator to peek at values in the stream:
```kotlin
myFlow
    .onEach { Log.d("Flow", "Upstream emitted: $it") }
    .collect { ... }
```

### 9. Testing
Use the **Turbine** library (the standard for Flow testing).
```kotlin
@Test
fun testProgress() = runTest {
    getSyncProgress().test {
        assertEquals(33, awaitItem())
        assertEquals(66, awaitItem())
        // ...
        cancelAndIgnoreRemainingEvents()
    }
}
```

### 10. Exercise
Write a `countdownFlow(start: Int)` that emits numbers down to 0, delaying 1 second between each. Collect it twice and observe that the countdown happens sequentially twice.

### 11. Deliberate failure
Put a `try/catch` around the *creation* of a flow that throws an exception inside its builder. Observe that the exception is NOT caught because the flow block doesn't run upon creation. Move the try/catch around `collect()` to fix it.

### 12. Interview questions
*Q: What is the difference between a `suspend` function returning a `List` and returning a `Flow`?*
A: A suspend function returning a list waits until the entire list is built and returns it all at once. A Flow can emit elements one by one over time, allowing the consumer to process them immediately.

### 13. Checkpoint
Why does collecting a database `Flow` twice result in two separate database queries being executed?

---

## 12. Hot `StateFlow` and `SharedFlow`

### 1. What is it
Hot flows are active regardless of whether they have collectors.
- `StateFlow`: Holds exactly one *current state*. Always replays the latest value to new collectors. Conceptually identical to a state holder. Conflates values (drops intermediate rapid updates).
- `SharedFlow`: Broadcasts events to all current subscribers. Can have a replay cache, but usually used for one-off events. Does not conflate by default.

### 2. Why does it exist
Cold flows are not suitable for representing UI state or application-wide event buses because they re-run for every collector. You need a "hot" source of truth that multiple observers can share.

### 3. Mental model
- **Cold Flow:** A CD you play from the start.
- **StateFlow:** A movie theater screen. If you walk in late, you just see exactly what is on the screen *right now*.
- **SharedFlow:** A live concert announcer. If you aren't listening when they shout "Free t-shirts!", you miss it.

### 4. How it works
A `MutableStateFlow` takes an initial value. Updating its `.value` property automatically emits to all active collectors. If the new value is `.equals()` to the old value, it is ignored (distinct until changed).

### 5. Code
```kotlin
// In a Repository or ViewModel
private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

fun addTransaction(tx: Transaction) {
    // StateFlow exposes a .value or .update (thread-safe)
    _transactions.update { currentList ->
        currentList + tx
    }
}

private val _navigationEvents = MutableSharedFlow<NavEvent>()
val navigationEvents = _navigationEvents.asSharedFlow()

suspend fun triggerNavigation() {
    _navigationEvents.emit(NavEvent.GoToDetails)
}
```

### 6. Production usage
- `StateFlow` is the absolute standard for exposing UI State from a ViewModel (replacing `LiveData`).
- `MutableStateFlow` is used for simple in-memory reactive caches in repositories.

### 7. Common mistakes
❌ **Using SharedFlow for UI State:** New subscribers (like a UI returning from the background) won't get the current state unless you configure a replay cache, and even then, it lacks the `.value` property.
✅ **Right way:** Use `StateFlow` for state, `SharedFlow` for events.

### 8. Debugging
If a `StateFlow` isn't triggering a UI update, check if you mutated the *content* of an existing object rather than creating a new one. `StateFlow` uses `.equals()`; mutating a data class without `copy()` won't trigger an emission.

### 9. Testing
```kotlin
@Test
fun testStateFlow() = runTest {
    val stateFlow = MutableStateFlow(0)
    stateFlow.test {
        assertEquals(0, awaitItem()) // StateFlow ALWAYS emits initial value immediately
        stateFlow.value = 1
        assertEquals(1, awaitItem())
    }
}
```

### 10. Exercise
Create a `MutableStateFlow` holding a data class `User(val name: String)`. Collect it in a coroutine. Try updating `.value` with the exact same object, then with a new object using `copy()`. Observe when it emits.

### 11. Deliberate failure
Try to assign `.value` to a `MutableSharedFlow`. Notice it doesn't compile—SharedFlow doesn't have a concept of "current value".

### 12. Interview questions
*Q: How does `StateFlow` differ from `LiveData`?*
A: StateFlow requires an initial value, isn't inherently lifecycle-aware on its own (needs explicit lifecycle collection in the UI), and operates within the Coroutine scope/dispatcher system.

### 13. Checkpoint
If you emit `A`, `B`, `C` into a `MutableStateFlow` very rapidly, and the collector is slow, what does the collector receive? (Answer: `A`, then `C`. `B` gets conflated/dropped).

---

## 13. Flow builders: `flow {}`, `flowOf`, `asFlow`, `channelFlow`, `callbackFlow`

### 1. What is it
These are functions used to create `Flow` instances from different types of sources.

### 2. Why does it exist
Not everything is a simple sequential loop. Sometimes you have existing lists, single values, or callback-based legacy APIs that need to be bridged into the Flow world.

### 3. Mental model
- `flow { }`: Hand-crafting an item.
- `flowOf(1, 2, 3)` / `list.asFlow()`: Dumping an existing box of items onto a conveyor belt.
- `callbackFlow`: Setting up a radio receiver that converts incoming radio signals (callbacks) into items on the conveyor belt.

### 4. How it works
- `flow { ... }`: The basic builder. You call `emit()`. Cannot emit from concurrent coroutines.
- `channelFlow { ... }`: Allows concurrent emissions from child coroutines using `send()`.
- `callbackFlow { ... }`: A specialized `channelFlow` that forces you to use `awaitClose { ... }` to clean up the callback when the flow is cancelled.

### 5. Code
```kotlin
// 1. callbackFlow (Wrapping an existing callback API)
fun locationUpdates(api: LocationApi): Flow<Location> = callbackFlow {
    val listener = object : LocationListener {
        override fun onLocation(loc: Location) {
            trySend(loc) // Use trySend inside non-suspend callbacks
        }
    }
    api.register(listener)
    
    // Suspend until the flow is cancelled, then clean up
    awaitClose { api.unregister(listener) }
}
```

### 6. Production usage
`callbackFlow` is heavily used to wrap Firebase Realtime Database listeners, SensorManager listeners, or 3rd-party SDKs that use traditional Java callbacks into Kotlin Flows.

### 7. Common mistakes
❌ **Forgetting `awaitClose` in `callbackFlow`:**
```kotlin
fun badFlow() = callbackFlow {
    api.register(listener)
    // CRASH: callbackFlow block finishes immediately without awaitClose
}
```
✅ **Right way:** Always end a `callbackFlow` with `awaitClose { /* unregister */ }`.

### 8. Debugging
If a `callbackFlow` leaks memory or keeps running, verify the `awaitClose` block is actually unregistering the listener.

### 9. Testing
Testing `callbackFlow` involves triggering the underlying callback mechanism and using Turbine to `awaitItem()`.

### 10. Exercise
Take a standard Android `TextWatcher` on an `EditText` and wrap it in a `callbackFlow<String>` that emits the text whenever it changes.

### 11. Deliberate failure
Try calling `emit()` from a `launch` block *inside* a standard `flow { }`. It will throw a `IllegalStateException: Flow invariant is violated`. You must use `channelFlow` for concurrent emissions.

### 12. Interview questions
*Q: When would you use `channelFlow` instead of `flow`?*
A: When you need to emit values from multiple concurrent coroutines (e.g., launching multiple child `async` tasks and emitting their results as they complete).

### 13. Checkpoint
Why is `trySend()` used instead of `emit()` inside the listener of a `callbackFlow`?

---

## 14. Operators: map, filter, combine, flatMapLatest, catch

### 1. What is it
Operators are functions that transform, combine, or react to the items emitted by a flow *before* they reach the final collector.

### 2. Why does it exist
To build complex reactive pipelines declaratively (like RxJava), avoiding massive blocks of imperative logic in the collector.

### 3. Mental model
Think of an assembly line. The flow builder is the raw materials entering. Operators are machines along the line (`filter` removes defects, `map` paints them, `combine` merges two belts). The collector is the shipping truck at the end.

### 4. How it works
Most operators return a *new* Flow. They apply their transformation sequentially.
- `map`: Transforms `A` to `B`.
- `filter`: Keeps items matching a predicate.
- `combine`: Merges latest values of Flow A and Flow B.
- `flatMapLatest`: When a new item arrives, cancels the previous nested flow and starts a new one.

### 5. Code
```kotlin
val searchQuery = MutableStateFlow("")
val transactions = repository.getAllFlow()

// Combine emits a new list whenever EITHER searchQuery or transactions changes
val filteredUiState: Flow<List<Transaction>> = combine(searchQuery, transactions) { query, txs ->
    if (query.isBlank()) txs
    else txs.filter { it.merchant.contains(query, ignoreCase = true) }
}.catch { e ->
    emit(emptyList()) // Handle errors gracefully
}
```

### 6. Production usage
`combine` is heavily used in ViewModels to merge multiple data sources (e.g., User Flow + Preferences Flow + Data Flow) into a single `UiState` flow. `flatMapLatest` is used for search queries (typing "A", then "B" cancels the DB search for "A").

### 7. Common mistakes
❌ **Using `zip` instead of `combine` for UI state:**
`zip` waits for a *pair* of new items (one from each flow). If Flow A emits twice, it waits for Flow B to emit before zipping.
✅ **Right way:** `combine` uses the *latest* available value from all flows whenever *any* flow emits.

### 8. Debugging
Chain the `onEach { Log.d(...) }` operator between other operators to see exactly how the data is mutating at each step of the pipeline.

### 9. Testing
```kotlin
@Test
fun testCombine() = runTest {
    // Set up flows, advance time, and use Turbine on the resulting combined flow
}
```

### 10. Exercise
Create a flow of ints (1..10). Use operators to: filter even numbers, map them to strings, and take only the first 2.

### 11. Deliberate failure
Put a `delay(5000)` inside a `map` operator. Notice how it delays the entire collection process because `map` is sequential. Then swap to `flatMapLatest` and see how new rapid emissions cancel the delayed ones.

### 12. Interview questions
*Q: Explain `flatMapLatest` and a common use case.*
A: It transforms an item into a Flow, and if a new item arrives before the previous Flow finishes, it cancels the previous one. Classic use case: Search autocomplete.

### 13. Checkpoint
If you have a `flow1` emitting `1, 2` and a `flow2` emitting `A, B`, what does `combine` do? What does `zip` do?

---

## 15. Context preservation — `flowOn` vs `withContext`

### 1. What is it
A strict rule in Kotlin Flows: A flow must not leak its internal execution context (dispatcher) to downstream collectors. You must use `flowOn` to change threads, NOT `withContext`.

### 2. Why does it exist
To make flows completely predictable. The collector (e.g., the UI thread) should have absolute guarantee that the collection block runs on the dispatcher it was called from, without upstream flows hijacking it.

### 3. Mental model
Imagine a train (the Flow). The collector sets the destination station (the Dispatcher). The train company guarantees the cargo will arrive at that exact station. If the cargo loaders (the Flow builder) suddenly switch tracks in the middle using `withContext`, the train derails. `flowOn` safely handles the logistics of switching tracks upstream.

### 4. How it works
`flowOn(Dispatcher)` affects everything *upstream* (above it) in the chain, up to the next `flowOn`. It creates a channel between the dispatchers to safely transfer emissions. `withContext` inside a `flow { }` builder violates context preservation and crashes.

### 5. Code
```kotlin
fun getDiskData(): Flow<String> = flow {
    // DO NOT DO THIS:
    // withContext(Dispatchers.IO) { emit(readDisk()) } // CRASHES!
    
    val data = readDisk()
    emit(data) 
}.flowOn(Dispatchers.IO) // Moves the flow builder and upstream to IO

// Usage
lifecycleScope.launch(Dispatchers.Main) {
    // This collect block runs on Main
    getDiskData()
        .map { it.uppercase() } // Runs on IO (affected by flowOn)
        .flowOn(Dispatchers.IO) 
        .collect { text ->
            textView.text = text // Safely on Main
        }
}
```

### 6. Production usage
Applying `flowOn(Dispatchers.IO)` at the end of a Repository function that returns a flow of Database/Network data, ensuring the caller doesn't have to worry about blocking the main thread.

### 7. Common mistakes
❌ **Using `withContext` to wrap an `emit()` call:** throws `IllegalStateException: Flow invariant is violated`.
✅ **Right way:** Append `.flowOn(Dispatchers.IO)` to the Flow chain.

### 8. Debugging
If you get `Flow invariant is violated`, look inside your `flow { }` builders or custom operators for `withContext`, `launch`, or any other mechanism that changes the coroutine context.

### 9. Testing
Inject a `TestDispatcher` into the class that provides the `flowOn` context, so you can control virtual time during tests.

### 10. Exercise
Write a flow that emits 3 strings. Print `Thread.currentThread().name` inside the flow builder, inside a `map` operator, and inside the `collect` block. Insert `flowOn(Dispatchers.IO)` and observe how the thread names change.

### 11. Deliberate failure
Write `flow { withContext(Dispatchers.IO) { emit(1) } }.collect()`. Watch it crash with the invariant violation exception.

### 12. Interview questions
*Q: Why does `withContext` crash inside a `flow { }` builder?*
A: Because of Context Preservation. A Flow must execute in the context of its collector. Switching contexts inside the builder breaks this guarantee; `flowOn` is required because it handles context switching across a channel boundary safely.

### 13. Checkpoint
If you chain `flowOn(IO).map { ... }.flowOn(Default)`, which dispatcher does the `flow {}` builder run on?

---

## 16. `stateIn` / `shareIn` and start strategies

### 1. What is it
Operators that convert a cold `Flow` into a hot `StateFlow` or `SharedFlow`. 
- `WhileSubscribed(5_000)`: Keeps the flow active as long as there are subscribers, and waits 5 seconds after the last subscriber leaves before cancelling upstream.

### 2. Why does it exist
Cold flows re-execute their expensive work per subscriber. If you have a Room database flow and rotate the screen, the UI re-collects it. You want to cache that state in the ViewModel so the new UI gets the data instantly without re-querying the DB.

### 3. Mental model
It's like hooking a water tank (`StateFlow`) to a garden hose (`Cold Flow`). The hose only runs to fill the tank. Everyone drinks from the tank. The start strategy dictates when the hose turns on and off.

### 4. How it works
`stateIn` takes a CoroutineScope (usually `viewModelScope`), a `SharingStarted` strategy, and an initial value. 

### 5. Code
```kotlin
class ExpenseViewModel(repo: TransactionRepository) : ViewModel() {
    
    val uiState: StateFlow<UiState> = repo.getTransactionsFlow()
        .map { UiState.Success(it) }
        .catch { emit(UiState.Error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading
        )
}
```

### 6. Production usage
The defacto standard for mapping Domain/Repository Flows into UI State flows in an Android ViewModel.

### 7. Common mistakes
❌ **Using `Eagerly` or `Lazily` for UI State:** If the user backgrounds the app, the Flow keeps collecting upstream (e.g., location updates), wasting battery.
✅ **Right way:** `WhileSubscribed(5_000)`. The 5-second buffer prevents the flow from restarting during a configuration change (screen rotation takes ~1-2 seconds).

### 8. Debugging
If data goes missing after a screen rotation, ensure you aren't using `WhileSubscribed(0)`. The flow cancels instantly on rotation and re-starts, potentially causing a flicker to the `initialValue`.

### 9. Testing
Testing `stateIn` requires careful management of the `TestScope` and background coroutines. Turbine handles this well if you collect the resulting StateFlow.

### 10. Exercise
Create a cold flow that prints "Starting" and emits an item every 1 second. Convert it with `stateIn` using `WhileSubscribed(2000)`. Collect it, cancel the collection, wait 3 seconds, and collect again. Note how it prints "Starting" twice.

### 11. Deliberate failure
Change the strategy to `Eagerly`. Observe that the cold flow starts executing immediately when the ViewModel is created, even if the UI never collects it.

### 12. Interview questions
*Q: Why do we use `WhileSubscribed(5_000)` instead of `500` or `0`?*
A: To gracefully handle Android configuration changes (screen rotations). The old Activity is destroyed and a new one created; this gap takes time. The 5-second delay keeps the upstream flow alive during this transition.

### 13. Checkpoint
What is the difference between `Lazily` and `WhileSubscribed`?

---

## 17. Never return a StateFlow from a function

### 1. What is it
A critical architectural rule: You should expose `StateFlow` and `SharedFlow` as **properties** (`val`), not as the return type of a **function** (`fun`).

### 2. Why does it exist
If you return a `StateFlow` from a function (e.g., `fun getState() = flow.stateIn(...)`), every time the function is called, it creates a *brand new* hot flow. This leaks memory (it launches a new coroutine in the provided scope) and completely breaks state sharing.

### 3. Mental model
If you ask me for the time (a property), I look at my watch and tell you. If you ask me to *build you a clock* (a function), I build a new clock every time. If 10 people ask, I build 10 clocks.

### 4. How it works
Properties are instantiated once when the class is created. The `stateIn` operator launches a coroutine. You only want that coroutine launched once per ViewModel/Repository.

### 5. Code
❌ **WRONG:**
```kotlin
class BadViewModel : ViewModel() {
    // Bug! Creates a new StateFlow and launches a new coroutine on every call!
    fun getTransactions(): StateFlow<List<Transaction>> {
        return repository.getFlow().stateIn(viewModelScope, ...)
    }
}
```
✅ **RIGHT:**
```kotlin
class GoodViewModel : ViewModel() {
    // Created once. Shares state properly.
    val transactions: StateFlow<List<Transaction>> = 
        repository.getFlow().stateIn(viewModelScope, ...)
}
```

### 6. Production usage
This applies to ViewModels, Repositories, and UseCases. Always expose Hot flows as `val`.

### 7. Common mistakes
❌ Overriding a custom getter: `val state: StateFlow<X> get() = ...stateIn(...)`. This is functionally identical to a function and has the same memory leak!

### 8. Debugging
If your UI isn't receiving updates from a ViewModel, check if you called a function to get the flow. The UI is likely collecting a newly minted Flow, while the rest of your ViewModel is updating a different instance.

### 9. Testing
If testing reveals that emissions aren't reaching the collector, verify the StateFlow is a stable property.

### 10. Exercise
Write a `BadClass` with a function returning a `stateIn` flow. Call it 5 times in a loop and collect them. Look at the memory/coroutine count. 

### 11. Deliberate failure
Implement the custom getter leak: `val state get() = myFlow.stateIn(...)`. Notice how multiple UI collectors receive different instances.

### 12. Interview questions
*Q: Why is `val state: StateFlow<T> get() = ...` a massive bug?*
A: Because custom getters execute their block on every access. It will repeatedly call `stateIn`, launching leaked coroutines in the provided scope and returning un-shared instances.

### 13. Checkpoint
How do you ensure a StateFlow is only created once in a ViewModel?

---

## 18. Collecting in UI: Lifecycle awareness

### 1. What is it
When a UI (Activity/Fragment/Compose) collects a Flow from a ViewModel, it must use lifecycle-aware APIs so that collection stops when the UI is hidden (backgrounded).
- **Compose:** `collectAsStateWithLifecycle()`
- **Views (XML):** `repeatOnLifecycle(Lifecycle.State.STARTED)`

### 2. Why does it exist
If you use plain `.collect { }` in `lifecycleScope.launch`, the collection *keeps running* even when the app is in the background (until the Activity is fully destroyed). If the flow emits, it might crash the app trying to update views that aren't visible, and wastes CPU/battery.

### 3. Mental model
- **Plain collect:** A TV that stays on and plays sound loudly even when you leave the house.
- **Lifecycle-aware collect:** A TV with a motion sensor that pauses the movie when you leave the room and resumes when you walk back in.

### 4. How it works
These APIs automatically cancel the collection coroutine when the lifecycle drops below `STARTED` (e.g., `onStop`), and automatically relaunch the coroutine when it reaches `STARTED` again (`onStart`).

### 5. Code
```kotlin
// Compose
@Composable
fun TransactionScreen(viewModel: TransactionViewModel) {
    // ✅ RIGHT WAY
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    // ❌ WRONG WAY (Keeps collecting in background)
    // val state by viewModel.uiState.collectAsState() 
}

// Views / Fragment
viewLifecycleOwner.lifecycleScope.launch {
    // ✅ RIGHT WAY
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state ->
            // Update UI
        }
    }
}
```

### 6. Production usage
This is mandatory for all Android applications. Never use `lifecycleScope.launchWhenStarted` (it's deprecated because it pauses execution but keeps the upstream flow active).

### 7. Common mistakes
❌ **Using `launch` without `repeatOnLifecycle`:**
```kotlin
lifecycleScope.launch {
    viewModel.uiState.collect { ... } // Memory leak in background!
}
```

### 8. Debugging
Background the app. If you see Logcat messages from your Flow still printing, you are not using lifecycle-aware collection.

### 9. Testing
UI testing (Espresso/Compose) automatically handles lifecycle transitions. Ensure tests pass when moving the Activity to the `CREATED` state and back.

### 10. Exercise
In an Activity, collect a flow using `lifecycleScope.launch`. Put a log in the flow. Background the app. Watch the logs keep printing. Fix it with `repeatOnLifecycle`.

### 11. Deliberate failure
Use `collectAsState()` in Compose. Background the app. The ViewModel will still be emitting to the active composition.

### 12. Interview questions
*Q: What is the exact difference between `collectAsState()` and `collectAsStateWithLifecycle()`?*
A: `collectAsState()` only cancels collection when the Composable leaves the composition completely. `collectAsStateWithLifecycle()` stops collection when the LifecycleOwner (Activity/Fragment) goes to the background (`onStop`), saving resources.

### 13. Checkpoint
Why is `repeatOnLifecycle(STARTED)` better than the deprecated `launchWhenStarted`? (Hint: Cancellation vs Pausing).

---

## 19. Back-pressure & buffering

### 1. What is it
Back-pressure occurs when a Flow emits items faster than the collector can process them. Kotlin provides tools to handle this: `buffer`, `conflate`, and `collectLatest`.

### 2. Why does it exist
If the producer is fast (e.g., 100 sensor updates per second) and the consumer is slow (e.g., writing to DB takes 50ms), the producer will suspend and wait for the consumer, slowing down the whole system.

### 3. Mental model
You are pouring water (producer) into a funnel (consumer). If you pour too fast, the funnel overflows. 
- `buffer`: Add a huge bucket to hold the water so you can keep pouring.
- `conflate`: Throw away old water, only keep the newest drops.
- `collectLatest`: Every time a new drop falls, throw away the bottle you were filling and start a new one.

### 4. How it works
- `buffer(N)`: Uses a channel to store `N` items concurrently.
- `conflate()`: Drops intermediate emitted values if the collector is busy.
- `collectLatest { ... }`: Cancels the collector's current block if a new value arrives before it finishes.

### 5. Code
```kotlin
val flow = flow {
    for (i in 1..10) {
        delay(10) // Fast producer
        emit(i)
    }
}

// Conflate
flow.conflate().collect { 
    delay(100) // Slow consumer
    println(it) // Prints 1, then maybe 8, then 10. Intermediate values dropped.
}

// CollectLatest
flow.collectLatest {
    delay(100)
    println(it) // ONLY prints 10. 1-9 were cancelled mid-processing.
}
```

### 6. Production usage
`collectLatest` is heavily used in UI for things like scroll events or search queries—you only care about rendering the absolute newest state, even if you were halfway through rendering the old one.

### 7. Common mistakes
❌ **Using `collect` for UI State:** If the UI is slow to render State A, and State B arrives, rendering A is a waste of time.
✅ **Right way:** Usually handled inherently by `StateFlow` (which conflates automatically) or by using `collectLatest`.

### 8. Debugging
If your app feels sluggish responding to rapid events, you might be lacking conflation, forcing the app to process every single event sequentially.

### 9. Testing
Use virtual time in `runTest` to simulate fast producers and slow consumers, asserting on the final output to ensure intermediate values were dropped.

### 10. Exercise
Create a fast flow (delay 10) and a slow collector (delay 100). Run it with `collect`, `conflate`, and `collectLatest`. Time the total execution of each.

### 11. Deliberate failure
Try to run a fast producer and slow consumer without any buffering. Notice how the total execution time is `(producer + consumer) * items`, proving the producer was forced to wait.

### 12. Interview questions
*Q: Explain how `StateFlow` handles back-pressure inherently.*
A: `StateFlow` inherently conflates values. It only holds the latest `.value`. If the collector is slow, it will miss intermediate updates and only receive the most recent state when it's ready.

### 13. Checkpoint
If you want to process *every single item* even if it takes a long time, but you don't want to slow down the producer, which operator do you use?

---

## Phase 2 Project — Expense Tracker v3

**Goal:** Wire coroutines and Flow into the Expense Tracker.

**Requirements:**
1. Create a `TransactionRepository` with a suspend function that simulates network delay and returns `Result<List<Transaction>>`
2. Create a `Flow`-based reactive cache using `MutableStateFlow` in the repository
3. Create a `GetTransactionsUseCase` using `operator fun invoke()` that returns `Flow<List<Transaction>>`
4. Wire a `TransactionListViewModel` that:
   - Uses `viewModelScope` for launching work
   - Exposes UI state as `StateFlow<TransactionListUiState>` via `stateIn` with `WhileSubscribed(5_000)`
   - Handles loading, success, error, and empty states using a sealed interface
   - Properly handles `CancellationException` (re-throws, never swallows)
5. Write tests using `runTest`, `TestDispatcher`, and **Turbine** for Flow assertions
6. Inject dispatchers via a `DispatcherProvider` interface (never hardcode `Dispatchers.IO`)

**Deliberate bugs to find and fix:**
- A `runCatching` that swallows `CancellationException`
- A `StateFlow` returned from a function (new instance per call)
- A `Flow` collected with plain `collect` in `lifecycleScope` (keeps running when backgrounded)
- Missing `flowOn` causing work on the main thread

**Acceptance Criteria:**
- All coroutine work is main-safe
- Dispatchers are injected, not hardcoded
- CancellationException is never swallowed
- StateFlow is stored as a property, not returned from a function
- UI collection uses lifecycle-aware collection
- Tests use virtual time and Turbine

---

## Phase 2 Checkpoint

Answer without looking:
1. Why is `fun state(): StateFlow<X> = flow.stateIn(scope, ...)` a bug?
2. What breaks if you use `withContext(Default)` around `emit()`?
3. What's the practical difference between `collectAsState()` and `collectAsStateWithLifecycle()`?
4. What happens if you `catch (e: Exception)` around a `launch` block — does it catch exceptions from the coroutine?
5. Why must `CancellationException` never be swallowed?
6. Explain the difference between `StateFlow` and `SharedFlow`.
7. When would you use `combine` vs `zip`?
8. Why `WhileSubscribed(5_000)` instead of `Lazily` for UI state?

---

## Complete Java Threading → Kotlin Coroutines Translation Table

| Java Way | Kotlin Coroutines Way | Notes |
|---|---|---|
| `new Thread(() -> ...).start()` | `launch { ... }` | Structured, scoped, cancellable |
| `ExecutorService.submit()` | `async { ... }.await()` | Returns a value |
| `Thread.sleep(1000)` | `delay(1000)` | Non-blocking, cancellable |
| `synchronized` | `Mutex` | Coroutine-safe |
| `Future<T>` | `Deferred<T>` | From `async` |
| `Callback<T>` | `suspend fun`: T | Sequential code |
| `RxJava Observable` | `Flow<T>` | Cold, lazy |
| `RxJava BehaviorSubject` | `MutableStateFlow<T>` | Always has current value |
| `RxJava PublishSubject` | `MutableSharedFlow<T>` | Events, no current value |
| `subscribeOn(io)` | `flowOn(Dispatchers.IO)` | Upstream dispatcher |
| `observeOn(main)` | `collectAsStateWithLifecycle()` | Lifecycle-aware |
| `CompositeDisposable.clear()` | Structured concurrency (automatic) | Scope cancels children |
