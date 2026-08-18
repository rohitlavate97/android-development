# PHASE 7 — NETWORKING (Week 11)

**Objective:** Integrate remote REST APIs with production-grade reliability, defensive serialization, and resilience across all failure paths.
**Why this phase matters:** Mobile networking is fundamentally different from backend-to-backend communication: mobile devices switch between Wi-Fi and cellular, enter tunnels, experience radio sleep, and receive unexpected payloads from backend microservices. A production app must handle flaky networks, token expiry races, and malformed responses without crashing.
**Prerequisites:** Phase 1 (Kotlin), Phase 2 (Coroutines & Flow), Phase 5 (App Architecture), Phase 6 (Dependency Injection).
**Project deliverable:** Expense Tracker v6 — Retrofit + OkHttp network client with auth interceptors, token refresh logic, custom serialization, and six-path error handling.
**Concepts covered:** 11 total, each with the full 13-step teaching sequence.

---

## Concept 1: HTTP Fundamentals & Mobile Networking Gotchas

### 1. What is it
The underlying protocols (HTTP/HTTPS, TLS, TCP/IP) and mobile-specific hardware realities (radio state machine, latency spikes) that dictate how an Android device communicates with servers.

### 2. Why does it exist
Unlike a backend server wired into fiber optic with steady power, a phone moves through space, switches networks, and relies on a tiny battery. You can't just blindly fire HTTP requests without understanding the physical radio cost and latency impact.

### 3. Mental model
**The Postman on a Bicycle:** A backend-to-backend call is like pneumatic tubes—instant, reliable. Mobile networking is a postman on a bicycle navigating traffic, rain, and flat tires.

### 4. How it works
- **Radio State Machine:** The phone's cellular radio has states: Idle, Low Power, High Power. Waking up the radio takes 1-2 seconds (latency) and uses immense battery. 
- **Timeouts:** Connect Timeout (time to establish TCP/TLS), Read Timeout (time waiting for a byte after connection), Write Timeout.
- **TLS Handshake:** Secure connections require multiple round-trips before the first byte of actual data is sent. Connection pooling mitigates this.

