# PHASE 10 — TESTING (Weeks 14–15)

**Objective:** Master the full Android test pyramid from unit tests with Turbine and Fakes to Compose UI tests, Robolectric, and screenshot diffing. Leverage your QA background from the inside of the codebase.
**Why this phase matters:** As a QA automation engineer, you already know test pyramids, flakiness diagnostics, and assertions. What's new is white-box unit testing of Kotlin coroutines, Flow pipelines, pure reducers, and declarative Compose trees. Unit tests run on the JVM in milliseconds, giving instant feedback and catching 80%+ of bugs before code ever reaches an emulator or CI device farm.
**Prerequisites:** Phase 1 (Kotlin), Phase 2 (Coroutines & Flow), Phase 4 (Jetpack Compose), Phase 5 (App Architecture), Phase 6 (Dependency Injection).
**Project deliverable:** Expense Tracker v9 — Full test suite spanning mappers, use cases with fakes, ViewModels with Turbine, Compose UI tests with semantics matchers, and JVM screenshot tests.
**Concepts covered:** 11 total, each with the full 13-step teaching sequence.

---

## 1. The Modern Android Test Pyramid

### 1. What is it
The architectural strategy for testing an Android app, shifting emphasis heavily away from slow, flaky UI tests on physical/emulated devices (which QA is used to) toward blazing-fast, deterministic JVM tests.

### 2. Why does it exist
In traditional QA, testing happens from the outside in (Black Box via Appium/Selenium). On mobile, these tests are slow, flaky (due to network, animations, device states), and expensive to maintain. By testing architecture components (ViewModels, UseCases) directly on the JVM, we get feedback in milliseconds.

### 3. Mental model
Think of testing a car.
- **Unit:** Testing the fuel injector on a workbench (milliseconds).
- **Compose UI / Robolectric:** Testing the engine block on a rig (seconds).
- **E2E (Appium):** Driving the assembled car on a track (minutes/hours).
You want 80% workbench tests, 15% rig tests, and 5% track tests.

### 4. How it works
The pyramid structure:
1. **Level 1: Unit (JVM):** Pure Kotlin tests. Tests UseCases, Mappers, ViewModels. Uses JUnit, Truth/Kotest, Turbine, Fakes.
2. **Level 2: JVM + Android Stubs (Robolectric):** Runs code that touches `android.*` packages without a real emulator by providing JVM implementations of Android internals.
3. **Level 3: Compose UI (JVM/Device):** Tests UI components in isolation using `createComposeRule()` and semantics matchers.
4. **Level 4: Instrumented:** Runs on an emulator/device. Usually Espresso for legacy Views or `createAndroidComposeRule`.
5. **Level 5: E2E:** Full flows (Maestro, Appium) testing integration.

### 5. Code
```kotlin
// QA background vs Android Unit Test

// ❌ QA E2E (Slow, requires emulator, UI changes break it):
// driver.findElement(By.id("email_input")).sendKeys("invalid")
// driver.findElement(By.id("submit")).click()
// assertTrue(driver.findElement(By.id("error")).isDisplayed())

// ✅ Android Unit Test (Fast JVM test, pure logic):
@Test
fun `when email is invalid then error state is emitted`() = runTest {
    val viewModel = LoginViewModel(ValidateEmailUseCase())
    viewModel.onEmailChanged("invalid")
    viewModel.onSubmit()
    
    // Assert state directly, no UI involved!
    assertEquals("Invalid email format", viewModel.state.value.emailError)
}
```

### 6. Production usage
Every PR triggers a CI pipeline that runs thousands of unit tests in < 2 minutes. If business logic is broken, the build fails instantly before wasting time spinning up Firebase Test Lab emulators.

### 7. Common mistakes
❌ **Wrong:** Writing an Espresso/Appium test to verify that "Age must be > 18" validation logic works.
✅ **Right:** Writing a pure Kotlin Unit test for `ValidateAgeUseCase`, testing edge cases (17, 18, 19, null) in 5 milliseconds.

### 8. Debugging
If a unit test fails locally but passes on CI, check timezone dependencies (e.g., `LocalDate.now()`). JVM tests are isolated, but your local JVM environment can bleed in if not mocked/injected properly.

### 9. Testing
Testing the pyramid strategy means monitoring your CI times. If a PR takes 30 minutes to verify, your pyramid is inverted (too many E2E tests).

### 10. Exercise
Look at your current or past QA suite. Find a test that clicks through 4 screens just to verify a calculation on the 5th screen. Write out how you would structure that as a unit test for a `CalculateTotalUseCase`.

### 11. Deliberate failure
Try calling `Log.d("Test", "Hello")` inside a pure JVM unit test. It will crash with `java.lang.RuntimeException: Method d in android.util.Log not mocked.` This proves you are on the JVM, not Android!

### 12. Interview questions
- "Why are we moving away from Espresso to Robolectric and JVM tests?"
- "What logic belongs in a ViewModel test vs a Compose UI test?"
- "How does the Android test pyramid differ from a backend web test pyramid?"

### 13. Checkpoint
Do you understand why testing the *state* of a ViewModel is vastly superior to testing the *text of a TextView* for verifying business logic?

---

## 2. Coroutine Testing & Virtual Time

### 1. What is it
The `kotlinx-coroutines-test` library, which provides special dispatchers and the `runTest` builder to execute coroutines in tests. It features "virtual time," allowing `delay(5000)` to execute instantaneously.

### 2. Why does it exist
If your production code has `delay(5000)` (e.g., for a splash screen or debouncing search), a normal test would actually pause for 5 seconds. Multiply that by 1,000 tests, and your test suite takes hours. Coroutine testing manipulates time so asynchronous code runs synchronously and instantly in tests.

### 3. Mental model
Think of standard coroutines as watching a movie at 1x speed. Think of `runTest` as having a remote control where you can instantly skip to the exact timestamp you want, bypassing the boring parts (delays).

