# PHASE 6 — DEPENDENCY INJECTION (Weeks 9–10)

**Objective:** Master construction, scoping, and lifetime management across Android components. Wire clean architecture with compile-time (Hilt) and runtime (Koin) frameworks.
**Why this phase matters:** Dependency injection eliminates manual factory boilerplate, enables effortless swapping of test fakes, and enforces explicit component lifetimes. Choosing the wrong scope (e.g. Singleton holding a screen state) causes memory leaks, state bleeding across users, and crashes.
**Prerequisites:** Phase 1 (Kotlin), Phase 2 (Coroutines & Flow), Phase 3 (Android Platform), Phase 4 (Jetpack Compose), Phase 5 (App Architecture).
**Project deliverable:** Expense Tracker v5 — Complete DI graph wired with Hilt AND Koin, with clean module organization and test fake injection.
**Concepts covered:** 9 total, each with the full 13-step teaching sequence.

---

## 1. Why Dependency Injection (DI) at All

### 1. What is it
Dependency Injection (DI) is the practice of passing a class's required dependencies (other objects it uses) through its constructor, rather than having the class instantiate them itself.

### 2. Why does it exist
It exists to decouple object usage from object creation. If a `ViewModel` instantiates its own database repository, it becomes impossible to test the `ViewModel` in isolation without triggering real database operations. DI ensures that classes are independent, swappable, and highly testable.

### 3. Mental model
Think of Spring Boot's `@Autowired` or `@Bean` configuration. A Spring `@RestController` doesn't do `new UserService()`; the Spring IoC container injects it. Similarly, in Android, instead of the ViewModel creating its own data layer, an Android DI container acts as the "supplier" handing the "chef" (ViewModel) its ingredients.

### 4. How it works
In Spring, the `ApplicationContext` scans for `@Service` or `@Component` and wires singletons at startup. In Android, the OS creates `Activity` and `Fragment` instances directly, meaning we can't pass constructor arguments to them. Android DI frameworks create a graph of objects. When an entry point (like a screen or ViewModel) is created, the DI framework intercepts the creation process and recursively supplies all required constructor parameters from its graph.

### 5. Code
```kotlin
// ❌ WRONG: Hard dependency. Impossible to unit test properly.
class ExpenseViewModel : ViewModel() {
    private val repository = ExpenseRepositoryImpl(DatabaseDriver.create())
    
    fun load() = repository.getExpenses()
}

// ✅ RIGHT: Constructor Injection.
// Hilt/Koin will provide the 'repository' automatically.
class ExpenseViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {
    fun load() = repository.getExpenses()
}
```