### 5. Code
Configuring timeouts appropriately in OkHttp (the underlying engine we'll use):
```kotlin
val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS) // Don't make this too long; fail fast
    .readTimeout(30, TimeUnit.SECONDS)    // Wait longer for large payloads
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()
```

### 6. Production usage
Batching non-urgent requests (like analytics or sync) to avoid waking up the radio constantly. Using cache headers (`ETag`, `Cache-Control`) to avoid fetching unchanged data over the network entirely.

### 7. Common mistakes
❌ **Wrong:** Polling a server every 5 seconds for updates (drains battery instantly).
✅ **Right:** Using WebSockets, Server-Sent Events, or FCM Push Notifications to let the server wake the app only when needed.

### 8. Debugging
Use the Android Studio Network Profiler to see traffic. Look for "radio active" periods in the Energy Profiler to see if you are keeping the radio awake unnecessarily.

### 9. Testing
In QA automation (REST Assured), you test the *server's* response. In Android testing, you mock the server (using MockWebServer) and test the *client's* resilience to 5xx, timeouts, and slow connections.

### 10. Exercise
Write down the theoretical timeline (in milliseconds) of a mobile HTTP request from a cold start vs. using a pooled connection.

### 11. Deliberate failure
Set `.connectTimeout(1, TimeUnit.MILLISECONDS)`. Run your app. Watch it immediately throw `SocketTimeoutException` because the TLS handshake takes longer than 1ms.

### 12. Interview questions
- **Q1:** What's the difference between connect timeout and read timeout?
- **Q2:** Why is connection pooling crucial on mobile?
- **Q3:** How does waking up the radio affect battery life, and how do you mitigate it?

### 13. Checkpoint
Do you understand why an HTTP request on Android is physically and temporally different from a REST Assured test running on a CI server?

---

## Concept 2: OkHttp: The Underlying Engine

### 1. What is it
The de-facto standard HTTP client for Android. It handles connection pooling, TLS, HTTP/2, interceptors, and caching.

### 2. Why does it exist
Managing low-level sockets, HTTP/2 multiplexing, and GZIP compression manually is complex and error-prone. OkHttp provides a robust, optimized engine that handles the gritty details.

### 3. Mental model
**The Engine Room:** You don't interact with the pistons directly to drive; you use the steering wheel (Retrofit). OkHttp is the engine room handling the combustion (sockets, connections, headers).

### 4. How it works
- **Interceptors:** Powerful middleware.
  - *Application Interceptors:* Fire once. Good for injecting Auth headers.
  - *Network Interceptors:* Fire for every network attempt (including redirects/retries). Good for logging actual wire bytes.
- **Authenticator:** A dedicated callback for responding to 401 Unauthorized by refreshing the token transparently.

### 5. Code
```kotlin
class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenManager.getAccessToken() // Assume synchronous or blocking for OkHttp
        
        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
            
        return chain.proceed(newRequest)
    }
}

val client = OkHttpClient.Builder()
    .addInterceptor(AuthInterceptor(tokenManager))
    .addNetworkInterceptor(HttpLoggingInterceptor().apply { level = Level.BODY })
    .build()
```

### 6. Production usage
Injecting `Authorization` headers universally. Centralized logging. Certificate Pinning (`CertificatePinner`) to prevent Man-in-the-Middle (MITM) attacks on compromised Wi-Fi networks.

### 7. Common mistakes
❌ **Wrong:** Putting Auth logic in every single repository call manually.
✅ **Right:** Using an `Interceptor` so Auth injection is centralized and impossible to forget.

### 8. Debugging
Enable `HttpLoggingInterceptor`. Be careful not to log bodies in production (PII/security leak).

### 9. Testing
Unit test the interceptor by passing a mocked `Interceptor.Chain` and asserting the output request has the correct headers.

### 10. Exercise
Create an interceptor that adds an `X-App-Version` header to every request automatically.

### 11. Deliberate failure
In your interceptor, forget to call `chain.proceed(newRequest)`. The app will hang forever because the HTTP request never actually fires.

### 12. Interview questions
- **Q1:** What is the difference between an Application Interceptor and a Network Interceptor?
- **Q2:** How would you handle an expired JWT token using OkHttp? (Answer: `Authenticator`).
- **Q3:** Why shouldn't you log network bodies in release builds?

### 13. Checkpoint
Do you understand how Interceptors act as middleware to globally mutate outgoing requests and incoming responses?

---

## Concept 3: Retrofit 2/3: Type-Safe API Interfaces

### 1. What is it
A type-safe HTTP client built on top of OkHttp that turns your REST API into a Kotlin interface.

### 2. Why does it exist
Manually building URLs, serializing objects to JSON strings, and parsing JSON back into objects is boilerplate-heavy. Retrofit uses annotations to generate this implementation at runtime.

### 3. Mental model
**The Translator & Dispatcher:** You speak Kotlin interfaces, the server speaks HTTP/JSON. Retrofit translates your method calls into OkHttp requests and translates the OkHttp responses back into Kotlin objects.

### 4. How it works
You define an interface with annotations (`@GET`, `@Path`). Retrofit generates a proxy implementation. When you call the interface method, Retrofit builds the OkHttp request, executes it, passes the JSON through a `Converter` (e.g., Moshi/Kotlinx Serialization), and returns the object. It natively supports Kotlin Coroutines via the `suspend` keyword.

### 5. Code
```kotlin
interface ExpenseApi {
    @GET("expenses")
    suspend fun getExpenses(@Query("month") month: String): List<ExpenseDto>

    @POST("expenses")
    suspend fun createExpense(@Body expense: CreateExpenseRequest): ExpenseDto
    
    // Returning Response<T> gives access to headers and status codes
    @DELETE("expenses/{id}")
    suspend fun deleteExpense(@Path("id") expenseId: String): Response<Unit>
}

// Construction
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.expensetracker.com/v1/")
    .client(okHttpClient) // Pass the OkHttp engine
    .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
    .build()

val api = retrofit.create(ExpenseApi::class.java)
```

### 6. Production usage
Used in >95% of Android apps for REST. The API interface is usually injected via Dagger/Hilt into a Repository, which calls it within a `try/catch` block or a custom Result wrapper.

### 7. Common mistakes
❌ **Wrong:** Defining the base URL as `"https://api.com/v1"` (missing trailing slash) and the endpoint as `"/users"` (leading slash).
✅ **Right:** Base URL `"https://api.com/v1/"` and endpoint `"users"`. Retrofit strictly follows standard URI resolution.

### 8. Debugging
If Retrofit throws `IllegalArgumentException` at startup, your annotations are wrong. If it throws during a call, usually the JSON structure doesn't match your DTO.

### 9. Testing
Don't mock Retrofit interfaces! Instead, use `MockWebServer` to enqueue real HTTP responses and test that Retrofit parses them correctly.

### 10. Exercise
Define a Retrofit interface for an endpoint that requires a dynamic header (e.g., `@Header("Idempotency-Key")`) and a dynamic path parameter.

### 11. Deliberate failure
Remove the `suspend` keyword from the interface, but try to call it from a Coroutine. Retrofit will try to execute it synchronously on the main thread, causing a `NetworkOnMainThreadException`.

### 12. Interview questions
- **Q1:** How does Retrofit handle Coroutines under the hood?
- **Q2:** When would you return `Response<T>` instead of just `T`?
- **Q3:** Explain the role of a `ConverterFactory`.

### 13. Checkpoint
Do you see how Retrofit makes REST calls look like simple local function calls while hiding the OkHttp complexity?

---

## Concept 4: Ktor Client: The Kotlin-First & KMP Alternative

### 1. What is it
A modern, asynchronous HTTP client built purely in Kotlin, designed heavily around Coroutines and Kotlin Multiplatform (KMP).

### 2. Why does it exist
Retrofit is Java-first (though it supports Kotlin well). If you are building a shared codebase for Android and iOS using Kotlin Multiplatform, Retrofit won't work on iOS. Ktor works everywhere.

### 3. Mental model
**The Swiss Army Knife:** While Retrofit is a specialized power tool for Android/Java REST, Ktor is a modular multi-tool built from the ground up for Kotlin that adapts to any platform via different "Engines" (OkHttp for Android, Darwin for iOS).

### 4. How it works
Instead of interfaces and annotations, Ktor uses a Kotlin DSL (Domain Specific Language). You configure the client with Plugins (ContentNegotiation, Logging) and make calls using DSL blocks.

### 5. Code
```kotlin
val client = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(Logging) {
        level = LogLevel.INFO
    }
}

// Usage in Repository
suspend fun fetchExpenses(): List<ExpenseDto> {
    return client.get("https://api.expensetracker.com/v1/expenses") {
        url {
            parameters.append("month", "2023-10")
        }
    }.body()
}
```

### 6. Production usage
Increasingly popular in modern Kotlin-only codebases, and mandatory if you are sharing your networking layer with iOS via KMP.

### 7. Common mistakes
❌ **Wrong:** Forgetting to call `.body()` and trying to return the raw `HttpResponse`.
✅ **Right:** Using `.body<T>()` to deserialize the response using the `ContentNegotiation` plugin.

### 8. Debugging
Ktor's Logging plugin is highly configurable. If Ktor fails to parse JSON, ensure the `ContentNegotiation` plugin is installed and properly configured.

### 9. Testing
Ktor provides a `MockEngine` specifically designed for testing, allowing you to intercept requests and return mocked responses purely in Kotlin without spinning up a local server like MockWebServer.

### 10. Exercise
Rewrite the Retrofit `getExpenses` and `createExpense` methods using Ktor client DSL.

### 11. Deliberate failure
Forget to `install(ContentNegotiation)`. Call an endpoint and try to extract `.body<ExpenseDto>()`. Watch it crash because it doesn't know how to deserialize JSON.

### 12. Interview questions
- **Q1:** Why might a team choose Ktor over Retrofit today?
- **Q2:** How does Ktor handle platform differences between iOS and Android?
- **Q3:** Compare Retrofit's Interceptors with Ktor's Plugins.

### 13. Checkpoint
Do you understand that Ktor is DSL-based and multiplatform, whereas Retrofit is annotation-based and JVM-bound?

---

## Concept 5: Serialization & Defensive Nullability

### 1. What is it
Converting JSON strings from the network into Kotlin data classes (deserialization), and vice versa. Specifically focusing on `kotlinx.serialization` and handling missing data safely.

### 2. Why does it exist
Backend APIs change. They drop fields, send `null` instead of strings, or add new keys. If your client crashes because a field it expected is missing, you have poor defensive serialization. Mobile apps cannot be updated instantly to fix a crash; they must be resilient to API variations.

### 3. Mental model
**The Suspicious Bouncer:** Your serialization layer is a bouncer at the club (your app). It must strictly check IDs (JSON types) but be forgiving if someone forgot their tie (optional fields), rather than shutting down the whole club (crashing).

### 4. How it works
In Kotlin, if a field is not nullable (`val name: String`), the JSON MUST contain a non-null string for that key. If the server omits the key, it crashes. 
To be defensive:
1. Make optional fields nullable.
2. Provide default values.
3. Configure the parser to ignore unknown keys.

### 5. Code
```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ExpenseDto(
    @SerialName("id") val id: String, // Mandatory. If missing, crash (this is correct, ID is required)
    @SerialName("amount") val amount: Double,
    @SerialName("merchant") val merchant: String,
    
    // Defensive Nullability: Server might send null, or omit the key entirely
    @SerialName("notes") val notes: String? = null,
    
    // Default value fallback if omitted
    @SerialName("is_recurring") val isRecurring: Boolean = false
)

// Configuration
val jsonConfig = Json { 
    ignoreUnknownKeys = true  // Don't crash if backend adds "new_feature_flag"
    coerceInputValues = true  // Coerce nulls into default values if necessary
}
```

### 6. Production usage
`kotlinx.serialization` is the standard for Kotlin. It generates parsing code at compile-time (no reflection, very fast). Gson (legacy Java, uses reflection, ignores Kotlin nullability rules entirely—AVOID) and Moshi (good, but requires codegen via KSP) are alternatives.

### 7. Common mistakes
❌ **Wrong:** Using `val notes: String` when the backend swagger says it *might* be null, resulting in `SerializationException` crashes in production.
❌ **Wrong:** Using Gson, which might inject `null` into a non-null Kotlin `String` via reflection, causing `NullPointerException` later in the app.
✅ **Right:** `val notes: String? = null` with `kotlinx.serialization`.

### 8. Debugging
If parsing fails, `kotlinx.serialization` provides excellent error messages detailing exactly which key was missing or had the wrong type.

### 9. Testing
Write unit tests that pass malformed or incomplete JSON strings into `Json.decodeFromString()` and assert that it parses gracefully or fails predictably.

### 10. Exercise
Write an `ExpenseDto` data class. Create a JSON string with an extra key not in the class, and a missing key that has a default value. Parse it and print the result.

### 11. Deliberate failure
Remove `ignoreUnknownKeys = true`. Add a random key `"foo": "bar"` to your JSON payload. Run it. Watch it throw `UnknownFieldException`.

### 12. Interview questions
- **Q1:** Why is Gson considered dangerous in a pure Kotlin project?
- **Q2:** What happens in `kotlinx.serialization` if a JSON key is missing but the property has no default value?
- **Q3:** Why should you use `@SerialName` instead of relying on variable names?

### 13. Checkpoint
Do you understand why defensive nullability and defaults are critical for mobile resilience against evolving backend APIs?

---

## Concept 6: DTO ≠ Domain Model: Clean Architecture at the Network Boundary

### 1. What is it
Separating the data model used for network parsing (Data Transfer Object / DTO) from the data model used throughout your application (Domain Model).

### 2. Why does it exist
Backend APIs are often messy, optimized for databases, or contain legacy fields. If you use the network model directly in your UI, backend changes will break your UI. By mapping DTOs to Domain Models at the edge of the app (the Repository), you isolate the rest of the app from network weirdness.

### 3. Mental model
**The Currency Exchange:** You arrive at the airport (network layer) with Euros (DTO). You immediately go to the exchange booth (Mapper) and get Dollars (Domain Model). You only spend Dollars inside the city (your app).

### 4. How it works
1. **Network Layer:** Parses JSON into `ExpenseDto`.
2. **Repository Layer:** Takes `ExpenseDto` and maps it into `Expense` (Domain Model).
3. **Domain/UI Layer:** Only knows about `Expense`. It doesn't know about annotations, nullable fallbacks, or raw strings.

### 5. Code
```kotlin
// 1. Network Boundary (Messy, Nullable, Annotated)
@Serializable
data class ExpenseDto(
    @SerialName("id") val id: String?,
    @SerialName("amt") val amount: Double?,
    @SerialName("created_at") val createdAt: String?
)

// 2. Domain Model (Clean, Strict, App-specific)
data class Expense(
    val id: String,
    val amount: BigDecimal, // Better precision for money
    val date: LocalDate
)

// 3. The Mapper (Extension function)
fun ExpenseDto.toDomain(): Expense {
    // We enforce business rules here. If ID is missing, we drop it or throw a specific domain exception.
    return Expense(
        id = this.id ?: throw InvalidDataException("ID missing"),
        amount = BigDecimal.valueOf(this.amount ?: 0.0),
        date = LocalDate.parse(this.createdAt ?: LocalDate.now().toString())
    )
}

// 4. Repository
class ExpenseRepository(private val api: ExpenseApi) {
    suspend fun getExpenses(): List<Expense> {
        val dtos = api.getExpenses()
        return dtos.map { it.toDomain() }
    }
}
```

### 6. Production usage
This is the core tenet of Clean Architecture. It prevents `@SerialName` annotations from leaking into Jetpack Compose files and allows you to write UI code before the backend API is even finalized.

### 7. Common mistakes
❌ **Wrong:** Passing `ExpenseDto` all the way to the ViewModel or View.
✅ **Right:** Mapping to `Expense` immediately inside the Repository.

### 8. Debugging
If a value looks wrong in the UI, check the Mapper. Usually, it's a default value fallback being triggered because the backend sent null.

### 9. Testing
Unit test the mapping functions heavily. Pass in DTOs with edge cases (nulls, empty strings, weird date formats) and ensure the Domain model comes out perfectly formed or throws an expected domain exception.

### 10. Exercise
Create a mapping function that takes a `UserDto` with `first_name` and `last_name` and maps it to a `User` domain model that only has a `fullName` property.

### 11. Deliberate failure
Change the `ExpenseDto` id to be non-nullable, but have the mock server return null. Watch the parser crash before the mapper even runs. This highlights the boundary perfectly.

### 12. Interview questions
- **Q1:** Why is it considered an anti-pattern to use network models directly in the UI?
- **Q2:** Where exactly in the architecture should DTO to Domain mapping occur?
- **Q3:** If the backend returns an unexpected null for a critical field (like a price), should the mapper provide a default value (like 0.0) or throw an exception? (Hint: It depends on business logic, but silent failures like $0.0 are often worse than dropping the item).

### 13. Checkpoint
Do you understand the firewall that mapping creates between the messy outside world and your clean internal architecture?


---

## 7. Uniform Error Handling & Result Architecture

### 1. What is it
A standardized, app-wide wrapper class (usually a sealed class or interface called `ApiResult` or `Result`) that represents the outcome of any network operation, completely eliminating the need for `try-catch` blocks in business logic.

### 2. Why does it exist
Network calls fail for many reasons: the server returns a 4xx/5xx error, the user goes into a tunnel (no internet), JSON changes unexpectedly, or the user navigates away (cancellation). If every repository method throws exceptions, UI code becomes an unreadable mess of `try-catch` blocks. A unified Result wrapper forces the UI to handle both success and failure states safely and explicitly.

### 3. Mental model
Think of `ApiResult` as a padded shipping box.
Inside the box is either the item you ordered (Data) or an apology note explaining why the item isn't there (Error). The delivery driver (Repository) hands you the box. You open it and deal with what's inside, rather than the delivery driver throwing the item at you if it's there, or punching you in the face (Exception) if it's not.

### 4. How it works
1. You define a sealed class hierarchy representing states: Success, HttpError, NetworkError, SerializationError.
2. You create a single helper function (`safeApiCall`) that wraps *every* Retrofit call.
3. This helper catches exceptions, checks HTTP response codes, and maps them into your sealed class.
4. **CRITICAL:** `CancellationException` is caught but immediately re-thrown. Coroutines use this exception to cancel work. If you catch it and return it as an `ApiResult.Error`, you break structured concurrency, and network calls will continue running in the background even after the user leaves the screen!

### 5. Code
```kotlin
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

// 1. The Result Wrapper
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    
    sealed interface Error : ApiResult<Nothing> {
        data class HttpError(val code: Int, val errorBody: String?) : Error
        data class NetworkError(val exception: IOException) : Error
        data class SerializationError(val exception: Exception) : Error
        data class UnknownError(val exception: Exception) : Error
    }
}

// 2. The wrapper helper function
suspend fun <T> safeApiCall(apiCall: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(apiCall())
    } catch (e: CancellationException) {
        // MUST RE-THROW! This allows coroutines to cancel normally.
        throw e
    } catch (e: HttpException) {
        // Non-2xx HTTP responses (Retrofit throws this if you return T instead of Response<T>)
        val errorBody = e.response()?.errorBody()?.string()
        ApiResult.Error.HttpError(e.code(), errorBody)
    } catch (e: IOException) {
        // No internet, timeout, etc.
        ApiResult.Error.NetworkError(e)
    } catch (e: kotlinx.serialization.SerializationException) {
        // Bad JSON
        ApiResult.Error.SerializationError(e)
    } catch (e: Exception) {
        ApiResult.Error.UnknownError(e)
    }
}

// 3. Usage in a Repository
class ExpenseRepository(private val api: ExpenseApi) {
    suspend fun getExpenses(): ApiResult<List<ExpenseDto>> {
        return safeApiCall {
            api.getExpenses() // suspend function returning List<ExpenseDto>
        }
    }
}

// 4. Usage in a ViewModel
class ExpenseViewModel(private val repo: ExpenseRepository) : ViewModel() {
    fun load() {
        viewModelScope.launch {
            when (val result = repo.getExpenses()) {
                is ApiResult.Success -> showData(result.data)
                is ApiResult.Error.HttpError -> showError("Server error: ${result.code}")
                is ApiResult.Error.NetworkError -> showError("Check your connection")
                is ApiResult.Error.SerializationError -> showError("App update required")
                is ApiResult.Error.UnknownError -> showError("Something went wrong")
            }
        }
    }
}
```

### 6. Production usage
Every modern Android app uses a variation of this. It centralizes parsing error bodies (e.g., extracting `"message": "Invalid password"` from a 400 Bad Request JSON) so ViewModels just get a clean string to display.

### 7. Common mistakes
**Catching `Exception` broadly and absorbing `CancellationException`.**
```kotlin
// WRONG
suspend fun <T> badSafeCall(apiCall: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(apiCall())
    } catch (e: Exception) { 
        // THIS CATCHES CANCELLATIONEXCEPTION!
        // The coroutine thinks it caught the error and keeps running!
        ApiResult.Error.UnknownError(e) 
    }
}
```

### 8. Debugging
If network calls keep running after you close a screen, or you see `ApiResult.Error.UnknownError` firing right when you navigate back, you are swallowing `CancellationException`.

### 9. Testing
Testing `safeApiCall` requires passing lambda blocks that deliberately throw specific exceptions and asserting the correct `ApiResult` type is returned.

```kotlin
@Test
fun `safeApiCall returns NetworkError on IOException`() = runTest {
    val result = safeApiCall<String> { throw IOException("No network") }
    assertTrue(result is ApiResult.Error.NetworkError)
}

@Test(expected = CancellationException::class)
fun `safeApiCall rethrows CancellationException`() = runTest {
    safeApiCall<String> { throw CancellationException("Cancelled") }
}
```

### 10. Exercise
Add a specific error case `ApiResult.Error.Unauthorized` to your sealed class. Modify `safeApiCall` so that if `HttpException` has a code of `401`, it returns this specific error instead of a generic `HttpError`.

### 11. Deliberate failure
Write a ViewModel that launches a coroutine, calls `badSafeCall` (which swallows Cancellation), and then immediately `cancel()`s the job. Add logging. Observe that the "Error" state is emitted even though the job was cancelled.

### 12. Interview questions
*   **Q: Why do we use a sealed class for Network Results instead of an open class or interface?**
    *   *A: Sealed classes allow the compiler to enforce exhaustive `when` statements. If we add a new error type later, the compiler will break everywhere we handle results until we explicitly handle the new error, preventing unhandled edge cases.*
*   **Q: Why must `CancellationException` be rethrown in a generic try-catch block inside a suspend function?**
    *   *A: Coroutines use `CancellationException` to cooperatively cancel execution. If you catch and return it as a normal error, the coroutine engine doesn't know it should stop processing the parent job, leading to wasted resources and unexpected UI states on detached screens.*

---

## 8. Resilience & Defensive Parsing Strategies

### 1. What is it
Techniques to ensure your app doesn't crash or fail completely when the backend sends unexpected data or the network is flaky. It involves partial successes, fallback values, and smart retries.

### 2. Why does it exist
Backends change. A new iOS developer might add a new Enum value to a shared API. If your Android app tries to parse "NEW_TYPE" and doesn't know what it is, standard JSON parsers will throw a `SerializationException` and crash the entire request. If that request was returning a list of 50 items, all 50 items fail to load because *one* item had an unknown enum value.

### 3. Mental model
Imagine a factory sorting apples.
Standard parsing: "Ah, this one apple is slightly purple instead of red. SHUT DOWN THE ENTIRE CONVEYOR BELT. BURN THE FACTORY."
Defensive parsing: "This apple is weird. Throw it in the trash bin, log a warning, and keep processing the other 49 perfectly good apples."

### 4. How it works
1. **`mapNotNull` for Lists:** When mapping DTOs (Data Transfer Objects) to Domain models, wrap the individual item mapping in a try-catch. Return null if parsing fails. Then use `mapNotNull` to filter out the nulls.
2. **Enum Fallbacks:** Configure your JSON parser to map unknown enum values to a default like `UNKNOWN` instead of throwing.
3. **Retries with Jitter:** If a request fails due to a network timeout, try again. But wait longer each time (exponential backoff) and add randomness (jitter) so millions of clients don't retry at the exact same millisecond and DDoS your server.

### 5. Code
```kotlin
// --- 1. Defensive List Parsing ---
// The DTO from the server
@Serializable
data class ExpenseDto(
    val id: String,
    val amount: Double,
    val type: String // E.g., "MEAL", "TRAVEL", or something new we don't know
)

// The Domain model
data class Expense(val id: String, val amount: Double, val type: ExpenseType)
enum class ExpenseType { MEAL, TRAVEL, UNKNOWN }

// The Mapper
fun ExpenseDto.toDomainOrNull(): Expense? {
    return try {
        Expense(
            id = id,
            amount = amount,
            // If type string isn't in Enum, throw exception
            type = ExpenseType.valueOf(type.uppercase()) 
        )
    } catch (e: Exception) {
        // LOG THIS TO CRASHLYTICS! "Failed to parse expense: ${e.message}"
        null // Return null instead of crashing
    }
}

// In Repository:
suspend fun getExpenses(): List<Expense> {
    val dtoList = api.getExpenses()
    // mapNotNull drops any 'null' values returned by the lambda
    return dtoList.mapNotNull { it.toDomainOrNull() } 
}

// --- 2. Retry with Backoff ---
suspend fun <T> retryWithBackoff(
    times: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: IOException) {
            // Only retry on network errors, not HTTP 4xx errors
            delay(currentDelay)
            // Exponentially increase delay, cap at maxDelay
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    return block() // Last attempt, will throw if it fails
}
```

### 6. Production usage
Defensive list parsing is mandatory in large companies. You never want a single corrupted database record on the backend to prevent the user's entire app from loading.

### 7. Common mistakes
**Using standard `.map` and throwing.**
```kotlin
// WRONG: If ONE dto fails to map, the whole mapping throws, and 
// the user sees an empty screen / error state.
val domainList = dtoList.map { it.toDomain() } 
```

### 8. Debugging
If users report missing items in lists, check your non-fatal crash logs (Crashlytics). Your defensive mapper should be logging the items it skips.

### 9. Testing
Create a list of 3 DTOs where the middle one has invalid data. Pass it through your mapping layer and assert that the resulting list has size 2, and contains only the valid items.

### 10. Exercise
Write an extension function for Kotlinx Serialization or Moshi that registers a custom adapter for an Enum, forcing any unknown string value to resolve to a specific `UNKNOWN` enum value rather than crashing.

### 11. Deliberate failure
Create an API response mocking 10 items. Intentionally make item 5 invalid (e.g., negative amount where positive is required). Use standard `.map`. Observe the crash. Change to `.mapNotNull` with a try-catch. Observe 9 items loading successfully.

### 12. Interview questions
*   **Q: A backend deploy introduces a new status enum value. Older versions of your Android app suddenly crash on startup. How do you prevent this architecturally?**
    *   *A: 1) Configure the JSON parser to provide a fallback/default value for unknown enums. 2) Implement defensive mapping using `mapNotNull` wrapped in a try/catch when converting lists of DTOs to Domain models, so unparseable items are silently dropped (and logged) rather than crashing the whole batch.*
