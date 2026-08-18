# PHASE 5 — APP ARCHITECTURE (Weeks 9–10)

**Objective:** Know where any given piece of code belongs, why it belongs there, and be able to defend architectural decisions with tradeoffs rather than dogma.  
**Why this phase matters:** As codebases grow, disorganized code leads to massive merge conflicts, untestable ViewModels, leaked platform classes, and rigid coupling. Clean Architecture and Unidirectional Data Flow (UDF) ensure that UI, business logic, and data sources can be changed and tested independently.  
**Prerequisites:** Phase 1 (Kotlin), Phase 2 (Coroutines & Flow), Phase 3 (Android Platform), Phase 4 (Jetpack Compose).  
**Project deliverable:** Expense Tracker v4 — Complete layered Clean Architecture slice (UI → ViewModel → Use Case → Repository → Data Sources) with DTO ↔ Domain ↔ UI mappers and offline-first cache abstraction.  
**Concepts covered:** 11 total, each with the full 13-step teaching sequence.

---

## 1. Google's Recommended Architecture & Layer Boundaries

### 1. What is it
A structured separation of concerns into three specific layers: UI (presentation), Domain (optional but recommended for business rules), and Data. The strict rule is that data flows down (UI requests data from Data layer), and events/state flow up (Data layer emits state to UI).

### 2. Why does it exist
In Android's early days, everything went into `Activity` or `Fragment` classes (Massive View Controller). This made testing impossible, reuse difficult, and UI configuration changes (like rotation) caused memory leaks and lost data. By separating UI from Data, we can unit test business logic, replace data sources (e.g., SQLite to Realm or Room) without touching the UI, and keep the UI reactive.

### 3. Mental model
Think of Spring Boot's Controller-Service-Repository pattern.
- **UI Layer (ViewModel + Compose)** = Spring `@Controller` (handles input, returns formatted views/state).
- **Domain Layer (Use Cases)** = Spring `@Service` (orchestrates business logic, applies rules).
- **Data Layer (Repository)** = Spring `@Repository` (fetches/saves data using DAOs).

### 4. How it works
- **UI Layer**: Compose functions display `UiState` and send user actions to the `ViewModel`. The `ViewModel` communicates with the Domain or Data layers.
- **Domain Layer**: Contains `UseCases` (or Interactors) that encapsulate a single business rule (e.g., `CalculateTaxUseCase`). It acts as a mediator between UI and Data.
- **Data Layer**: Contains `Repositories` and `DataSources` (Network, DB). It exposes data as streams (Kotlin `Flow`) to the layers above.

### 5. Code
```kotlin
// --- DATA LAYER ---
class ExpenseRepositoryImpl(
    private val api: ExpenseApi,
    private val dao: ExpenseDao
) : ExpenseRepository {
    override fun getExpenses(): Flow<List<Expense>> = dao.observeExpenses().map { it.toDomain() }
}

// --- DOMAIN LAYER ---
class GetHighValueExpensesUseCase(
    private val repository: ExpenseRepository
) {
    operator fun invoke(): Flow<List<Expense>> {
        return repository.getExpenses().map { list ->
            list.filter { it.amount > 1000.0 }
        }
    }
}

// --- UI LAYER ---
class ExpenseViewModel(
    private val getHighValueExpenses: GetHighValueExpensesUseCase
) : ViewModel() {
    val uiState: StateFlow<ExpenseUiState> = getHighValueExpenses()
        .map { ExpenseUiState.Success(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExpenseUiState.Loading)
}
```

> **[Extension] Note on `SharingStarted.WhileSubscribed(5000)`:** This shows up in almost every `stateIn()` call in production code, but it's rarely explained. `SharingStarted` has three strategies: `Eagerly` (start collecting the upstream Flow immediately and never stop, even with zero subscribers — wastes resources), `Lazily` (start on first subscriber, but then never stop, even after the last subscriber leaves — still leaks background work), and `WhileSubscribed(stopTimeoutMillis)` (start on first subscriber, stop `stopTimeoutMillis` after the *last* subscriber disappears). The magic number `5000` (5 seconds) is Google's own recommended default: it's long enough to survive a configuration change (screen rotation briefly unsubscribes and resubscribes the Composable) or a quick app-switch, but short enough that backgrounding the app for good eventually cancels the expensive upstream work (Room queries, network polling) instead of leaking it forever. For ViewModel-scoped `StateFlow` exposed to the UI, `WhileSubscribed(5000)` is almost always the right choice — `Lazily` and `Eagerly` should be reserved for flows you deliberately want to keep alive for the whole app/ViewModel lifetime.

### 6. Production usage
In the Expense Tracker, when a user wants to see their monthly summary, the Compose UI calls `viewModel.loadSummary()`, the ViewModel invokes `GetMonthlySummaryUseCase`, which fetches data from `ExpenseRepository`, which checks the local Room database first, falling back to Retrofit if empty.

### 7. Common mistakes
- ❌ **Wrong:** UI layer directly accessing `ExpenseDao` or Retrofit APIs.
- ❌ **Wrong:** Passing Context or View instances into the Data layer.
- ✅ **Right:** UI observes a `Flow` from a ViewModel; ViewModel calls a UseCase/Repository.

### 8. Debugging
When data isn't updating on screen: Check if the `Flow` chain is broken. Did a `map` or `catch` block swallow the emission? Use `.onEach { Log.d("TAG", "Emitted: $it") }` at layer boundaries to see where the data stops.

### 9. Testing
Because layers are decoupled, you can unit test the ViewModel by passing a fake/mock UseCase. You can test the UseCase by passing a fake Repository.
```kotlin
@Test
fun `usecase filters out low value expenses`() = runTest {
    val fakeRepo = FakeExpenseRepository(listOf(Expense(id="1", amount=500.0), Expense(id="2", amount=1500.0)))
    val useCase = GetHighValueExpensesUseCase(fakeRepo)
    val result = useCase().first()
    assertEquals(1, result.size)
    assertEquals(1500.0, result.first().amount)
}
```

### 10. Exercise
Create a layered structure for a User Profile screen. 
1. Define a `UserRepository` interface and Fake implementation.
2. Define a `GetUserProfileUseCase`.
3. Define a `ProfileViewModel` that exposes a `StateFlow<ProfileState>`.

### 11. Deliberate failure
Have the ViewModel hold a reference to a `Compose` state object or an `Activity` context. Rotate the device (or simulate configuration change). Watch the memory leak or crash happen.

### 12. Interview questions
- **Q:** What is the difference between a Repository and a Data Source? (A Data Source connects to one specific API/DB; a Repository orchestrates multiple Data Sources to provide a single truth.)
- **Q:** When would you skip the Domain layer? (When the app is a simple CRUD app with no complex business rules—ViewModel can call Repository directly.)