### 6. Production usage
Every single business logic class, repository, and ViewModel in a professional Android app uses constructor injection. You will almost never see the `new` keyword (or Kotlin's equivalent constructor call) for a repository or service anywhere in the UI layer.

### 7. Common mistakes
**Mistake:** Using global singletons or "Service Locators" everywhere instead of DI.
```kotlin
// ❌ Anti-pattern: Global mutable state / Singleton access
class ExpenseViewModel : ViewModel() {
    private val repo = AppInstance.repository
}
```

### 8. Debugging
If DI fails, you will see a `MissingBinding` error at compile-time (Hilt) or a `NoBeanDefFoundException` equivalent at runtime (Koin). Look at the stack trace to find exactly which parameter in which constructor the DI framework couldn't find.

### 9. Testing
DI makes testing trivial. You just pass a fake object into the constructor during the test.
```kotlin
@Test
fun `test loading expenses`() {
    val fakeRepo = FakeExpenseRepository()
    val viewModel = ExpenseViewModel(fakeRepo)
    // assert...
}
```

### 10. Exercise
Refactor a deeply nested class creation. Create a `ReportGenerator` class that internally instantiates a `CurrencyConverter`. Change it to receive the `CurrencyConverter` via its constructor.

### 11. Deliberate failure
Create a class that instantiates `Room.databaseBuilder(...)` directly inside its `init` block. Try to write a JUnit test for it without starting an Android emulator. Notice how it crashes because standard JUnit has no Android Context.

### 12. Interview questions
*   **Q:** Why can't we use constructor injection on an `Activity`?
    *   **A:** Because the Android OS (ActivityManager) instantiates Activities via reflection using their default empty constructor.
*   **Q:** How does Dependency Injection adhere to SOLID principles?
    *   **A:** It satisfies Dependency Inversion (depending on abstractions, not concretions) and Single Responsibility (separating business logic from creation logic).

### 13. Checkpoint
Can you explain why typing `val api = Retrofit.Builder().build().create(Api::class.java)` inside a ViewModel is a massive red flag?

---

## 2. Scopes & Object Lifetimes

### 1. What is it
Scoping defines how long an injected object lives. By default, DI frameworks create a new instance every time an object is requested. Scopes (like Singleton, ViewModel-scoped, or Session-scoped) tell the DI framework to reuse the exact same instance across multiple injections within a specific boundary.

### 2. Why does it exist
Memory and state management. An HTTP client or Database instance is expensive to build, so we want only one per app (`@Singleton`). A screen's transient state should live exactly as long as the screen is open, and die when closed. A user's authentication token manager should live as long as the user is logged in, and clear on logout.

### 3. Mental model
In Spring Boot, beans are Singletons by default. In Android DI, classes are typically **Factories (unscoped)** by default—a new instance every time! 
- `@Singleton` = "One server for the whole city"
- `@ViewModelScoped` = "One waiter per table"
- Unscoped = "A disposable napkin handed out on every request"

### 4. How it works
The DI framework maintains internal caches (maps) tied to lifecycle owners. If you request a `@Singleton`, the framework checks its Application-level map. If found, it returns it; if not, it creates it, stores it, and returns it. If you request a ViewModel-scoped dependency, it stores it in a map tied to the `ViewModel`'s destruction callback. When the ViewModel clears, the scoped dependencies are garbage collected.

### 5. Code
```kotlin
// Hilt Example

// 1. Factory/Unscoped: New instance every time it's injected
class DateFormatter @Inject constructor() { ... }

// 2. Singleton: One instance for the entire app life
@Singleton
class ExpenseDatabase @Inject constructor() { ... }

// 3. ViewModelScoped: Lives as long as the ViewModel
@ViewModelScoped
class DraftExpenseCache @Inject constructor() { 
    var currentAmount: Double = 0.0 // Cleared when user leaves screen
}
```

### 6. Production usage
- `@Singleton`: `Retrofit`, `RoomDatabase`, `AnalyticsLogger`.
- `@ViewModelScoped`: Screen-specific state caches, multi-step form progress.
- `@ActivityScoped` (rare): Managing Activity-specific things like WindowInsets or Navigation Controllers.
- Unscoped (default): Stateless mappers, simple helper classes, use-cases (`GetExpensesUseCase`).

### 7. Common mistakes
**Mistake:** Making user-specific state or screen-specific state a `@Singleton`.
```kotlin
// ❌ WRONG: If user A logs out and user B logs in, User B sees User A's draft!
@Singleton
class ExpenseDraftManager @Inject constructor() {
    var draftName: String = ""
}
```

### 8. Debugging
If data "bleeds" between screens (e.g., you open a screen, type text, close it, open it again, and the old text is still there), your cache is scoped too broadly (e.g., Singleton instead of ViewModelScoped). If data is lost on rotation, it's scoped too narrowly or not cached.

### 9. Testing
Testing scoped objects is just testing standard objects. The scoping is handled by the DI framework. However, you must test that Singletons are thread-safe if they hold mutable state.

### 10. Exercise
Identify the correct scope: 
1. `BluetoothConnectionManager`
2. `JsonParser`
3. `UserSessionTokenHolder`
4. `CheckoutCart`

### 11. Deliberate failure
Create an object marked `@Singleton` that takes an `Activity` instance as a constructor parameter. Watch the DI framework crash at compile time (Hilt) because a Singleton outlives an Activity, causing a massive memory leak.

### 12. Interview questions
*   **Q:** What is the default scope in Dagger/Hilt or Koin?
    *   **A:** Unscoped (a new instance is created on every injection).
*   **Q:** Why is holding onto a `View` or `Activity` reference inside a `@Singleton` disastrous?
    *   **A:** Because the Singleton lives forever. When the Activity is destroyed (e.g., rotation), the Singleton keeps a reference to it, preventing the Garbage Collector from freeing the Activity and all its Views.

### 13. Checkpoint
Can you explain why a Spring Boot dev might mistakenly assume all Android injected classes are singletons, and what bug that would cause?

---

## 3. Binding Interfaces to Implementations & Test Swapping

### 1. What is it
Binding is the configuration step where you tell the DI framework: "When a class asks for this `Interface`, give it this concrete `Implementation`."

### 2. Why does it exist
To adhere to the Dependency Inversion Principle. A ViewModel shouldn't know that it's talking to a SQL database or a REST API; it should just know it's talking to an `ExpenseRepository`. By injecting the interface, you can effortlessly swap the real implementation for a `FakeExpenseRepository` during testing.

### 3. Mental model
It's like a Spring `@Configuration` class declaring a `@Bean`. When someone autowires `PaymentProcessor`, the configuration decides whether to wire `StripeProcessor` or `PaypalProcessor`. In Android, modules serve this exact mapping purpose.

### 4. How it works
You define a DI Module. In Hilt, it's an interface or class annotated with `@Module`. You use `@Binds` or `@Provides` to map the interface to the concretion. At compile time, Hilt generates factories that route requests for the interface to the concrete class's constructor. In UI tests, Hilt provides a `@TestInstallIn` mechanism to completely replace the production module with a test module.

### 5. Code
```kotlin
// 1. The Interface (Domain Layer)
interface ExpenseRepository {
    fun getExpenses(): Flow<List<Expense>>
}

// 2. The Implementation (Data Layer)
class ExpenseRepositoryImpl @Inject constructor(
    private val db: ExpenseDao
) : ExpenseRepository { ... }

// 3. The Binding Module (Hilt)
@Module
@InstallIn(SingletonComponent::class) // Tell Hilt this binding lives in the Singleton scope
abstract class RepositoryModule {
    
    @Binds
    abstract fun bindExpenseRepository(
        impl: ExpenseRepositoryImpl
    ): ExpenseRepository
}

// 4. Usage
@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repo: ExpenseRepository // Asks for interface, gets Impl
) : ViewModel()
```

### 6. Production usage
This is the core enabler of Clean Architecture. The Domain layer defines interfaces (`ExpenseRepository`), and the Data layer implements them (`ExpenseRepositoryImpl`). DI modules wire them together so the Domain layer never has a dependency on the Data layer's code.

### 7. Common mistakes
**Mistake:** Injecting the concrete implementation directly.
```kotlin
// ❌ WRONG: Defeats the purpose of the interface
class ExpenseViewModel @Inject constructor(
    private val repo: ExpenseRepositoryImpl 
)
```

### 8. Debugging
"Cannot be provided without an @Provides-annotated method." This Hilt compile error means you asked for an Interface in a constructor, but forgot to write the `@Binds` or `@Provides` function in a Module.

### 9. Testing
In unit tests, you don't even use Hilt. You just instantiate the fake and pass it in:
```kotlin
class FakeExpenseRepository : ExpenseRepository {
    override fun getExpenses() = flowOf(listOf(Expense("Coffee", 5.0)))
}
// val vm = ExpenseViewModel(FakeExpenseRepository())
```
For Espresso UI tests, you use Hilt's `@TestInstallIn` to swap the real `RepositoryModule` with a `FakeRepositoryModule`.

### 10. Exercise
Create a `NetworkLogger` interface and a `LogcatNetworkLogger` implementation. Create a DI module that binds the two, and inject the interface into an OkHttp Interceptor.

### 11. Deliberate failure
Remove the `@Binds` method from your module but keep `ExpenseRepository` as a constructor parameter in your ViewModel. Try to compile the app and read the Hilt error message carefully.

### 12. Interview questions
*   **Q:** What is the difference between `@Binds` and `@Provides` in Hilt?
    *   **A:** `@Binds` is used for mapping an interface to an implementation that is already injected via constructor. It is more efficient (generates less code). `@Provides` is used when you need to write manual initialization code (like `Retrofit.Builder().build()`) or handle external library classes.

### 13. Checkpoint
Can you draw the dependency arrow: Does the Data layer depend on the Domain layer, or vice versa? How does DI make this possible?

---

## 4. ViewModel Injection & `SavedStateHandle`

### 1. What is it
ViewModel injection is the mechanism by which DI frameworks instantiate Android ViewModels. `SavedStateHandle` is a specialized map injected into ViewModels that contains navigation arguments and survives process death.

### 2. Why does it exist
ViewModels have a unique lifecycle—they survive configuration changes (rotations). Standard DI cannot create them like normal classes because they must be created via Android's `ViewModelProvider`. Furthermore, when navigating between screens, you often pass IDs (e.g., `expenseId`). `SavedStateHandle` allows the ViewModel to receive these arguments directly upon creation.

### 3. Mental model
Imagine a ViewModel is an engine. The DI framework builds the engine. But because the engine needs to survive the car crashing and restarting (rotation/process death), Android provides a special "glovebox" (`SavedStateHandle`) that survives crashes. Hilt drops both the dependencies and the glovebox into the engine at the factory.

### 4. How it works
When you annotate a ViewModel with `@HiltViewModel` (or use `viewModelOf` in Koin), the DI framework generates a custom `ViewModelProvider.Factory`. When the Compose screen asks for the ViewModel (`hiltViewModel()`), this factory resolves the standard dependencies from the DI graph, retrieves the `SavedStateHandle` from the Navigation component, and calls the ViewModel's constructor.

### 5. Code
```kotlin
@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    savedStateHandle: SavedStateHandle // Injected automatically!
) : ViewModel() {

    // Retrieve the argument passed via Jetpack Navigation route: "detail/{expenseId}"
    private val expenseId: String = checkNotNull(savedStateHandle["expenseId"])

    val expense: StateFlow<Expense?> = repository.getExpense(expenseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
```

### 6. Production usage
This replaces the old pattern of observing an intent/bundle in the UI layer and calling `viewModel.init(id)`. Now, the ViewModel is entirely self-sufficient—it reads its own arguments on creation and immediately starts fetching data.

### 7. Common mistakes
**Mistake:** Creating an `init` method to pass arguments from the UI.
```kotlin
// ❌ WRONG: UI shouldn't drive initialization
class MyViewModel : ViewModel() {
    fun init(id: String) { ... }
}

// UI:
// val vm = viewModel()
// vm.init(navArgs.id) 
```

### 8. Debugging
If `savedStateHandle["id"]` is null, ensure the key matches *exactly* the argument name defined in your Jetpack Navigation route (e.g., `composable("detail/{id}")`).

### 9. Testing
To test a ViewModel that uses `SavedStateHandle`, manually create the handle in your test setup.
```kotlin
@Test
fun testViewModel() {
    val handle = SavedStateHandle(mapOf("expenseId" to "123"))
    val viewModel = ExpenseDetailViewModel(fakeRepo, handle)
    // ...
}
```

### 10. Exercise
Create a `UserProfileViewModel` that requires a `UserRepository` and a `SavedStateHandle`. Configure the handle to expect a `userId`. Initialize a StateFlow in the ViewModel that immediately fetches that user.

### 11. Deliberate failure
Annotate a ViewModel with `@HiltViewModel` but forget to add `@Inject constructor`. Try to compile. Hilt will throw an error stating it doesn't know how to create the ViewModel.

### 12. Interview questions
*   **Q:** How does a ViewModel survive rotation, and how does `SavedStateHandle` survive process death?
    *   **A:** ViewModels are retained in a `ViewModelStore` outside the Activity lifecycle. `SavedStateHandle` is serialized into the Activity's `outState` Bundle via `onSaveInstanceState` and restored when the OS recreates the app.

### 13. Checkpoint
Why shouldn't you pass the navigation arguments to the ViewModel via a function call from the Composable?

---

## 5. DI Module Organization in Multi-Layer Apps

### 1. What is it
Module organization is how you structure your DI configuration files. Instead of one massive `AppModule` with 500 `@Provides` functions, you split them logically by architectural layer (Data, Domain, Network) or by Feature (Auth, Expenses, Settings).

### 2. Why does it exist
Maintainability and Build Times. A monolithic DI module becomes a merge conflict nightmare in teams. In multi-module Gradle projects, feature modules cannot "see" each other. DI must be organized so that each Gradle module provides its own DI bindings.

### 3. Mental model
Instead of one massive circuit breaker box for an entire skyscraper (one massive AppModule), you have a local breaker panel on every floor (DataModule, NetworkModule, FeatureExpenseModule). They all connect to the main power grid at the base (the Application class).

### 4. How it works
You define a DI Module in the layer where the implementation lives. 
- The `data` module provides the `DatabaseModule` and `NetworkModule`.
- The `feature:expenses` module provides the `ExpensePresentationModule`.
Hilt collects all these `@Module` classes at compile-time across all Gradle modules and merges them into a single Dagger component in the `app` module.

### 5. Code
```kotlin
// In 'core:network' Gradle module
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder().build()
}

// In 'data:expenses' Gradle module
@Module
@InstallIn(SingletonComponent::class)
abstract class ExpenseDataModule {
    @Binds
    abstract fun bindRepo(impl: ExpenseRepositoryImpl): ExpenseRepository
}

// In 'app' Gradle module
@HiltAndroidApp
class TrackerApplication : Application() // Hilt merges everything here!
```

### 6. Production usage
Enterprise apps enforce strict boundaries. The `feature:login` module knows absolutely nothing about `feature:dashboard`. Because each module declares its own Hilt `@Module`, the `app` module simply depends on both features, and Hilt magically wires the dependencies at compile time without the features needing to know about each other.

### 7. Common mistakes
**Mistake:** Putting `NetworkModule` inside the `app` module instead of a dedicated `core-network` module, preventing other feature modules from using network classes independently.

### 8. Debugging
If you get a "MissingBinding" error for a class you *know* you provided in a module, verify that the Gradle module containing the DI Module is actually included as a dependency (`implementation project(...)`) in the final `app` build.gradle.

### 9. Testing
Clean module organization makes testing easier because you can swap out an entire layer's module using `@TestInstallIn`. For example, replace `NetworkModule` with `MockServerNetworkModule` in UI tests.

### 10. Exercise
Create three separate Kotlin files representing different modules: `DatabaseModule.kt`, `NetworkModule.kt`, and `UseCaseModule.kt`. Group related dependencies logically into them.

### 11. Deliberate failure
Create an interface in a `domain` Gradle module, and its implementation in a `data` Gradle module. Try to put the Hilt `@Binds` module in the `domain` module. Notice it won't compile because `domain` doesn't know about `data`. Move the Hilt module to the `data` layer to fix it.

### 12. Interview questions
*   **Q:** In a Clean Architecture multi-module project, where should the Hilt module that binds `ExpenseRepositoryImpl` to `ExpenseRepository` reside?
    *   **A:** In the Data module, because that is where the concrete implementation (`ExpenseRepositoryImpl`) exists. The Domain module should only contain the interface and have no DI dependencies.

### 13. Checkpoint
How does Hilt's `@InstallIn` annotation help with multi-module DI compared to manual Dagger components?


---

## 6. Hilt Deep Dive (Compile-Time / Dagger)

### 1. What is it?
Hilt is a compile-time Dependency Injection library for Android, built on top of Dagger. It provides standard containers for Android classes (like Application and Activity) and manages their lifecycles automatically.

### 2. Why does it exist?
Dagger was extremely powerful but notoriously difficult to set up on Android. You had to manually wire components to the `Application`, `Activity`, and `Fragment` lifecycles, leading to massive boilerplate. Hilt exists to automate this Android-specific Dagger boilerplate.

### 3. Mental model
Think of Hilt as a **meticulous factory inspector**. Before the factory (your app) is even allowed to turn on (compile), the inspector walks through the entire assembly line. If a single wire is missing (a missing dependency), the inspector shuts down the factory immediately with a red alert (compile error). 

### 4. How it works
Hilt uses annotations (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@Inject`, `@Module`) combined with a compiler plugin (KAPT or KSP). During the build process, it generates the underlying Dagger Java code to wire everything together. It strictly ties dependencies to Android lifecycles (`SingletonComponent` = App lifecycle, `ActivityComponent` = Activity lifecycle).

### 5. Code
```kotlin
// 1. The Application class setup
@HiltAndroidApp
class ExpenseTrackerApp : Application()

// 2. A Module using @Binds (Zero-bytecode abstract delegation)
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    // @Binds is preferred over @Provides for interfaces! 
    // It tells Hilt "when someone asks for ExpenseRepository, give them ExpenseRepositoryImpl"
    // It generates NO extra factory methods.
    @Binds
    @Singleton
    abstract fun bindExpenseRepository(
        impl: ExpenseRepositoryImpl
    ): ExpenseRepository
}

// 3. A Module using @Provides (for 3rd party classes we don't own)
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder().baseUrl("https://api.example.com").build()
    }
}