### 4. How it works
- `runTest { }`: Replaces `runBlocking`. It sets up a `TestScope` with a `TestCoroutineScheduler`.
- `StandardTestDispatcher`: Queues up new coroutines. They don't run until you explicitly command time to move forward (`advanceUntilIdle()`, `runCurrent()`).
- `UnconfinedTestDispatcher`: Eagerly executes coroutines immediately upon creation, useful for testing simple `StateFlow` updates.
- `Dispatchers.Main` must be overridden in tests since the JVM doesn't have an Android Main thread.

### 5. Code
```kotlin
// Production Code
class SplashViewModel(private val dispatcher: CoroutineDispatcher) {
    val isReady = MutableStateFlow(false)
    
    fun startTimer() {
        viewModelScope.launch(dispatcher) {
            delay(5000) // 5 real seconds
            isReady.value = true
        }
    }
}

// Test Code
@Test
fun `splash timer completes after 5 seconds`() = runTest {
    // 1. Setup with a TestDispatcher
    val dispatcher = StandardTestDispatcher(testScheduler)
    val viewModel = SplashViewModel(dispatcher)
    
    // 2. Act
    viewModel.startTimer()
    
    // 3. Assert initial state
    assertFalse(viewModel.isReady.value)
    
    // 4. Time travel! Fast forward 5 seconds instantly.
    advanceTimeBy(5000)
    runCurrent() // Execute pending tasks
    
    // 5. Assert final state
    assertTrue(viewModel.isReady.value)
}
```

### 6. Production usage
Used in every ViewModel or UseCase test that involves Flows, delays, timeouts, or asynchronous data fetching. 

### 7. Common mistakes
❌ **Wrong:** Hardcoding `Dispatchers.IO` in your production code. You cannot replace it in tests!
```kotlin
launch(Dispatchers.IO) { ... } // Untestable!
```
✅ **Right:** Injecting a Dispatcher.
```kotlin
class MyViewModel(private val ioDispatcher: CoroutineDispatcher) {
    launch(ioDispatcher) { ... } // Replaced with TestDispatcher in tests
}
```

### 8. Debugging
If a test fails with "This job has not completed yet", you likely used `StandardTestDispatcher` but forgot to call `advanceUntilIdle()` or `runCurrent()`, so the coroutine is still sitting in the queue unexecuted.

### 9. Testing
You test time-based logic by asserting the state *before* `advanceTimeBy()`, and then asserting the state *after*.

### 10. Exercise
Write a `MainDispatcherRule` (a JUnit TestRule) that uses `Dispatchers.setMain(UnconfinedTestDispatcher())` before tests and `Dispatchers.resetMain()` after tests. 

### 11. Deliberate failure
Write a test using `runTest` but launch a coroutine inside it using `GlobalScope.launch { delay(1000) }`. Watch the test fail or finish before the coroutine executes, proving why structured concurrency and test scopes are required.

### 12. Interview questions
- "What is the difference between `StandardTestDispatcher` and `UnconfinedTestDispatcher`?"
- "Why do we use `runTest` instead of `runBlocking`?"
- "How do you test a coroutine that retries a network request 3 times with exponential backoff?"

### 13. Checkpoint
Can you explain why injecting dispatchers is an absolute requirement for writing unit tests in Kotlin?

---

## 3. Flow Testing with Turbine

### 1. What is it
Turbine is a brilliant open-source library from Cash App specifically designed for testing Kotlin Flows. It provides a simple API to assert the sequential emissions of a Flow.

### 2. Why does it exist
Testing Flows natively in Kotlin is tedious. You have to collect the flow in a separate coroutine, store emissions in a mutable list, and then assert against the list. Turbine wraps this into a clean, blocking-style DSL (`flow.test { }`).

### 3. Mental model
Think of a Flow as a water pipe. Standard testing is like putting a bucket under the pipe, waiting for the water to stop, and then examining the bucket. Turbine is like a turnstile at the end of the pipe; you command it: "give me the next drop", "expect an error", "expect the pipe to close".

