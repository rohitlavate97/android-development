# PHASE 9 — NAVIGATION (Week 13)

**Objective:** Master type-safe routing, back stack management, deep links, and decoupled navigation architectures in Jetpack Compose.

**Why this phase matters:** Broken back stacks, duplicate screens on multiple clicks, lost state during process death, and tightly coupled `NavController` references across screens are among the most common bugs in Android apps. Navigation must be type-safe, lifecycle-aware, and testable without UI frameworks.

**Prerequisites:** Phase 1 (Kotlin), Phase 2 (Coroutines & Flow), Phase 3 (Android Platform), Phase 4 (Jetpack Compose), Phase 5 (App Architecture).

**Project deliverable:** Expense Tracker v8 — Type-safe Navigation-Compose graph with nested flows, deep links, result passing, and process-death survival.

**Concepts covered:** 10 total, each with the full 13-step teaching sequence.

---

## 1. Navigation Fundamentals & The Back Stack

### 1. What is it
The Jetpack Navigation component is a framework for moving between screens ("destinations"). The **Back Stack** is a LIFO (Last-In-First-Out) stack data structure that tracks the history of screens the user has visited, so the system Back button knows where to return.

### 2. Why does it exist
Before Jetpack Navigation, Android developers manually managed screen transitions using `FragmentTransaction` or starting new `Activity` instances. This led to complex backstack bugs, `IllegalStateException` crashes when state was lost, and duplicate screens on double-taps. Navigation-Compose standardizes routing for Single-Activity apps.

### 3. Mental model
Think of the Back Stack like your **browser's history tab**. Every time you click a link, a new page is PUSHED onto the stack. When you hit the back button, the current page is POPPED, revealing the previous one. `NavController` is your browser's address bar + history engine.

### 4. How it works
- `NavController`: The API you call to `navigate()` or `popBackStack()`.
- `NavHost`: The Compose container that physically hosts the current screen.
- `NavBackStackEntry`: An object representing one screen in the back stack. It holds the screen's route, arguments, and local `ViewModelStore`.
- **Back stack manipulation**: 
  - `popUpTo(Route)`: Clears intermediate screens (e.g., going from "Payment Success" straight back to "Home").
  - `launchSingleTop = true`: Prevents PUSHING a duplicate screen if it's already on top (fixes double-click bugs).
  - `saveState` / `restoreState`: Crucial for bottom tabs so you don't lose scroll position when switching tabs.

### 5. Code
```kotlin
// Setup in your top-level Composable
val navController = rememberNavController()

NavHost(navController = navController, startDestination = HomeRoute) {
    composable<HomeRoute> { HomeScreen(onNavigateToSettings = { navController.navigate(SettingsRoute) }) }
    composable<SettingsRoute> { SettingsScreen() }
}

// In your Navigation logic
fun navigateToProfile() {
    navController.navigate(ProfileRoute) {
        // Pop everything up to Home to prevent a massive back stack
        popUpTo(HomeRoute) { 
            inclusive = false 
            saveState = true // Save state of popped screens
        }
        // Avoid multiple copies of Profile if user double clicks
        launchSingleTop = true
        // Restore state if we previously saved it
        restoreState = true
    }
}
```

### 6. Production usage
In an Expense Tracker, when a user completes adding an expense, you don't just call `navigate(Dashboard)`. You do `navigate(Dashboard) { popUpTo(Dashboard) { inclusive = true } }` so if they hit the physical Back button, it exits the app rather than returning to the "Add Expense" success screen.

### 7. Common mistakes
❌ **Wrong:** `navController.navigate(Login)` after successful logout. (The user can hit 'Back' and return to the secure app!).
✅ **Right:** 
```kotlin
navController.navigate(LoginRoute) {
    popUpTo(0) { inclusive = true } // Clears the ENTIRE back stack
}
```

### 8. Debugging
To see the back stack in real-time, register an `OnDestinationChangedListener`:
```kotlin
navController.addOnDestinationChangedListener { controller, destination, arguments ->
    Log.d("Nav", "Navigated to ${destination.route}. Backstack size: ${controller.backQueue.size}")
}
```

### 9. Testing
Testing navigation means verifying `NavController` is called correctly. Don't use a real `NavController` in unit tests. Pass lambda callbacks (`onNavigateToX: () -> Unit`) to your Composables and verify the callback is invoked.