// 4. Constructor Injection in Domain/Data layers
class ExpenseRepositoryImpl @Inject constructor(
    private val api: ExpenseApi, // Hilt knows how to build this
    private val db: ExpenseDao
) : ExpenseRepository { ... }

// 5. ViewModel Injection
@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() { ... }

// 6. UI Layer (Compose)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Hilt automatically resolves the ViewModel and its dependencies
            val viewModel: ExpenseListViewModel = hiltViewModel()
            ExpenseScreen(viewModel)
        }
    }
}
```

### 6. Production usage
Hilt is the officially recommended DI solution by Google. It is the standard in almost all medium-to-large pure Android applications that do not use Kotlin Multiplatform (KMP).

### 7. Common mistakes
- **Using `@Provides` instead of `@Binds` for interfaces:** Writing `@Provides fun provideRepo(impl: RepoImpl): Repo = impl` creates unnecessary factory classes and method calls in the generated code. `@Binds` is a direct mapping.
- **Forgetting `@AndroidEntryPoint`:** If you forget this on an Activity/Fragment, Hilt won't inject the ViewModel, resulting in an obscure creation crash.
- **Scoping everything to `@Singleton`:** Keeping screen-specific data in a `@Singleton` keeps it in memory forever. Use `@ViewModelScoped` for data tied to a specific screen's lifetime.

### 8. Debugging
When Hilt fails, it fails at **compile time**. The error messages can be overwhelmingly long, but you only need to look for:
`[Dagger/MissingBinding] com.example.MyDependency cannot be provided without an @Inject constructor or an @Provides-annotated method.`
This explicitly tells you exactly what type it couldn't find.

### 9. Testing
Hilt provides a testing library. You annotate tests with `@HiltAndroidTest`, use `@UninstallModules` to remove production modules, and replace them with mock modules.

### 10. Exercise
Create an interface `AnalyticsLogger` and an implementation `FirebaseAnalyticsLogger`. Create a Hilt module to bind them together, then inject it into a `@HiltViewModel`.

### 11. Deliberate failure
Remove the `@Inject constructor` from `ExpenseRepositoryImpl` and try to build the app. Observe the compile-time Dagger error pointing out the missing binding.

### 12. Interview questions
- **Q:** What is the difference between `@Provides` and `@Binds`? *(A: @Provides is for executing code to instantiate external classes; @Binds is an abstract function mapping an interface to an implementation, generating zero runtime overhead.)*
- **Q:** Can you use Hilt in a Kotlin Multiplatform (KMP) project? *(A: No, Hilt relies on Android-specific classes and Java/KAPT codegen.)*

### 13. Checkpoint
Can you explain why a missing dependency in Hilt breaks the build, but doesn't crash the app at runtime?

---

## 7. Koin Deep Dive (Kotlin DSL, Runtime)

### 1. What is it?
Koin is a lightweight, purely Kotlin dependency injection framework (technically a Service Locator with DI capabilities). It uses no code generation, no reflection, and relies entirely on a Kotlin DSL and inline reified functions.

### 2. Why does it exist?
Many developers found Dagger/Hilt's build times (due to KAPT/codegen) and steep learning curve unbearable. Koin was created to be the most "Kotlin-idiomatic" way to wire dependencies, offering extremely fast compile times and a readable syntax.

### 3. Mental model
Think of Koin as a **dynamic hash map of recipes**. You give it a map of `Type -> Recipe to build the Type`. When you ask for an object at runtime, Koin looks in the map, finds the recipe, and cooks (instantiates) the object for you on the spot.

### 4. How it works
You define your dependencies inside `module { ... }` blocks using functions like `singleOf` (singleton) or `factoryOf` (new instance every time). You start Koin once in your Application class using `startKoin`. When a class requests a dependency via `koinViewModel()` or `inject()`, Koin resolves the graph dynamically at runtime.

### 5. Code
```kotlin
// 1. Defining Modules with Koin DSL
val dataModule = module {
    // Creates a Singleton Retrofit instance
    single { Retrofit.Builder().baseUrl("https://api.example.com").build() }
    
    // singleOf automatically maps the constructor parameters!
    // bind links the implementation to the interface
    singleOf(::ExpenseRepositoryImpl) bind ExpenseRepository::class
}