*   **Q: Why add "jitter" (randomness) to retry delays?**
    *   *A: If a server goes down temporarily, thousands of clients will fail simultaneously. If they all retry in exactly 2000ms, they will hit the server at the exact same moment, potentially bringing it back down (Thundering Herd problem). Jitter spreads the retries out over a time window.*

---

## 9. Authentication, 401 Handling & Token Refresh Race Conditions

### 1. What is it
The mechanism to securely attach session tokens to API requests, detect when a token is expired (HTTP 401 Unauthorized), request a new token, and automatically retry the original failed requests seamlessly without logging the user out.

### 2. Why does it exist
Access tokens are short-lived (e.g., 15 minutes) for security. Refresh tokens are long-lived (e.g., 30 days). We don't want the user to log in every 15 minutes. We need a system that detects a 401, uses the refresh token to get a new access token, saves it, and replays the original request.

### 3. Mental model
Imagine an Amusement Park.
Your Access Token is a VIP wristband that expires every hour. Your Refresh Token is your fingerprint.
You try to get on a ride (API call). The operator says "Wristband expired" (401).
Instead of kicking you out of the park, an escort takes you to the kiosk, scans your fingerprint, gives you a new wristband, and walks you right back to the front of the line for the ride. You never noticed you were "unauthorized".