### 10. Exercise
Create a 3-screen flow: `Home -> Search -> Detail`. Add a button on `Detail` that says "Back to Home". Implement the navigation so that clicking it takes you to Home, and hitting the system Back button from Home exits the app (doesn't go back to Search).

### 11. Deliberate failure
Write `navController.navigate(Detail)` on a button click. Click it rapidly 5 times. Notice how hitting the back button requires 5 presses to leave the Detail screen. Fix it using `launchSingleTop = true`.

### 12. Interview questions
- **Q:** How do you clear the entire back stack when logging out?
- **Q:** What is the difference between `popUpTo(Route) { inclusive = true }` and `inclusive = false`?
- **Q:** Why shouldn't you pass the `NavController` directly into your deep UI composables?

### 13. Checkpoint
You understand that Navigation is just state management for a Stack data structure, and `launchSingleTop` is your defense against double-clicks.

---

## 2. Type-Safe Navigation-Compose (Modern Standard)

### 1. What is it
Since mid-2024, Navigation-Compose uses Kotlin Serialization to define screens as strongly-typed objects (`data object` or `data class`) rather than string-based routes (like `"details/{id}"`). 

### 2. Why does it exist
String-based routing is brittle. If you had `"profile/{userId}?showEdit={showEdit}"`, a typo in the string or arg names would cause a runtime crash. Type-safety turns routing errors into compile-time errors. It matches the safety of passing method arguments.

### 3. Mental model
Compare string routing to a dynamically typed language (JavaScript) where you hope the property exists. Type-safe routing is like Java/Kotlin strict typing. It’s a generated contract for your screen transitions.

### 4. How it works
You annotate route classes with `@Serializable`.
- No arguments: `@Serializable data object Dashboard`
- With arguments: `@Serializable data class ExpenseDetail(val expenseId: String)`
To get arguments out, you call `toRoute<ExpenseDetail>()` on the `NavBackStackEntry` (in Compose) or the `SavedStateHandle` (in ViewModel).

### 5. Code
```kotlin
// 1. Define Routes
@Serializable
data object DashboardRoute

@Serializable
data class ExpenseDetailRoute(val expenseId: String, val autoFocusEdit: Boolean = false)

// 2. Setup Graph
NavHost(navController, startDestination = DashboardRoute) {
    composable<DashboardRoute> {
        DashboardScreen(onExpenseClick = { id -> 
            navController.navigate(ExpenseDetailRoute(id, false)) 
        })
    }
    
    composable<ExpenseDetailRoute> { backStackEntry ->
        // Extract args in UI (if needed)
        val route = backStackEntry.toRoute<ExpenseDetailRoute>()
        ExpenseDetailScreen(expenseId = route.expenseId)
    }
}

// 3. Extract in ViewModel (Recommended)
@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ExpenseRepository
) : ViewModel() {
    // Type-safe extraction directly from SavedStateHandle!
    private val route = savedStateHandle.toRoute<ExpenseDetailRoute>()
    val expenseId = route.expenseId
}
```

### 6. Production usage
Every screen in the Expense Tracker has a corresponding `@Serializable` class defined in a `navigation` package. ViewModels instantly know what ID to load data for without relying on fragile string keys.

### 7. Common mistakes
❌ **Wrong:** Continuing to use `navController.navigate("expense_detail/$id")`. 
✅ **Right:** Migrating all string routes to `@Serializable` data classes.

### 8. Debugging
If `toRoute<T>()` crashes, it means the class structure of `T` does not match the parameters passed, or you forgot the `@Serializable` annotation, which usually causes a compiler warning but can manifest at runtime if proguard strips things.

### 9. Testing
Unit testing ViewModels becomes trivial. You can populate a `SavedStateHandle` using `SavedStateHandle.createHandle(null, ExpenseDetailRoute("123"))` (using the navigation testing artifacts) to test how the ViewModel initializes.

### 10. Exercise
Refactor an old string-based route `composable("settings/{userId}")` to use a type-safe `@Serializable data class Settings(val userId: String)`.

### 11. Deliberate failure
Create a `@Serializable data class Route(val id: Int)`. Try to navigate to it passing `Route("123")` (a string). Watch the compiler stop you. This is the whole point!

### 12. Interview questions
- **Q:** How does Navigation-Compose pass data under the hood when using `@Serializable`? (Answer: It serializes the object into a Bundle string/primitive format).
- **Q:** How do you extract type-safe arguments inside a ViewModel?

### 13. Checkpoint
You understand that strings are dead, and Kotlin Serialization is the future of Android routing.

---

## 3. Navigation Arguments Discipline: Pass IDs, Never Whole Objects

### 1. What is it
The golden rule of Android Navigation: Pass the absolute minimum amount of data necessary to identify the content for the next screen. Usually, this means passing a `String` or `Int` ID, and NEVER a full complex object (like a full `Expense` data class).

### 2. Why does it exist
1. **TransactionTooLargeException**: Android limits `Bundle` sizes (used under the hood for navigation and process death) to ~500KB. Passing large objects crashes the app.
2. **Stale Data**: If you pass `Expense(amount=50)` to Screen B, and a background sync updates the DB to `amount=100`, Screen B is showing stale data because it's relying on the navigation argument rather than observing the Single Source of Truth (Database).

### 3. Mental model
When you text a friend to look at a web page, you send them the URL (the ID), not a screenshot of the entire webpage text. They use the URL to fetch the live webpage. Do the same for your screens.

### 4. How it works
The sending screen passes `id`. The receiving screen's ViewModel reads `id` from `SavedStateHandle`, then queries the Repository: `repository.getExpenseFlow(id)`. The UI observes this Flow.

### 5. Code
```kotlin
// ❌ WRONG ROUTE (Do not do this)
@Serializable
data class WrongDetailRoute(val expense: Expense) // Complex object!

// ✅ RIGHT ROUTE
@Serializable
data class RightDetailRoute(val expenseId: String) // Just the ID

// ✅ Receiving ViewModel
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ExpenseRepository
) : ViewModel() {
    private val expenseId = savedStateHandle.toRoute<RightDetailRoute>().expenseId
    
    // Fetch live data from DB based on ID
    val expenseFlow: StateFlow<Expense?> = repository.getExpense(expenseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
```

### 6. Production usage
In an Expense Tracker, the List screen shows summaries. Clicking an item passes the UUID. The Detail ViewModel observes the DB for that UUID. If the user edits the expense, the DB updates, and the Detail screen immediately reflects it because it's observing the DB, not a static argument.

### 7. Common mistakes
❌ **Wrong:** Passing an object because "I already fetched it on the previous screen, I want to save a database call." (A local Room DB read takes ~2ms. It is practically free. Do not sacrifice architecture for a 2ms micro-optimization).

### 8. Debugging
If data looks outdated after an edit, check if the screen is rendering state from `SavedStateHandle` arguments instead of a `StateFlow` backed by a Repository.

### 9. Testing
Verify your ViewModel fetches from the repository immediately upon init using the ID from the `SavedStateHandle`.

### 10. Exercise
Create a scenario where Screen A passes an ID to Screen B. While on Screen B, simulate a database update (via a delayed coroutine or button). Ensure Screen B updates automatically.

### 11. Deliberate failure
Create a `@Serializable` route that takes a massive List of objects. Navigate to it. Watch the app crash with `TransactionTooLargeException` in Logcat.

### 12. Interview questions
- **Q:** Why shouldn't you pass Parcelable domain models as navigation arguments?
- **Q:** What is the maximum size of a Bundle in Android, and what happens if you exceed it?

### 13. Checkpoint
You understand that arguments are just "pointers" (IDs), and the Database is the only source of truth.

---

## 4. Nested Graphs & Flow-Scoped ViewModels

### 1. What is it
A Nested Graph groups related screens together under a single parent route. A Flow-Scoped ViewModel is a ViewModel whose lifecycle is tied to this parent graph, rather than a single screen, allowing state to be shared across the entire flow.

### 2. Why does it exist
Some features are multi-step wizards (e.g., Onboarding: Step 1 -> Step 2 -> Step 3). If you use a single-screen ViewModel, state is lost when moving to the next screen. If you use an Activity/Singleton ViewModel, the state lives forever and leaks memory. A Flow-Scoped ViewModel lives exactly as long as the user is inside the wizard, and dies when they finish or exit.

### 3. Mental model
Think of a Nested Graph as a "folder" in your website path (`/checkout/cart`, `/checkout/payment`). The Flow-Scoped ViewModel is a temporary shopping cart that exists only while you are inside the `/checkout` folder.

### 4. How it works
Use the `navigation<ParentRoute>(startDestination = ...)` builder. 
To scope a ViewModel to the parent, get the `NavBackStackEntry` of the parent route using `navController.getBackStackEntry(ParentRoute::class)`, then pass that entry to `hiltViewModel()`.

### 5. Code
```kotlin
// 1. Define Routes
@Serializable data object OnboardingGraph
@Serializable data object OnboardingStep1
@Serializable data object OnboardingStep2

// 2. Setup Graph
NavHost(navController, startDestination = OnboardingGraph) {
    
    // Nested Graph
    navigation<OnboardingGraph>(startDestination = OnboardingStep1) {
        
        composable<OnboardingStep1> { backStackEntry ->
            // Get the parent entry to scope the ViewModel
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(OnboardingGraph)
            }
            // This ViewModel survives until OnboardingGraph is popped
            val sharedViewModel: OnboardingViewModel = hiltViewModel(parentEntry)
            
            Step1Screen(sharedViewModel, onNext = { navController.navigate(OnboardingStep2) })
        }
        
        composable<OnboardingStep2> { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(OnboardingGraph)
            }
            // Retrieves the EXACT SAME INSTANCE of the ViewModel
            val sharedViewModel: OnboardingViewModel = hiltViewModel(parentEntry)
            
            Step2Screen(sharedViewModel, onFinish = { 
                // Pop the entire graph to clear the ViewModel
                navController.navigate(DashboardRoute) {
                    popUpTo(OnboardingGraph) { inclusive = true }
                }
            })
        }
    }
}
```

### 6. Production usage
Used for complex Expense creation where Step 1 is Amount/Category, Step 2 is receipt scanning, and Step 3 is tagging. The `CreateExpenseViewModel` holds the draft expense and is scoped to `CreateExpenseGraph`.

### 7. Common mistakes
❌ **Wrong:** Storing flow state in a Singleton Database/Repository before it's finalized, resulting in "abandoned drafts" if the user force-closes the app.
✅ **Right:** Storing draft state in a Flow-Scoped ViewModel. It's automatically garbage collected if the user backs out.

### 8. Debugging
If state is resetting between steps, you likely called `hiltViewModel()` without passing the `parentEntry`, which created a brand new ViewModel instance scoped to the current screen instead of the parent.

### 9. Testing
Unit test the ViewModel normally. UI tests need a test `NavHost` to verify state persists across the nested navigation.

### 10. Exercise
Implement a two-step "Add User" wizard. Screen 1 asks for Name. Screen 2 asks for Email. Screen 2 should be able to display the Name entered in Screen 1 using a Flow-Scoped ViewModel.

### 11. Deliberate failure
In the exercise above, forget to use `navController.getBackStackEntry(Parent)`. See how Screen 2 shows a blank Name because it got a fresh ViewModel.

### 12. Interview questions
- **Q:** How do you share state between three screens in a multi-step form without using a Singleton?
- **Q:** When does a ViewModel scoped to a nested navigation graph get destroyed?

### 13. Checkpoint
You understand that Navigation graphs can dictate the lifespan of a ViewModel, bridging the gap between single-screen and app-wide state.

---

## 5. Dialogs & Bottom Sheets as First-Class Destinations

### 1. What is it
Instead of showing Dialogs or Bottom Sheets using conditional Compose state (`if (showDialog) { AlertDialog(...) }`), you declare them as actual destinations in your `NavHost` using `dialog<Route>` or `bottomSheet<Route>`.

### 2. Why does it exist
If you use boolean flags (`var showDialog by remember { mutableStateOf(false) }`), the dialog doesn't know about the navigation back stack. Pressing the physical Back button might close the whole app instead of just the dialog. Also, you cannot "deep link" directly into a dialog. Making them destinations solves this.

### 3. Mental model
A dialog is just a screen with a transparent background. It deserves a URL (route) and a spot in the history (back stack) just like any other page.

### 4. How it works
Replace `composable<Route>` with `dialog<Route>` (built-in) or `bottomSheet<Route>` (requires `accompanist-navigation-material` or modern Compose Material 3 navigation APIs). When navigated to, it renders over the previous back stack entry without destroying it.

### 5. Code
```kotlin
@Serializable data class DeleteConfirmation(val itemId: String)

NavHost(navController, startDestination = HomeRoute) {
    composable<HomeRoute> { 
        HomeScreen(
            onDeleteClick = { id -> navController.navigate(DeleteConfirmation(id)) }
        ) 
    }
    
    // Treated as a navigation destination!
    dialog<DeleteConfirmation> { backStackEntry ->
        val route = backStackEntry.toRoute<DeleteConfirmation>()
        
        AlertDialog(
            onDismissRequest = { navController.popBackStack() },
            title = { Text("Delete item ${route.itemId}?") },
            confirmButton = {
                Button(onClick = { 
                    // Perform delete, then pop
                    navController.popBackStack() 
                }) { Text("Confirm") }
            }
        )
    }
}
```

### 6. Production usage
In the Expense Tracker, tapping a date in a chart opens a Bottom Sheet showing expenses for that day. It is routed via `bottomSheet<DailySummaryRoute>`. Swiping it away or pressing Back pops the stack naturally.

### 7. Common mistakes
❌ **Wrong:** Passing a callback from a ViewModel to a UI just to toggle a boolean `showErrorDialog` flag.
✅ **Right:** Emitting a navigation event from the ViewModel to navigate to an `ErrorDialogRoute`.

### 8. Debugging
If a dialog route crashes complaining that the destination isn't found, ensure you used `dialog<...>` instead of `composable<...>` in the `NavHost` builder.

### 9. Testing
Test it exactly like a normal screen navigation. Verify that triggering the delete action issues a `navigate(DeleteConfirmation)` command.

### 10. Exercise
Refactor an existing `if (showDialog)` Compose implementation into a `dialog<Route>` Navigation-Compose implementation. Verify the physical back button dismisses it properly.

### 11. Deliberate failure
Implement a standard `composable<DialogRoute>` and try to draw an `AlertDialog` inside it. Notice how the background behind the dialog goes completely blank/white instead of showing the previous screen. Change it to `dialog<DialogRoute>` and observe the difference.

### 12. Interview questions
- **Q:** Why is handling dialogs via Navigation Compose preferable to using local `mutableStateOf(false)`?
- **Q:** How does a `dialog` destination differ from a `composable` destination in how it affects the back stack UI rendering?

### 13. Checkpoint
You understand that Popups, Dialogs, and Sheets are just specialized screens and should participate in the single app routing system.


---

## 6. Decoupling Navigation from Business Logic & Composables

### 1. What is it?
Decoupling navigation means ensuring your individual `@Composable` screens and ViewModels have absolutely zero knowledge of the `NavController` or the overall application routing structure.

### 2. Why does it exist?
If you pass `NavController` into a Composable (e.g., `ExpenseDetailScreen(navController)`), that screen is now permanently tied to that specific navigation graph. It becomes impossible to preview, test in isolation, or reuse in a different context (like a dual-pane tablet layout).

### 3. Mental model
Think of a **Button in a web UI component library (like React/Vue)**. The button doesn't call `window.location.href = "/next-page"`. Instead, it exposes an `onClick` prop. The parent page that uses the button decides what happens when it's clicked.

### 4. How it works
Screens expose callback lambdas (e.g., `onNavigateBack: () -> Unit`, `onExpenseClick: (String) -> Unit`). The top-level `NavHost` builder (the "Route" layer) is the only place that knows about `NavController` and connects those callbacks to actual `navController.navigate()` calls.

### 5. Code
```kotlin
// ❌ WRONG: Tightly coupled to NavController
@Composable
fun BadExpenseListScreen(navController: NavController, viewModel: ExpenseViewModel) {
    Button(onClick = { navController.navigate(ExpenseDetailRoute(id = "123")) }) {
        Text("View Detail")
    }
}

// ✅ RIGHT: Decoupled using callbacks
@Composable
fun ExpenseListScreen(
    state: ExpenseListState,
    onExpenseClick: (String) -> Unit // Callback!
) {
    Button(onClick = { onExpenseClick("123") }) {
        Text("View Detail")
    }
}

// 🌐 THE ROUTE LAYER: Connects the screen to navigation
fun NavGraphBuilder.expenseGraph(navController: NavController) {
    composable<ExpenseListRoute> {
        val viewModel: ExpenseViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        
        ExpenseListScreen(
            state = state,
            onExpenseClick = { id -> 
                navController.navigate(ExpenseDetailRoute(id)) 
            }
        )
    }
}
```

### 6. Production usage
In multi-module Enterprise Android apps, feature modules (like `:feature:expenses`) often don't even have a dependency on Jetpack Navigation. They use a centralized `Navigator` interface injected into the ViewModel, which emits `NavEvent` streams collected by the single `MainActivity`.

### 7. Common mistakes
- **Passing NavController to ViewModel:** Android ViewModels outlive UI screens. Holding a `NavController` reference in a ViewModel causes severe memory leaks.
- **Putting navigation calls in business logic:** `viewModel.saveAndNavigate()` mixes domain logic with UI routing.

### 8. Debugging
If a button click does nothing, put a breakpoint or log statement in the top-level Route where the callback is wired up to `navController.navigate()`. If the log doesn't fire, the lambda wiring is broken.

### 9. Testing
Testing becomes trivial. You don't need a fake `NavController`.
```kotlin
@Test
fun `clicking expense triggers callback`() {
    var clickedId: String? = null
    composeTestRule.setContent {
        ExpenseListScreen(state = sampleState, onExpenseClick = { clickedId = it })
    }
    composeTestRule.onNodeWithText("View Detail").performClick()
    assertEquals("123", clickedId)
}
```

### 10. Exercise
Refactor a composable that currently takes `NavController` so that it uses a data class of actions: `data class ScreenActions(val onBack: () -> Unit, val onSave: (String) -> Unit)`, and pass that action data class instead.

### 11. Deliberate failure
Pass a `NavController` into a ViewModel, rotate the device 5 times, and attempt to navigate. Watch the app crash with an `IllegalStateException` because the ViewModel is trying to use a stale `NavController` instance from a destroyed Activity.

### 12. Interview questions
- **Q:** Why shouldn't you inject `NavController` into a ViewModel?
  - **A:** `NavController` is tied to the Activity/View lifecycle. ViewModels survive configuration changes. You will leak the Activity and crash when trying to navigate.
- **Q:** How do you test navigation if the screen doesn't have the `NavController`?
  - **A:** You assert that the expected callback lambda was invoked with the correct arguments.

### 13. Checkpoint
Can you explain the difference between a UI Component (Screen) and a Routing Component (NavGraphBuilder)? UI components emit events upwards; Routing components consume events and execute navigation.

---

## 7. Deep Links, App Links & Flag-Driven Routing

### 1. What is it?
Deep links allow users to open a specific screen in your app directly from an external source (a website URL, an email link, or a push notification).

### 2. Why does it exist?
To drive user engagement. Instead of telling users "Open the app, go to Settings, then Profile", you send them a link `https://expensetracker.com/profile` that opens the exact screen immediately.

### 3. Mental model
It's exactly like hitting a specific route in a Spring Boot controller (`@GetMapping("/expense/{id}")`). The Android OS acts as the reverse proxy, forwarding the URL to your app's `NavHost`.

### 4. How it works
You define `<intent-filter>` in your `AndroidManifest.xml` so the OS knows your app handles `expensetracker.com`. In Compose Navigation, you attach a `navDeepLink` to a type-safe `@Serializable` route. The library parses the URL path and query parameters directly into your route class properties.

### 5. Code
```kotlin
@Serializable
data class ExpenseDetailRoute(val id: String)

// In your NavGraph:
composable<ExpenseDetailRoute>(
    deepLinks = listOf(
        navDeepLink<ExpenseDetailRoute>(basePath = "https://expensetracker.com/expense")
    )
) { backStackEntry ->
    // The 'id' is automatically extracted from the URL!
    // e.g. https://expensetracker.com/expense/123 -> id = "123"
    val route = backStackEntry.toRoute<ExpenseDetailRoute>()
    ExpenseDetailScreen(expenseId = route.id)
}
```

**Flag-driven routing (Conditional Navigation):**
```kotlin
composable<DashboardRoute> {
    val userManager = koinInject<UserManager>()
    val remoteConfig = koinInject<RemoteConfig>()
    
    LaunchedEffect(Unit) {
        if (!userManager.isLoggedIn) {
            navController.navigate(LoginRoute) {
                popUpTo(0) // Clear stack
            }
        } else if (remoteConfig.isFeatureXEnabled) {
            navController.navigate(NewDashboardVariantRoute) {
                popUpTo<DashboardRoute> { inclusive = true }
            }
        }
    }
    
    DashboardScreen() // Fallback/Original
}
```

### 6. Production usage
Marketing campaigns send emails with App Links. Push notifications map their payload data to a deep link URI, which is fired via `Intent`, seamlessly dropping the user on the relevant screen.

### 7. Common mistakes
- Forgetting to configure the `AndroidManifest.xml`. Compose navigation parses the link, but the OS won't send the link to your app without the manifest `intent-filter`.
- Not handling the "Back" stack. If a user deep-links directly into `ExpenseDetail`, pressing Back might exit the app instead of going to `Dashboard` unless you construct the back stack properly using `TaskStackBuilder` (or let Navigation compose handle implicit deep link stacks based on graph nesting).

### 8. Debugging
Use ADB to simulate a deep link click from the terminal:
`adb shell am start -W -a android.intent.action.VIEW -d "https://expensetracker.com/expense/123"`
If it opens the browser instead of your app, your Manifest is wrong.

### 9. Testing
Write UI tests that verify the correct screen is displayed when the `NavController` processes a deep link URI via `navController.handleDeepLink(intent)`.

### 10. Exercise
Add a deep link for `SearchRoute(val query: String)`. The deep link should be `https://expensetracker.com/search?query=food`.

### 11. Deliberate failure
Try to pass a complex nested object via a deep link URL (e.g., `https://.../search?complexObj={...}`). Watch the parsing fail. Deep links are strictly strings, ints, and booleans.

### 12. Interview questions
- **Q:** How do you test deep links during development?
  - **A:** Using the `adb shell am start` command with the `-d` flag and the URI.
- **Q:** What is the difference between a Deep Link and an Android App Link?
  - **A:** Deep Links require the user to choose an app if multiple apps handle the scheme. App Links are verified HTTP URLs (via `assetlinks.json` on your server) that open your app instantly without a disambiguation dialog.

### 13. Checkpoint
Does Jetpack Navigation automatically extract URL path segments into your `@Serializable` route's properties? (Yes, if names match and the route is registered via `navDeepLink`).

---

## 8. Screen Transitions & Predictive Back

### 1. What is it?
Customizing how screens animate in and out when navigating, and supporting Android 14's "Predictive Back" gesture which lets the user peek at the previous screen while swiping back.

### 2. Why does it exist?
Standard cross-fades feel cheap. Sliding screens horizontally mimics standard mobile UX paradigms. Predictive back gives users confidence that they won't accidentally exit the app.

### 3. Mental model
Think of a slide deck. When you hit "Next", the current slide slides left (Exit) and the new slide comes in from the right (Enter). When you hit "Back", the reverse happens (PopEnter and PopExit).

### 4. How it works
Compose Navigation allows you to specify `enterTransition`, `exitTransition`, `popEnterTransition`, and `popExitTransition` at the `NavHost` level (for defaults) or at the individual `composable` level.

### 5. Code
```kotlin
NavHost(
    navController = navController,
    startDestination = DashboardRoute,
    // Global defaults for all screens
    enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) },
    exitTransition = { fadeOut() },
    popEnterTransition = { fadeIn() },
    popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right) }
) {
    composable<DashboardRoute> { ... }
    
    // Override for a specific screen (e.g., a modal coming from bottom)
    composable<AddExpenseRoute>(
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down) }
    ) { ... }
}
```

### 6. Production usage
High-quality consumer apps use shared-element transitions (e.g., clicking a thumbnail expands it into the full-screen header) and precise slide animations based on material design guidelines.

### 7. Common mistakes
- Making animations too slow. Anything over 300ms feels sluggish.
- Not enabling `android:enableOnBackInvokedCallback="true"` in the `AndroidManifest.xml`, breaking Predictive Back entirely.

### 8. Debugging
If screens blink or flash black during transition, check if you have heavy synchronous initialization in your ViewModel's `init` block blocking the main thread during the animation.

### 9. Testing
Visual/Screenshot testing of transitions is difficult. Usually, you test that the destination is reached, relying on manual QA for animation smoothness.

### 10. Exercise
Implement a cross-fade transition for a login flow, but a horizontal slide for the main app content.

### 11. Deliberate failure
Set an `enterTransition` of `tween(durationMillis = 5000)`. Observe how agonizingly slow it is to navigate, proving that animation durations drastically impact UX.

### 12. Interview questions
- **Q:** What are the four types of transitions in Compose Navigation?
  - **A:** `enter` (going to a new screen), `exit` (current screen leaving), `popEnter` (returning to previous screen), `popExit` (current screen leaving via back press).

### 13. Checkpoint
Why is it important to support Predictive Back on modern Android? (Because it prevents accidental app exits by letting the user visually confirm what screen they are swiping back to).

---

## 9. Result Passing Between Screens (BackStackEntry Pattern)

### 1. What is it?
A mechanism for a child screen (like a Category Picker) to send a piece of data back to the parent screen (like the Add Expense screen) that launched it.

### 2. Why does it exist?
Sometimes you have a complex UI that spans multiple screens, but only the final screen submits the data to the server. You need a way to pass selections back without relying on gross global variables.

### 3. Mental model
It's like a function returning a value. `AddExpenseScreen` calls `CategoryPickerScreen()`. `CategoryPicker` returns a `String` (the ID) back to the caller.

### 4. How it works
The `NavController` holds a stack of `NavBackStackEntry` objects. Each entry has a `SavedStateHandle` (a reactive map). The child screen writes to the `previousBackStackEntry`'s `SavedStateHandle`. The parent screen reads from its `currentBackStackEntry`'s `SavedStateHandle`.

### 5. Code
**In the Parent Screen (Add Expense):**
```kotlin
composable<AddExpenseRoute> { backStackEntry ->
    // Listen for the result from the child
    val selectedCategoryFlow = backStackEntry
        .savedStateHandle
        .getStateFlow<String?>("SELECTED_CATEGORY", null)
        
    val categoryId by selectedCategoryFlow.collectAsStateWithLifecycle()

    AddExpenseScreen(
        selectedCategoryId = categoryId,
        onPickCategory = { navController.navigate(CategoryPickerRoute) }
    )
}
```

**In the Child Screen (Category Picker):**
```kotlin
composable<CategoryPickerRoute> {
    CategoryPickerScreen(
        onCategorySelected = { id ->
            // Set the result in the PARENT's saved state handle
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set("SELECTED_CATEGORY", id)
                
            // Go back to parent
            navController.popBackStack()
        }
    )
}
```

### 6. Production usage
Anywhere you have a generic picker: Selecting a country code, picking a contact, choosing an exact location on a map, or filtering a list.

### 7. Common mistakes
- **Using Singleton/Global State:** `object AppState { var selectedCategory: String? = null }`. This leaks memory, persists beyond the session, and breaks if the user opens multiple instances of the app.
- Putting large objects (like Bitmap) in `SavedStateHandle`. Only put primitive IDs or small strings.

### 8. Debugging
If the parent screen doesn't update, verify that the child used `previousBackStackEntry` and exactly the same string key (`"SELECTED_CATEGORY"`) that the parent is listening to.

### 9. Testing
Mock the `SavedStateHandle` in the ViewModel test to ensure the ViewModel correctly responds when a result key is updated.

### 10. Exercise
Create a `SelectCurrencyRoute` that passes a currency code (e.g., "USD", "EUR") back to a `SettingsRoute`.

### 11. Deliberate failure
In the child screen, call `navController.currentBackStackEntry?.savedStateHandle?.set(...)`. The parent will never see it, because the current entry is destroyed immediately upon `popBackStack()`.

### 12. Interview questions
- **Q:** How do you pass data back to a previous screen in Jetpack Navigation without a database or singleton?
  - **A:** Using the `SavedStateHandle` of the `previousBackStackEntry`.

### 13. Checkpoint
Why is `SavedStateHandle` safe to use for result passing across process death? (Because Android natively serializes `SavedStateHandle` bundles to disk when killing background processes).

---

## 10. Navigation State Under Process Death & Restoration

### 1. What is it?
When a user minimizes your app to play a heavy 3D game, the OS kills your app's process to free memory. When the user returns, the app must perfectly restore exactly where they were, including the entire back stack.

### 2. Why does it exist?
If process death returns the user to the starting Dashboard instead of the `TransactionDetailRoute` they were on, they will be frustrated and uninstall your app.

### 3. Mental model
Think of hitting "Save State" on a Nintendo emulator. The emulator is completely shut down. Later, you "Load State" and you are exactly in the middle of the jump where you left off.

### 4. How it works
`NavController` integrates directly with Android's `SavedStateRegistry`. Every time the back stack changes, it serializes the route graph into a `Bundle`. When the process restarts, `rememberNavController()` automatically reads that `Bundle` and reconstructs the stack.

### 5. Code
*You don't write code for this! Navigation-Compose does it for free.*
However, you MUST ensure your Route arguments are standard types (`Int`, `String`, etc.) or custom `@Serializable` types, otherwise the serialization fails.

```kotlin
// ✅ SURVIVES PROCESS DEATH
@Serializable
data class DetailRoute(val id: String) 

// ❌ CRASHES ON PROCESS DEATH (Not Serializable/Parcelable)
data class BadRoute(val callback: () -> Unit)
```

### 6. Production usage
Strictly mandated in all tier-1 applications. QA teams are specifically instructed to test process death on every single screen.

### 7. Common mistakes
- Using non-serializable objects in navigation arguments.
- Relying on `ViewModel` in-memory state for navigation context. If a user returns to a detail screen, the ViewModel is recreated. If the detail screen relies on an ID stored only in the previous screen's ViewModel, it will crash. The ID *must* be in the Route argument.

### 8. Debugging
To simulate process death in development:
1. Open your app and navigate deep into the stack.
2. Press Home to background the app.
3. In terminal: `adb shell am kill com.your.app.package`
4. Re-open the app from the recent apps menu.
5. If it crashes or starts at the dashboard, you failed.

### 9. Testing
Automated UI tests can simulate process death using UI Automator, verifying that specific text is still on the screen after the activity is destroyed and recreated.

### 10. Exercise
Pass a custom `enum class` as an argument in a type-safe route. Ensure the app survives process death.

### 11. Deliberate failure
Add a non-parcelable complex data structure to a route argument. Trigger process death. Watch the app fail to restart with a `ParcelFormatException`.

### 12. Interview questions
- **Q:** How do you test if your Navigation graph handles process death properly?
  - **A:** Put the app in the background, run `adb shell am kill <package>`, and bring the app to the foreground.

### 13. Checkpoint
Why does Navigation-Compose require route arguments to be primitives or Serializable? (So they can be automatically bundled to disk to survive process death).

---

## Phase 9 Project — Expense Tracker v8 (Type-Safe Navigation & Deep Links)

**Goal:** Build a complete type-safe navigation graph for the Expense Tracker.

**Requirements:**
1. **Route Hierarchy:**
   - `DashboardRoute` (start destination)
   - `TransactionListRoute(val categoryId: String? = null)`
   - `TransactionDetailRoute(val transactionId: String)`
   - `AddExpenseRoute` (nested graph / wizard with `CategoryPickerBottomSheetRoute`)
2. **Features to Implement:**
   - Result passing: Selecting a category in `CategoryPickerBottomSheetRoute` passes the selected `CategoryId` back to `AddExpenseRoute`.
   - Deep link: `https://expensetracker.com/transaction/{transactionId}` navigates directly to `TransactionDetailRoute` with correct back stack to Dashboard.
   - Clear stack: Logging out pops all screens back to `LoginRoute` with `popUpTo(0) { inclusive = true }`.
3. **Verification & Testing:**
   - Test back stack operations using `TestNavHostController`.
   - Test process death survival using `adb shell am kill` while on the detail screen.

---

## Phase 9 Checkpoint

Answer without looking:
1. Why is passing a 1MB `Parcelable` object through a Navigation argument considered dangerous, and what should you do instead?
2. What is the difference between `popUpTo(DashboardRoute) { inclusive = false }` and `popUpTo(DashboardRoute) { inclusive = true }`?
3. Why should `@Composable` screen functions never accept `NavController` as a parameter?
4. How do you return a selected item from a child screen back to a parent screen without using a shared singleton?
5. How does Navigation-Compose handle deep link back stacks (e.g., if a user opens a deep link directly to a detail screen, what happens when they press the Back button)?

---

## Complete Web Routing / QA Automation → Android Navigation-Compose Translation Table

| Web / QA / Selenium Routing Concept | Android Navigation-Compose Equivalent | Notes |
|---|---|---|
| URL Path (`/expense/123`) | `@Serializable` route (`TransactionDetailRoute(id = "123")`) | Type-safe Kotlin route class |
| Query parameter (`/search?q=food`) | Default parameter (`data class SearchRoute(val q: String = "")`) | Optional query argument |
| Browser History Back / Forward | `NavController.popBackStack()` / Back Stack | Android Back Stack management |
| Redirect / Replace URL (`location.replace`) | `popUpTo(CurrentRoute) { inclusive = true }` | Removes current screen from back stack |
| Single-Page App (React/Vue) Router | `NavHost(navController, startDestination)` | Top-level client-side routing container |
| Modal Popup / Overlay URL route | `dialog<DialogRoute>` / `bottomSheet<SheetRoute>` | First-class dialog navigation destinations |
| Passing query state to previous page | `previousBackStackEntry.savedStateHandle.set()` | Result passing pattern |
| Direct URL navigation in Appium / Web | Deep Linking (`adb am start -d "https://..."`) | System intent filter dispatch to route |