### 4. How it works
You call `.test {}` on any Flow. Inside the lambda, you are in a suspending context where you can call Turbine commands:
- `awaitItem()`: Suspends until the flow emits a value, then returns it.
- `skip(n)`: Ignores the next `n` emissions (the older `skipItems(n)` name was renamed to `skip(n)` in Turbine 0.8 — if you see `skipItems` in a codebase or tutorial, it's on an old Turbine version).
- `awaitError()`: Suspends until the flow throws an exception.
- `awaitComplete()`: Suspends until the flow finishes naturally.

### 5. Code
```kotlin
// Production Code
fun countdownFlow(): Flow<Int> = flow {
    emit(3)
    delay(1000)
    emit(2)
    delay(1000)
    emit(1)
}

// Test Code using Turbine
@Test
fun `countdown emits 3 2 1`() = runTest {
    countdownFlow().test {
        assertEquals(3, awaitItem())
        assertEquals(2, awaitItem()) // virtual time advances automatically!
        assertEquals(1, awaitItem())
        awaitComplete()
    }
}
```

### 6. Production usage
Testing `StateFlow` from ViewModels to ensure the UI states transition correctly (e.g., Loading -> Success). Testing complex data transformation pipelines in UseCases.

### 7. Common mistakes
❌ **Wrong:** Forgetting that `StateFlow` never completes!
```kotlin
viewModel.stateFlow.test {
    assertEquals(InitialState, awaitItem())
    awaitComplete() // ❌ Test will hang forever! StateFlows don't complete.
}
```
✅ **Right:** Using `cancelAndIgnoreRemainingEvents()` for hot flows.
```kotlin
viewModel.stateFlow.test {
    assertEquals(InitialState, awaitItem())
    cancelAndIgnoreRemainingEvents() // ✅ Correct termination
}
```

### 8. Debugging
If a Turbine test times out (usually after 1 second), it means you called `awaitItem()` but the flow never emitted anything. Check your coroutine dispatchers or see if a cold flow wasn't triggered properly.

### 9. Testing
When testing ViewModels mapped with `SharingStarted.WhileSubscribed(5000)`, you often need to use Turbine in combination with `runCurrent()` to trigger the initial subscription.

### 10. Exercise
Create a Flow that emits "A", delays 100ms, emits "B", throws an `IOException`, and test it using Turbine ensuring you catch the exact exception type.

### 11. Deliberate failure
Write a Turbine test where you expect 3 items, but the flow only emits 2. Watch Turbine throw a clear assertion error indicating it timed out waiting for the 3rd item.

### 12. Interview questions
- "How do you test a `StateFlow` if it never completes?"
- "What happens if a Flow emits an item that you don't call `awaitItem()` for in Turbine?"
- "How do you test a `Flow` that uses `.debounce(500)`?"

### 13. Checkpoint
Why is Turbine vastly superior to manually calling `.toList()` on a Flow in a test?

---

## 4. Fakes vs Mocks: The Production Philosophy

### 1. What is it
The strategy for replacing real dependencies (like databases or networks) in unit tests. 
- **Fakes:** Hand-written classes implementing an interface with working, in-memory logic (e.g., storing data in a `HashMap`).
- **Mocks:** Dynamically generated dummy objects (via Mockito/MockK) where you program specific return values for specific method calls.

### 2. Why does it exist
You can't hit a real production API or database in a unit test. As a QA, you might be used to spinning up staging environments. In unit testing, we isolate the subject under test. Modern Android development strongly prefers **Fakes over Mocks**.

### 3. Mental model
- **Mock:** A cardboard cutout of a vending machine. If you program it to say "when button A is pressed, dispense Cola", it will do that. But if you press B, it crashes.
- **Fake:** A mini vending machine. It actually holds real cans. You can put a can in, and get it out later. It has real state, just no coin slot.

### 4. How it works
Instead of mocking `UserRepository`, you create `FakeUserRepository : UserRepository`. You use a `MutableList` or `MutableStateFlow` to hold users in memory. When `saveUser()` is called, it adds to the list. When `getUser()` is called, it reads from the list.

### 5. Code
```kotlin
// The Interface
interface ExpenseRepository {
    fun getExpenses(): Flow<List<Expense>>
    suspend fun addExpense(expense: Expense)
}

// ✅ The Fake (Hand-written, stateful, reusable)
class FakeExpenseRepository : ExpenseRepository {
    // In-memory state!
    private val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
    
    // Test-only flags to simulate network errors
    var shouldReturnError = false 

    override fun getExpenses(): Flow<List<Expense>> = flow {
        if (shouldReturnError) throw IOException("Network down")
        emitAll(expensesFlow)
    }

    override suspend fun addExpense(expense: Expense) {
        expensesFlow.update { it + expense }
    }
}

// ❌ The MockK equivalent (Verbose, fragile, implementation-bound)
val mockRepo = mockk<ExpenseRepository>()
every { mockRepo.getExpenses() } returns flowOf(listOf(expense1))
coEvery { mockRepo.addExpense(any()) } just Runs
```

### 6. Production usage
Google and Square strongly advocate for Fakes. In your `src/test` directory, you maintain a `fakes` package. Every repository interface has one Fake implementation shared across hundreds of tests.

### 7. Common mistakes
❌ **Wrong:** Mocking everything. "When repo.get() is called return X. Verify repo.save() was called with Y." This tests *how* the code is written, not *what* it does. If you rename a method, the test breaks.
✅ **Right:** Using Fakes to test State. "Add expense to Fake. Assert ViewModel state shows the expense." Refactoring internals won't break the test.

### 8. Debugging
If a Mock-based test is failing because `verify { ... }` didn't match, it's often because an argument changed slightly. Fakes rarely have this issue because they act like real implementations.

### 9. Testing
Only use Mocks (MockK) for types you do not own and cannot easily fake (e.g., `android.content.Context`, `WorkManager`).

> **[Extension] The honest downside of Fakes:** Fakes are not a free lunch — teach both sides. A hand-written `FakeExpenseRepository` is itself untested production-adjacent code that someone has to maintain, and it can silently drift from the real implementation's behavior (e.g., the real Room-backed repository throws on a unique-constraint violation, but the Fake's `HashMap` happily overwrites the entry — your tests stay green while production breaks). Two mitigations senior teams use: (1) write a shared **contract test suite** (a set of behavior assertions defined once against the `ExpenseRepository` interface) and run it against *both* the Fake and the real implementation, so drift is caught automatically; (2) keep Fakes deliberately simple and push edge-case behavior (errors, empty states, race conditions) into explicit toggles (`shouldReturnError`) rather than trying to perfectly replicate the real backend's every quirk. Mocks avoid the "who tests the test double" problem entirely by generating from the interface at test time, which is exactly why they still earn a place for one-off, rarely-touched dependencies — the ongoing maintenance cost of a Fake isn't worth it for something used in one test file.

### 10. Exercise
Write a `FakeAuthRepository` that takes a constructor parameter `var isNetworkAvailable: Boolean = true`. Implement `login(user, pass)` so it throws an exception if the network is unavailable, or returns success if credentials match "admin"/"password".

### 11. Deliberate failure
Write a MockK test. Change the production code to call `addExpense` twice instead of once (a bug!). Watch the mock test silently pass because you didn't explicitly use `verify(exactly = 1)`. A Fake would have added two items to the list, catching the bug in your state assertion!

### 12. Interview questions
- "Why do modern Android teams prefer Fakes over Mocks (Mockito/MockK)?"
- "What is the danger of `verify { ... }` assertions in mock frameworks?"
- "When is it acceptable to use a Mock instead of a Fake?"