### 4. How it works
OkHttp provides two key components:
1.  **`Interceptor`:** Adds the `Authorization: Bearer <token>` header to every outgoing request.
2.  **`Authenticator`:** Triggered *only* when the server returns a 401. It runs synchronously on a background thread. You make a synchronous network call to your `/refresh` endpoint here. If successful, you return a new `Request` with the new token, and OkHttp automatically executes it.
**The Race Condition:** If a screen launches 5 parallel API calls and the token is expired, all 5 get a 401 simultaneously. If you aren't careful, you will fire 5 simultaneous `/refresh` requests. You must use `synchronized` or a `Mutex` to ensure only the *first* thread refreshes the token, while the others wait and use the newly refreshed token.

### 5. Code
```kotlin
import okhttp3.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TokenAuthenticator(
    private val tokenStorage: TokenStorage,
    private val refreshApi: RefreshApi 
) : Authenticator {

    // Mutex to prevent multiple parallel refresh calls
    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // 1. Check if the request already had a token. If it didn't, we can't refresh.
        val token = tokenStorage.getAccessToken()
        if (response.request.header("Authorization") == null || token == null) {
            return null // Give up, triggers 401 to the app
        }

        // 2. We use runBlocking because Authenticator is synchronous OkHttp interface, 
        // but our Mutex and network calls are suspend functions.
        // (In a real app, ensure OkHttp isn't running on the main thread!)
        return runBlocking {
            mutex.withLock {
                // 3. CHECK AGAIN inside the lock! 
                // Thread 2 might have waited for Thread 1 to finish.
                // If Thread 1 refreshed the token, Thread 2 should just use the NEW token.
                val currentToken = tokenStorage.getAccessToken()
                if (currentToken != null && currentToken != token) {
                    // Token changed while we were waiting! Replay with new token.
                    return@runBlocking response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                }

                // 4. Actually perform the refresh call synchronously
                val refreshToken = tokenStorage.getRefreshToken() ?: return@runBlocking null
                
                try {
                    // Call API to get new tokens
                    val newTokens = refreshApi.refreshTokenSync(refreshToken).execute()
                    
                    if (newTokens.isSuccessful && newTokens.body() != null) {
                        val newToken = newTokens.body()!!.accessToken
                        // Save to EncryptedSharedPreferences
                        tokenStorage.saveAccessToken(newToken)
                        
                        // 5. Rebuild and return the original request with the new token
                        return@runBlocking response.request.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                    } else {
                        // Refresh token expired or invalid!
                        tokenStorage.clearAll()
                        // Trigger App-level logout event via EventBus/SharedFlow
                        return@runBlocking null 
                    }
                } catch (e: Exception) {
                    return@runBlocking null
                }
            }
        }
    }
}
```

