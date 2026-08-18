# PHASE 1 — KOTLIN (Weeks 1–2)

**Objective:** Read and write idiomatic Kotlin without translating from Java in your head.
**Why this phase matters:** A solid domain model is the foundation of clean architecture. In Kotlin, features like data classes, sealed classes, and null safety allow you to model complex state explicitly, making invalid states unrepresentable. This eliminates entire classes of bugs (like NullPointerExceptions) that plague Java apps.
**Prerequisites:** Familiarity with Java (OOP, interfaces, generics), basic testing (JUnit/TestNG).
**Project deliverable:** Expense Tracker v1 — Kotlin-only domain model (no Android SDK).
**Concepts covered:** 18 total, each with the full teaching sequence (What → Why → Mental model → Mechanics → Code → Production → Mistakes → Debugging → Testing → Exercise → Deliberate failure → Interview Qs → Checkpoint).

---

## 1. `val` vs `var`; Immutability as the Default

**1. What is it**
`val` (value) declares a read-only reference, similar to a `final` variable in Java. `var` (variable) declares a mutable reference.

**2. Why does it exist**
Java's default is mutable; you have to opt-in to immutability using `final`. Kotlin makes immutability ergonomic, encouraging safer, thread-safe code by default.

**3. Mental model**
`val` is a permanent name tag for a box. You can't stick the tag on a different box later. `var` is a sticky note you can move around. Note: `val` only guarantees the reference won't change; if the box is a mutable list, you can still add items to it.

**4. How it works**
Under the hood, a Kotlin `val` property on a class generates a private backing field and a getter. A `var` property generates a private field, a getter, and a setter.

**5. Code (Expense Tracker)**
```kotlin
class ExpenseTrackerApp {
    val appName = "Enterprise Expense Tracker" // val means reference cannot be reassigned
    var currentActiveUserId = "U1234"          // var can be reassigned

    fun switchUser(newUserId: String) {
        // appName = "New App" // ERROR: Val cannot be reassigned
        currentActiveUserId = newUserId // OK
    }
}
```

**6. Production usage**
Use `val` for 95% of your variables. Use `var` only when a specific algorithm requires mutation (e.g., counters, UI state holders).

**7. Common mistakes**
Mistaking `val` for deep immutability.
```kotlin
val expenses = mutableListOf<Expense>()
// This is legal because the list reference doesn't change, just its contents.
expenses.add(newExpense) 
```
Instead, use immutable collections (`List<T>`) when exposing state.

**8. Debugging**
If state is changing unexpectedly, trace your `var`s. In IntelliJ/Android Studio, you can highlight a `var` to see all write accesses (they are often underlined or highlighted differently).

**9. Testing**
Testing immutable state is trivial: construct, act, assert output. No setup/teardown needed for shared mutable state.

**10. Exercise**
Take a standard Java POJO with 5 fields and getters/setters. Convert it to a Kotlin class using `val` and `var`. Notice how much boilerplate vanishes.

**11. Deliberate failure**
```kotlin
val limit = 500
limit++ // Fix this: either make it `var` or re-evaluate if you need mutation.
```

**12. Interview questions**
* Is `val` truly immutable? (Answer: No, it's a read-only reference. The underlying object might be mutable or it might have a custom getter that returns dynamic values).
* How does `val` compile down in Java?

**13. Checkpoint**
Can you explain why Kotlin developers prefer `val` and when you are forced to use `var`?

---

## 2. Null safety — `?`, `?.`, `?:`, `!!`, `lateinit`, platform types

**1. What is it**
Kotlin's type system distinguishes between types that can hold `null` (e.g., `String?`) and those that cannot (e.g., `String`).

**2. Why does it exist**
To eliminate `NullPointerException` (The Billion Dollar Mistake). In Java, any object reference can be null. In Kotlin, nullability is explicitly modeled and checked at compile time.

**3. Mental model**
A non-null type is a solid box that guaranteed has an item. A nullable type is a Schrodinger's box—it might have an item, or it might be empty. You must open it carefully (using Kotlin's operators) before using it.

**4. How it works**
The compiler enforces null checks.
* `?` marks a type nullable.
* `?.` (Safe call) calls a method only if non-null, else returns null.
* `?:` (Elvis operator) provides a default value if null.
* `!!` (Not-null assertion) forces the compiler to treat it as non-null (throws NPE if wrong).
* `lateinit` promises you will initialize a `var` before reading it (used for DI/frameworks).

**5. Code (Expense Tracker)**
```kotlin
class User(val id: String, val taxIdentifier: String?) // taxIdentifier might be null

fun processTax(user: User) {
    // val length = user.taxIdentifier.length // ERROR: Only safe calls allowed
    
    // Safe call
    val lengthIfPresent: Int? = user.taxIdentifier?.length
    
    // Elvis operator (if null, fallback to "DEFAULT")
    val safeId: String = user.taxIdentifier ?: "DEFAULT"
    
    // Smart cast: Kotlin remembers the null check
    if (user.taxIdentifier != null) {
        val len = user.taxIdentifier.length // No safe call needed here!
    }
}
```

**6. Production usage**
Domain models rarely use `!!`. We use `?.let { ... }` heavily to execute blocks only if a value is present. `lateinit` is used heavily in Android activities or Spring Boot `@Autowired` beans.

**7. Common mistakes**
Using `!!` to silence compiler warnings. This brings NPEs back to your app.
*Wrong:* `val name = user.profile!!.name`
*Right:* `val name = user.profile?.name ?: "Unknown"`

**8. Debugging**
If a `NullPointerException` occurs in Kotlin, it's almost always due to: 1) Using `!!`, 2) `lateinit` variable not initialized, 3) Interoperating with Java (Platform Types).

**9. Testing**
Pass explicit `null` to your functions to ensure your Elvis operators or safe calls handle them gracefully.

**10. Exercise**
Write a function `getEmployeeManagerName(emp: Employee?): String` that navigates `emp -> department -> manager -> name`. Use safe calls and return "No Manager" if any step is null.

**11. Deliberate failure**
```kotlin
var name: String? = "John"
name = null
val length = name!!.length // Observe the NullPointerException
```

