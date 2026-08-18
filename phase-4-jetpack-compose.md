# PHASE 4 — JETPACK COMPOSE (Weeks 6–8)

**Objective:** Build screens declaratively and understand recomposition, state, and stability well enough not to write janky UI.  
**Why this phase matters:** Jetpack Compose replaces imperative XML views (`findViewById`, ViewBinding, XML layouts) with reactive Kotlin UI functions. In declarative UI, you describe what the UI looks like for any given state, and Compose updates only what changed.  
**Prerequisites:** Phase 1 (Kotlin), Phase 2 (Coroutines & Flow), Phase 3 (Android Platform).  
**Project deliverable:** Expense Tracker v2 — Multi-screen Compose UI (Login, Dashboard, Transaction List & Details) with 4 UI states (Loading/Content/Empty/Error), Material 3 theming, dark mode, and LazyColumn optimizations.  
**Concepts covered:** 16 total, each with the full 13-step teaching sequence.

---

## Concept 1: `@Composable` Functions

### 1. What is it
A Kotlin function annotated with `@Composable` that transforms data into a UI hierarchy. It does not return UI objects (like `View` in Android or `WebElement` in Selenium); instead, it "emits" UI nodes to the Compose compiler. `f(State) = UI`.

### 2. Why does it exist
In Java/XML Android (or traditional web DOM), you manually instantiate UI widgets and mutate them when state changes (`textView.setText(val)`). This leads to out-of-sync bugs. Compose fixes this by entirely regenerating the relevant UI subtree whenever the state changes.

### 3. Mental model
**Selenium/QA Analogy:** Imagine if instead of writing a script that clicks, finds an element, and updates its text (imperative DOM mutation), you wrote a script that said, "Given the user is logged in, the screen looks like this." Whenever the "logged in" variable changes, a magic framework instantly diffs the screen and updates the necessary pixels.

### 4. How it works
The Compose compiler plugin intercepts `@Composable` functions. It rewrites them under the hood to pass an implicit `Composer` object. This object tracks the function's location in the UI tree, the parameters passed to it, and deciding if it needs to be run again (recomposed) when data changes. Composable functions must be idempotent, fast, and side-effect free.

### 5. Code
```kotlin
@Composable
fun TransactionRow(transaction: Transaction) {
    // We declare the UI. No return statement, just emitting elements.
    Text(text = transaction.merchantName)
    Text(text = transaction.amount.toString())
}
```

### 6. Production usage
Used as the fundamental building block of all UI in modern Android apps. An entire screen is just a composable function that calls other composable functions.

### 7. Common mistakes
❌ **Wrong:** Trying to return a value or mutate external state inside a composable.
```kotlin
// WRONG: Side effects in composable
var callCount = 0
@Composable
fun MyText(text: String) {
    callCount++ // Recomposition will make this unpredictable!
    Text(text)
}
```
✅ **Right:** Side-effect free, purely mapping input to UI.

### 8. Debugging
Use the Layout Inspector in Android Studio. You can see the tree of composables. Since composables are just functions, you can also place breakpoints, though they might hit rapidly during recomposition.

### 9. Testing
Use `ComposeTestRule`.
```kotlin
composeTestRule.setContent { TransactionRow(tx) }
composeTestRule.onNodeWithText("Starbucks").assertIsDisplayed()
```

### 10. Exercise
Write a `@Composable` function `ExpenseHeader` that takes a `totalAmount: Double` and displays it.

### 11. Deliberate failure
Write a composable that updates a global `var counter = 0` every time it's called. Notice how it increments unpredictably when you interact with the UI. Fix it by removing the side effect.

### 12. Interview questions
- What does the `@Composable` annotation actually do?
- Why can't you call a `@Composable` function from a regular function?

### 13. Checkpoint
Can you explain why `@Composable` functions don't return `View` objects?

---

## Concept 2: Recomposition Mechanics

### 1. What is it
The process where Compose re-executes `@Composable` functions whose inputs (state) have changed, skipping those whose inputs haven't changed.

### 2. Why does it exist
Re-running the entire UI tree for every data change would be disastrous for performance (60/120 frames per second). Recomposition optimizes by only running the functions that read the changed state.

### 3. Mental model
**Spreadsheet Analogy:** If cell C1 is `=A1+B1`, changing A1 updates C1, but doesn't recalculate D1 which is `=E1+F1`. Compose acts like a reactive spreadsheet for UI.

### 4. How it works
During the initial composition, Compose tracks which composables read which state objects. When a state object changes, Compose schedules the reader composables for recomposition. Recomposition can run in parallel, in any order, and can be cancelled if the state changes again before it finishes.

### 5. Code
```kotlin
@Composable
fun Dashboard(user: User, balances: Balances) {
    // If 'user' changes, Header recomposes. 
    // If 'balances' is unchanged, BalanceList is SKIPPED.
    Header(user) 
    BalanceList(balances) 
}
```

### 6. Production usage
Crucial for building smooth, non-janky lists and animations. Understanding skipping is the #1 skill for Compose performance.

### 7. Common mistakes
❌ **Wrong:** Relying on execution order.
```kotlin
@Composable
fun BadOrder() {
    var isReady = false
    Step1(onDone = { isReady = true })
    if (isReady) Step2() // Never rely on Step1 executing before this line synchronously
}
```

### 8. Debugging
Android Studio has "Recomposition Counts" in the Layout Inspector. If a composable is recomposing on every frame unnecessarily, it highlights a performance bug.

### 9. Testing
Write a test that clicks a button to update state, and assert the new UI element appears. You don't test recomposition directly; you test the UI output.

### 10. Exercise
Create a screen with a button and a text field. Pass a constant string to the text field, and a changing counter to the button. Use logs to prove the text field composable doesn't run when the button is clicked.

### 11. Deliberate failure
Pass an unstable object (like a generic `List` or a `var` class) to a composable. Observe that it recomposes even when the data didn't change, because Compose can't guarantee it hasn't mutated. Fix it by using `persistentListOf` or an `@Immutable` data class.