### 6. Production usage
This is the standard, bulletproof way to handle session management in Android. Every banking, social media, and enterprise app uses this OkHttp `Authenticator` pattern.

### 7. Common mistakes
**Forgetting the Double-Check pattern.**
If you lock the mutex, perform the refresh, unlock, and the next waiting thread immediately performs *another* refresh because it didn't check if the token had *already* changed while it was waiting. This invalidates tokens and causes endless 401 loops.

### 8. Debugging
If you see users constantly getting logged out, or 4-5 `/refresh` calls firing in Charles Proxy sequentially, your thread synchronization in the Authenticator is broken.

### 9. Testing
MockWebServer is perfect here.
1. Enqueue a 401 response.
2. Enqueue a 200 Token Refresh response.
3. Enqueue a 200 Success response.
4. Make your API call. Assert that the final result is Success, and that the server received exactly 3 requests in that order.

### 10. Exercise
Create a Fake `TokenStorage` backed by a simple variable. Write a test that launches 10 coroutines simultaneously, all hitting a MockWebServer that returns 401. Verify your `Authenticator` only executes the Refresh logic *once*.

### 11. Deliberate failure
Remove the `if (currentToken != token)` double-check inside the lock. Launch 3 parallel requests. Watch your logs show the `/refresh` endpoint being hit 3 times in a row, often resulting in the backend revoking the session due to replay attacks.