val presentationModule = module {
    // Declares a ViewModel. Koin automatically handles ViewModel factory logic.
    viewModelOf(::ExpenseListViewModel)
}

// 2. Starting Koin
class ExpenseTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ExpenseTrackerApp)
            modules(dataModule, presentationModule)
        }
    }
}

// 3. Domain layer remains purely Kotlin (NO Koin annotations!)
class ExpenseRepositoryImpl(
    private val api: ExpenseApi, 
    private val db: ExpenseDao
) : ExpenseRepository { ... }

// 4. UI Layer (Compose)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Resolves ViewModel from Koin's registry
            val viewModel: ExpenseListViewModel = koinViewModel()
            ExpenseScreen(viewModel)
        }
    }
}
```

### 6. Production usage
Koin is extremely popular in agile teams, startups, and **Kotlin Multiplatform (KMP)** projects, as it is 100% Kotlin and doesn't rely on Android/JVM specific code generation.

### 7. Common mistakes
- **Missing bindings causing runtime crashes:** Because Koin doesn't check the graph at compile-time, forgetting to declare a module means your app compiles fine, but crashes with `NoBeanDefFoundException` when the user navigates to the screen requiring that dependency.

### 8. Debugging
Koin crashes explicitly at runtime if a dependency is missing. 
`org.koin.core.error.NoBeanDefFoundException: |- No definition found for class:'com.example.ExpenseRepository'.`
To fix it, you simply find where you missed adding it to a `module { }`.

### 9. Testing
To prevent the runtime crashes mentioned above, Koin provides a compile-time-like safeguard via tests:
```kotlin
class KoinGraphTest {
    @Test
    fun checkKoinModules() {
        // This will verify your entire DI graph without launching the app!
        val myModules = listOf(dataModule, presentationModule)
        myModules.verify() // or checkModules()
    }
}
```

### 10. Exercise
Write a Koin module that provides a `factory` for a `DateFormatter` class, and inject it into a `ReceiptParser` class using `factoryOf(::ReceiptParser)`.

### 11. Deliberate failure
Comment out `singleOf(::ExpenseRepositoryImpl)` in your Koin module. Run the app. Watch it crash. Then, run the Koin `verify()` unit test and see how it catches the exact same error safely in your CI pipeline.

### 12. Interview questions
- **Q:** How does Koin resolve dependencies without reflection or code generation? *(A: It relies entirely on Kotlin's inline reified type parameters and DSL lambdas to map types to factory functions at runtime.)*
- **Q:** Is Koin a true Dependency Injection framework or a Service Locator? *(A: Under the hood, Koin operates as a Service Locator. However, when used purely with constructor injection, it fulfills the exact same architectural purpose as DI.)*

### 13. Checkpoint
Why is setting up a `verify()` test absolutely mandatory when using Koin?

---

## 8. Hilt vs Koin Side-by-Side Tradeoff Comparison

### 1. What is it?
The architectural decision of choosing between Google's statically generated Hilt and the community's dynamically resolved Koin.

### 2. Why does it exist?
No single tool is perfect. You are trading off **Build Speed & Readability** (Koin) against **Compile-Time Safety & Standardization** (Hilt).

### 3. Mental model
- **Hilt (C++ / Java style):** The compiler checks every single pointer and memory reference before you run the app. It takes longer to build, but you sleep well at night.
- **Koin (Python / JS style):** You write a flexible script. It builds instantly. But if you made a typo, it blows up in your face while the app is running.

### 4. How it works (The Tradeoff Matrix)

| Feature | Hilt | Koin |
| :--- | :--- | :--- |
| **Validation** | Compile-Time (Build fails) | Runtime (App crashes, unless `verify()` tested) |
| **Build Speed** | Slower (KSP / KAPT codegen overhead) | Fast (Zero codegen) |
| **Boilerplate** | High (`@Module`, `@InstallIn`, `@AndroidEntryPoint`) | Low (Clean Kotlin DSL) |
| **KMP Support** | No (Android/JVM only) | Yes (100% Kotlin) |
| **Learning Curve**| Steep (Dagger concepts, scopes, components) | Very Easy |

### 5. Code: Side-by-Side

**Hilt:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindRepo(impl: ExpenseRepoImpl): ExpenseRepo
}

@HiltViewModel
class MyViewModel @Inject constructor(val repo: ExpenseRepo) : ViewModel()
```

