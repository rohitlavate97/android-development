package com.enterprise.financetracker.data.model

import com.enterprise.financetracker.domain.model.TransactionType

/**
 * Data Transfer Object (DTO) matching external API / storage payload schemas.
 * (Phase 5 Concept 7 & Phase 7)
 */
data class CategoryDto(
    val id: String? = null,
    val name: String? = null,
    val iconName: String? = null,
    val colorHex: String? = null,
    val isDefault: Boolean? = null
)

data class TransactionDto(
    val id: String? = null,
    val accountId: String? = null,
    val title: String? = null,
    val amount: Double? = null,
    val type: String? = null,
    val category: CategoryDto? = null,
    val timestampEpochMillis: Long? = null,
    val note: String? = null,
    val tags: List<String>? = null,
    val isRecurring: Boolean? = null
)

data class InvestmentHoldingDto(
    val ticker: String? = null,
    val name: String? = null,
    val shares: Double? = null,
    val averageBuyPrice: Double? = null,
    val currentMarketPrice: Double? = null,
    val assetClass: String? = null
)

data class PortfolioDto(
    val id: String? = null,
    val name: String? = null,
    val holdings: List<InvestmentHoldingDto>? = null
)