### 12. Interview questions
*   **Q: Explain the double-checked locking pattern inside an OkHttp Authenticator.**
    *   *A: When multiple API calls fail with 401 simultaneously, they all queue up at the Authenticator. The Mutex allows only the first one to proceed. Once it finishes refreshing the token, it unlocks. The second call enters the lock. If it doesn't check if the token was ALREADY updated by the first call, it will execute a redundant refresh request. Checking if the local token differs from the one that triggered the 401 prevents this race condition.*

---

## 10. Realtime Communication & Sockets

### 1. What is it
Technologies to keep a persistent connection open between the Android app and the server, allowing the server to push data to the app instantly without the app having to repeatedly ask ("polling").

### 2. Why does it exist
For chat apps, live stock tickers, or multi-player games, standard HTTP REST (client requests, server responds, connection closes) is too slow and wastes too much battery/bandwidth if you poll every 1 second.

### 3. Mental model
**Polling (HTTP REST):** Kids in the backseat asking "Are we there yet?" every 5 minutes.
**WebSockets:** The driver saying "Go to sleep. I will wake you up the exact millisecond we arrive."

### 4. How it works
*   **WebSockets:** A persistent, bi-directional connection. Both client and server can send messages at any time. OkHttp supports this natively via `OkHttpClient.newWebSocket()`.
*   **Server-Sent Events (SSE):** A persistent, uni-directional connection. Client opens it, server streams data down. Client cannot send data up the same pipe.
*   **callbackFlow:** Sockets use callback listeners (`onMessage`, `onClosed`). Kotlin uses Flows. We bridge them using `callbackFlow`, turning a messy callback listener into a clean, cold Flow that automatically closes the socket when the Flow collection stops.