**Koin:**
```kotlin
val dataModule = module {
    singleOf(::ExpenseRepoImpl) bind ExpenseRepo::class
    viewModelOf(::MyViewModel)
}

// ViewModel has ZERO annotations.
class MyViewModel(val repo: ExpenseRepo) : ViewModel()
```

### 6. Production usage
- Use **Hilt** if: You have a massive team, standard Android-only app, and prioritize compile-time safety.
- Use **Koin** if: You are building a Kotlin Multiplatform app, want zero boilerplate, or prioritize fast build times.

### 7. Common mistakes
Trying to migrate from one to the other mid-project without a clear architectural need. Both work perfectly fine for 99% of use cases.

### 8. Debugging
Hilt debugs in the `Build` tab. Koin debugs in `Logcat`.

### 9. Testing
Hilt requires generating mock components. Koin allows you to simply call `loadKoinModules(mockModule)` to override dependencies instantly in tests.

### 10. Exercise
Look at your current project requirements. If you were porting this app to iOS next year, which DI framework must you choose? (Answer: Koin).

### 11. Deliberate failure
Try to use Hilt in a Kotlin Multiplatform `shared` module. Observe that `@Inject` from `javax.inject` does not exist in native iOS code.

### 12. Interview questions
- **Q:** Your team's build times are exceeding 10 minutes due to KAPT/Dagger. What are your options? *(A: Migrate to KSP for Hilt, or migrate to Koin to drop codegen entirely).*