### 13. Checkpoint
Can you map an Android UI action all the way down to a network request and explain how the result propagates back up through the layers?

---

## 2. The Inward Dependency Rule & Dependency Inversion

### 1. What is it
The architecture is structured in concentric circles (Clean Architecture). The core rule is that inner layers (Domain) know NOTHING about outer layers (Data, UI, Frameworks). Outer layers depend on inner layers. If the Domain needs data from the outside, it defines an interface (Dependency Inversion), and the outer layer implements it.

### 2. Why does it exist
If your business logic depends directly on Room (SQLite) or Retrofit (Network), changing your database or API client means rewriting your business logic. By enforcing inward dependencies, your core business rules become purely Kotlin, instantly testable on the JVM, and independent of Android OS lifecycles.

### 3. Mental model
Think of a power outlet in your house. The house (Domain) defines the outlet interface (shape and voltage). The TV or Microwave (Data implementations) must build a plug that fits that interface. The house doesn't care if it's a Sony TV or a toaster, as long as it implements the plug correctly.

### 4. How it works
The Domain layer declares an interface: `interface ExpenseRepository { fun getExpenses(): List<Expense> }`.
The Data layer (which depends on the Domain layer so it knows about the interface) implements it: `class ExpenseRepositoryImpl : ExpenseRepository`.
At runtime, Dependency Injection (like Hilt or Dagger) wires the `Impl` to wherever the interface is requested.

### 5. Code
```kotlin
// --- DOMAIN MODULE (Pure Kotlin, no Android imports) ---
package com.tracker.domain

data class Expense(val id: String, val amount: Double)

// Domain defines the contract
interface ExpenseRepository {
    suspend fun save(expense: Expense)
}

class SaveExpenseUseCase(private val repo: ExpenseRepository) {
    suspend operator fun invoke(expense: Expense) {
        if (expense.amount > 0) repo.save(expense)
    }
}

// --- DATA MODULE (Android/Framework dependent) ---
package com.tracker.data
import com.tracker.domain.Expense
import com.tracker.domain.ExpenseRepository

// Data implements the contract
class RoomExpenseRepository(private val dao: ExpenseDao) : ExpenseRepository {
    override suspend fun save(expense: Expense) {
        dao.insert(expense.toEntity()) // toEntity maps Domain -> Data
    }
}
```

### 6. Production usage
In our Expense Tracker, the core rules of tax calculation and spending limits are in the Domain layer. We can compile and run tests on this layer in milliseconds because it has zero Android dependencies. The data persistence details (Room database) are plugged in at runtime.

### 7. Common mistakes
- ❌ **Wrong:** Domain module having a Gradle dependency on `androidx.room:room-runtime` or `com.squareup.retrofit2`.
- ❌ **Wrong:** Repository interfaces declared in the Data layer. (They must be in Domain).
- ✅ **Right:** Domain declares interface; Data implements it; UI calls Domain.