### 5. Code
```kotlin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okio.ByteString

class ExpenseSocketManager(private val client: OkHttpClient) {

    // Returns a Flow that emits new expenses as they arrive via WebSocket
    fun listenForLiveExpenses(): Flow<String> = callbackFlow {
        
        val request = Request.Builder()
            .url("wss://api.example.com/expenses/live")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Connection opened
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Send the received text into the Flow
                trySend(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Close the flow with an error
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        }

        val webSocket = client.newWebSocket(request, listener)

        // awaitClose is MANDATORY for callbackFlow!
        // It suspends until the flow consumer cancels the coroutine.
        // When cancelled, it executes the block inside, allowing us to clean up the socket.
        awaitClose {
            webSocket.close(1000, "Flow cancelled by client")
        }
    }
}

// Usage in ViewModel
class LiveExpenseViewModel(private val socketManager: ExpenseSocketManager) : ViewModel() {
    init {
        viewModelScope.launch {
            socketManager.listenForLiveExpenses()
                .collect { message ->
                    println("New live expense: $message")
                }
        }
    }
}
```

### 6. Production usage
Used in trading apps (Robinhood), chat apps (WhatsApp/Slack), and collaborative tools.

### 7. Common mistakes
**Forgetting `awaitClose` in `callbackFlow`.**
If you forget `awaitClose`, the `callbackFlow` builder finishes immediately, the coroutine ends, and your socket is left dangling in memory, leaking connections and battery.

### 8. Debugging
WebSockets do not show up in the standard Android Studio Network Inspector easily. You usually need an external proxy tool like Charles or Proxyman to inspect WebSocket frames.

### 9. Testing
Testing WebSockets usually involves mocking the `OkHttpClient` to return a fake `WebSocket` implementation, then triggering the listener callbacks manually and asserting the Flow emits the correct items.

### 10. Exercise
Write a `callbackFlow` that wraps Android's `ConnectivityManager.NetworkCallback` to emit a `Boolean` (true for connected, false for disconnected) whenever the device internet state changes.

### 11. Deliberate failure
Write the WebSocket `callbackFlow` without `awaitClose`. Collect it in a Fragment. Navigate away from the Fragment. Look at Logcat and observe that `onMessage` is still receiving data because the socket was never closed.

### 12. Interview questions
*   **Q: What is the primary advantage of WebSockets over HTTP Polling?**
    *   *A: Lower latency and drastically reduced overhead. Polling requires opening a new TCP connection, performing SSL handshakes, and sending HTTP headers over and over again. WebSockets do the handshake once and keep the pipe open, exchanging minimal framing data.*
*   **Q: How do you prevent a `callbackFlow` from terminating immediately after setup?**
    *   *A: You must invoke `awaitClose { ... }` at the end of the `callbackFlow` block. This suspends the block indefinitely until the downstream consumer cancels the flow, at which point the lambda inside `awaitClose` runs to perform cleanup (like unregistering listeners or closing sockets).*

---

## 11. Network Debugging & Traffic Inspection

### 1. What is it
Tools and configurations to see exactly what HTTP requests are leaving your app and what JSON is coming back. Essential for figuring out if a bug is the app's fault or the backend's fault.

### 2. Why does it exist
Network requests are invisible by default. When the app says "Error", you need to know: Did the app send the wrong parameters? Did the server return 500? Did the server return valid JSON but our app failed to parse it?

### 3. Mental model
A glass pipe. Normally, data flows through an opaque pipe. You can only see the UI at the end. Network Interceptors and Proxies replace the opaque pipe with a glass one, letting you watch the data flow.

### 4. How it works
1.  **HttpLoggingInterceptor:** An OkHttp interceptor that prints requests/responses to Android Logcat.
2.  **Network Inspector:** Built into Android Studio. Shows a timeline of requests, headers, and bodies.
3.  **Proxy (Charles/Proxyman):** A separate app on your Mac/PC. You tell your Android device to route all wifi traffic through your Mac's IP. 
4.  **Network Security Config:** Android 7+ blocks man-in-the-middle proxies by default. You must configure `network_security_config.xml` to trust user-installed certificates *only in debug builds* so Proxyman can decrypt your app's HTTPS traffic.