**12. Interview questions**
* What is a Platform Type (like `String!`)? (Answer: A type coming from Java without nullability annotations. Kotlin disables null safety checks for it).
* Difference between `lateinit` and `lazy`?

**13. Checkpoint**
Do you instinctively reach for `?:` instead of `if (x != null)`?

---

## 3. `data class`

**1. What is it**
A class primarily built to hold data, where the compiler automatically generates `equals()`, `hashCode()`, `toString()`, and `copy()` based on the primary constructor properties.

**2. Why does it exist**
Java requires Lombok or massive IDE-generated boilerplate for plain data objects (POJOs/DTOs). Data classes give you all this in one line.

**3. Mental model**
It's a "record" (like Java 14 records). It focuses purely on "what data do I hold" rather than "what behavior do I have".

**4. How it works**
When you prefix `data class`, the compiler inspects only the `val`/`var` parameters declared in the *primary constructor* to build the boilerplate.

**5. Code (Expense Tracker)**
```kotlin
data class Expense(
    val id: String,
    val amount: Double,
    val category: String,
    val note: String? = null
)

// Usage
val coffee = Expense("1", 4.50, "Food", "Starbucks")
val coffeeDuplicate = Expense("1", 4.50, "Food", "Starbucks")

println(coffee == coffeeDuplicate) // true (structural equality, like Java .equals)
val editedCoffee = coffee.copy(amount = 5.00) // copy with modification
```

**6. Production usage**
Used extensively for API Responses, UI State (in MVI/MVVM), Room Database entities, and anywhere you need a simple bag of data.