### 13. Checkpoint
Do you understand how Fakes make your test suite resilient to refactoring, whereas Mocks make your test suite brittle?

---

## 5. Test Hygiene, Structure & Factory Patterns

### 1. What is it
The standardized practices for writing clean, readable, and maintainable test code. This includes Given/When/Then structures, descriptive naming conventions, Data Factories, and Clock injection.

### 2. Why does it exist
Test code is production code. If a test is messy, developers will delete it or ignore it when it breaks. QA engineers know this well: a flaky, unreadable test suite is worse than no test suite.

### 3. Mental model
A test should read like a user story specification. You shouldn't have to read the production code to understand what the test is verifying. 

### 4. How it works
- **Naming:** Backticks allow spaces and clear sentences in Kotlin test names.
- **Structure:** Arrange (Given) / Act (When) / Assert (Then) blocks separated by newlines.
- **Factories:** Static methods or objects that generate dummy data with default values, so tests only specify the fields they care about.
- **Clocks:** Never use `System.currentTimeMillis()`. Inject `java.time.Clock` so tests can fix time to a specific date.

### 5. Code
```kotlin
// Data Factory (hides 15 constructor parameters!)
fun createTestExpense(
    id: String = "1",
    amount: Double = 100.0,
    category: String = "Food" // Only override what matters for the test
) = Expense(id, amount, category, Date(), /* ... 10 other default params ... */)

@Test
fun `when fetching expenses fails, then error state is emitted`() = runTest {
    // GIVEN (Arrange)
    val fakeRepo = FakeExpenseRepository().apply { 
        shouldReturnError = true 
    }
    val viewModel = ExpenseViewModel(fakeRepo)

    // WHEN (Act)
    viewModel.loadExpenses()

    // THEN (Assert)
    assertTrue(viewModel.state.value is State.Error)
}
```

### 6. Production usage
Every test class in a serious codebase strictly follows Given/When/Then. Factories are kept in a `sharedTest` folder accessible to both Unit and Instrumented tests.

### 7. Common mistakes
❌ **Wrong:** Using `Thread.sleep(1000)` in a test. Absolute zero tolerance. It makes tests slow and flaky.
❌ **Wrong:** `fun testLoginFailure()`. Vague, doesn't explain the condition or expected outcome.
✅ **Right:** ``fun `when invalid password entered, then login fails`()``

### 8. Debugging
If a test suddenly starts failing at the end of the month, you probably used `LocalDate.now()` instead of injecting a fixed `Clock` instance. 

### 9. Testing
Code reviews for test files should be as rigorous as for production code. Look for hardcoded magic strings, repeated setup code, and missing Given/When/Then structure.

### 10. Exercise
Create a file `ExpenseFactory.kt`. Write a function `makeExpense()` with default parameters for every field in your `Expense` domain model. Update an existing test to use it.

### 11. Deliberate failure
Write a test that asserts an item was created "today" using `System.currentTimeMillis()`. Run the test right before midnight. Watch it fail. Fix it by injecting a `Clock.fixed()`.

### 12. Interview questions
- "Why should we avoid `Thread.sleep()` in tests, and what are the alternatives?"
- "How do Data Factories improve test maintainability when a model's constructor changes?"
- "Why is injecting a `Clock` necessary for testing time-sensitive business logic?"

### 13. Checkpoint
As a QA engineer, how does standardizing test naming and factories improve collaboration between QA and developers?

---
*End of Part 1. Part 2 will cover Robolectric, Compose UI testing, Screenshot testing, and coverage analysis.*


---

## 6. Testing Domain Layer: Mappers, Use Cases, Orchestrators

**1. What is it**
Testing the core business rules of your application in isolation from external systems. This includes data transformations (Mappers), business rules (Use Cases), and process coordinators (Orchestrators).

**2. Why does it exist**
The domain layer holds the critical business logic of your app. If this fails, the app's primary value proposition fails. Testing this layer ensures that data flows correctly from raw API/DB models to the clean domain models the UI expects, and that business operations (like calculating totals or validating inputs) are correct regardless of the UI or network state.

**3. Mental model**
Think of the domain layer as a pure functional pipeline in a factory. Mappers are the initial sorting machines (rejecting bad parts, shaping good parts). Use Cases are the assembly stations where specific parts are put together according to blueprints. Orchestrators are the floor managers coordinating multiple assembly stations. You test these by feeding them raw materials and inspecting the output, without needing the delivery trucks (Network) or storefront (UI).

**4. How it works**
You instantiate the domain classes (usually pure Kotlin classes with no Android dependencies) in your JVM tests. You provide inputs (often mock or stubbed dependencies for Use Cases/Orchestrators) and assert the outputs or state changes.

**5. Code**
```kotlin
// 1. Mapper testing
data class ExpenseDto(val id: String?, val amountCents: Int?, val category: String?)
data class Expense(val id: String, val amount: Double, val category: String)

fun ExpenseDto.toDomainOrNull(): Expense? {
    if (id == null || amountCents == null || category == null) return null
    return Expense(id, amountCents / 100.0, category)
}

// 2. Use Case & Orchestrator testing
class GetExpensesUseCase(private val repository: ExpenseRepository) {
    operator fun invoke(): Flow<List<Expense>> = repository.getAll()
}

class FakeExpenseRepository : ExpenseRepository {
    private val flow = MutableStateFlow<List<Expense>>(emptyList())
    override fun getAll(): Flow<List<Expense>> = flow
    fun emit(expenses: List<Expense>) { flow.value = expenses }
}
```

**6. Production usage**
Every enterprise Android app will have hundreds of these tests. They run on the JVM in milliseconds and form the vast base of the testing pyramid.