### 12. Interview questions
- What makes a composable "skippable"?
- Can recomposition be cancelled? What happens to side effects if it is?

### 13. Checkpoint
If you have 10 items in a list and 1 changes, how many item composables are re-executed?

---

## Concept 3: State in Compose

### 1. What is it
The tools Compose uses to hold data that drives the UI: `mutableStateOf` (holds a value and triggers recomposition), `remember` (keeps a value across recompositions), `rememberSaveable` (keeps it across config changes), `derivedStateOf` (computed state), and `snapshotFlow` (converting state to Flow).

### 2. Why does it exist
Local variables in a composable function are re-initialized on every recomposition. You need a way to tell Compose "remember this value" and "notify me when it changes".

### 3. Mental model
**Memory Analogy:** A regular variable is like short-term memory (lost when the function ends). `remember` is a sticky note on the fridge (survives function calls). `rememberSaveable` is writing it in a notebook (survives the house turning upside down / screen rotation).

### 4. How it works
`mutableStateOf` creates an observable `State` object. `remember` caches an object in the composition tree. When the state changes, Compose finds all composables reading that state and re-executes them.

### 5. Code
```kotlin
@Composable
fun Counter() {
    // Survives recomposition, triggers recomposition on change
    var count by remember { mutableStateOf(0) }
    
    // Survives rotation
    var textInput by rememberSaveable { mutableStateOf("") }
    
    // Only recalculates when count > 10 changes, not on every count change
    val isHigh by remember { derivedStateOf { count > 10 } }

    Button(onClick = { count++ }) {
        Text("Count: $count")
    }
}
```

### 6. Production usage
Managing local UI state (like whether a dialog is open, text input fields) directly in the view layer without cluttering the ViewModel.

### 7. Common mistakes
❌ **Wrong:** Using a regular variable.
```kotlin
@Composable
fun BadCounter() {
    var count = 0 // Resets to 0 on every recomposition!
    Button(onClick = { count++ }) { Text("$count") }
}
```
✅ **Right:** Using `remember { mutableStateOf() }`.

### 8. Debugging
If UI isn't updating, you probably forgot `mutableStateOf` or are mutating an object's internal properties instead of assigning a new object.

### 9. Testing
Simulate clicks and assert text changes. If state isn't remembered, the assert will fail.

### 10. Exercise
Build a generic `ExpandableCard` that holds its own `isExpanded` state using `rememberSaveable`.

### 11. Deliberate failure
Use `remember` instead of `rememberSaveable` for a text input. Rotate the emulator. Watch the text disappear. Fix by changing to `rememberSaveable`.

### 12. Interview questions
- When should you use `derivedStateOf` instead of just calculating the value directly?
- What is the difference between `remember` and `rememberSaveable`?

### 13. Checkpoint
Explain why mutating a standard Kotlin `ArrayList` inside a remembered state won't trigger recomposition.

---

## Concept 4: State Hoisting & Unidirectional Data Flow (UDF)

### 1. What is it
Moving state out of a composable and passing it in as a parameter (State Hoisting). Data flows down from the caller, and events (lambdas) flow up. (UDF).

### 2. Why does it exist
Composables that manage their own state are hard to test, reuse, and sync with other components. Hoisting makes them "stateless".

### 3. Mental model
**Puppeteer Analogy:** A stateless composable is a puppet. It doesn't decide to move its arm; the puppeteer (caller/ViewModel) pulls the string (passes state). When the puppet gets hit (event), it doesn't react, it just yells "I got hit!" (invokes lambda) and waits for the puppeteer to move it.

### 4. How it works
Replace internal `remember` state with a parameter for the value, and a lambda parameter for the event that changes the value.

### 5. Code
```kotlin
// Stateless, reusable, testable
@Composable
fun AmountInput(amount: String, onAmountChange: (String) -> Unit) {
    TextField(
        value = amount,
        onValueChange = onAmountChange // Event flows up
    )
}

// Stateful caller (Puppeteer)
@Composable
fun TransactionScreen(viewModel: TxViewModel) {
    // Data flows down
    val currentAmount = viewModel.amountState.collectAsState().value
    AmountInput(
        amount = currentAmount, 
        onAmountChange = { viewModel.updateAmount(it) }
    )
}
```

### 6. Production usage
Every enterprise app uses UDF. ViewModels hold the state, screens collect the state and pass it down to small, stateless UI components.

### 7. Common mistakes
❌ **Wrong:** Passing ViewModels deep into the hierarchy.
```kotlin
// WRONG: Tightly coupled
@Composable
fun AmountInput(viewModel: TxViewModel) { ... }
```
✅ **Right:** Pass only what the component needs (String, lambda).

### 8. Debugging
If a UI element acts weirdly on its own, it probably holds internal state that is conflicting with the ViewModel state. Hoist it.

### 9. Testing
Stateless composables are trivial to test: just pass dummy data and empty lambdas. No mock viewmodels needed.

### 10. Exercise
Refactor the `ExpandableCard` from Concept 3 to be stateless. Pass `isExpanded` and `onToggle` as parameters.

### 11. Deliberate failure
Build a form where two input fields need to validate against each other. Try doing it with internal `remember` state in each field. Watch it become a tangled mess. Fix it by hoisting both states to a parent container.

### 12. Interview questions
- What are the benefits of state hoisting?
- How deep should you pass state down the tree?

### 13. Checkpoint
Define Unidirectional Data Flow in one sentence.

---

## Concept 5: `Modifier` System

### 1. What is it
An immutable, chained object used to decorate or augment a composable. It handles styling (padding, background), layout (size, constraints), and behavior (clickable, scrollable).

### 2. Why does it exist
Instead of having 100 XML attributes (`android:padding`, `android:background`) on every single view class, Compose extracts these into a universal `Modifier` interface.

### 3. Mental model
**Photoshop Layers Analogy:** Modifiers wrap the composable sequentially. `Modifier.padding(16).background(Red)` applies padding, *then* paints the background (so the padding is uncolored). `Modifier.background(Red).padding(16)` paints the background, *then* adds padding inside it (so the padding is red). **Order matters fundamentally.**

