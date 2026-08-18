package com.enterprise.financetracker.ui.model

import com.enterprise.financetracker.domain.model.*

/**
 * UI-Specific Presentation Models.
 * Contains pre-formatted display strings, localized symbols, and visual badges.
 * (Phase 5 Concept 7 & UI Model Discipline)
 */
data class CategoryUiModel(
    val id: String,
    val name: String,
    val iconName: String,
    val colorHex: String
)

data class TransactionUiModel(
    val id: String,
    val title: String,
    val formattedAmount: String,
    val isPositive: Boolean,
    val typeLabel: String,
    val category: CategoryUiModel,
    val accountLabel: String,
    val isRecurring: Boolean,
    val note: String?
)

data class HoldingUiModel(
    val ticker: String,
    val name: String,
    val sharesText: String,
    val formattedMarketValue: String,
    val returnPercentageText: String,
    val isGain: Boolean,
    val allocationPercentageText: String,
    val rawAllocation: Float
)

data class PortfolioUiModel(
    val name: String,
    val formattedNetWorth: String,
    val formattedCash: String,
    val formattedInvested: String,
    val holdings: List<HoldingUiModel>
)