**7. Common mistakes**
- **Testing implementation details instead of behavior:** Asserting that a specific internal method was called rather than checking the final result or state.
- **Using heavy mocking frameworks for simple domain logic:** Using Mockito/MockK to mock simple data classes or pure functions.
- **Not testing edge cases in mappers:** Forgetting to test what happens when the API returns `null` or empty strings.

**8. Debugging**
If a domain test fails, standard JVM debugging tools work perfectly. Set a breakpoint in the mapper or use case, and step through the logic. No device or emulator required.

**9. Testing**
```kotlin
@Test
fun `mapper returns null when id is missing`() {
    val dto = ExpenseDto(id = null, amountCents = 1000, category = "Food")
    val result = dto.toDomainOrNull()
    assertThat(result).isNull()
}

@Test
fun `use case returns expenses from repository`() = runTest {
    val repository = FakeExpenseRepository()
    val useCase = GetExpensesUseCase(repository)
    
    val expected = listOf(Expense("1", 10.0, "Food"))
    repository.emit(expected)
    
    val result = useCase().first()
    assertThat(result).isEqualTo(expected)
}
```

**10. Exercise**
Write an `ExpenseSummaryOrchestrator` that takes a `GetExpensesUseCase` and calculates the total amount spent. Write tests for it using a `FakeExpenseRepository`.

**11. Deliberate failure**
Change the `amountCents / 100.0` logic in the mapper to `amountCents / 10.0`. Run the test. It should fail because the assertion expects `10.0` but gets `100.0`. Fix the code.

**12. Interview questions**
- **Junior:** How do you test a pure function in Kotlin?
- **Mid:** Why is it preferable to test use cases with Fake repositories instead of Mocking frameworks?
- **Senior:** How do you architect your domain layer to maximize testability and minimize test setup fragility?

**13. Checkpoint**
Can you explain why testing the domain layer is faster and less flaky than testing the UI layer?

---

## 7. Testing Presentation Layer: ViewModels & Reducers

**1. What is it**
Validating the state management and interaction logic of the application. This involves testing how `ViewModels` process user intents, interact with domain use cases, and emit state updates to the UI, specifically testing the transition of states (e.g., Loading -> Success -> Error).

**2. Why does it exist**
The Presentation layer connects business logic to the UI. It handles concurrency (Coroutines), state emission (StateFlow), and user inputs. Testing this ensures the UI *will* receive the correct state based on user actions and asynchronous operations without needing to render the actual UI.

**3. Mental model**
Think of the ViewModel as a state machine. It has an initial state. When an event occurs (user clicks a button, data arrives from the network), the machine transitions to a new state. Your tests are poking the machine with events and verifying the light bulb (state emission) turns the correct color.

**4. How it works**
You instantiate the ViewModel in a JVM test. You use `kotlinx-coroutines-test` (specifically `TestDispatcher` and `runTest`) to control time and concurrency. You use libraries like **Turbine** to easily consume and assert on the `StateFlow` emissions.

**5. Code**
```kotlin
data class ViewState(val isLoading: Boolean = false, val data: String = "")

class MyViewModel(private val dispatcher: CoroutineDispatcher = Dispatchers.Main) : ViewModel() {
    private val _state = MutableStateFlow(ViewState())
    val state = _state.asStateFlow()

    fun loadData() {
        viewModelScope.launch(dispatcher) {
            _state.value = _state.value.copy(isLoading = true)
            delay(1000) // Simulate network
            _state.value = _state.value.copy(isLoading = false, data = "Success")
        }
    }
}
```

**6. Production usage**
ViewModels are the heart of Android architecture. Testing them thoroughly is crucial for app stability. You'll see ViewModels tested extensively using Turbine to verify state sequences during complex user flows.

**7. Common mistakes**
- **Not swapping the Main dispatcher:** ViewModels often use `viewModelScope` which defaults to `Dispatchers.Main`. In unit tests, `Dispatchers.Main` throws an exception unless you replace it with a `TestDispatcher`.
- **Race conditions in state assertions:** Asserting on `state.value` before the coroutine has actually updated it.
- **Ignoring intermediate states:** Forgetting to test the `Loading` state emission before the `Success` state.

**8. Debugging**
If a ViewModel test is failing or hanging, it's often a concurrency issue. Check if you are advancing virtual time correctly (`advanceUntilIdle()`) or if your test dispatcher is set up correctly. Turbine will usually give very clear error messages about unconsumed events or timeouts.

**9. Testing**
```kotlin
@Test
fun `loadData emits loading then success`() = runTest {
    // 1. Setup Test Dispatcher for Main
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    Dispatchers.setMain(testDispatcher)
    
    val viewModel = MyViewModel(testDispatcher)
    
    // 2. Test state emissions with Turbine
    viewModel.state.test {
        assertThat(awaitItem()).isEqualTo(ViewState(isLoading = false, data = "")) // Initial state
        
        viewModel.loadData()
        
        assertThat(awaitItem()).isEqualTo(ViewState(isLoading = true, data = "")) // Loading
        assertThat(awaitItem()).isEqualTo(ViewState(isLoading = false, data = "Success")) // Success
        
        cancelAndIgnoreRemainingEvents()
    }
    
    // 3. Teardown
    Dispatchers.resetMain()
}
```

**10. Exercise**
Write a ViewModel that fetches an expense list. Handle the error case (e.g., repository throws exception) and write a Turbine test to verify the `Error` state is emitted correctly.

**11. Deliberate failure**
Comment out the `_state.value = _state.value.copy(isLoading = true)` line in the ViewModel. Run the test. Turbine will fail, stating it expected the Loading state but got the Success state. Fix the code.

**12. Interview questions**
- **Junior:** What is Turbine and why is it useful for testing StateFlows?
- **Mid:** Explain the difference between `StandardTestDispatcher` and `UnconfinedTestDispatcher`.
- **Senior:** How do you test complex state reducers in an MVI architecture to ensure pure functional transformations without side effects?

