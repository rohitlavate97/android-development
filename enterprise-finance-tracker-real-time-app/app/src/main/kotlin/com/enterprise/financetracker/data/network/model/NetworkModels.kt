package com.enterprise.financetracker.data.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkCategoryDto(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("icon_name") val iconName: String? = null,
    @SerialName("color_hex") val colorHex: String? = null,
    @SerialName("is_default") val isDefault: Boolean? = null
)

@Serializable
data class NetworkTransactionDto(
    @SerialName("id") val id: String? = null,
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("amount") val amount: Double? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("category") val category: NetworkCategoryDto? = null,
    @SerialName("timestamp_epoch_millis") val timestampEpochMillis: Long? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("tags") val tags: List<String>? = null,
    @SerialName("is_recurring") val isRecurring: Boolean? = null
)

@Serializable
data class AuthTokensDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Long
)

@Serializable
data class RefreshTokenRequestDto(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class ApiErrorEnvelopeDto(
    @SerialName("status_code") val statusCode: Int,
    @SerialName("error_code") val errorCode: String,
    @SerialName("message") val message: String,
    @SerialName("timestamp") val timestamp: Long
)