### 13. Checkpoint
Summarize the Hilt vs Koin debate in one sentence.

---

## 9. Universal DI Anti-Patterns

### 1. What is it?
Architectural mistakes made when using *any* DI framework (Hilt, Koin, or Spring) that defeat the purpose of Dependency Injection, creating tight coupling, memory leaks, or untestable code.

### 2. Why does it exist?
Developers often treat DI containers as "magic global variables" rather than tools to facilitate clean constructor injection.

### 3. Mental model
Imagine hiring a personal chef (the DI container) to make your meals. 
- **Good DI (Constructor Injection):** The chef hands you a sandwich. You eat it.
- **Anti-pattern (Service Locator):** You swallow the chef whole, and whenever you are hungry, you ask the chef inside your stomach to make a sandwich. 

### 4. How it works (The Anti-Patterns)

1. **Service Locator inside Domain classes:** Calling `get()` or `inject()` directly inside a Use Case or Repository.
2. **Injecting the DI Container:** Passing the whole `ApplicationContext` or `Koin` instance into a class just so it can fish out what it needs.
3. **Leaking UI Contexts:** Injecting an `Activity` into a `@Singleton`. When the Activity is destroyed on rotation, the Singleton holds the dead Activity in memory, causing a massive memory leak.
4. **God Modules:** Putting all 200 app dependencies into a single `AppModule` file, creating merge conflict hell.