**13. Checkpoint**
Why must you call `Dispatchers.setMain()` in a ViewModel unit test, but not in a pure Domain layer test?

---

## 8. Compose UI Testing & Semantics Tree

**1. What is it**
Testing Jetpack Compose UI components in isolation to verify they render correctly based on a given state and react appropriately to user interactions.

**2. Why does it exist**
You need to guarantee that your UI actually displays the data correctly and that buttons trigger the right callbacks. Compose UI tests let you verify layout structure, accessibility information, and user interaction logic without needing a full device emulator (via Robolectric).

**3. Mental model**
Imagine Compose UI as a physical control panel. Your tests are a robot that examines the panel (Semantics Tree). The robot can read labels (`onNodeWithText`), find specific buttons by ID (`onNodeWithTag`), press them (`performClick`), and verify lights turn on (`assertIsDisplayed`).

**4. How it works**
Compose doesn't use the standard Android View hierarchy (no XML, no `findViewById`). Instead, it builds a **Semantics Tree** parallel to the UI tree, used for accessibility and testing. You use `createComposeRule()` to set the content (your composable) and then use matchers to find nodes in this semantics tree and perform actions or assertions.

**5. Code**
```kotlin
@Composable
fun SubmitButton(isLoading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !isLoading, modifier = Modifier.testTag("submit_btn")) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.testTag("loading_spinner"))
        } else {
            Text("Submit")
        }
    }
}
```

**6. Production usage**
Used extensively to test custom UI components (design systems) and complete screens in various states (Loading, Error, Empty, Content). By using Robolectric, these tests can run on the JVM on CI servers extremely fast.

**7. Common mistakes**
- **Testing implementation details:** Checking exact pixel padding instead of functional behavior.
- **Relying on fragile text matchers:** Using `onNodeWithText("Submit")` which breaks if the text is translated. Use `testTag` or resource string lookups.
- **Not testing accessibility:** Forgetting to add content descriptions which the semantics tree relies heavily on.

**8. Debugging**
If a node cannot be found, use `composeTestRule.onRoot().printToLog("TEST_TAG")` to print the entire semantics tree to the console. This lets you see exactly what the test runner "sees."

**9. Testing**
```kotlin
@RunWith(RobolectricTestRunner::class) // Run on JVM
class SubmitButtonTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `shows loading spinner when isLoading is true`() {
        composeTestRule.setContent { SubmitButton(isLoading = true, onClick = {}) }
        
        composeTestRule.onNodeWithTag("loading_spinner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Submit").assertDoesNotExist()
    }

    @Test
    fun `click triggers callback when not loading`() {
        var clicked = false
        composeTestRule.setContent { SubmitButton(isLoading = false, onClick = { clicked = true }) }
        
        composeTestRule.onNodeWithTag("submit_btn").performClick()
        assertThat(clicked).isTrue()
    }
}
```

**10. Exercise**
Create a complex Compose screen with a list of items and a "Delete" icon on each. Write a test that finds the 3rd item's delete icon using hierarchical semantics and clicks it, verifying the callback receives the correct item ID.

**11. Deliberate failure**
Change `enabled = !isLoading` to `enabled = true` in the production code. Write a test that asserts the button is disabled during loading (`assertIsNotEnabled()`). It should fail. Fix it.

**12. Interview questions**
- **Junior:** How do you find a specific Composable in a UI test?
- **Mid:** What is the Semantics Tree in Compose and why is it essential for accessibility and testing?
- **Senior:** How do you structure Compose UI tests to run blazingly fast on the JVM without sacrificing confidence in device-specific rendering?

**13. Checkpoint**
How does `Modifier.testTag("foo")` relate to the Semantics Tree?

---

## 9. Screenshot Testing (Roborazzi / Paparazzi)

**1. What is it**
Capturing images of your UI components or entire screens and comparing them pixel-by-pixel against known "golden" baseline images to detect unintended visual regressions.

**2. Why does it exist**
Standard UI tests assert that elements exist, but they don't catch visual bugs like text overlapping, wrong colors, or layout clipping. Screenshot testing automatically catches any visual change, ensuring UI consistency across refactors and OS updates.

**3. Mental model**
It's like taking a "before" and "after" photo of your house during renovation. If you only meant to paint a wall but accidentally knocked over a vase, the photo comparison will highlight the broken vase immediately, even if a standard checklist ("is wall painted?") passed.

**4. How it works**
Modern tools like **Paparazzi** or **Roborazzi** run entirely on the JVM (no emulators needed). They hook into Android's rendering pipeline (via LayoutLib or Robolectric) to draw the Composable in memory and save it as a PNG. During a test run, they capture a new image and use image comparison algorithms to generate a diff against the baseline.

**5. Code (Roborazzi Setup)**
```kotlin
// Build.gradle setup required for Roborazzi

@Composable
fun MyCard(title: String) {
    Card(modifier = Modifier.padding(16.dp)) { Text(text = title, modifier = Modifier.padding(8.dp)) }
}
```

**6. Production usage**
Screenshot testing is crucial for Design Systems. Every reusable component is tested in light/dark modes, different font scales, and RTL layouts. It provides absolute confidence when updating foundational UI libraries.

**7. Common mistakes**
- **Running them on emulators:** Emulator screenshot tests are flaky due to hardware differences, OS animations, and time rendering. Always use JVM tools (Paparazzi/Roborazzi).
- **Not managing golden files properly:** Failing to store baselines in Git LFS or having a messy update process.
- **Testing dynamic data:** Screenshots with real timestamps or random data will always fail. Inject static mock data.

