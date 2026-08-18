package com.enterprise.financetracker.core.network

import com.enterprise.financetracker.data.network.api.FinanceApiService
import com.enterprise.financetracker.data.network.model.RefreshTokenRequestDto
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Thread-safe In-Memory Token Manager.
 * In Stage 8, this will be backed by EncryptedDataStore.
 */
class TokenManager {
    var accessToken: String? = "sample_jwt_access_token"
    var refreshToken: String? = "sample_jwt_refresh_token"

    fun updateTokens(newAccessToken: String, newRefreshToken: String) {
        this.accessToken = newAccessToken
        this.refreshToken = newRefreshToken
    }

    fun clearTokens() {
        this.accessToken = null
        this.refreshToken = null
    }
}

/**
 * Appends Authorization: Bearer <token> to all outgoing requests.
 */
class AuthInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenManager.accessToken

        val newRequest = if (!token.isNullOrBlank() && !originalRequest.url.encodedPath.contains("auth/refresh")) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(newRequest)
    }
}

/**
 * Thread-safe 401 Authenticator with Coroutine Mutex locking.
 * Prevents the "Token Refresh Storm" where 5 parallel 401 responses trigger 5 simultaneous refresh calls.
 * (Phase 7 Concept 7 & ADR 019)
 */
class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val apiServiceProvider: () -> FinanceApiService
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite loops if refresh endpoint itself returns 401
        if (response.request.url.encodedPath.contains("auth/refresh")) {
            tokenManager.clearTokens()
            return null
        }

        // Check if another thread already refreshed the token
        val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        if (requestToken != tokenManager.accessToken && tokenManager.accessToken != null) {
            // Retry request with the newly refreshed token
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${tokenManager.accessToken}")
                .build()
        }

        return runBlocking {
            refreshMutex.withLock {
                val currentRefreshToken = tokenManager.refreshToken
                if (currentRefreshToken == null) {
                    tokenManager.clearTokens()
                    return@withLock null
                }

                try {
                    val apiService = apiServiceProvider()
                    val newTokens = apiService.refreshToken(RefreshTokenRequestDto(currentRefreshToken))
                    tokenManager.updateTokens(newTokens.accessToken, newTokens.refreshToken)

                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${newTokens.accessToken}")
                        .build()
                } catch (e: Exception) {
                    tokenManager.clearTokens()
                    null
                }
            }
        }
    }
}