### 5. Code: The Wrong Way vs The Right Way

**WRONG (Service Locator / Hidden Dependencies):**
```kotlin
// Bad: The class fetches its own dependencies. Hard to test!
class ProcessPaymentUseCase {
    // Anti-pattern! Depending on Koin globally inside business logic.
    private val paymentApi: PaymentApi by inject(PaymentApi::class.java)
    
    fun execute(amount: Double) {
        paymentApi.charge(amount)
    }
}
```

**RIGHT (Constructor Injection):**
```kotlin
// Good: Dependencies are explicit. Easy to mock in a unit test!
class ProcessPaymentUseCase(
    private val paymentApi: PaymentApi
) {
    fun execute(amount: Double) {
        paymentApi.charge(amount)
    }
}
```

### 6. Production usage
Enforcing Constructor Injection universally allows you to test any class by just passing in mock objects `ProcessPaymentUseCase(mockApi)`, without needing to start Kilt or Hilt in your unit tests.

### 7. Common mistakes
Using `@Inject lateinit var` (Field injection) inside regular Kotlin classes instead of passing dependencies through the constructor. Field injection should be strictly reserved for framework-managed classes like Android `Activity` or `Fragment` where you don't control the constructor.

### 8. Debugging
If a unit test requires you to set up `startKoin {}` or a Hilt Test Component just to test a simple Use Case, you have committed a DI anti-pattern. Domain classes should be testable with pure Kotlin.