> **[Extension] Paparazzi vs. Roborazzi — choosing between them:** Both render on the JVM, but they take different routes and trade off differently:
> - **Paparazzi** renders via LayoutLib — the same renderer that powers Android Studio's `@Preview`. It's simple to set up, historically very fast, and needs no Robolectric test lifecycle. Its ceiling is lower, though: it cannot capture `Dialog`/`Popup` content (they render cut off or missing), and because it only renders a static frame rather than driving a live Compose test session, it cannot `performClick()` and then screenshot the result — you can only snapshot the state you construct up front.
> - **Roborazzi** is built on top of Robolectric (hence the `@RunWith(RobolectricTestRunner::class)` + `GraphicsMode.NATIVE` requirement in the sample above), which means you get a real, interactive Compose test session: you can click, scroll, or advance state and then capture the *resulting* frame, and dialogs/popups render correctly. It also has first-class support for capturing animated GIF sequences and scanning `@Preview` functions automatically. The tradeoff is more setup overhead and (historically) slower runs unless you apply the known Robolectric UI-dispatcher workaround.
> - As of 2025, Roborazzi is the more actively developed of the two and is generally the safer default for new projects that need dialog coverage or click-then-screenshot flows; Paparazzi remains a reasonable, simpler choice for teams doing pure static-state component snapshots who want to avoid any Robolectric setup at all. Either way, run them on the JVM as this section already emphasizes — never on emulators.

**8. Debugging**
When a screenshot test fails, the tool generates a visual "diff" image highlighting the exact pixels that changed. Look at the diff image to understand what broke.

**9. Testing**
```kotlin
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // Required for Roborazzi
class MyCardScreenshotTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `MyCard default appearance`() {
        composeTestRule.setContent { MyCard(title = "Expense Report") }
        
        // Capture and compare against golden image
        composeTestRule.onRoot().captureRoboImage() 
    }
}
```

**10. Exercise**
Configure Roborazzi in your project. Write a screenshot test for a complex `ExpenseDetailScreen` that captures both Light and Dark mode variations in a single test run.

**11. Deliberate failure**
Change the padding in `MyCard` from `16.dp` to `24.dp`. Run the screenshot test. It should fail and generate a diff image showing the layout shift. Revert the code to pass.

**12. Interview questions**
- **Junior:** What is the purpose of screenshot testing?
- **Mid:** Why are JVM-based screenshot testing tools like Paparazzi preferred over Espresso-based device screenshot tests?
- **Senior:** How do you integrate screenshot testing into a CI pipeline and manage the storage of hundreds of baseline images?

**13. Checkpoint**
Why must you inject static fake data into a Composable before taking a screenshot test?

---

## 10. Instrumented & E2E Testing (Espresso, UIAutomator, Maestro)

**1. What is it**
Tests that run on a real Android device or emulator. They cover integration with the Android OS (databases, keystore, permissions) and true End-to-End (E2E) user flows spanning multiple screens and apps.

**2. Why does it exist**
While JVM tests are fast, they are simulations. Sometimes you need to prove the app actually works on a real OS. Does SQLite transaction rollback work on an actual device? Can the user complete a full checkout flow without crashing?

**3. Mental model**
JVM tests are practicing in a flight simulator. Instrumented E2E tests are taking a real plane for a test flight. It's slower, more expensive, and prone to weather delays (flakiness), but it proves the plane can actually fly.

**4. How it works**
The test code is compiled into a separate APK, which is installed alongside the app APK on a device. The test app drives the main app using instrumentation frameworks (Espresso) or system-level automation (UIAutomator).

**Modern Alternative - Maestro:** Maestro simplifies E2E testing by using declarative YAML scripts instead of complex code, continuously polling the UI tree, and dramatically reducing flakiness.

**5. Code (Maestro YAML approach)**
```yaml
appId: com.example.expense
---
- launchApp
- tapOn: "Add Expense" # Matches text or accessibility ID
- inputText: "15.00"
- tapOn: "Save"
- assertVisible: "15.00" # Verify it appeared on the list
```

**6. Production usage**
Used sparingly. A production app might have 5,000 unit tests, 500 UI/Screenshot tests, but only 20 E2E tests covering the most critical "golden paths" (Login, Checkout, Core creation flows) run nightly.

**7. Common mistakes**
- **Writing too many E2E tests:** This creates a slow, flaky, unmaintainable test suite (The Ice Cream Cone anti-pattern).
- **Relying on sleep/delays:** Using hardcoded delays instead of waiting for specific UI elements to appear.
- **Testing backend integration:** E2E tests should often still use mock servers (like WireMock) to avoid failing due to network blips.

**8. Debugging**
Debugging E2E tests is notoriously difficult. Rely on logs, video recordings of the test execution, and taking screenshots at points of failure. Maestro offers a built-in interactive studio for debugging.

**9. Testing**
Instrumented tests usually reside in the `androidTest` source set. E2E tests (like Maestro) often sit in a separate repository or top-level folder and run against release builds.

**10. Exercise**
Write a Maestro flow (or simple Espresso test) that launches the app, navigates to the settings screen, toggles a switch, and verifies the preference was saved.

**11. Deliberate failure**
Break the navigation logic so the "Add Expense" button does nothing. Run the Maestro test. It should fail to find the text field on the next screen. Fix the navigation.

**12. Interview questions**
- **Junior:** What is the difference between `test` and `androidTest` folders in Android Studio?
- **Mid:** Explain the concept of the Testing Pyramid and why E2E tests should be kept to a minimum.
- **Senior:** How do you design an architecture that allows you to easily swap out the real network layer for a MockWebServer during E2E UIAutomator tests?

**13. Checkpoint**
Name two scenarios where a JVM unit test using Robolectric is insufficient and a real device instrumented test is mandatory.

---

## 11. Deliberate Breaking & Mutation Testing Discipline

**1. What is it**
The practice of intentionally introducing bugs (mutations) into your production code to verify that your test suite actually catches them and fails with a meaningful error message.

**2. Why does it exist**
Writing a test that passes gives a false sense of security. You must prove the test can fail. A test that always passes, even when the code is broken, is worse than no test at all.