### 8. Debugging
If you try to use a Room annotation like `@Entity` inside your domain model, the compiler will fail if you set up your Gradle modules correctly (Domain module shouldn't have Room dependencies). This compiler error is a feature, not a bug!

### 9. Testing
Because of Dependency Inversion, you can trivially unit test the Domain layer using simple in-memory implementations of the interfaces.
```kotlin
class InMemoryExpenseRepository : ExpenseRepository {
    val items = mutableListOf<Expense>()
    override suspend fun save(expense: Expense) { items.add(expense) }
}
```

### 10. Exercise
Refactor a tightly coupled class. Given a `ReceiptScanner` class that directly creates a `java.io.File` and calls a specific OCR API, extract an interface `ImageProcessor` into the domain, and move the OCR implementation to the data layer.

### 11. Deliberate failure
Try to make the Domain layer module depend on the Data layer module in `build.gradle.kts`. Then make Data depend on Domain. Observe the "Circular Dependency" Gradle error.

### 12. Interview questions
- **Q:** Explain Dependency Inversion vs Dependency Injection. (Inversion is the architectural principle of depending on abstractions; Injection is the mechanism/tool used to provide concrete implementations of those abstractions at runtime.)
- **Q:** Why shouldn't the Data layer define the Repository interface? (Because then the Domain would have to depend on the Data layer to know about the interface, violating the inward dependency rule.)

### 13. Checkpoint
Are your Domain interfaces defined by what the business logic *needs*, rather than what the database *provides*?

---

## 3. Zero Android Imports in the Domain Layer

### 1. What is it
A strict rule that the Domain Layer (Entities and Use Cases) must be 100% pure Kotlin. No `android.*` or `androidx.*` imports are allowed.

### 2. Why does it exist
The Android framework is notoriously difficult to mock and test on a local JVM. If a UseCase imports `android.content.Context` to read a string resource or a SharedPreference, you can no longer run a fast JUnit test; you need an instrumented test (running on an emulator) or a complex mocking framework like Robolectric.

### 3. Mental model
Imagine writing the rules for Chess. The rules (Domain) shouldn't care if they are being played on a wooden board, a plastic board, or an Android tablet. If your chess rules import `android.graphics.Canvas`, they are permanently tied to Android.

### 4. How it works
When the Domain layer needs something from the Android system (like string localization, checking network connectivity, or accessing SharedPreferences), you define a generic interface in the Domain layer and implement it in a platform-specific wrapper in the Data or UI layer.

### 5. Code
```kotlin
// --- DOMAIN LAYER (Pure Kotlin) ---
// ❌ WRONG: Don't do this
class FormatCurrencyUseCase(private val context: Context) {
    fun invoke(amount: Double): String {
        return context.getString(R.string.currency_format, amount)
    }
}

// ✅ RIGHT: Define a capability interface
interface ResourceProvider {
    fun getCurrencyString(amount: Double): String
}

class FormatCurrencyUseCase(private val resourceProvider: ResourceProvider) {
    fun invoke(amount: Double): String = resourceProvider.getCurrencyString(amount)
}

// --- APP/FRAMEWORK LAYER ---
class AndroidResourceProvider(private val context: Context) : ResourceProvider {
    override fun getCurrencyString(amount: Double): String {
        return context.getString(R.string.currency_format, amount)
    }
}
```

### 6. Production usage
In the Expense Tracker, we need to schedule a background sync. The Domain layer has a `SyncScheduler` interface. The implementation in the framework layer uses Android's `WorkManager`. The domain logic orchestrates the scheduling without knowing what WorkManager is.

### 7. Common mistakes
- ❌ **Wrong:** Passing `Context` or `Activity` into a UseCase.
- ❌ **Wrong:** Using `LiveData` in the Domain layer (LiveData is part of `androidx.lifecycle`).
- ✅ **Right:** Using pure Kotlin `Flow` in the Domain layer and passing wrapper interfaces.

### 8. Debugging
Use a multimodule architecture. Create a `domain` module as a pure `java-library` or `kotlin("jvm")` module, not an `com.android.library`. The build system will physically prevent you from importing Android classes.

### 9. Testing
Because the Domain layer is pure Kotlin, your JUnit tests run in milliseconds.
```kotlin
@Test
fun `test format currency`() {
    val fakeProvider = object : ResourceProvider {
        override fun getCurrencyString(amount: Double) = "$$amount"
    }
    val useCase = FormatCurrencyUseCase(fakeProvider)
    assertEquals("$50.0", useCase(50.0))
}
```

### 10. Exercise
You have a UseCase that checks if the user has a valid session token. The token is stored in Android's `SharedPreferences`. Extract this into a pure Kotlin domain interface and an Android-specific implementation.

### 11. Deliberate failure
Try to return a `android.net.Uri` from a UseCase. Try to write a unit test for it without Robolectric. Watch the "Method ... not mocked" error from the Android SDK stub.

### 12. Interview questions
- **Q:** How do you handle string localization in the Domain layer? (You don't. The Domain should handle IDs or raw data, and localization should happen in the UI layer, OR you provide a `StringProvider` interface).
- **Q:** Why is Kotlin `Flow` acceptable in the Domain layer but `LiveData` is not? (`Flow` is part of the core Kotlin Coroutines library, independent of Android. `LiveData` is tied to Android's Lifecycle).

### 13. Checkpoint
Can your Domain module compile and run on a backend Ktor server or a Desktop JVM application without modifications?

---

## 4. The Repository as the Single Source of Truth (SSOT)

### 1. What is it
The Repository pattern mediates between the domain and data mapping layers, acting like an in-memory collection of domain objects. It hides the complexity of managing multiple data sources (Network, Local DB, Memory Cache) and exposes a single consistent stream of data.

### 2. Why does it exist
If a ViewModel directly calls a network API and a database, it has to handle the synchronization logic: "Fetch from network, if successful save to DB, then show DB. If offline, just show DB." This creates complex, duplicated code across view models. The Repository centralized this "offline-first" cache policy.

### 3. Mental model
Think of a Library Help Desk. You ask the librarian (Repository) for a book. You don't care if they get it from the display shelf (Memory Cache), the back room (Local DB), or order it from another branch (Network API). You just get the book.

### 4. How it works
The typical Android SSOT pattern uses the Database as the single source of truth.
1. The Repository returns a `Flow` observing the Room database.
2. The Repository concurrently triggers a network fetch.
3. When the network returns, it saves the data into the Room database.
4. Room automatically emits the new data to the `Flow`.
5. The UI updates reactively.

### 5. Code
```kotlin
class ExpenseRepositoryImpl(
    private val dao: ExpenseDao,
    private val api: ExpenseNetworkService
) : ExpenseRepository {

    // 1. Return a Flow that constantly observes the local DB.
    // Callers always see the DB as the source of truth.
    override fun getExpenses(): Flow<List<Expense>> {
        return dao.observeAll().map { entities -> entities.map { it.toDomain() } }
    }

    // 2. Separate method to refresh data from network
    override suspend fun syncExpenses() {
        try {
            val remoteData = api.fetchExpenses()
            // 3. Save to DB. The Flow in getExpenses() will automatically emit!
            dao.insertAll(remoteData.map { it.toEntity() })
        } catch (e: Exception) {
            // Handle network failure (e.g., ignore if we want offline support)
            Log.e("Repo", "Sync failed, relying on cache")
        }
    }
}
```

### 6. Production usage
In the Expense Tracker, when the app opens, we instantly show the cached expenses from Room (zero loading screens). In the background, `syncExpenses()` runs. If the user added an expense on the web dashboard, it fetches it, saves it to Room, and the Android UI instantly animates the new item into the list.

### 7. Common mistakes
- ❌ **Wrong:** Returning network data directly to the ViewModel, bypassing the local database.
- ❌ **Wrong:** ViewModels deciding whether to call `dao.get()` or `api.get()`.
- ✅ **Right:** ViewModels always observe the Repository; Repository manages the DB/Network dance.

### 8. Debugging
If the UI shows stale data, check if the network call succeeded but failed to write to the DB, or if the `observeAll()` flow from Room is correctly wired to the UI.

### 9. Testing
Test the cache policy: Call the repository, mock a network failure, and ensure it still returns the local database items. Mock a network success, and verify that the `dao.insertAll` was called with the correct mapped entities.

### 10. Exercise
Implement a `NetworkBoundResource` flow builder or a simple function that emits `Resource.Loading`, fetches from local, attempts network fetch, updates local, and emits `Resource.Success`.

### 11. Deliberate failure
Have the repository return `api.fetchExpenses()` directly. Turn off the device's WiFi and open the app. Observe the crash or empty screen, proving the lack of an offline-first SSOT.

### 12. Interview questions
- **Q:** How do you resolve conflicts if the local DB was updated while offline, but the server also changed? (This is complex; usually involves timestamping, 'dirty' flags for sync queues, or conflict resolution algorithms on the backend).
- **Q:** Why expose a `Flow` from the DB instead of a suspend function returning a `List`? (A `Flow` stays open and automatically pushes updates whenever the DB changes, ensuring the UI is never stale.)

### 13. Checkpoint
Can you draw the flow of data from a Retrofit response down to Room and back up to Compose without the ViewModel ever knowing about Retrofit?

---

## 5. Mappers at Every Boundary

### 1. What is it
Mapping functions (usually extension functions) that convert data models between layers. For example, converting a `NetworkExpenseDto` (from API) into a `ExpenseEntity` (for Database), and then into a `Expense` (Domain model).

### 2. Why does it exist
APIs change. Databases have constraints. If you use the same data class for Retrofit, Room, and your UI, you tightly couple your entire app to the backend's JSON structure. If the backend renames `expense_amt` to `amount`, your whole app breaks. Mappers isolate these changes to a single file.

### 3. Mental model
Think of electrical adapters for international travel. The wall socket provides 220V with round pins (Network DTO). Your laptop expects 110V with flat pins (Domain Model). The power adapter (Mapper) translates the shape and voltage so your laptop doesn't explode.

### 4. How it works
You define separate data classes for each layer:
- `ExpenseDto` (Data layer - Network): Has `@SerializedName`, nullable fields for safety.
- `ExpenseEntity` (Data layer - DB): Has `@Entity`, `@PrimaryKey`.
- `Expense` (Domain layer): Clean pure Kotlin class, strict nullability, business validations.
- `ExpenseUiModel` (UI layer): Formatted strings (e.g., "$50.00" instead of `50.0`).

### 5. Code
```kotlin
// --- NETWORK DTO ---
data class ExpenseDto(
    @SerializedName("id") val id: String?,
    @SerializedName("expense_amt") val amount: Double?,
    @SerializedName("desc") val description: String?
)

// --- DOMAIN MODEL ---
data class Expense(val id: String, val amount: Double, val description: String)

// --- MAPPER ---
fun ExpenseDto.toDomain(): Expense? {
    // Resilience: Log and drop malformed items rather than crashing the whole list
    if (id == null || amount == null) return null 
    return Expense(
        id = this.id,
        amount = this.amount,
        description = this.description ?: "No description" // Provide safe defaults
    )
}

// In Repository:
// val remoteList = api.getExpenses()
// val validDomainList = remoteList.mapNotNull { it.toDomain() }
```

### 6. Production usage
In real apps, backends frequently return nulls or missing fields unexpectedly. A robust mapper acts as a shield. If an array of 50 expenses contains 1 corrupted item, `mapNotNull` safely drops the bad item and passes 49 good items to the UI, instead of crashing the entire screen with a `NullPointerException`.

### 7. Common mistakes
- ❌ **Wrong:** Using `@Entity` and `@SerializedName` on the exact same data class to "save time."
- ❌ **Wrong:** Crashing the app if a non-critical field from the API is null.
- ✅ **Right:** Domain models having zero annotations. Extension functions handling mapping safely.

### 8. Debugging
When data appears incorrect on screen, trace it back through the mappers. Is the network returning cents but the UI expects dollars? The mapper is the exact place to handle that conversion.

### 9. Testing
Mappers are pure functions and incredibly easy to test.
```kotlin
@Test
fun `maps valid DTO to Domain`() {
    val dto = ExpenseDto("1", 50.0, null)
    val domain = dto.toDomain()
    assertEquals("1", domain?.id)
    assertEquals("No description", domain?.description) // tests default fallback
}
```

### 10. Exercise
Create a `UserDto` with a `birth_date` string (e.g., "1990-01-01"). Create a `User` domain model with an `Int` representing `age`. Write a mapper that calculates the age during the conversion.

### 11. Deliberate failure
Force a single model across all layers. Add a Room `@ColumnInfo` annotation. Then try to reuse that core Domain module in an iOS Kotlin Multiplatform project. Watch it fail because iOS doesn't have Room.

### 12. Interview questions
- **Q:** Isn't creating 3 different data classes for the same object a lot of boilerplate? (Yes, but it's "necessary boilerplate." It prevents catastrophic coupling. Code generators or tools like MapStruct in Java can help, but in Kotlin, simple extension functions are preferred).
- **Q:** Where should formatting (like converting a Date to "Today at 5PM") happen? (In a mapper converting the Domain model to a UI State model, usually in the ViewModel).

### 13. Checkpoint
If the backend completely redesigns their JSON structure, how many files in your presentation and domain layers will you need to change? (Answer: Zero. Only the DTO and the Mapper should change.)

---

## 6. ViewModel as a Pure State Holder

### 1. What is it
In modern Android, the `ViewModel` is an orchestrator. It does exactly two things:
1. Translates data from UseCases/Repositories into an immutable `UiState` representing the screen.
2. Accepts user intents (clicks, typing) and triggers the appropriate business logic.

### 2. Why does it exist
Android destroys and recreates Views (Activities/Fragments/Compose nodes) constantly (e.g., when rotating the screen). If state is held in the View, it is lost. The `ViewModel` survives these configuration changes. By making it a "Pure State Holder", we enforce Unidirectional Data Flow—the UI is a dumb reflection of the ViewModel's state.

### 3. Mental model
Think of a puppeteer and a puppet. The `ViewModel` is the puppeteer. The `UiState` is the strings. The Compose UI is the puppet. The puppet makes no decisions; it just moves exactly how the strings dictate. If the puppet gets destroyed and rebuilt, the puppeteer just attaches the strings to the new puppet.

### 4. How it works
The ViewModel exposes a single `StateFlow<UiState>`. It never exposes mutable flows to the UI. It never holds references to UI elements (no `View`, no `Context`, no `NavController`). When a user clicks a button, the UI calls a function on the ViewModel (e.g., `onSaveClicked()`), the ViewModel does the work, mutates its internal state, and emits a new immutable `UiState`.

### 5. Code
```kotlin
// 1. Define the complete state of the screen
data class AddExpenseUiState(
    val amount: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSavedCompleted: Boolean = false
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val saveExpenseUseCase: SaveExpenseUseCase
) : ViewModel() {

    // 2. Private mutable state
    private val _uiState = MutableStateFlow(AddExpenseUiState())
    
    // 3. Public immutable state exposed to UI
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    // 4. Intent handling
    fun onAmountChanged(newAmount: String) {
        _uiState.update { it.copy(amount = newAmount, errorMessage = null) }
    }

    fun onSaveClicked() {
        val amountDouble = uiState.value.amount.toDoubleOrNull()
        if (amountDouble == null) {
            _uiState.update { it.copy(errorMessage = "Invalid amount") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                saveExpenseUseCase(Expense(id = UUID.randomUUID().toString(), amount = amountDouble))
                _uiState.update { it.copy(isLoading = false, isSavedCompleted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Save failed") }
            }
        }
    }
}
```

### 6. Production usage
In Compose, the UI simply observes this state:
`val state by viewModel.uiState.collectAsStateWithLifecycle()`
If `state.isLoading` is true, show a spinner. If `state.errorMessage` is not null, show a Snackbar. If `state.isSavedCompleted` is true, trigger navigation back.

### 7. Common mistakes
- ❌ **Wrong:** `var amount = mutableStateOf("")` inside a Composable instead of hoisting to ViewModel.
- ❌ **Wrong:** ViewModel holding a reference to `Context` to show a Toast.
- ❌ **Wrong:** Exposing `MutableStateFlow` directly to the UI, allowing the UI to mutate state bypassing the ViewModel.

### 8. Debugging
If the screen state is weird (e.g., loading spinner won't disappear), check the `_uiState.update` blocks. Are you remembering to reset `isLoading = false` in both the `try` and `catch` blocks?

### 9. Testing
ViewModels are tested by calling an intent and asserting the state flow emissions.
```kotlin
@Test
fun `invalid amount shows error state`() = runTest {
    val viewModel = AddExpenseViewModel(fakeUseCase)
    viewModel.onAmountChanged("abc")
    viewModel.onSaveClicked()
    
    assertEquals("Invalid amount", viewModel.uiState.value.errorMessage)
}
```

### 10. Exercise
Create a `LoginViewModel` with a `LoginUiState` containing `username`, `password`, `isSubmitting`, and `error`. Implement the intent functions to update the credentials and submit the form, updating the state accordingly.

### 11. Deliberate failure
Pass a `Context` into the ViewModel constructor. In the `onSaveClicked` method, use that context to show an `AlertDialog`. Rotate the device while the dialog is showing. Watch the `WindowLeaked` crash.

### 12. Interview questions
- **Q:** Why use `StateFlow` instead of `LiveData` in modern Android? (`StateFlow` is pure Kotlin and works cleanly with Coroutines and Multiplatform. `LiveData` only runs on the main thread and is tied to the Android framework.)
- **Q:** What is the `viewModelScope`? (A CoroutineScope tied to the ViewModel's lifecycle. Any coroutines launched in it are automatically cancelled when the ViewModel is cleared, preventing memory leaks.)

### 13. Checkpoint
Can your UI layer be completely deleted and replaced with a Command Line Interface (CLI) without modifying a single line of your ViewModel logic?

---
*End of Phase 5 - Part 1.*


---

## 7. Precise Class Roles Breakdown

### 1. What is it
A strict, enforceable set of rules defining exactly what each component in Clean Architecture (Composable, ViewModel, Use Case, Orchestrator, Repository, Data Source, Mapper) is responsible for, and crucially, what it is forbidden to do.

### 2. Why does it exist
In large codebases, boundaries blur. ViewModels start making network calls, Repositories contain business logic, and UI components format data. Strict roles prevent the "God object" anti-pattern and ensure every class has exactly one reason to change (Single Responsibility Principle).

### 3. Mental model
Think of a restaurant kitchen:
*   **Data Source:** The supplier delivering raw ingredients (DTOs/Entities).
*   **Repository:** The pantry manager receiving deliveries, storing them, and providing them to the chefs.
*   **Use Case:** The prep cook chopping vegetables (single business rule).
*   **Orchestrator:** The head chef assembling the chopped veg, cooked meat, and sauce into a final dish.
*   **ViewModel:** The waiter taking the completed dish and placing it on a tray (formatting for UI).
*   **Composable:** The customer eating the food (displaying pixels).
*   **Mapper:** The translation dictionary between the supplier's invoice, the chef's recipe, and the menu description.

### 4. How it works
By defining rules on **imports** and **injected dependencies**:
*   **Composable:** Only imports UI components and `UiState`. Cannot import Repositories.
*   **ViewModel:** Injects Use Cases/Orchestrators. Maps domain data to `UiState`. Cannot import Android `Context` or Data Sources.
*   **Use Case:** Injects Repositories. Contains 100% pure Kotlin business logic. Zero Android imports.
*   **Orchestrator:** Injects multiple Use Cases. Coordinates complex flows.
*   **Repository:** Injects Data Sources. Handles data fetching/caching logic (Single Source of Truth). Returns Domain models.
*   **Data Source:** Injects Retrofit/Room. Returns DTOs/Entities.
*   **Mapper:** Pure functions converting between DTO ↔ Entity ↔ Domain ↔ UI models.

### 5. Code
```kotlin
// 1. Data Source (Raw data from network)
class RemoteExpenseDataSource(private val api: ExpenseApi) {
    suspend fun fetch(): List<ExpenseDto> = api.getExpenses()
}

// 2. Mapper (Boundary translation)
fun ExpenseDto.toDomain(): Expense = Expense(id = this.id, amount = this.amt)

// 3. Repository (Data strategy & coordination)
class ExpenseRepositoryImpl(
    private val remote: RemoteExpenseDataSource,
    private val local: LocalExpenseDataSource
) : ExpenseRepository {
    override fun getExpenses(): Flow<List<Expense>> = flow {
        val cached = local.getAll().map { it.toDomain() }
        emit(cached)
        val fresh = remote.fetch().map { it.toDomain() }
        local.saveAll(fresh.map { it.toEntity() })
        emit(fresh)
    }
}

// 4. Use Case (Single business rule)
class GetHighValueExpensesUseCase(
    private val repository: ExpenseRepository
) {
    operator fun invoke(threshold: Double): Flow<List<Expense>> {
        return repository.getExpenses().map { list ->
            list.filter { it.amount > threshold }
        }
    }
}

// 5. Orchestrator (Combining multiple rules)
class ExpenseSummaryOrchestrator(
    private val getExpenses: GetHighValueExpensesUseCase,
    private val getBudget: GetBudgetUseCase
) {
    operator fun invoke(): Flow<ExpenseSummary> {
        return combine(getExpenses(100.0), getBudget()) { expenses, budget ->
            ExpenseSummary(total = expenses.sumOf { it.amount }, limit = budget)
        }
    }
}

// 6. ViewModel (State management for UI)
class ExpenseViewModel(
    private val summaryOrchestrator: ExpenseSummaryOrchestrator
) : ViewModel() {
    val uiState: StateFlow<UiState> = summaryOrchestrator()
        .map { UiState.Success(it.toViewState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)
}

// 7. Composable (Dumb UI)
@Composable
fun ExpenseScreen(state: UiState) { /* Draw pixels */ }
```

### 6. Production usage
Enforced via static analysis tools like **Konsist** or **ArchUnit**. A test will literally fail the CI build if a ViewModel imports a Repository or a Domain class imports an Android class.

### 7. Common mistakes
*   **Passing Context to ViewModel:** Leads to memory leaks.
*   **Use Cases returning DTOs:** Leaks data layer details into the domain layer.
*   **Fat Repositories:** Putting business logic (e.g., filtering out invalid expenses based on user settings) inside the Repository instead of a Use Case.

### 8. Debugging
If a UI bug occurs (e.g., wrong date format), check the ViewModel/Mapper. If a business logic bug occurs (wrong calculation), check the Use Case. The rigid structure tells you exactly *where* to look.

### 9. Testing
Every component is tested in isolation. You fake the injected dependencies.
*   Test ViewModel: Fake Use Case.
*   Test Use Case: Fake Repository.
*   Test Repository: Fake Data Sources.

### 10. Exercise
Write a Konsist or ArchUnit test (pseudocode or real if you know it) that verifies all classes in the `domain` package do not import anything from the `android.*` package.

### 11. Deliberate failure
```kotlin
// BAD: Repository doing business logic and returning a DTO to the ViewModel
class BadRepo(private val api: Api) {
    suspend fun getExpenses(): List<ExpenseDto> {
        val data = api.getExpenses()
        return data.filter { it.isValidUser } // Business logic!
    }
}
```
**Fix:** The repository should just return `List<Expense>`. A Use Case should do the filtering.

### 12. Interview questions
*   **Q:** Why not just have the ViewModel call the Repository directly? Why use a Use Case?
    *   **A:** If multiple ViewModels need the exact same business logic (e.g., calculate tax), you'd have to duplicate it. Use Cases allow reusing pure business logic across ViewModels.
*   **Q:** What is the difference between an Orchestrator and a Use Case?
    *   **A:** A Use Case does one thing. An Orchestrator combines multiple Use Cases to build a complex state required by a specific feature.

### 13. Checkpoint
Can you map a Page Object Model from QA to the Composable, and a Test Service to a Use Case? (Yes, boundaries are universal).

---

## 8. MVVM (Model-View-ViewModel) Pattern

### 1. What is it
An architectural pattern where the View (Composable) observes state from the ViewModel, and triggers actions by directly calling functions on the ViewModel.

### 2. Why does it exist
To separate UI rendering logic from state management and business logic coordination. It allows the UI to be a pure reflection of state, surviving configuration changes (like screen rotations).

### 3. Mental model
A dashboard (View) and a control panel (ViewModel). The dashboard has dials and lights that passively update based on the control panel's state. When you want to do something, you push a specific, labeled button on the control panel (calling a method like `viewModel.addExpense()`).

### 4. How it works
1.  **State:** ViewModel exposes a `StateFlow<UiState>`.
2.  **View:** Collects the state and renders it.
3.  **Action:** View triggers events by calling functions: `viewModel.onDeleteClicked(id)`.
4.  **Mutation:** ViewModel updates its internal state (e.g., `MutableStateFlow`), which emits a new state to the View.

### 5. Code
```kotlin
data class ExpenseState(
    val isLoading: Boolean = false,
    val expenses: List<Expense> = emptyList(),
    val error: String? = null
)

class ExpenseMvvmViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(ExpenseState())
    val state: StateFlow<ExpenseState> = _state.asStateFlow()

    init {
        loadExpenses()
    }

    private fun loadExpenses() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val data = repository.getExpenses().first()
                _state.update { it.copy(isLoading = false, expenses = data) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // Direct function call from UI
    fun addExpense(amount: Double) {
        viewModelScope.launch {
            repository.add(Expense(amount = amount))
            loadExpenses() // Reload
        }
    }
}

@Composable
fun ExpenseScreen(viewModel: ExpenseMvvmViewModel) {
    val state by viewModel.state.collectAsState()
    
    if (state.isLoading) CircularProgressIndicator()
    
    Button(onClick = { viewModel.addExpense(100.0) }) {
        Text("Add $100")
    }
}
```

### 6. Production usage
The default standard for ~80% of Android apps. It provides a great balance of low boilerplate and clean separation.

### 7. Common mistakes
*   **Multiple StateFlows:** Exposing `val isLoading = MutableStateFlow()`, `val data = MutableStateFlow()`, etc. This leads to inconsistent UI states (e.g., data is present but loading is still true). Always use a single `data class` for state.

### 8. Debugging
If the UI doesn't update, verify that the `_state.update { ... }` block is actually producing a *new* instance of the data class. Modifying a mutable list inside a data class won't trigger a Recomposition because the object reference hasn't changed.

### 9. Testing
Call the function, assert the state flow emits the expected values.
```kotlin
@Test
fun `addExpense updates state`() = runTest {
    viewModel.state.test {
        assertEquals(ExpenseState(isLoading = true), awaitItem())
        viewModel.addExpense(100.0)
        // assert subsequent states...
    }
}
```

### 10. Exercise
Add a `deleteExpense(id: String)` function to the MVVM ViewModel and wire it to a button in the UI.

### 11. Deliberate failure
```kotlin
// BAD: Mutating a list directly does not emit a new state
fun addExpenseBad(expense: Expense) {
    val currentList = _state.value.expenses as MutableList
    currentList.add(expense) 
    _state.value = _state.value.copy(expenses = currentList) // Same list reference!
}
```

### 12. Interview questions
*   **Q:** Why do we use `StateFlow` instead of `LiveData` in modern Android?
    *   **A:** `StateFlow` is pure Kotlin, meaning the ViewModel can be unit tested without Android dependencies or special JUnit rules. It also integrates seamlessly with Coroutines.

### 13. Checkpoint
Can you explain why calling `viewModel.addExpense()` directly from a button click is simple but might get messy if the screen has 50 different possible actions?

---

## 9. MVI (Model-View-Intent) Pattern

### 1. What is it
An architectural pattern enforcing a strict unidirectional, cyclical data flow. Instead of calling methods on the ViewModel, the View sends "Intents" (Events/Actions) to a single processor in the ViewModel, which returns a new State.

### 2. Why does it exist
As apps grow highly complex, having 20 distinct functions in a ViewModel (`onAddClicked`, `onDeleteClicked`, `onTextChanged`) becomes hard to track. State mutations can overlap, causing race conditions. MVI forces all actions through a single funnel (the Reducer), making state transitions highly predictable, testable, and even replayable.

### 3. Mental model
A Redux store in React, or a state machine. You don't tell the machine *what to do*; you just drop a coin in the slot (an Intent/Event), and the machine's internal gears (Reducer) calculate the new state based on the current state and the coin.

### 4. How it works
1.  **State:** The current state of the UI.
2.  **Intent (Event):** An action fired by the user (e.g., `UiEvent.AddExpense`).
3.  **Reducer:** A pure function: `(CurrentState, Intent) -> NewState`.
4.  **Side Effects:** Asynchronous work (network calls) triggered by Intents, which eventually emit *new* Intents (e.g., `UiEvent.ExpenseLoaded`) back into the Reducer.

### 5. Code (Exact same feature as MVVM)
```kotlin
// 1. Define Contract
data class MviState(val isLoading: Boolean = false, val expenses: List<Expense> = emptyList())

sealed interface MviEvent {
    object LoadExpenses : MviEvent
    data class ExpensesLoaded(val data: List<Expense>) : MviEvent
    data class AddExpense(val amount: Double) : MviEvent
}

class ExpenseMviViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MviState())
    val state: StateFlow<MviState> = _state.asStateFlow()

    // 2. The Single Funnel for UI Actions
    fun onEvent(event: MviEvent) {
        when (event) {
            is MviEvent.LoadExpenses -> loadExpenses()
            is MviEvent.AddExpense -> addExpense(event.amount)
            is MviEvent.ExpensesLoaded -> {
                // REDUCER: Pure state mutation
                _state.update { it.copy(isLoading = false, expenses = event.data) }
            }
        }
    }

    // 3. Side Effects
    private fun loadExpenses() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val data = repository.getExpenses().first()
            onEvent(MviEvent.ExpensesLoaded(data)) // Loop back to reducer
        }
    }

    private fun addExpense(amount: Double) {
        viewModelScope.launch {
            repository.add(Expense(amount = amount))
            onEvent(MviEvent.LoadExpenses) // Trigger reload
        }
    }
}

@Composable
fun ExpenseMviScreen(viewModel: ExpenseMviViewModel) {
    val state by viewModel.state.collectAsState()
    
    if (state.isLoading) CircularProgressIndicator()
    
    // UI fires an INTENT instead of calling a method
    Button(onClick = { viewModel.onEvent(MviEvent.AddExpense(100.0)) }) {
        Text("Add $100")
    }
}
```

### 6. Production usage
Standard in complex, highly interactive screens (e.g., a video editor screen, a complex filtering map). Often paired with frameworks like Orbit MVI or Mavericks to reduce boilerplate.

### 7. Common mistakes
*   **Over-engineering:** Using MVI for a simple "About Us" page. The boilerplate is significant.
*   **Impure Reducers:** Putting network calls *inside* the state reducer block. Reducers must be pure synchronous functions.

### 8. Debugging
Because every action is an object (`MviEvent`), you can easily log every transition: `Log.d("MVI", "State: $state, Event: $event")`. You get a perfect audit trail of exactly what the user did and how the state changed.

### 9. Testing
Extremely easy to test. You push a sequence of Events, and assert the resulting States.

### 10. Exercise
Compare the MVVM and MVI ViewModels above. Notice how MVI uses a `sealed interface` for events. Add a `DeleteExpense` event to the MVI implementation.

### 11. Deliberate failure
Putting async work in the reducer block. State mutations must be instant.

### 12. Interview questions
*   **Q:** What is the main drawback of MVI compared to MVVM?
    *   **A:** Boilerplate. For every simple action, you have to create a new class/object in a sealed interface, handle it in a `when` statement, and route it to logic.

### 13. Checkpoint
Can you explain the Unidirectional Data Flow? (State goes down to UI, Events go up to ViewModel, Reducer spits out new State).

---

### [Extension] MVVM vs MVI Side-by-Side Comparison

Neither pattern is objectively "better" — they trade off boilerplate against predictability. Use this table to answer Phase 5 Checkpoint Q3 (boilerplate, testability, debugging, team scalability) precisely rather than from memory of vibes.

| Criterion | MVVM (direct function calls) | MVI (Intent → Reducer → State) |
| :--- | :--- | :--- |
| **Boilerplate** | Low. One function per action (`onDeleteClicked()`). | Higher. Every action needs a sealed class member, a `when` branch, and often a matching "result" event for async work. |
| **Testability** | Good. Call the function, assert the resulting `StateFlow` value. | Excellent. Push a sequence of `Intent`s, assert the sequence of `State`s — the entire screen's behavior becomes a table of (input, output) pairs, which is trivial to golden-test or property-test. |
| **Debugging state regressions** | Harder at scale. To find *why* a field changed, you must find every function that could have called `_state.update`. | Easier at scale. Every state change is provoked by exactly one `Intent` passing through one `Reducer`. Logging `(oldState, event, newState)` gives a full replayable audit trail — this is why MVI is popular for screens with complex, overlapping async operations (multi-step forms, live collaborative editors). |
| **Team scalability** | Fine for small-to-medium screens (under ~10 actions). New engineers can read top-to-bottom function bodies immediately. | Shines on large screens with many contributors, because the `sealed interface` of Intents is a self-documenting, compiler-enforced list of "everything this screen can do." Merge conflicts tend to be additive (new `when` branch) rather than structural. |
| **Common failure mode** | Multiple independent `MutableStateFlow`s drift out of sync (loading=true but data already present). | Impure reducers: someone sneaks a network call or a `Random()` inside the `when` block that's supposed to be a pure state transition, destroying replayability and testability. |
| **When to reach for it** | CRUD screens, simple forms, most day-to-day feature work. | Screens with many concurrent async sources (search-as-you-type + filters + live sync), or screens where QA/support need a precise repro log of "what sequence of actions produced this bug." |

**The honest takeaway:** both patterns produce the exact same runtime shape — an immutable `UiState` flowing down and events flowing up. MVI just adds a compiler-enforced funnel for the "events flowing up" half. If your MVVM ViewModel is starting to grow a dozen barely-related public functions, that's the signal to migrate the class (not necessarily the whole app) to MVI — you don't need to pick one pattern for every screen in a codebase.

---

## 10. One-Off Events (Navigation, Snackbars)

### 1. What is it
Handling actions that should only happen exactly **once**, like showing a Toast, navigating to a new screen, or displaying a Snackbar.

### 2. Why does it exist
If you put `showSnackbar = true` in your `UiState`, rotate the device. The ViewModel survives, pushes the latest `UiState` to the recreated View, and the Snackbar shows *again*. We need a way to consume events so they don't replay on configuration changes.

### 3. Mental model
A doorbell (One-off event) vs a porch light (State). The porch light stays on (StateFlow) and you see it every time you look. The doorbell rings once (Event); if you were in the other room and missed it, it doesn't keep ringing indefinitely.

### 4. How it works
Two main approaches in modern Android:
*   **Option A (State Mutation):** Put the event in the state, and require the UI to call a `onSnackbarShown()` method to clear it.
*   **Option B (Channels):** Use a Kotlin `Channel` exposed as a `Flow` which inherently drops events once consumed.

### 5. Code (Option B - Channel)
```kotlin
sealed interface UiEffect {
    data class ShowSnackbar(val message: String) : UiEffect
    object NavigateToHome : UiEffect
}

class EventViewModel : ViewModel() {
    // Channel guarantees single delivery
    private val _effectChannel = Channel<UiEffect>()
    val effects = _effectChannel.receiveAsFlow()

    fun doWork() {
        viewModelScope.launch {
            // ... work ...
            _effectChannel.send(UiEffect.ShowSnackbar("Saved!"))
        }
    }
}

@Composable
fun EventScreen(viewModel: EventViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect safely with lifecycle awareness
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is UiEffect.NavigateToHome -> { /* Nav */ }
            }
        }
    }
}
```

### 6. Production usage
Crucial for all navigation and transient UI notifications. Using `SharedFlow` or `Channel` is the Google-recommended approach for one-off side effects.

### 7. Common mistakes
*   Using `SingleLiveEvent` (an old Java workaround).
*   Using `StateFlow` for events.
*   Not tying the collection to the Android Lifecycle (in Compose, `LaunchedEffect` handles this; in Views, use `repeatOnLifecycle`).

### 8. Debugging
If a navigation triggers twice, or drops completely, you likely used a `SharedFlow` without proper replay configurations, or sent the event before the UI was actively collecting. `Channel` buffers events until a subscriber appears, which is safer.

### 9. Testing
Use Turbine to test Channels/SharedFlows just like StateFlows.

### 10. Exercise
Implement an event channel that fires a `NavigateToDetails(id)` effect when an expense is clicked.

### 11. Deliberate failure
```kotlin
// BAD: StateFlow will replay this on rotation!
private val _error = MutableStateFlow<String?>(null)
val error = _error.asStateFlow()
// UI shows toast based on error != null. Rotates device -> Toast shows again.
```

### 12. Interview questions
*   **Q:** Why prefer `Channel` over `SharedFlow` for one-off events?
    *   **A:** A `Channel` is designed for single-consumer delivery. If the UI is in the background (not collecting), the `Channel` suspends/buffers the event until the UI returns. A default `SharedFlow` drops events if there are no active subscribers.

### 13. Checkpoint
Can you explain the "doorbell vs porch light" analogy?

---

## 11. Internal Domain State vs UI ViewState Split

### 1. What is it
Maintaining a rich, complex state model internally in the ViewModel (or Use Case) and applying a pure mapping function to generate a flattened, dumbed-down `UiState` specifically for the Composable.

### 2. Why does it exist
The UI should be incredibly dumb. It shouldn't calculate percentages, format dates, or combine fields. If the ViewModel exposes the raw Domain model, the Composable is forced to do logic.

### 3. Mental model
The Internal State is the raw spreadsheet with complex formulas. The UI State is the exported PDF report given to the executive. The executive (UI) just reads the PDF; they don't calculate the numbers.

### 4. How it works
You maintain `InternalState` in a `MutableStateFlow`. You expose `val uiState: StateFlow<UiState> = internalState.map { it.toUiState() }`.

### 5. Code
```kotlin
// Domain Model (Rich, raw types)
data class UserDomainModel(
    val firstName: String,
    val lastName: String,
    val dateOfBirth: LocalDate,
    val balanceCents: Int
)

// UI State (Dumb, formatted strings)
data class UserUiState(
    val fullName: String,
    val ageDisplay: String,
    val formattedBalance: String,
    val isBroke: Boolean
)

class UserViewModel : ViewModel() {
    private val internalState = MutableStateFlow<UserDomainModel>(/*...*/)

    // The UI only sees this mapped version
    val uiState: StateFlow<UserUiState> = internalState.map { domain ->
        UserUiState(
            fullName = "${domain.firstName} ${domain.lastName}",
            ageDisplay = "${Period.between(domain.dateOfBirth, LocalDate.now()).years} yrs",
            formattedBalance = "$${domain.balanceCents / 100.0}",
            isBroke = domain.balanceCents < 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserUiState(/* default */))
}
```

### 6. Production usage
Essential for keeping Composables clean. If designers change how the balance is displayed, you change the ViewModel mapper, and your unit tests catch it.

### 7. Common mistakes
Putting logic in the Composable: `Text("Balance: $" + (user.balanceCents / 100.0))`. This is untestable UI logic.

### 8. Debugging
If the UI displays data wrong, you only check the `.map { ... }` block in the ViewModel. The Compose code is innocent.

### 9. Testing
You unit test the ViewModel's state emission. You assert that a `balanceCents` of `150` results in a `formattedBalance` of `"$1.50"`.

### 10. Exercise
Create an extension function `UserDomainModel.toUiState()` to abstract the mapping logic out of the ViewModel body.

### 11. Deliberate failure
Passing raw Dates or Enums to the UI layer and making the UI format them based on Android `Context`. Formatting should happen before the UI State is emitted, or via localized string resources driven by UI State IDs.

### 12. Interview questions
*   **Q:** Why not just format the strings in the domain layer Use Case?
    *   **A:** The Domain layer is business logic. String formatting (adding '$', handling UI layouts) is Presentation logic. The Domain shouldn't know about UI formatting rules.

### 13. Checkpoint
Is your Compose function completely free of `if/else` logic dictating *what* data to show? (It should only have `if/else` for *how* to draw it).

---

## Phase 5 Project — Expense Tracker v4 (Layered Architecture)

**Goal:** Refactor the Expense Tracker into a complete multi-layered Clean Architecture project.

**Requirements:**
1. **Data Layer:**
   - `ExpenseRemoteDataSource` (simulated REST client) & `ExpenseLocalDataSource` (in-memory reactive cache)
   - `ExpenseRepositoryImpl` coordinating remote and local sources as Single Source of Truth
   - `ExpenseDto` ↔ `ExpenseEntity` ↔ `Expense` domain model mappers with resilience (log-and-drop malformed items)
2. **Domain Layer (Zero Android imports):**
   - `Expense` model
   - `GetExpensesUseCase`, `AddExpenseUseCase`, `DeleteExpenseUseCase` using `operator fun invoke()`
   - `ExpenseSummaryOrchestrator` (combines expenses and budget use cases to produce monthly summary)
3. **Presentation Layer:**
   - Implement `ExpenseListViewModel` in **MVVM** and `ExpenseListMviViewModel` in **MVI**.
   - Unit tests for both ViewModels using Turbine and fake repositories, comparing the testing effort.

---

## Phase 5 Checkpoint

Answer without looking:
1. Given any class in an Android project, how do you determine which layer it belongs to and what it is forbidden to import?
2. Why is exposing a Retrofit DTO directly to a Jetpack Compose screen considered a dangerous architectural anti-pattern?
3. Compare MVVM and MVI on: boilerplate, testability, debugging state regressions, and team scalability.
4. Why does an Orchestrator exist in the domain layer, and what rule does it enforce on ViewModels?
5. How do you handle a "Show Snackbar" event without causing it to show again when the user rotates the device?

---

## Complete Spring Boot / QA Architecture → Android Clean Architecture Translation Table

| Spring Boot / Backend / QA Concept | Android Clean Architecture Equivalent | Notes |
|---|---|---|
| `@RestController` / API Endpoint | `@Composable` Screen + `ViewModel` | UI endpoint handling user input & rendering state |
| Spring Service Layer (`@Service`) | Use Case / Interactor (`operator fun invoke`) | Single business action |
| Complex multi-service orchestrator | Domain Orchestrator (`domain/orchestrator/`) | Combines 2+ use cases |
| Spring Data Repository (`@Repository`) | Repository (`domain/repository/` + `data/repository/`) | Single source of truth, caching strategy |
| Database DAO / Remote Client | Data Source (`data/remote/` & `data/local/`) | Direct interface to API or SQLite |
| DTO ↔ Entity MapStruct / ModelMapper | Mapper extension functions (`toDomain()`, `toDto()`) | Explicit conversion at layer boundaries |
| Page Object Model (Test Automation) | 3-Layer Screen Pattern (Route → Screen → Component) | Modular UI hierarchy |