**7. Common mistakes**
Putting logic in `equals()` manually instead of letting the compiler do it. Defining properties inside the class body (they won't be part of the generated `equals`/`hashCode`/`toString`).

**8. Debugging**
If `==` is returning false for what looks like identical objects, check if one of the properties is an Array (use `.contentEquals()` instead) or if a property is declared in the class body rather than the primary constructor.

**9. Testing**
Data classes are trivial to assert in tests because structural equality (`==`) works out of the box. `assertEquals(expectedExpense, actualExpense)` just works.

**10. Exercise**
Create a `Receipt` data class. Try instantiating it, copying it, and destructing it: `val (id, total) = receipt`.

**11. Deliberate failure**
```kotlin
data class Session(val token: String) {
    var userId: String = "" // This property is ignored by equals() and hashCode()
}
// Create two sessions with same token but different userIds, observe they are ==.
```

**12. Interview questions**
* Can a data class be abstract or open? (Answer: No, they are final by default and cannot be inherited from).
* Explain destructuring declarations with data classes.

**13. Checkpoint**
Why is `copy()` so crucial for immutable state management in UI architectures?

---

## 4. `sealed class` / `sealed interface` + exhaustive `when`

**1. What is it**
Sealed types restrict class hierarchies. All direct subclasses must be known at compile time (defined in the same package/module).

**2. Why does it exist**
To model restricted state (Algebraic Data Types). Java `enum`s are restricted but every enum constant must have the exact same shape. Sealed classes allow subclasses to have completely different fields and behaviors.

**3. Mental model**
Enums on steroids. An Enum is a fixed set of *values*. A Sealed Class is a fixed set of *types*.

**4. How it works**
Because the compiler knows every possible subclass, it can enforce exhaustive checks when you use a `when` statement (Kotlin's switch). If you add a new subclass later, your code won't compile until you handle it in the `when` block.

**5. Code (Expense Tracker)**
```kotlin
sealed interface SyncState {
    data object Idle : SyncState
    data class Syncing(val progressPercent: Int) : SyncState
    data class Error(val exception: Exception, val canRetry: Boolean) : SyncState
}

fun renderUI(state: SyncState) {
    // The compiler forces us to handle all 3 cases. No 'else' branch needed!
    when (state) {
        is SyncState.Idle -> showIdleUi()
        is SyncState.Syncing -> showProgressBar(state.progressPercent)
        is SyncState.Error -> showError(state.exception, state.canRetry)
    }
}
```

**6. Production usage**
This is the absolute backbone of modern Android UI state (Jetpack Compose, Redux/MVI patterns), Network Result wrappers (`Success`, `Loading`, `Error`), and domain event modelling.

**7. Common mistakes**
Adding an `else ->` branch in your `when` expression. If you use `else`, you defeat the purpose of sealed classes. When you add a new state, the compiler won't warn you because it falls into the `else` branch.

**8. Debugging**
Errors usually happen at compile-time: "'when' expression must be exhaustive". This is a good thing!

**9. Testing**
Tests often look like: `val result = repository.fetch(); assert(result is NetworkResult.Success)`.

**10. Exercise**
Model an `ExpenseCategory` sealed class. It can be `Food` (has restaurantName), `Travel` (has miles), or `Other` (has reason). Write an exhaustive `when` function to print details.

**11. Deliberate failure**
Create a `sealed class` with two subclasses. Write a `when` statement that only checks one. See the compile error.

**12. Interview questions**
* When would you use a sealed class vs an enum? (Answer: When the states need to carry different parameters/data).
* Difference between `sealed class` and `sealed interface`?

**13. Checkpoint**
Do you understand why adding `else` to a `when` block checking a sealed class is an anti-pattern?

---

## 5. `object`, `data object`, `companion object`

**1. What is it**
`object` is Kotlin's native Singleton. `companion object` is Kotlin's alternative to Java's `static` members. `data object` is a singleton with generated `toString`.

**2. Why does it exist**
Implementing singletons in Java is famously error-prone (double-checked locking, etc). Kotlin makes it a language feature. Also, Kotlin has no `static` keyword; everything is an object.

**3. Mental model**
`object`: A class where only exactly one instance exists, managed by the language.
`companion object`: A special singleton permanently attached to a class, acting like static methods/fields.

**4. How it works**
Declaring `object Logger` creates both the class definition and its single instance. `companion object` inside a class allows you to call methods on the class name itself (like `Factory.create()`).

**5. Code (Expense Tracker)**
```kotlin
// Singleton
object DatabaseConfig {
    val version = 1
    fun getPath(): String = "/data/db"
}

// Companion Object
class Expense(val amount: Double) {
    companion object {
        const val MAX_AMOUNT = 10_000.0 // Like static final
        fun createDefault() = Expense(0.0) // Factory method
    }
}

// Usage
val dbPath = DatabaseConfig.getPath()
val defaultExp = Expense.createDefault()
```

**6. Production usage**
`object` is used for stateless utility wrappers or config. `companion object` is strictly used for factory methods (`newInstance()`) and constants (`const val`).

**7. Common mistakes**
Using `object` for classes that hold application state that should be scoped to a user session. If you switch users, the singleton retains old data. (Prefer Dependency Injection for these).

**8. Debugging**
State leaking between tests. Since `object` state persists across the JVM lifetime, test A can pollute test B if they mutate an `object`.

**9. Testing**
Avoid mutable state in `object` declarations. If they only hold functions or constants, testing is easy.

**10. Exercise**
Convert a Java `DateUtils` class with static methods into a Kotlin `object`.

**11. Deliberate failure**
Create an `object SessionState { var username = "" }`. Write two tests. Set username in test 1. Print it in test 2. Watch state bleed.

**12. Interview questions**
* How is a Kotlin `object` represented in Java byte code? (Answer: A class with a static `INSTANCE` field).
* Why doesn't Kotlin have a `static` keyword?

**13. Checkpoint**
Can you identify when to use `object` vs Dependency Injection for singletons?

---

## 6. Extension functions & properties

**1. What is it**
The ability to add new functions or properties to an existing class without inheriting from it or using Decorators.

**2. Why does it exist**
Java uses endless `*Utils` classes (`StringUtils.capitalize(str)`). Extension functions make this look like native class methods (`str.capitalize()`), resulting in fluent, readable code.

**3. Mental model**
It's an optical illusion. You aren't actually altering the original class; the compiler is just writing a static utility method for you but letting you use dot-notation.

**4. How it works**
You prefix the function name with the type you are extending (`ReceiverType.`). Inside the function, `this` refers to the receiver instance.

**5. Code (Expense Tracker)**
```kotlin
// We want to format Doubles as currency, but we don't own the Double class.
fun Double.toCurrencyString(): String {
    return "$${String.format("%.2f", this)}"
}

// Extension property
val String.isValidEmail: Boolean
    get() = this.contains("@") && this.contains(".")

// Usage
val amount = 45.5
println(amount.toCurrencyString()) // "$45.50"
println("user@test.com".isValidEmail) // true
```

**6. Production usage**
Used universally. Android's KTX libraries are just thousands of extension functions over ancient Java APIs to make them Kotlin-friendly.

**7. Common mistakes**
Overusing them. If a function requires access to a class's internal private state, it should be a regular method, not an extension. Extensions cannot break encapsulation.

**8. Debugging**
If you have multiple extensions with the same name, IDE auto-imports can get confused. Pay attention to which package you imported it from.

**9. Testing**
Test them exactly like regular pure functions.

**10. Exercise**
Write an extension function on `List<Expense>` called `totalAmount()` that sums up all expenses.

**11. Deliberate failure**
Try to access a `private` property of a class from an extension function on that class. See the compiler block you.

**12. Interview questions**
* Are extension functions resolved statically or dynamically? (Answer: Statically. If you have open classes, the extension for the declared type is called, not the runtime type).

**13. Checkpoint**
Understand how `fun String.isCool()` becomes `public static boolean isCool(String receiver)` in Java.

---

## 7. Default & named arguments

**1. What is it**
Functions can provide default values for parameters. When calling functions, you can name the arguments explicitly.

**2. Why does it exist**
It completely eliminates the need for the Builder pattern and method overloading in 99% of cases.

**3. Mental model**
Instead of having 5 different constructors for a class, you have one master constructor, and you just fill out the fields you care about; the rest get default values.

**4. How it works**
Specify `= value` in the parameter list. When calling, use `paramName = value`.

**5. Code (Expense Tracker)**
```kotlin
// One function replaces 4 overloaded Java methods
fun fetchTransactions(
    userId: String,
    limit: Int = 50,
    sortBy: String = "date",
    ascending: Boolean = false
) { ... }

// Usage
fetchTransactions("U1") // Uses all defaults
fetchTransactions("U1", ascending = true) // Named argument, skips limit and sortBy
```

**6. Production usage**
Used heavily in data classes, UI components (Compose uses this extensively so you only configure the UI props you want to change), and API clients.

**7. Common mistakes**
Not using named arguments when you have multiple parameters of the same type (e.g., `boolean`). `update(true, false, true)` is unreadable. `update(force = true, async = false, retry = true)` is clear.

**8. Debugging**
If interacting with Java, Java cannot see Kotlin's default arguments unless you annotate the Kotlin function with `@JvmOverloads`, which generates the actual overloaded methods.

**9. Testing**
Makes creating mock objects in tests extremely easy, as you only pass the fields relevant to the specific test via named arguments.

**10. Exercise**
Write a `User` data class with 5 properties. Make 3 of them optional via defaults. Instantiate it using named arguments for just the optional fields.

**11. Deliberate failure**
Mix named and positional arguments out of order. `fetchTransactions(userId = "1", 50)` — observe IDE rules on mixing.

**12. Interview questions**
* What does `@JvmOverloads` do?

**13. Checkpoint**
Can you explain why the Builder pattern is largely obsolete in Kotlin?

---

## 8. Higher-order functions, lambdas, inline

**1. What is it**
Functions that take other functions as parameters, or return functions.

**2. Why does it exist**
To support Functional Programming. It allows you to extract control structures and pass behavior around as easily as data.

**3. Mental model**
A higher-order function is a factory line that has a "plug-in" slot. You provide a custom piece of logic (the lambda) to plug into the slot to change how the factory operates.

**4. How it works**
Function types are declared like `(Int) -> String` (takes an Int, returns a String). Lambdas are usually enclosed in curly braces `{ }`. If a lambda is the *last* parameter, it can be written outside the parentheses (trailing lambda syntax).

**5. Code (Expense Tracker)**
```kotlin
// Higher-order function
fun filterExpenses(expenses: List<Expense>, predicate: (Expense) -> Boolean): List<Expense> {
    val result = mutableListOf<Expense>()
    for (e in expenses) {
        if (predicate(e)) { // executing the passed function
            result.add(e)
        }
    }
    return result
}

// Usage using trailing lambda syntax
val highValue = filterExpenses(allExpenses) { expense -> 
    expense.amount > 1000.0 
}

// 'it' is the implicit name for a single parameter
val foodExpenses = filterExpenses(allExpenses) { it.category == "Food" }
```

**6. Production usage**
Collections (`map`, `filter`), Coroutines, Jetpack Compose (`Button(onClick = { ... })`), and DSLs.

**7. Common mistakes**
Overusing `it`. If lambdas are nested, `it` becomes ambiguous. Always name your variables explicitly in nested lambdas.

**8. Debugging**
Lambdas create anonymous classes under the hood in Java. This can impact performance if called in tight loops. Using the `inline` keyword copies the lambda's byte code directly into the call site, eliminating overhead.

**9. Testing**
You can pass dummy lambdas `{ true }` or spy lambdas to verify they were invoked.

**10. Exercise**
Write a `retryOnFailure(times: Int, block: () -> Unit)` function that executes `block()`, catching exceptions, up to `times`.

**11. Deliberate failure**
Nest three lambdas using `it` in all of them. Notice how the compiler gets confused, and your code becomes unreadable.

**12. Interview questions**
* What does the `inline` modifier do, and when should you NOT use it? (Answer: Don't use it for large functions or functions passed around as variables, use it for small functional utilities).
* Explain Kotlin's trailing lambda syntax.

**13. Checkpoint**
Can you read `(String, Int) -> Unit` and immediately understand what type of function it describes?

---

## 9. Scope functions: `let`, `run`, `with`, `apply`, `also`

**1. What is it**
Five standard library functions whose sole purpose is to execute a block of code within the context of an object.

**2. Why does it exist**
To make code more concise and expressive. They eliminate temporary variables and make builder-style initialization easy on any object.

**3. Mental model**
* `apply`: "Take this thing, configure it, and return the thing itself."
* `let`: "Take this thing, do something with it, and return the result."
* `also`: "Take this thing, do some side-effect with it, and return the thing itself."
* `run`: Similar to let, but uses `this` instead of `it`.

**4. How it works**
They vary based on two axes:
* How the object is referenced (`this` vs `it`).
* What the block returns (the object itself vs the block's return value).

**5. Code (Expense Tracker)**
```kotlin
// --- APPLY: Object configuration ---
// Context: 'this'. Returns: The object.
val intent = Intent().apply {
    action = "VIEW"
    putExtra("id", "123")
}

// --- LET: Null-checks & transformations ---
// Context: 'it'. Returns: Result of block.
val user: User? = repository.getUser()
val uiMessage = user?.let {
    "Welcome, ${it.name}!"
} ?: "Please log in"

// --- ALSO: Side effects ---
// Context: 'it'. Returns: The object.
val newExpense = Expense(5.0, "Coffee").also {
    logger.info("Created expense: $it")
}
```

**6. Production usage**
* `apply` is heavily used for Android View initialization.
* `let` is the standard way to unwrap Nullable types (`?.let`).
* `also` is for logging/validation chains.

**7. Common mistakes**
Nesting scope functions.
```kotlin
user?.let { u ->
    u.profile?.apply {
        // confusing mix of 'this' and 'it'
    }
}
```
If you nest them, extract the logic to a private method.

**8. Debugging**
When returning values from `let` or `run`, it's easy to accidentally return `Unit` because your last line was a print statement rather than the intended value.

**9. Testing**
Scope functions are inline standard library functions; they don't affect testing strategy.

**10. Exercise**
Take a 5-line configuration of a `java.io.File` (create, set readable, set writable) and rewrite it as a 1-liner using `apply`.

**11. Deliberate failure**
Use `apply` when you meant to use `let` (try to transform a String to an Int). Observe how `apply` insists on returning the original String.

**12. Interview questions**
* What is the difference between `apply` and `let`?
* When would you use `takeIf`? (Answer: To chain conditionals natively: `user.takeIf { it.isActive }?.let { ... }`)

**13. Checkpoint**
Can you map the 5 functions to the (Context, Return) matrix without looking it up?


---


## 10. `operator fun invoke()`

**1. What is it**  
A language feature that allows an instance of a class to be called as if it were a function.

**2. Why does it exist**  
It enforces the Single Responsibility Principle (SRP) by standardizing class execution. It's the cornerstone of the Clean Architecture "Use Case" or "Interactor" pattern in Android. Instead of naming methods `execute()`, `run()`, or `perform()`, the class itself represents the action.

**3. Mental model**  
Think of a single-purpose appliance, like a toaster. You don't say `toaster.toast()`; you just put bread in and push the lever. The object *is* the action.

**4. How it works**  
By defining a function named `invoke` and prepending it with the `operator` keyword, Kotlin compiler translates `myObject(args)` into `myObject.invoke(args)`.

**5. Code**  
```kotlin
class ProcessTransactionUseCase(private val repository: TransactionRepository) {
    // The class defines exactly one action
    suspend operator fun invoke(transaction: Transaction): Result<Unit> {
        if (transaction.amount <= 0) return Result.failure(Exception("Invalid amount"))
        return repository.save(transaction)
    }
}

// Usage:
val processTransaction = ProcessTransactionUseCase(repo)
// Looks like a function call, but it's an object instance
val result = processTransaction(Transaction(id = "1", amount = 50.0)) 
```

**6. Production usage**  
Domain layer Use Cases. In Android, ViewModels inject these use cases and call them directly: `getUser(userId)`.

**7. Common mistakes**  
Creating a use case class but giving it multiple `invoke` operators with different responsibilities, turning it back into a messy repository or service class.

```kotlin
// WRONG: A Use Case should have one responsibility
class UserUseCase {
    operator fun invoke(id: String): User { ... }
    operator fun invoke(user: User): Result<Unit> { ... } // Mixing get and save!
}
```

**8. Debugging**  
If you try to call an object and get a compilation error "Expression 'xyz' of type 'Xyz' cannot be invoked as a function", verify the `invoke` function has the `operator` modifier.

**9. Testing**  
Test it exactly as you would test any single-method class.
```kotlin
@Test
fun `invoking use case with valid transaction returns success`() = runTest {
    val useCase = ProcessTransactionUseCase(FakeRepo())
    val result = useCase(validTransaction) // Testing the invoke operator
    assertTrue(result.isSuccess)
}
```

**10. Exercise**  
Create a `CalculateTaxUseCase` that takes an `income` and returns the tax amount. Use `operator fun invoke`.

**11. Deliberate failure**  
Remove the `operator` keyword from your `invoke` function and try to call the class instance. Observe the compiler error.

**12. Interview questions**  
*   *Junior:* How do you make an object callable like a function?
*   *Mid:* Why do we use `operator fun invoke` for Use Cases instead of just naming a function `execute()`?
*   *Senior:* What are the memory implications of injecting 50 Use Cases into a ViewModel versus one large Repository?

**13. Checkpoint**  
You should understand that `operator fun invoke` is syntactic sugar for `.invoke()` that promotes highly cohesive, single-action classes.

---

## 11. Visibility Modifiers

**1. What is it**  
Keywords controlling access: `public` (default), `internal` (module-wide), `private` (file/class-wide), `protected` (subclasses).

**2. Why does it exist**  
To encapsulate implementation details and define rigid API boundaries.

**3. Mental model**  
*   `public`: The storefront (open to everyone).
*   `internal`: The employee breakroom (anyone in the store's module can enter).
*   `protected`: The manager's office (managers and their direct successors only).
*   `private`: Your personal locker (only you can open it).

**4. How it works**  
Unlike Java where default is package-private, Kotlin's default is `public`. Kotlin removes package-private entirely and replaces it with `internal`, meaning visible anywhere within the same compiled module (e.g., Gradle module).

**5. Code**  
```kotlin
// default is public
class PublicClass

internal class ModuleInternalClass // Visible only in this Gradle module

class Encapsulated {
    private val secret = 42 // Visible only inside Encapsulated
    
    // Public getter, private setter
    var readOnlyOutside: Int = 0
        private set
}
```

**6. Production usage**  
Using `internal` aggressively in multi-module Android projects. For a feature module (e.g., `:feature:login`), the `LoginViewModel` and `LoginRepositoryImpl` should be `internal`, while only the navigation destination or public interface is `public`.

**7. Common mistakes**  
Leaving everything `public`. This ruins compilation time in multi-module builds (any change invalidates the public ABI) and breaks encapsulation.

**8. Debugging**  
"Cannot access 'X': it is internal in 'xyz'". You are trying to use an `internal` class from a different Gradle module.

**9. Testing**  
You can test `internal` classes from the same module's test source set (Kotlin compiler considers `src/main` and `src/test` the same module). Testing `private` requires reflection (which you should avoid; test via public APIs).

**10. Exercise**  
Create a class with a property that anyone can read, but only the class itself can write.

**11. Deliberate failure**  
Try to access a `private` property from outside its defining class. Note the compiler error.

**12. Interview questions**  
*   *Mid:* How does Kotlin's `internal` differ from Java's default (package-private) visibility?

**13. Checkpoint**  
Kotlin defaults to `public`. `internal` is the boundary for modules, unlike Java's package boundary.

---

## 12. Collections API & Sequences

**1. What is it**  
A rich set of functional operators (`map`, `filter`, `flatMap`, `associate`, etc.) on collections, along with `Sequence` for lazy evaluation.

**2. Why does it exist**  
To replace verbose Java `for` loops and clunky Java Streams with concise, readable data transformations.

**3. Mental model**  
*   Collections API: An assembly line where *every step creates a new batch of items* before passing it to the next step.
*   Sequences: An assembly line where *one item goes through all steps* before the next item starts.

**4. How it works**  
Standard collection functions (like `.filter {}`) are `inline`, meaning the compiler replaces them with loops. They execute eagerly. Sequences execute lazily, pulling elements only when a terminal operation (like `.toList()`) is called.

**5. Code**  
```kotlin
val transactions = listOf(
    Transaction("1", 50.0, "FOOD"),
    Transaction("2", 120.0, "TECH"),
    Transaction("3", 10.0, "FOOD")
)

// map, filter, groupBy, associateBy
val foodCosts = transactions
    .filter { it.category == "FOOD" }
    .map { it.amount }
    .fold(0.0) { acc, amount -> acc + amount } // Sums to 60.0

val byCategory: Map<String, List<Transaction>> = transactions.groupBy { it.category }
val byId: Map<String, Transaction> = transactions.associateBy { it.id }

// Sequences for large data
val largeData = generateSequence(1) { it + 1 }
val result = largeData
    .filter { it % 2 == 0 }
    .map { it * 2 }
    .take(5)
    .toList() // Terminal operation
```

**6. Production usage**  
Formatting lists for UI (e.g., mapping `DomainModel` to `UiModel`), grouping transactions by date for sticky headers in a `RecyclerView`/`LazyColumn`.

**7. Common mistakes**  
Using eager collections for huge datasets or chaining 5+ operators on a large list (creating multiple intermediate lists in memory).
```kotlin
// WRONG: Creates 3 intermediate lists in memory!
hugeList.filter { it.isValid }.map { it.id }.sorted()

// RIGHT: Use Sequence
hugeList.asSequence().filter { it.isValid }.map { it.id }.sorted().toList()
```

**8. Debugging**  
Use the IDE's "Sequence Debugger" tool or sprinkle `.onEach { println(it) }` in the chain to see what's happening at each step.

**9. Testing**  
Verify transformations yield expected subsets. Use `assertEquals` on the resulting lists.

**10. Exercise**  
Given a list of users, find the first user older than 18 using `firstOrNull`, then map to their email, returning "No email" if none found.

**11. Deliberate failure**  
Call `.first { ... }` with a condition that matches no elements. Observe the `NoSuchElementException`. Replace with `firstOrNull`.

**12. Interview questions**  
*   *Junior:* What is the difference between `map` and `flatMap`?
*   *Mid:* When would you use `asSequence()` over standard list operations?
*   *Senior:* Why are standard collection functions like `filter` marked as `inline`?

**13. Checkpoint**  
Kotlin collections are eager by default. Sequences are lazy. `associateBy` creates maps from lists based on a key.

---

## 13. `Result<T>`, `runCatching`, and Sealed-Class Results

**1. What is it**  
Mechanisms for handling errors without throwing exceptions. `Result<T>` is a standard library inline class. `runCatching` is a block that catches exceptions and returns `Result`. Custom Sealed classes represent domain-specific outcomes.

**2. Why does it exist**  
Throwing exceptions hides control flow (GoTo in disguise). Returning types makes error handling explicit and compiler-enforced.

**3. Mental model**  
Instead of throwing a grenade (Exception) that blows up the call stack unless caught, you hand the caller a sealed box. The box either contains the item (`Success`) or a note explaining why it's empty (`Failure`).

**4. How it works**  
`runCatching` wraps a `try/catch(Throwable)` block. Sealed classes allow you to define finite states (Success, NetworkError, Unauthorized) which the compiler forces you to check via `when`.

**5. Code**  
```kotlin
// 1. Standard Result (Good for simple boundaries)
fun parseId(input: String): Result<Int> = runCatching {
    input.toInt() // Throws NumberFormatException if invalid
}

// 2. Custom Sealed Result (Best for Domain/App layers)
sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class HttpError(val code: Int, val message: String) : NetworkResult<Nothing>
    object NoInternet : NetworkResult<Nothing>
}

// Usage
when (val response = fetchApi()) {
    is NetworkResult.Success -> showData(response.data)
    is NetworkResult.HttpError -> showError("Code ${response.code}")
    NetworkResult.NoInternet -> showOfflineUi()
}
```

**6. Production usage**  
Repository boundaries returning domain-specific sealed classes. `Result<T>` used internally or for simple utilities.

**7. Common mistakes**  
Using `runCatching` in Coroutines without re-throwing `CancellationException`. `runCatching` catches *everything*, including the exception Coroutines use to cancel themselves.
```kotlin
// WRONG & DANGEROUS in Coroutines:
suspend fun fetchData() {
    runCatching {
        delay(5000) // If coroutine is cancelled, runCatching swallows it!
    }
}
```

**8. Debugging**  
If a coroutine refuses to cancel, look for a naked `try { ... } catch (e: Exception)` or `runCatching` swallowing the `CancellationException`.

**9. Testing**  
Return `Result.failure()` or your `Sealed.Error` from fakes to test UI error states.

**10. Exercise**  
Create a `sealed interface LoginResult`. Handle `Success(val token: String)`, `InvalidCredentials`, and `LockedOut(val timeRemaining: Duration)`. Write a `when` block to handle them.

**11. Deliberate failure**  
Add a new state to your sealed interface. Do not update the `when` block. Watch the compiler complain about an non-exhaustive `when`.

**12. Interview questions**  
*   *Mid:* Why shouldn't you use `runCatching` around suspending functions without modifications?
*   *Senior:* Compare Java's Checked Exceptions to Kotlin's Sealed Class return types.

**13. Checkpoint**  
Explicit types > Exceptions. Sealed classes enforce exhaustive error handling at compile time.

---

## 14. Generics: Variance and Reified Types

**1. What is it**  
Variance (`in` / `out`) controls subtyping rules for generics. `reified` allows accessing the actual type of a generic inside an inline function at runtime.

**2. Why does it exist**  
Java generics are invariant and suffer from type erasure at runtime. Kotlin fixes the verbosity of Java's `? extends T` and allows bypassing type erasure via `reified`.

**3. Mental model**  
*   `out` (Covariant): "Producer". A vending machine that only gives out `T`.
*   `in` (Contravariant): "Consumer". A trash can that only takes in `T`.
*   `reified`: Magic X-ray glasses that let you see erased types at runtime.

**4. How it works**  
Declaration-site variance (`class Source<out T>`) applies rules to the whole class. `inline fun <reified T>` tells the compiler to copy the function body to the call site and replace `T` with the actual class type.

**5. Code**  
```kotlin
// Variance
interface Producer<out T> { fun produce(): T } // Can return T, cannot take T
interface Consumer<in T> { fun consume(item: T) } // Can take T, cannot return T

// Reified (Impossible in Java without passing Class<T>)
inline fun <reified T> String.toObject(): T {
    // We can use T::class.java because T is reified!
    return Gson().fromJson(this, T::class.java) 
}

// Usage
val user: User = "{name: 'Rohit'}".toObject()
```

**6. Production usage**  
`out` is heavily used in generic states: `sealed class State<out T>`. `reified` is used for JSON parsing, casting utilities, and Android's `startActivity<MyActivity>()`.

**7. Common mistakes**  
Trying to use `reified` without marking the function `inline`. The compiler will block this.

**8. Debugging**  
ClassCastExceptions or "Cannot check for instance of erased type: T". Fix by using `reified`.

**9. Testing**  
Test type-casting utilities to ensure they handle malformed JSON or incorrect casts gracefully.

**10. Exercise**  
Write an inline function with a reified type `inline fun <reified T> Any.isInstanceOf(): Boolean` that checks if the object is of type `T`.

**11. Deliberate failure**  
Try to check `if (myList is List<String>)`. Observe the "erased type" error.

**12. Interview questions**  
*   *Mid:* What does `reified` do and why must it be combined with `inline`?
*   *Senior:* Explain the difference between `out T` in Kotlin and `? extends T` in Java.

**13. Checkpoint**  
`out` produces, `in` consumes. `reified` defeats type erasure for inline functions.

---

## 15. Delegation

**1. What is it**  
Handing over the implementation of an interface or the getter/setter of a property to another object.

**2. Why does it exist**  
To favor composition over inheritance natively, without writing boilerplate forwarding methods.

**3. Mental model**  
Like a boss delegating work. The boss (your class) claims they can do the job (implements Interface), but behind the scenes, they just hand the task to a subordinate (the delegate).

**4. How it works**  
Using the `by` keyword. For interfaces, `class A(b: B) : B by b`. For properties, `val x by lazy { ... }`.

**5. Code**  
```kotlin
// 1. Property Delegation
val heavyResource: Database by lazy { Database() } // Init only on first access

// 2. Interface Delegation
interface Analytics { fun logEvent(name: String) }
class FirebaseAnalytics : Analytics {
    override fun logEvent(name: String) = println("Firebase: $name")
}

// UseCases doesn't need to inherit; it delegates
class PaymentProcessor(
    analyticsDelegate: Analytics
) : Analytics by analyticsDelegate { // Forward all Analytics calls to delegate
    
    fun process() {
        logEvent("Process_Started") // Handled by delegate
    }
}
```

**6. Production usage**  
`by lazy` for expensive initialization. `by viewModels()` in Android Fragments. Interface delegation for cross-cutting concerns (Analytics, Logging) without deep inheritance hierarchies.

**7. Common mistakes**  
Overusing interface delegation for massive interfaces, hiding where the actual work is happening and making the code hard to trace.

**8. Debugging**  
If a delegated property crashes, check the initialization block of the delegate (e.g., the lambda in `lazy`).

**9. Testing**  
Pass fakes or mocks as the delegate in tests.

**10. Exercise**  
Create a generic `lazy` property that prints "Initializing" the first time it is accessed.

**11. Deliberate failure**  
Access a `by lazy` property from multiple threads simultaneously without thread safety (though `lazy` is thread-safe by default, you can pass `LazyThreadSafetyMode.NONE` to break it).

**12. Interview questions**  
*   *Mid:* How does `by lazy` differ from `lateinit var`?
*   *Senior:* How does Kotlin implement interface delegation under the hood? (Bytecode generation of forwarding methods).

**13. Checkpoint**  
`by` removes boilerplate for composition and lazy initialization.

---

## 16. Aliases, Value Classes, Enums vs Sealed

**1. What is it**  
Ways to represent domain concepts. `typealias` renames an existing type. `value class` wraps a type with zero runtime overhead. Enums group constants; Sealed classes group distinct types.

**2. Why does it exist**  
To make types more expressive. A string is just a string, but an `EmailAddress` conveys intent.

**3. Mental model**  
*   `typealias`: A nickname. "William" -> "Bill". They are identical.
*   `value class`: A uniform. A `String` puts on an `Email` uniform. You can't treat it like a normal `String` anymore.
*   Enums: Different flavors of the same item (Red, Green, Blue).
*   Sealed classes: Different vehicles (Car, Boat, Plane).

**4. How it works**  
`typealias` is resolved at compile time. `value class` inline at compile time (no object allocation).

**5. Code**  
```kotlin
// Typealias: Just a nickname. Can easily mix them up.
typealias UserId = String
typealias AuthToken = String

// Value Class: Type-safe, zero overhead. Cannot mix them up.
@JvmInline
value class AccountId(val value: String)

// Enum: Instances are identical in structure
enum class Role { ADMIN, USER }

// Sealed: Instances can hold different data
sealed interface UIEvent {
    object Click : UIEvent
    data class TextChange(val text: String) : UIEvent
}
```

**6. Production usage**  
Value classes for IDs so you don't accidentally pass a `TransactionId` into an `AccountId` parameter. Sealed interfaces for MVI architecture intents/events.

**7. Common mistakes**  
Using `typealias` for IDs and accidentally passing the wrong string. Always prefer `value class` for strong typing.

**8. Debugging**  
`value class` disappears at runtime, which can make reflection or Java interop tricky.

**9. Testing**  
Value classes are tested just like primitive types.

**10. Exercise**  
Create a `value class Password` that throws an `IllegalArgumentException` in its `init` block if length < 8.

**11. Deliberate failure**  
Try to pass a `String` into a function requiring an `AccountId` value class.

**12. Interview questions**  
*   *Junior:* When would you use a Sealed Class instead of an Enum?
*   *Senior:* What is the runtime overhead of a `value class` compared to a standard `data class` wrapping a primitive?

**13. Checkpoint**  
Use `value class` for IDs. Use sealed classes for state/events with distinct payloads.

---

## 17. Time and Duration

**1. What is it**  
`kotlinx.datetime` is Kotlin's multiplatform time library. `kotlin.time.Duration` represents time spans.

**2. Why does it exist**  
`java.util.Date` is notoriously terrible. `java.time` is great but JVM-only. Kotlin's Duration replaces messy raw longs (e.g., `delay(5000L)` -> `delay(5.seconds)`).

**3. Mental model**  
*   `Instant`: A point on the timeline (UTC).
*   `LocalDateTime`: What the clock on your wall says.
*   `Duration`: A stopwatch measuring time elapsed.

**4. How it works**  
Extension properties on Int/Long create Durations.

**5. Code**  
```kotlin
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.hours

val timeout = 30.seconds
val oneDay = 24.hours

suspend fun poll() {
    delay(timeout) // Takes Duration natively
}
```

**6. Production usage**  
Setting timeouts for network requests, calculating "time ago" in UIs, Coroutine delays.

**7. Common mistakes**  
Storing `LocalDateTime` in a database instead of `Instant` (UTC epoch). Always store UTC, only format to local time for the UI.

**8. Debugging**  
If time math is weird, verify you aren't mixing up `Instant` with `LocalDateTime`.

**9. Testing**  
Durations are easy to assert: `assertTrue(timeTaken < 1.seconds)`.

**10. Exercise**  
Write a function that takes a `Duration` and returns a string like "5m 30s".

**11. Deliberate failure**  
Try passing an `Int` to a Coroutine `delay` function that expects a `Duration`.

**12. Interview questions**  
*   *Mid:* Why shouldn't you use `java.util.Date` in new projects?

**13. Checkpoint**  
Use `Instant` for absolute time, `Duration` for spans.

---

## 18. Java Interop

**1. What is it**  
Annotations like `@JvmStatic`, `@JvmOverloads`, and `@Throws` that instruct the Kotlin compiler on how to generate Java bytecode.

**2. Why does it exist**  
To make Kotlin APIs look natural when called *from* Java. (Calling Java *from* Kotlin usually just works).

**3. Mental model**  
A translator adapting Kotlin idioms (like default parameters, which Java lacks) into things Java understands (overloaded methods).

**4. How it works**  
*   `@JvmStatic`: Generates a real static method in Java instead of requiring `Companion.method()`.
*   `@JvmOverloads`: Generates multiple method overloads in Java for a Kotlin function with default args.

**5. Code**  
```kotlin
class Utilities {
    companion object {
        @JvmStatic // Java sees: Utilities.doMath()
        fun doMath() {}
    }
}

class CustomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr)
```

**6. Production usage**  
Crucial in legacy codebases transitioning from Java to Kotlin. Custom Android Views often use `@JvmOverloads` for constructors.

**7. Common mistakes**  
Adding these annotations in a 100% Kotlin codebase. They add bloat and are unnecessary if no Java code is calling them.

**8. Debugging**  
If Java can't see your default parameters, you forgot `@JvmOverloads`.

**9. Testing**  
Write a Java test class to ensure the Kotlin API feels idiomatic from the Java side.

**10. Exercise**  
Write a Kotlin function with 3 default parameters. Call it from a Java class with 1 parameter. Fix the compilation error using `@JvmOverloads`.

**11. Deliberate failure**  
Try to catch a specific Exception in Java that is thrown by a Kotlin function. Add `@Throws` to the Kotlin function to fix it.

**12. Interview questions**  
*   *Mid:* What does `@JvmOverloads` do under the hood?

**13. Checkpoint**  
Only use Jvm annotations when calling Kotlin from Java.

---

## Phase 1 Project — Expense Tracker v1

**Goal:** Design a complete Kotlin-only domain model for the Expense Tracker. No Android SDK, no UI, no Databases. Pure Kotlin modeling.

**Requirements:**

1.  **Value Classes:** Use `@JvmInline value class` for `TransactionId`, `AccountId`, `CategoryId`.
2.  **Domain Entities:**
    *   `Transaction` (id, amount, date, description, categoryId, accountId, type)
    *   `Account` (id, name, balance)
3.  **Sealed Types:**
    *   `TransactionType` (INCOME, EXPENSE, TRANSFER)
    *   `DomainError` (InsufficientFunds, AccountNotFound, InvalidAmount)
    *   `Result<T, E>` (Custom sealed interface with Success and Error variants)
4.  **Repository Interface:**
    *   `TransactionRepository` outlining suspending functions to save, delete, and fetch transactions.
5.  **Use Cases (Operator invoke):**
    *   `AddTransactionUseCase`
    *   `CalculateBalanceUseCase`
6.  **Collections & Time:**
    *   A function to group transactions by Month.
    *   Use `kotlinx.datetime.Instant` and `Duration`.

**Acceptance Criteria:**
*   A `Transaction` cannot be created with a negative amount (throw exception in `init` or use a factory).
*   `AddTransactionUseCase` must return your custom `Result<Unit, DomainError>`. It should return `InsufficientFunds` if an expense exceeds account balance.
*   Unit tests covering the use case logic.

---

## Phase 1 Checkpoint

Revisit the code from the introduction:

```kotlin
internal class GetUser(private val repo: UserRepo) {
    suspend operator fun invoke(id: String, refresh: Boolean = false): Result<User> = repo.get(id, refresh)
}
```

**Explanation of every construct:**
*   `internal`: This class is only visible within its compiled module.
*   `class GetUser`: A Use Case class following Single Responsibility.
*   `(private val repo: UserRepo)`: Primary constructor injecting dependencies. `val` makes it a property, `private` hides it from outside.
*   `suspend`: This function can pause execution (Coroutines). Must be called from a coroutine or another suspend function.
*   `operator fun invoke`: Allows instances of `GetUser` to be called like a function: `val user = getUser("123")`.
*   `id: String`: Explicit non-nullable string type.
*   `refresh: Boolean = false`: Default argument. Callers can omit this.
*   `: Result<User>`: The return type. Standard library `Result` encapsulating success or failure.
*   `= repo.get(id, refresh)`: Single-expression function syntax. Replaces `{ return ... }`.

**Why `else ->` in a `when` over a sealed type is usually a latent bug:**
Sealed types represent a finite, known set of possibilities. If you use `else ->`, you bypass the compiler's exhaustiveness check. If a colleague adds a new state (e.g., `Result.Loading`) later, the compiler won't warn you to handle it; the code will silently fall into the `else` block, potentially causing logical errors or UI bugs. Always explicitly list all sealed subclasses.

---

## Complete Java → Kotlin Translation Table

| Concept | Java Way | Kotlin Way | Notes |
| :--- | :--- | :--- | :--- |
| **Visibility** | package-private (default) | `public` (default), `internal` | `internal` is module-wide |
| **Variables** | `final String name` | `val name: String` | `val` = read-only, `var` = mutable |
| **Null Safety** | `@Nullable String` | `String?` | Enforced at compile time |
| **Constructors** | Explicit `public ClassName(...)` | Primary constructor `class User(val id: String)` | Data classes generate boilerplate |
| **Static Methods** | `public static void doIt()` | `companion object { fun doIt() }` | Objects are singletons |
| **Data Classes** | Boilerplate getters/equals | `data class User(val id: Int)` | Auto-generates `copy`, `equals`, `hashCode` |
| **Switch** | `switch (val) { case 1: break; }` | `when (val) { 1 -> ... }` | `when` can return expressions |
| **Extending APIs**| Utils classes (`StringUtils.cap()`) | Extension functions `fun String.cap()` | Appears as part of the class |
| **Lambdas** | `(a) -> { return a * 2; }` | `{ a -> a * 2 }` or `{ it * 2 }` | `it` for single parameter |
| **Streams** | `.stream().filter().collect()` | `.filter().map()` | Collections are eager, Use `asSequence()` for lazy |
| **Use Cases** | `public void execute()` | `operator fun invoke()` | Call instances like functions |
| **Async** | Threads, RxJava, Callbacks | `suspend fun`, Coroutines | Sequential asynchronous code |
| **Errors** | `throw new Exception()` | `Result<T>`, Sealed Classes | Typesafe error handling |
| **Interfaces** | Boilerplate delegation | `class A : B by delegate` | Built-in composition |
| **Constants** | `public static final` | `const val` | Primitive types only |
| **IDs** | `String id` | `@JvmInline value class Id` | Zero allocation wrapper |