### 4. How it works
Each modifier element creates a new layout node wrapping the previous one. It evaluates from outside-in for measurement, and inside-out for returning sizes.

### 5. Code
```kotlin
@Composable
fun StyledButton(onClick: () -> Unit) {
    Text(
        text = "Save",
        modifier = Modifier
            .clickable { onClick() }
            .padding(16.dp) // Padding INSIDE the clickable area
            .background(Color.Blue)
            .padding(8.dp) // Padding INSIDE the blue background
    )
}
```

### 6. Production usage
Virtually every composable takes a `modifier: Modifier = Modifier` as its first optional parameter. This allows callers to dictate sizing/spacing without the composable needing to know about its parent.

### 7. Common mistakes
❌ **Wrong:** Not exposing a modifier parameter, or applying a caller's modifier at the wrong place in the internal tree.
```kotlin
@Composable
fun MyCard(modifier: Modifier = Modifier) {
    // WRONG: Ignoring the caller's modifier
    Box(Modifier.padding(16.dp)) { ... } 
}
```
✅ **Right:** Applying the caller's modifier to the root element. `Box(modifier.padding(16.dp))`

### 8. Debugging
If a click area is too small, or background colors look wrong, the modifier order is almost certainly backwards.

### 9. Testing
Modifiers like `testTag` are used explicitly for testing.
```kotlin
Modifier.testTag("save_button")
```

### 10. Exercise
Create a green square that is 100x100. Make it clickable, but only the inner 50x50 area should respond to clicks (hint: use padding).

### 11. Deliberate failure
Try to center text by adding `Modifier.fillMaxSize()` to a `Text` composable, but put it *after* `Modifier.background()`. Notice the background only covers the text itself. Fix by swapping order.

### 12. Interview questions
- Why does the order of `Modifier` functions matter?
- Why is it a best practice to pass a `Modifier` as the first optional parameter to every composable?

### 13. Checkpoint
What is the visual difference between `Modifier.padding(10.dp).background(Color.Red)` and `Modifier.background(Color.Red).padding(10.dp)`?

---

## Concept 6: Layouts & Measure/Layout/Draw Phases

### 1. What is it
The core layout components (`Column`, `Row`, `Box`) and the architecture of how a frame is rendered in 3 distinct phases: Composition (what to show), Layout (where to place it), and Draw (rendering pixels).

### 2. Why does it exist
Replaces `LinearLayout`, `RelativeLayout`, and `FrameLayout`. The 3-phase system exists to prevent the infamous "multiple measurement passes" issue in XML, which killed performance in deeply nested layouts.

### 3. Mental model
**Factory Assembly Line:**
1. **Composition:** What parts do we need? (Generate UI tree)
2. **Layout/Measure:** How big are they and where do they go? (Measure once, place once)
3. **Draw:** Paint them on the canvas.

### 4. How it works
- `Column` = Vertical `LinearLayout`
- `Row` = Horizontal `LinearLayout`
- `Box` = `FrameLayout` (stacking on top of each other)
Compose enforces single-pass measurement. A parent measures its children, children return their sizes, parent places them. 

### 5. Code
```kotlin
@Composable
fun TransactionItem() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).background(Color.Gray)) // Icon placeholder
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) { // Takes remaining space
            Text("Netflix")
            Text("Subscription")
        }
        Text("-$15.99")
    }
}
```

### 6. Production usage
These three (`Row`, `Column`, `Box`) make up 95% of all UI layouts in Compose. `ConstraintLayout` exists but is rarely needed compared to XML.

### 7. Common mistakes
❌ **Wrong:** Deeply nesting layouts when a `weight` or `Arrangement` would solve it.
❌ **Wrong:** Triggering Composition from a Layout or Draw phase (e.g., reading a scroll state to conditionally show an item can cause infinite loops or jank if not using derived state).

### 8. Debugging
Use Layout Inspector to see boundaries. If text is getting cut off, check if a parent is constraining it, or if you forgot a `.weight(1f)`.

### 9. Testing
Assert position/boundaries occasionally, but mostly rely on visual regression tests (Screenshot testing with Paparazzi or Roborazzi) for layout verifications.

### 10. Exercise
Build a top app bar layout using a `Row` containing a back arrow, a centered title, and a profile picture on the far right. Use `Arrangement.SpaceBetween`.

### 11. Deliberate failure
Put a `Row` inside a `Row`, give the inner `Row` `Modifier.weight(1f)`, but forget to give the outer `Row` a width constraint. Watch it collapse or behave weirdly.

### 12. Interview questions
- What are the three phases of rendering a frame in Compose?
- Why is Compose layout performance better than nested XML `LinearLayouts`?

### 13. Checkpoint
Which Compose layout maps to `FrameLayout` for stacking elements on the Z-axis?

---

## Concept 7: Lazy Lists & Performance Optimization

### 1. What is it
`LazyColumn`, `LazyRow`, and `LazyVerticalGrid`. The Compose equivalents of `RecyclerView`.

### 2. Why does it exist
If you have 1,000 transactions, rendering all of them in a `Column` will crash the app with OutOfMemory or severe lag. Lazy lists only compose and render items currently visible on screen.

### 3. Mental model
**Window Analogy:** A `Column` paints a 100-foot mural on a giant wall. A `LazyColumn` is a small window sliding over a scrolling track. The painter (Compose) furiously paints only the section you can currently see through the window, wiping away the parts that scroll out of view.

### 4. How it works
Lazy lists intercept scroll events and compose items just-in-time. 
**Crucial optimization:** By providing a `key` to `items()`, you tell Compose the unique ID of the data. When the list order changes, Compose uses the keys to just move the existing items visually, rather than destroying and recreating them.

### 5. Code
```kotlin
@Composable
fun TransactionList(transactions: List<Transaction>) {
    LazyColumn {
        // stickyHeader { DateHeader("Today") }
        
        items(
            items = transactions,
            key = { transaction -> transaction.id }, // MANDATORY for performance
            contentType = { it.type } // Helps reuse composables of same type
        ) { tx ->
            TransactionRow(tx)
        }
    }
}
```