### 9. Testing
Proper DI makes testing trivial. You just instantiate the class and pass fakes/mocks into the constructor.

### 10. Exercise
Identify the memory leak:
```kotlin
@Singleton
class NavigationManager @Inject constructor(
    private val activity: MainActivity // Uh oh
)
```

### 11. Deliberate failure
Implement the memory leak above. Rotate the device 5 times. Look at the Android Studio Memory Profiler and see 5 dead `MainActivity` instances sitting in the heap.

### 12. Interview questions
- **Q:** Why is passing an Activity context into a Singleton Repository a catastrophic mistake? *(A: Singletons live as long as the Application. Activities are destroyed and recreated on rotation. The Singleton will hold a reference to the destroyed Activity, preventing Garbage Collection and causing an OutOfMemory Exception).*

### 13. Checkpoint
Why should your Domain layer (Use Cases, Entities) have absolutely zero knowledge of Hilt or Koin annotations?

---

## Phase 6 Project — Expense Tracker v5 (Dependency Injection)

**Goal:** Wire the Expense Tracker architecture with both Hilt and Koin, and build tests with DI test doubles.

**Requirements:**
1. **Hilt Implementation:**
   - Create `DatabaseModule`, `NetworkModule`, `RepositoryModule` (`@Binds`), and `UseCaseModule`.
   - Annotate `ExpenseTrackerApp` with `@HiltAndroidApp` and `MainActivity` with `@AndroidEntryPoint`.
   - Inject `ExpenseListViewModel` via `@HiltViewModel`.
2. **Koin Implementation:**
   - Create `dataModule`, `domainModule`, and `presentationModule` using `singleOf`, `factoryOf`, and `viewModelOf`.
   - Write a Koin verification unit test using `checkModules()` to prove that the DI graph has no missing bindings.
3. **Scope Bug Scenario:**
   - Plant and debug a planted scope error: Registering a screen-scoped filter cache as a `@Singleton` and observing state leak across test runs, then fixing it to `factoryOf` / `@ViewModelScoped`.

---

## Phase 6 Checkpoint

Answer without looking:
1. What breaks in production if an in-memory search filter cache is registered as a `@Singleton` instead of `@ViewModelScoped` or `factory`?
2. What breaks if an authentication session manager (storing the active OAuth token) is registered as a `factory` instead of a `single`?
3. What is the difference between `@Provides` and `@Binds` in Hilt, and why should you prefer `@Binds` whenever possible?
4. Why is `checkModules()` or `verify()` crucial when using Koin, whereas Hilt does not require a similar test?
5. Why is calling `KoinPlatform.get()` or `ServiceLocator.get()` inside a Use Case or Composable considered an architectural anti-pattern?

---

## Complete Spring Boot / Backend → Android DI (Hilt & Koin) Translation Table

| Spring Boot / Backend Concept | Hilt (Android) | Koin (Android) | Notes |
|---|---|---|---|
| `@Configuration` | `@Module` + `@InstallIn` | `val myModule = module { ... }` | DI configuration container |
| `@Bean` (factory method) | `@Provides` | `single { ... }` / `factory { ... }` | Instantiation logic |
| `@Component` / `@Service` | `@Inject constructor(...)` | `singleOf(::MyService)` | Registering a class |
| `@Autowired` | `@Inject lateinit var` / constructor | `by inject()` / constructor | Dependency resolution |
| `@Scope("singleton")` (Default) | `@Singleton` | `singleOf(...)` | One instance per App lifecycle |
| `@Scope("prototype")` | Unscoped (no annotation) | `factoryOf(...)` | New instance per injection |
| `@Scope("request")` | `@ViewModelScoped` / `@ActivityScoped` | `viewModelOf(...)` / Koin Scope | Tied to screen/request lifecycle |
| `ApplicationContext.getBean()` | EntryPoints / `hiltViewModel()` | `get()` / `koinViewModel()` | Service locator retrieval |
