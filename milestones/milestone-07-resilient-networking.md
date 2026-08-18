# Milestone 7: Resilient Networking

## Title, Goal & Phase Alignment
**Goal:** Implement robust API communication using Retrofit and OkHttp, including token refresh logic and defensive parsing.
**Phase:** Expense Tracker v6 - Cloud Sync

## Architecture & Component Blueprint
- **Retrofit Client:** Defined with an interface for API operations.
- **OkHttp Interceptors:** Auth Interceptor for adding tokens, `Authenticator` for transparent token refresh using a `Mutex`.
- **Error Modeling:** A sealed class `Resource<T>` to model Success, Error, and Loading states.
- **Defensive Parsing:** Extension functions or custom adapters to safely map backend lists, discarding malformed objects.

## Step-by-Step Implementation Instructions
1. Setup Retrofit and OkHttp with required timeouts (Connect, Read, Write).
2. Create `Resource<T>` sealed interface.
3. Build the `AuthInterceptor` to inject Bearer tokens.
4. Implement `TokenAuthenticator` using Coroutine `Mutex` to prevent race conditions when multiple API calls fail auth simultaneously.
5. Apply defensive mapping: parse server JSON and `mapNotNull` over elements.
6. Setup `MockWebServer` in test source set.

## Code Snippets & Signatures
```kotlin
sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val exception: Throwable, val message: String) : Resource<Nothing>
    data object Loading : Resource<Nothing>
}

class TokenAuthenticator(
    private val tokenStorage: TokenStorage,
    private val api: AuthApi
) : Authenticator {
    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? = runBlocking {
        mutex.withLock {
            // refresh token logic
            val newAccessToken = api.refreshToken(tokenStorage.getRefreshToken())
            tokenStorage.save(newAccessToken)
            response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        }
    }
}
```

## Deliberate Bugs to Catch & Debug
- Missing `runBlocking` or improper threading inside the OkHttp `Authenticator`.
- `Mutex` deadlock by nesting locks or calling an authenticated route *inside* the authenticator.
- Missing `mapNotNull` handling where a single bad JSON object in a list fails the entire network call.

## Unit Testing Requirements (Given-When-Then)
- **Given** a 401 response, **When** `Authenticator` runs, **Then** it fetches a new token and retries the request exactly once.
- **Given** concurrent 401 responses, **When** `Authenticator` handles them, **Then** only one token refresh API call is made.
- **Given** MockWebServer enqueues a malformed JSON list, **When** parsed, **Then** the result list contains only the valid items.

## Acceptance Criteria Checklist
- [ ] OkHttp client configured with timeouts and logging.
- [ ] Token refresh logic works without infinite loops.
- [ ] Coroutine `Mutex` ensures synchronized token refresh.
- [ ] API responses are correctly mapped to `Resource<T>`.
- [ ] Tests verify behavior using `MockWebServer`.