### 6. Production usage
Any scrollable feed, list, or grid.

### 7. Common mistakes
❌ **Wrong:** Not providing a `key`. If an item is deleted from the top of the list, Compose will recompose *every single item* below it because the index changed.
❌ **Wrong:** Using a `LazyColumn` inside a scrollable `Column`. (Compose will throw an exception).

### 8. Debugging
If list animations are broken, or the wrong item gets checked when deleting a row, you have missing or duplicate `key`s.

### 9. Testing
`composeTestRule.onNodeWithText("Item 50").performScrollTo()`

### 10. Exercise
Create a `LazyColumn` of 100 items. Add a delete button to each item. 

### 11. Deliberate failure
Do the exercise without providing a `key`. Add a random `var color = remember { listOf(Red, Blue, Green).random() }` to the row. Delete the first item. Watch the colors of all other items change randomly as they get recomposed incorrectly. Add the `key`, observe it fix the bug.

### 12. Interview questions
- Why is the `key` parameter so important in `LazyColumn` `items()`?
- How does `contentType` improve LazyColumn performance?

### 13. Checkpoint
What happens if you omit the `key` block in a `LazyColumn` and insert an item at the top of the list?

---

## Concept 8: Theming & Design Tokens (Material 3)

### 1. What is it
The Compose implementation of Material Design 3. A hierarchy of `MaterialTheme` providing `ColorScheme` (colors), `Typography` (fonts), and `Shapes` (corners). 

### 2. Why does it exist
To enforce design consistency. Instead of hardcoding `Color(0xFF0000)`, you use semantic design tokens like `MaterialTheme.colorScheme.primary`. This enables instant Dark Mode and Dynamic Color (Android 12+ wallpaper colors).

### 3. Mental model
**Variables in CSS:** Instead of writing `#FF6200` everywhere, you write `var(--primary-color)`. `MaterialTheme` is essentially a giant configuration wrapper that passes these variables down to all its children implicitly via `CompositionLocal`.

### 4. How it works
At the root of your app, you wrap everything in `<YourApp>Theme`. Internally, this sets `CompositionLocalProvider` for colors, shapes, and typography. Any composable inside this block calling `MaterialTheme.colorScheme` gets the current theme's values.

### 5. Code
```kotlin
@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Usage in a screen
@Composable
fun ThemedButton() {
    Button(
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) { ... }
}
```

### 6. Production usage
Standardized styling across enterprise apps. Defines "Primary", "Secondary", "Surface", "Error" colors, and their respective "On" colors (e.g., text color *on* the primary background).

### 7. Common mistakes
❌ **Wrong:** Hardcoding colors in UI components.
```kotlin
Text("Error", color = Color.Red) // Fails in dark mode!
```
✅ **Right:** Using theme semantic colors. `color = MaterialTheme.colorScheme.error`

### 8. Debugging
Use `@PreviewLightDark` in Android Studio to instantly see your component in both themes side-by-side.

### 9. Testing
Screenshot testing (Paparazzi/Roborazzi) is the standard for verifying theming logic (running the same test with dark mode flag = true).

### 10. Exercise
Define a custom `LightColorScheme` and `DarkColorScheme`. Create a card with `containerColor = surfaceVariant` and text `color = onSurfaceVariant`. Preview it in both modes.

### 11. Deliberate failure
Hardcode a dark gray text color on a white background. Run the app in Dark Mode. Notice the text becomes invisible against the dark background. Replace with `colorScheme.onBackground`.

### 12. Interview questions
- How does `MaterialTheme` pass data down the UI tree without passing it as explicit parameters? (Hint: `CompositionLocal`).
- What is the difference between `primary` and `onPrimary` in Material 3?

### 13. Checkpoint
If you want to style the text that sits *on top* of an Error-colored background, which color token should you use?

---
*Phase 4 Part 2 will cover Concepts 9-16 (Side Effects, ViewModels, Navigation, UI Testing, interop).*


---

## 9. Side Effects & Effect Handlers

**1. What is it?**
Compose Effect Handlers are specialized functions (like `LaunchedEffect`, `rememberCoroutineScope`, `DisposableEffect`) that allow you to safely execute non-UI operations (like starting coroutines, fetching data, or subscribing to streams) that are aware of the Composable's lifecycle.

**2. Why does it exist?**
Composable functions can be executed repeatedly, in any order, and potentially in parallel during recomposition. If you make a network call or launch a coroutine directly inside the body of a Composable, it will fire every time the UI recomposes, causing massive bugs, memory leaks, and redundant API calls. Effect handlers safely bridge the UI lifecycle to the outside world.

**3. Mental model**
Imagine a Composable as a rapidly spinning fan. If you stick your hand (a side effect) in directly, you get hit repeatedly. An Effect Handler is like an automated arm that synchronizes with the fan's rotation, reaching in safely exactly when the fan starts spinning, and pulling out when it stops.

**4. How it works**
- **`LaunchedEffect(key)`:** Launches a coroutine when the Composable enters the composition. It cancels and relaunches if the `key` changes.
- **`rememberCoroutineScope()`:** Returns a coroutine scope bound to the Composable's lifecycle. Used exclusively for *user-triggered* events (like button clicks) where you can't use `LaunchedEffect`.
- **`DisposableEffect(key)`:** Used for side effects that require explicit cleanup (e.g., unregistering a listener).
- **`rememberUpdatedState(value)`:** Captures a value (usually a lambda) inside a long-running effect without restarting the effect when the value changes.

**5. Code**
```kotlin
@Composable
fun TransactionScreen(
    transactionId: String,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope() // For manual triggers
    
    // Executes ONCE when transactionId changes. 
    // Safely tied to the UI lifecycle.
    LaunchedEffect(key1 = transactionId) {
        Analytics.logScreenView("TransactionDetail", transactionId)
    }
    
    // Cleanup required
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event -> 
            if (event == Lifecycle.Event.ON_STOP) { /* pause video */ }
        }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        lifecycle.addObserver(observer)
        
        onDispose {
            lifecycle.removeObserver(observer) // Teardown!
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Button(
            modifier = Modifier.padding(padding),
            onClick = {
                // User-triggered async work MUST use the remembered scope
                scope.launch {
                    snackbarHostState.showSnackbar("Transaction deleted!")
                    onBack()
                }
            }
        ) {
            Text("Delete")
        }
    }
}
```