### 5. Code
**1. Logging Interceptor Setup:**
```kotlin
val loggingInterceptor = HttpLoggingInterceptor().apply {
    // NONE: No logging
    // BASIC: Logs request method, url, response code (e.g. GET /api/v1/users -> 200 OK)
    // HEADERS: Logs Basic + headers
    // BODY: Logs everything including the full JSON body. WARNING: Leaks PII in logs!
    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
}

val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(loggingInterceptor) // Add as application interceptor
    .build()
```

**2. network_security_config.xml (res/xml/):**
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- ONLY trust user certs (Proxies) in debug builds -->
    <debug-overrides>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

**3. AndroidManifest.xml:**
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

### 6. Production usage
You **must** turn off `BODY` level logging in production, otherwise passwords and credit card numbers will be printed to plain text system logs, violating compliance laws (GDPR/HIPAA).

### 7. Common mistakes
**Using Charles Proxy on Android 7+ without a Network Security Config.**
The proxy will capture the traffic, but it will show up as encrypted garbage, and the Android app will crash with an `SSLHandshakeException`.

### 8. Debugging
If `HttpLoggingInterceptor` shows the request going out, but Charles Proxy doesn't see it, ensure your device's WiFi is manually configured to use a proxy pointing to your Mac's local IP address, and that the proxy port matches (usually 8888 or 9090).

### 9. Testing
Verify that `BuildConfig.DEBUG` correctly restricts logging interceptors from running in release builds.

### 10. Exercise
Install Proxyman or Charles Proxy on your computer. Configure your Android device/emulator to proxy through it. Setup the `network_security_config.xml`. Intercept a request and use the "Map Local" or "Rewrite" feature to change the backend's JSON response before it hits your app. (This is how QA tests edge cases!).

### 11. Deliberate failure
Run Charles Proxy but remove the `network_security_config.xml`. Try to make an API call. Observe the `SSLHandshakeException` because Android detects Charles as a malicious Man-In-The-Middle attacker.

### 12. Interview questions
*   **Q: What is the difference between an OkHttp `addInterceptor` and `addNetworkInterceptor`?**
    *   *A: Application Interceptors (`addInterceptor`) run once per call. If you use caching, and the response is served from cache, it fires. Network Interceptors (`addNetworkInterceptor`) run right before data goes over the wire. If the response is served from cache, it does NOT fire. Network interceptors also see low-level transformations like GZIP compression.*
*   **Q: Why shouldn't you log HTTP request bodies in production builds?**
    *   *A: It leaks Personally Identifiable Information (PII), authentication tokens, and passwords into the system logcat buffer, which can be read by malicious apps (on older OS versions) or physical access.*

---

## Phase 7 Project — Expense Tracker v6 (Networking & API Integration)

**Goal:** Build a robust, resilient networking layer for the Expense Tracker.

**Requirements:**
1. **Retrofit & OkHttp Setup:**
   - Configure `OkHttpClient` with connection timeouts (15s), auth interceptor (attaching Bearer token), and logging interceptor (debug builds only).
   - Define `ExpenseApiDefinition` with endpoints: `GET /v1/expenses`, `POST /v1/expenses`, `GET /v1/expenses/{id}`, `DELETE /v1/expenses/{id}`.
2. **Defensive Remote Data Source:**
   - Implement `safeApiCall` helper ensuring `CancellationException` is never caught.
   - `ExpenseRemoteDataSourceImpl` wrapping calls into `ApiResult<T>`.
   - `ExpenseDto` with `@SerialName` / `@SerializedName` and default values for optional fields.
   - List mapper using `mapNotNull` with try/catch to drop individual malformed records without failing the batch.
3. **Token Refresh Authenticator:**
   - Implement an OkHttp `Authenticator` with a synchronized `Mutex` that refreshes the access token on 401 and retries the original request.
4. **Failure Path Testing:**
   - Write unit tests covering all 6 failure paths: 1) HTTP 400 Bad Request with parsed error body, 2) HTTP 500 Server Error, 3) Timeout / SocketTimeoutException, 4) Airplane mode / UnknownHostException, 5) Malformed JSON / SerializationException, 6) Cancelled coroutine mid-request.

---

## Phase 7 Checkpoint

Answer without looking:
1. Show your single call-wrapping helper function and explain how `CancellationException` passes through it without being converted into an `ApiResult.Error`.
2. What happens if 4 parallel network calls simultaneously receive HTTP 401, and how does your `Authenticator` prevent sending 4 simultaneous refresh token requests?
3. Why should you use `mapNotNull` instead of standard `map` when parsing a list of items from a remote API response?
4. What is the difference between an Application Interceptor and a Network Interceptor in OkHttp?
5. Why does committing API keys or secrets in source control constitute a severe security vulnerability, and how should mobile API credentials be handled?

---

## Complete REST Assured / QA API Automation → Android Networking Translation Table

| REST Assured / Backend Test Concept | Android Production Networking Equivalent | Notes |
|---|---|---|
| `given().header("Auth", ...)` | OkHttp `Interceptor` | Interceptor automatically adds headers to every request |
| `when().get("/api/endpoint")` | Retrofit interface (`@GET("api/endpoint")`) | Type-safe Kotlin suspend function |
| `then().statusCode(200)` | `Response.isSuccessful` / `Response.code()` | Checked in Remote Data Source |
| `.extract().as(MyDto.class)` | `Converter.Factory` (`kotlinx.serialization` / Moshi) | Automatic JSON deserialization |
| Mockoon / WireMock / Postman Mock Server | MockWebServer (`okhttp3.mockwebserver`) | In-memory local HTTP server for unit testing |
| 401 retry in API test suite | OkHttp `Authenticator` | Automatic token refresh and request replay |
| Charles / Fiddler proxy setup | `network_security_config.xml` + Proxyman | Trust user certificates in debug builds |
| Polling loop in TestNG | `flow { while(true) { emit(api.get()); delay(t) } }` | Reactive polling Flow in Kotlin |
