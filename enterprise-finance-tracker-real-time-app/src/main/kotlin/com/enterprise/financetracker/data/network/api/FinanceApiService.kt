package com.enterprise.financetracker.data.network.api

import com.enterprise.financetracker.data.network.model.AuthTokensDto
import com.enterprise.financetracker.data.network.model.NetworkTransactionDto
import com.enterprise.financetracker.data.network.model.RefreshTokenRequestDto
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit Interface for Enterprise Finance Cloud Services.
 * (Phase 7 Concept 3 & Stage 7)
 */
interface FinanceApiService {

    @GET("v1/transactions")
    suspend fun getTransactions(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): List<NetworkTransactionDto>

    @GET("v1/transactions/{id}")
    suspend fun getTransactionById(
        @Path("id") id: String
    ): NetworkTransactionDto

    @POST("v1/transactions")
    suspend fun createTransaction(
        @Body transaction: NetworkTransactionDto
    ): NetworkTransactionDto

    @DELETE("v1/transactions/{id}")
    suspend fun deleteTransaction(
        @Path("id") id: String
    ): Response<Unit>

    @POST("v1/auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequestDto
    ): AuthTokensDto
}