**6. Production usage**
- Firing initial API loads (`LaunchedEffect(Unit)`).
- Showing snackbars on button clicks (`rememberCoroutineScope`).
- Registering/unregistering sensor or broadcast listeners (`DisposableEffect`).

**7. Common mistakes**
```kotlin
// WRONG: This will fetch data on EVERY frame of a scroll or animation!
@Composable
fun BadScreen(viewModel: MyViewModel) {
    viewModel.fetchData() // 🔥 DANGER
    Text("Hello")
}
```

**8. Debugging**
If your API backend is getting hammered with identical requests, you probably put a fetch call outside of a `LaunchedEffect`. Check your Logcat; if a log statement inside a Composable prints hundreds of times a second, it's causing a recomposition loop.

**9. Testing**
To test side effects, you usually test the *State* changes that trigger them. For UI testing, Compose provides `IdlingResource` equivalents so tests wait for `LaunchedEffect` coroutines to finish before asserting.

**10. Exercise**
Write a Composable that starts a 5-second countdown timer when it appears, updates a `Text` with the remaining seconds, and shows a Snackbar when it hits 0.

**11. Deliberate failure**
Create a Composable that logs "Screen Viewed" directly in its body. Add a button that increments a counter state. Click the button and watch the logs spam "Screen Viewed" multiple times. Fix it by wrapping the log in `LaunchedEffect(Unit)`.

**12. Interview questions**
- *Q: Why can't you use `LaunchedEffect` inside the `onClick` listener of a Button?* (Because `LaunchedEffect` is a Composable function, and `onClick` is a standard Kotlin lambda callback. You must use `rememberCoroutineScope` to launch coroutines from standard callbacks).
- *Q: What does the `key` parameter in `LaunchedEffect` do?* (It determines when the effect should be canceled and restarted. Passing `Unit` or `true` means it only runs once and never restarts).

**13. Checkpoint**
You understand that Composable bodies are for *defining UI trees*, while Effect Handlers are for *doing work*.

---

## 10. Previews & Tooling Loop

**1. What is it?**
The Compose Tooling Loop provides annotations (`@Preview`) that render your Composables directly in Android Studio without needing to build the app or launch an emulator.

**2. Why does it exist?**
Compiling an Android app takes time. QA and UI iteration requires instant feedback. Previews allow you to see UI states (Loading, Success, Error, Dark Mode) instantly in the IDE.

**3. Mental model**
It's like an isolated test environment just for your UI components. You can mount a single button or a whole screen in a vacuum, supply it with dummy data, and look at it.

**4. How it works**
You write a standard function, annotate it with `@Preview`, and call your UI Composable with hardcoded or mock data. Android Studio renders it in the Split View pane.

**5. Code**
```kotlin
@Composable
fun ExpenseCard(expense: Expense) {
    Card {
        Text(expense.title)
        Text(expense.amount.toString())
    }
}

// Basic preview
@Preview(showBackground = true)
@Composable
fun PreviewExpenseCard() {
    ExpenseTrackerTheme {
        ExpenseCard(expense = Expense("Coffee", 4.50))
    }
}

// Multi-configuration previews
@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewExpenseCardTheme() {
    ExpenseTrackerTheme {
        ExpenseCard(expense = Expense("Lunch", 15.00))
    }
}

// Using PreviewParameter for multiple states
class ExpenseProvider : PreviewParameterProvider<Expense> {
    override val values = sequenceOf(
        Expense("Coffee", 4.50),
        Expense("MacBook", 2500.00), // very long number
        Expense("", 0.0) // edge case
    )
}

@Preview
@Composable
fun PreviewExpenseList(@PreviewParameter(ExpenseProvider::class) expense: Expense) {
    ExpenseCard(expense = expense)
}
```

**6. Production usage**
Teams create a comprehensive "Gallery" or "Design System" file full of Previews for every atomic component in every possible state (dark mode, large font, different languages).

**7. Common mistakes**
Passing a `ViewModel` directly into a screen Composable. This breaks Previews because the IDE cannot instantiate a complex ViewModel with network dependencies. Always pass *State* and *Event Lambdas* instead.

**8. Debugging**
If a Preview won't render, check the "Problems" pane. Usually, it's caused by a Composable trying to access a Context property or dependency injection framework that doesn't exist in the IDE rendering engine.

**9. Testing**
Previews form the exact basis for Screenshot Testing (using tools like Paparazzi or Roborazzi). You can configure tests to take pictures of your `@Preview` functions.

**10. Exercise**
Create a `LoadingButton` Composable. Write two previews: one where `isLoading = true` and one where `isLoading = false`.

**11. Deliberate failure**
Create a Composable that takes a `ViewModel`. Try to preview it. Watch the rendering engine crash with a missing dependency error. Refactor to take a UI State data class instead.