**3. Mental model**
It's like installing a smoke detector. Pressing the "test" button only proves the battery works. To prove it actually detects smoke, you have to intentionally create smoke near it and see if the alarm sounds.

**4. How it works**
After writing a test and seeing it pass (Green), you intentionally break the production code (e.g., change `+` to `-`, negate an `if` condition, return `null`). You run the test again. It *must* fail (Red), and the failure message must clearly explain what went wrong. Then you fix the code (Green).

**5. Code (Process)**
```kotlin
// 1. Production Code
fun calculateTax(amount: Double): Double = amount * 0.10

// 2. Test Code
@Test fun `tax calculation`() {
    assertThat(calculateTax(100.0)).isEqualTo(10.0)
}
// 3. See it Pass ✅

// 4. Deliberate Break
fun calculateTax(amount: Double): Double = amount * 0.20

// 5. See it Fail ❌ -> "Expected 10.0 but was 20.0"

// 6. Fix Code ✅
```

**6. Production usage**
Senior engineers do this instinctively for every critical test they write. Automated mutation testing tools (like PITest) can automate this process, but the manual discipline is essential.

**7. Common mistakes**
- **Writing tests after the fact:** Writing tests for code that already works without ever seeing them fail.
- **Ignoring assertion messages:** A test fails, but the error is a cryptic `NullPointerException` instead of `Expected X but got Y`.
- **Testing the mock:** Breaking the mock configuration instead of the production code.

**8. Debugging**
If you break the production code and the test still passes, your test is flawed. You are likely mocking the thing you are trying to test, or your assertions are not checking the right conditions.

**9. Testing**
This is a meta-testing concept. It's how you test your tests.

**10. Exercise**
Take any existing test in your codebase. Comment out a crucial line of business logic in the production code it covers. If the test still passes, rewrite the test until it fails appropriately.

**11. Deliberate failure**
(This concept *is* the deliberate failure).

**12. Interview questions**
- **Junior:** Why is it important to see a test fail before seeing it pass?
- **Mid:** What is Mutation Testing and how does it improve test suite quality?
- **Senior:** Describe a time when a green test suite hid a critical bug in production. How did you change your testing strategy to prevent it from happening again?

**13. Checkpoint**
"A test you have never seen fail is not yet a test." Explain this quote in the context of TDD or test maintenance.

---

## Phase 10 Project — Expense Tracker v9 (The Full Test Pyramid)

**Goal:** Implement a production-grade test suite covering every layer of the Expense Tracker.

**Requirements:**
1. **Data & Domain Tests (JVM):**
   - Unit tests for `ExpenseDto.toDomain()` mapper covering all malformed/nullable edge cases.
   - Unit tests for `GetExpensesUseCase` and `AddExpenseUseCase` using `FakeExpenseRepository`.
   - Unit test for `ExpenseSummaryOrchestrator`.
2. **Presentation Tests (JVM):**
   - Unit tests for `ExpenseListViewModel` using Turbine and `StandardTestDispatcher`, asserting `Loading` → `Content` transitions and error retry logic.
3. **Compose UI Tests (JVM / Robolectric):**
   - Compose tests for `ExpenseListScreen` asserting: 1) Shimmer/spinner shown on `Loading`, 2) Empty state illustration shown on `Empty`, 3) List items rendered on `Success`, 4) Retry button click fires callback on `Error`.
4. **Screenshot Tests (Roborazzi):**
   - Capture golden screenshot records of `ExpenseDashboardScreen` in both Light and Dark modes.
5. **Deliberate Mutation Verification:**
   - For each test written, introduce a deliberate bug in production code, run `./gradlew test`, and record the failure before fixing it.

---

## Phase 10 Checkpoint

Answer without looking:
1. What does `runTest`'s virtual time do when your production code executes `delay(30_000)`?
2. Why is using a hand-written `FakeExpenseRepository` generally superior to writing `every { repo.getExpenses() } returns flowOf(...)` with MockK?
3. What is the difference between `StandardTestDispatcher` and `UnconfinedTestDispatcher`, and when should you choose each?
4. How do `Modifier.testTag` and Compose semantics matchers replace Selenium's `By.xpath` or `By.id`?
5. Why is `Thread.sleep()` in a unit or UI test considered an antipattern, and what should you use instead?

---

## Complete QA / Test Automation (TestNG / Selenium / Appium) → Android Test Pyramid Translation Table

| QA / Automation Concept (TestNG / Selenium / Appium) | Android Modern Testing Equivalent | Notes |
|---|---|---|
| TestNG `@Test`, `@BeforeMethod` | JUnit 4/5 `@Test`, `@Before` / `@BeforeEach` | JVM unit test runner |
| TestNG `Assert.assertEquals()` | Google Truth `assertThat().isEqualTo()` / Kotlin Test | Fluent assertions |
| Selenium `driver.findElement(By.id("..."))` | Compose `composeTestRule.onNodeWithTag("...")` | Semantics tree node finder |
| Selenium `element.click()` / `sendKeys()` | Compose `.performClick()` / `.performTextInput()` | Synthetic user interactions |
| Explicit Wait (`WebDriverWait.until(...)`) | Turbine `awaitItem()` / `advanceUntilIdle()` | Virtual time advancement & Flow assertions |
| `Thread.sleep(5000)` (Flaky wait) | `advanceTimeBy(5000)` (Virtual time in `runTest`) | Executes in 0 milliseconds |
| Page Object Model class | Stateless `@Composable` Screen + Test | Screen state tested in isolation |
| Appium device farm test (SauceLabs/BrowserStack) | Roborazzi / Paparazzi JVM Screenshot Tests | Runs on local JVM in 200ms without device |
| Mockoon / WireMock server | In-memory Fakes / `MockWebServer` | Fast local network doubles |
| Appium E2E Automation | Maestro / UIAutomator | High-level critical journey validation |