**12. Interview questions**
- *Q: How do you preview a Composable that requires a ViewModel?* (You shouldn't. You should extract the UI into a stateless Composable that takes raw data and lambdas, and preview *that*).
- *Q: What is `@PreviewParameter`?* (An interface that allows providing a sequence of mock data objects to render the same Preview multiple times with different inputs).

**13. Checkpoint**
You understand how to design UI components in isolation for fast iteration.

---

## 11. Stability & Skippability

**1. What is it?**
Compose's engine tries to skip recomposing UI nodes if their input parameters haven't changed. "Stability" refers to whether Compose can *guarantee* that an object hasn't changed.

**2. Why does it exist?**
Performance. If you have a list of 100 items, and you change 1 item, you only want that 1 row to redraw. If Compose isn't sure if the list changed, it redraws all 100.

**3. Mental model**
Think of the Compose engine as a bouncer at a club checking IDs. 
- Primitive types (`Int`, `String`) and `data class`es with `val` properties are trusted (Stable).
- Standard Kotlin `List`, `Set`, `Map` are NOT trusted (Unstable), because a standard `List` in Kotlin can actually be an `ArrayList` under the hood, meaning it could be mutated without Compose knowing.

**4. How it works**
If all parameters to a Composable are "Stable" and haven't changed since the last frame, Compose **skips** executing that function entirely.
Standard `List<T>` is considered unstable. To fix this, you either annotate a wrapper class with `@Immutable`/`@Stable`, or use `kotlinx.collections.immutable` (e.g., `ImmutableList`).

**5. Code**
```kotlin
// UNSTABLE: Compose will NEVER skip this function during recomposition
@Composable
fun UnstableTransactionList(transactions: List<Transaction>) { ... }

// STABLE WAY 1: Use kotlinx.collections.immutable
@Composable
fun StableTransactionList1(transactions: ImmutableList<Transaction>) { ... }

// STABLE WAY 2: Wrapper with @Immutable annotation
@Immutable
data class TransactionState(val items: List<Transaction>)

@Composable
fun StableTransactionList2(state: TransactionState) { ... }
```

**6. Production usage**
Crucial for `LazyColumn` performance, especially in enterprise apps with complex lists, charts, and dashboards. 

**7. Common mistakes**
Passing a standard `List<T>` to a deeply nested Composable, causing the entire tree to recompose on every tiny state change (like a timer ticking).

**8. Debugging**
You run the Compose Compiler Metrics (a Gradle flag). It outputs a report showing exactly which Composable functions are "Restartable" but not "Skippable" due to unstable parameters.

**9. Testing**
You can write UI tests that count recompositions to ensure stability, but usually, this is monitored via compiler metrics.

**10. Exercise**
Enable Compose Compiler Metrics in a dummy project. Write a function taking `List<String>`. See that it is not skippable. Change it to `ImmutableList<String>` and verify it becomes skippable.

**11. Deliberate failure**
Create a counter app that also displays a standard `List` of items. Observe via Layout Inspector that every time the counter ticks, the list recomposes too.

**12. Interview questions**
- *Q: Why is `List<T>` unstable in Compose?* (Because it's an interface that could be backed by a mutable implementation like `ArrayList`, making it impossible for Compose to track changes reliably).
- *Q: What is the difference between `@Stable` and `@Immutable`?* (`@Immutable` promises the object will *never* change after construction. `@Stable` promises that *if* it changes, Compose will be notified).

**13. Checkpoint**
You understand why `List` ruins performance and how to use `ImmutableList`.

---

## 12. Animations

**1. What is it?**
Compose's declarative animation APIs that automatically animate state changes over time.

**2. Why does it exist?**
XML animations (ViewPropertyAnimator, ObjectAnimator) were complex, highly stateful, and often disconnected from the actual data state. Compose animations are driven entirely by State.

**3. Mental model**
Instead of saying "Move this button 100 pixels to the right over 300ms", you just say "The button's X position is `targetX`". Compose interpolates the values between the old state and the new state automatically on every frame.

**4. How it works**
- `animate*AsState`: Simple fire-and-forget value transitions (colors, floats, Dp).
- `AnimatedVisibility`: For showing/hiding content with enter/exit transitions.
- `AnimatedContent`: For transitioning between different composables.
- `Modifier.animateItem()`: For `LazyColumn` items moving, adding, or deleting.

**5. Code**
```kotlin
@Composable
fun AnimatedCard(isExpanded: Boolean) {
    // 1. Simple value animation
    val cardColor by animateColorAsState(
        targetValue = if (isExpanded) Color.Blue else Color.Gray,
        label = "colorAnim"
    )

    Card(modifier = Modifier.background(cardColor)) {
        Column {
            Text("Title")
            
            // 2. Visibility animation
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text("Here are the detailed contents...")
            }
        }
    }
}
```

**6. Production usage**
Expanding/collapsing cards, changing button colors on click, animating list items when a transaction is deleted (swipe-to-dismiss).

**7. Common mistakes**
Forgetting to provide a `key` to `LazyColumn` items. Without a key, Compose cannot track which item was removed, so `Modifier.animateItem()` won't know how to animate the remaining items up.

**8. Debugging**
Android Studio provides an "Animation Preview" inspector that lets you scrub animations back and forth frame-by-frame.

**9. Testing**
UI testing animations can be flaky. Often, you disable animations during UI tests via TestRules.

**10. Exercise**
Create a Floating Action Button that transforms from a circle into an elongated pill shape containing text when you scroll a list down.

**11. Deliberate failure**
Create a `LazyColumn` without keys. Add `Modifier.animateItem()` to the rows. Delete an item. Notice the animation looks broken or jumps instantly. Add `key = { it.id }` to fix it.

**12. Interview questions**
- *Q: How do you animate the removal of an item in a LazyColumn?* (Use the `key` parameter in the `items` block, and apply `Modifier.animateItem()` to the row Composable).

**13. Checkpoint**
You can implement smooth state-driven animations without XML choreographers.

---

## 13. Semantics, Accessibility & `testTag`

**1. What is it?**
Semantics is Compose's parallel tree that describes the *meaning* of your UI (rather than how it looks) for Accessibility services (TalkBack) and UI testing frameworks.

**2. Why does it exist?**
A Composable tree is just functions emitting drawings. A screen reader or an Appium test script cannot "see" pixels; it needs to know "This is a button, it says 'Submit', and it is enabled." 

**3. Mental model**
The UI tree is the paint on the canvas. The Semantics tree is the braille overlay on top of the painting.

**4. How it works**
Compose automatically merges semantics for standard components (Text, Button). You can add or override semantics using `Modifier.semantics`. For QA/Testing, `Modifier.testTag` is the most critical bridge.

**5. Code**
```kotlin
@Composable
fun SubmitButton(isLoading: Boolean) {
    Button(
        onClick = { /* ... */ },
        // Providing a locator for Appium / Espresso
        modifier = Modifier.testTag("submit_button_tag"),
        enabled = !isLoading
    ) {
        // The Text content is automatically merged into the Button's semantics
        Text("Submit")
    }
}

// Custom Semantics for a complex custom widget
@Composable
fun CustomGraph(progress: Float) {
    Canvas(
        modifier = Modifier
            .size(100.dp)
            .semantics { 
                contentDescription = "Progress graph at ${progress * 100} percent" 
            }
    ) {
        // Draw graph...
    }
}
```

**6. Production usage**
Making apps usable by visually impaired users, and providing reliable IDs (`testTag`) for QA automation teams writing UI tests.

**7. Common mistakes**
Using highly dynamic data in `testTag` which breaks test stability, or completely ignoring `contentDescription` on non-decorative images.

**8. Debugging**
You can use the Layout Inspector in Android Studio and switch to the "Semantics" view to see exactly what TalkBack and UI tests see.

**9. Testing**
This *is* the foundation of UI testing.
```kotlin
composeTestRule.onNodeWithTag("submit_button_tag").performClick()
```

**10. Exercise**
Create a custom star-rating widget using `Canvas`. Give it a `testTag` and dynamic semantics so TalkBack reads "3 out of 5 stars".

**11. Deliberate failure**
Create an `Image` icon that acts as a button but has no `contentDescription` or text. Observe a lint warning, and see that UI tests cannot easily locate it.

**12. Interview questions**
- *Q: How do QA automation tools like Appium locate elements in Jetpack Compose, given there are no XML IDs?* (By using `Modifier.testTag`, which adds an identifier to the Semantics node).

**13. Checkpoint**
You understand the bridge between Jetpack Compose and QA Automation.

---

## 14. Interop (Compose ↔ XML)

**1. What is it?**
Mechanisms to use legacy Android Views inside Compose (`AndroidView`), and to use Compose inside legacy XML layouts (`ComposeView`).

**2. Why does it exist?**
Migrations take years. Also, some libraries (like Google Maps, AdMob, CameraX, or legacy third-party SDKs) do not have native Compose equivalents yet.

**3. Mental model**
It's an adapter plug. `ComposeView` is an XML adapter that plugs Compose into Java/XML. `AndroidView` is a Compose adapter that plugs Java/XML Views into Compose.

**4. How it works**
- **`AndroidView`:** A Composable that takes a factory lambda to instantiate a traditional View, and an update lambda that runs upon recomposition to update the View's state.
- **`ComposeView`:** A traditional `android.view.View` that you define in XML, which has a `.setContent {}` block where you write Compose code in your Fragment/Activity.

**5. Code**
**Compose inside XML (`ComposeView`):**
```xml
<!-- In layout/fragment_legacy.xml -->
<LinearLayout>
    <TextView android:text="Legacy XML Text" />
    
    <androidx.compose.ui.platform.ComposeView
        android:id="@+id/compose_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
</LinearLayout>
```
```kotlin
// In Fragment.kt
binding.composeContainer.setContent {
    ExpenseTrackerTheme {
        NewComposeComponent()
    }
}
```

**XML inside Compose (`AndroidView`):**
```kotlin
@Composable
fun MapScreen(location: Location) {
    // Wrapping a legacy View
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                // Initial setup
            }
        },
        update = { mapView ->
            // Runs on recomposition. Update map view to center on 'location'
            mapView.centerOn(location) 
        }
    )
}
```

**6. Production usage**
Incrementally migrating an Enterprise app screen-by-screen, or embedding a legacy custom charting library into a new Compose screen.

**7. Common mistakes**
Instantiating the View in the `update` block of `AndroidView`. The `factory` runs once; `update` runs constantly.

**8. Debugging**
Lifecycle mismatches. Often legacy Views require `onResume`/`onPause` calls (like MapView). You must use `DisposableEffect` to hook the Compose lifecycle to the legacy View's requirements.

**9. Testing**
Testing interop boundaries is difficult. Usually, you test the Compose and XML parts separately.

**10. Exercise**
Embed a standard Android `TextView` inside a Compose screen using `AndroidView`, and update its text via Compose State.

**11. Deliberate failure**
Create an `AndroidView`, but define the View outside the `factory` lambda. Watch the app crash on recomposition due to illegal View parent mutations.

**12. Interview questions**
- *Q: How do you embed Google Maps (which uses standard Android Views) inside a Jetpack Compose screen?* (Use the `AndroidView` composable).

**13. Checkpoint**
You can confidently migrate legacy applications without needing to rewrite everything at once.

---

## 15. `CompositionLocal`

**1. What is it?**
A tool for passing data down through the Composition tree implicitly, without needing to pass it explicitly through Composable function parameters.

**2. Why does it exist?**
Some data is needed by almost every Composable in the tree (Context, Theme colors, Density). Passing `context` or `colors` as a parameter to *every single* function would create massive boilerplate. 

**3. Mental model**
It's like ambient background radiation or gravity. Instead of handing a color down a chain of 10 people, you paint the room that color, and everyone inside the room can just look around and see it.

**4. How it works**
You define a `CompositionLocal`. At a high level in your UI tree, you `Provide` a value for it. Any Composable anywhere below that point can read `.current` to get the value. 

**5. Code**
```kotlin
// Define it
val LocalUserRole = compositionLocalOf { "Guest" }

@Composable
fun App() {
    // Provide it at the top
    CompositionLocalProvider(LocalUserRole provides "Admin") {
        DashboardScreen()
    }
}

@Composable
fun DashboardScreen() {
    // Deep down the tree...
    val role = LocalUserRole.current // Reads "Admin"
    Text("Welcome, $role")
}

// Built-in usage you will see constantly:
@Composable
fun ContextExample() {
    val context = LocalContext.current
    Toast.makeText(context, "Hello", Toast.LENGTH_SHORT).show()
}
```

**6. Production usage**
Themes (`MaterialTheme.colorScheme` uses it under the hood), injecting the Android `Context`, handling Window size classes.

**7. Common mistakes**
**The Antipattern:** Using `CompositionLocal` for explicit business data or ViewModels. It hides dependencies, making Composables very hard to test and reason about. Only use it for truly cross-cutting UI concerns.

**8. Debugging**
If a `CompositionLocal` is read before a value is provided, it returns its default value (or throws an exception if none is defined), which can lead to silent UI bugs.

**9. Testing**
When unit testing a UI component that relies on a custom `CompositionLocal`, you must wrap your test component in `CompositionLocalProvider` inside the test script.

**10. Exercise**
Create a `LocalSpacing` composition local that provides dynamic padding values based on screen size.

**11. Deliberate failure**
Pass your `ViewModel` using a `CompositionLocal`. Note how it ruins `@Preview` capability unless you mock the entire provider tree.

**12. Interview questions**
- *Q: What is the downside of using `CompositionLocal`?* (It creates implicit dependencies, violating the principle of explicit data flow in Compose, making components harder to reuse and test).
- *Q: How do you get the Android `Context` in Compose?* (`val context = LocalContext.current`).

**13. Checkpoint**
You understand the tradeoff between explicit parameters and implicit `CompositionLocal`s.

---

## 16. The Three-Layer Screen Architecture

To build maintainable, scalable, and highly testable apps with Jetpack Compose, industry standard dictates splitting every "Screen" into three distinct layers. This separates State Management (ViewModels) from UI Rendering.

### Layer 1: The Route Layer (Stateful)
**Purpose:** Connects the UI to the Architecture. 
- Injects the ViewModel.
- Collects `StateFlow` from the ViewModel into Compose State using `collectAsStateWithLifecycle()`.
- Wires up Navigation actions.
- **NEVER** contains actual UI components (Text, Buttons).

```kotlin
@Composable
fun ExpenseRoute(
    viewModel: ExpenseViewModel = hiltViewModel(),
    navigateToDetail: (String) -> Unit
) {
    // 1. Collect State
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // 2. Pass State & Events to Layer 2
    ExpenseScreen(
        state = uiState,
        onAddClick = { viewModel.addExpense() },
        onExpenseClick = navigateToDetail
    )
}
```

### Layer 2: The Screen Layer (Stateless)
**Purpose:** The layout and structure of the screen.
- Receives all data via a data class (`uiState`).
- Receives all actions via lambdas.
- Has **ZERO knowledge** of ViewModels, Databases, or Network.
- **100% Previewable and Testable.**

```kotlin
@Composable
fun ExpenseScreen(
    state: ExpenseUiState,
    onAddClick: () -> Unit,
    onExpenseClick: (String) -> Unit
) {
    Scaffold(
        floatingActionButton = { 
            FloatingActionButton(onClick = onAddClick) { Icon(Icons.Default.Add, "Add") } 
        }
    ) { padding ->
        when (state) {
            is ExpenseUiState.Loading -> CircularProgressIndicator()
            is ExpenseUiState.Success -> {
                LazyColumn(contentPadding = padding) {
                    items(state.expenses, key = { it.id }) { expense ->
                        // 3. Delegate to Layer 3
                        ExpenseCard(
                            expense = expense, 
                            onClick = { onExpenseClick(expense.id) }
                        )
                    }
                }
            }
        }
    }
}

// 🎉 Because it's stateless, we can Preview it instantly!
@Preview
@Composable
fun PreviewExpenseScreen() {
    ExpenseScreen(
        state = ExpenseUiState.Success(listOf(Expense("Coffee", 5.0))),
        onAddClick = {},
        onExpenseClick = {}
    )
}
```

### Layer 3: The Component Layer (Atomic)
**Purpose:** Reusable, small UI widgets.
- Buttons, Cards, Custom Graphs.
- Driven by primitive types or small models.

```kotlin
@Composable
fun ExpenseCard(expense: Expense, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Text(expense.title)
        Text("$${expense.amount}")
    }
}
```

---

## Phase 4 Project — Expense Tracker v2 (Compose UI)

**Goal:** Build a complete multi-screen UI in Jetpack Compose without XML layouts.

**Requirements:**
1. **Login Screen:** Email/Password fields, validation error state, loading indicator on button click.
2. **Dashboard Screen:** Total balance card, quick action buttons, spending breakdown by category with animated progress bars.
3. **Transaction List Screen:** `LazyColumn` with sticky month headers, `key = { it.id }`, custom swipe-to-dismiss or click to view details. Support 4 states: `Loading` (shimmer/spinner), `Content` (list of transactions), `Empty` (illustration + message), `Error` (retry button).
4. **Transaction Detail Screen:** Card view of transaction, category badge, timestamp, note.
5. All screens structured using the **3-Layer Screen Pattern** (Route → Screen → Components).
6. Full dark mode support using Material 3 design tokens.

---

## Phase 4 Checkpoint

Answer without looking:
1. Why does passing `List<Transaction>` to a Composable prevent Compose from skipping recomposition, and what are 3 ways to fix it?
2. What is the difference between `LaunchedEffect(Unit)` and `LaunchedEffect(transactionId)`?
3. Why must you never call `viewModelScope.launch` or execute business logic directly inside the body of a Composable function?
4. Explain how `Modifier.testTag("...")` bridges Compose with your QA test automation framework (Appium/Espresso).
5. Draw or explain the 3-Layer Screen structure and explain why Layer 2 (Screen) has no ViewModel reference.

---

## Complete QA / Selenium / Automation → Jetpack Compose Translation Table

| QA / Selenium / Web Concept | Jetpack Compose Equivalent | Notes |
|---|---|---|
| Page Object Component / Widget | Stateless `@Composable` component | Takes state, emits events |
| `By.id("login_btn")` / `By.xpath(...)` | `Modifier.testTag("login_button")` | Semantic test locator |
| Imperative DOM update (`element.setText()`) | Reactive State Update (`state = state.copy(...)`) | Declarative UI updates automatically |
| CSS Stylesheet / Theme Classes | `MaterialTheme` + Design Tokens | Type-safe theming in Kotlin |
| Web Component / React Hook | `remember` / `rememberSaveable` / Composable | Reusable UI & state logic |
| Virtual DOM reconciliation diffing | Compose Slot Table & Recomposition Skipping | Skips unchanged UI nodes |
| UI State snapshot assertion | Paparazzi / Roborazzi Screenshot Testing | Fast JVM screenshot testing |
