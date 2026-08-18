package com.enterprise.financetracker.ui.model

import androidx.compose.runtime.Immutable

/**
 * UI State Models annotated with @Immutable for Compose Compiler Stability.
 * Enables the Compose runtime to skip recompositions when inputs are structurally equal.
 * (Phase 12 Concept 3 & ADR 034)
 */

@Immutable
data class CategoryUiModel(
    val id: String,
    val name: String,
    val iconName: String,
    val colorHex: String
)

@Immutable
data class TransactionUiModel(
    val id: String,
    val title: String,
    val formattedAmount: String,
    val isPositive: Boolean,
    val typeLabel: String,
    val category: CategoryUiModel,
    val accountLabel: String,
    val isRecurring: Boolean,
    val note: String? = null
)

@Immutable
data class HoldingUiModel(
    val ticker: String,
    val name: String,
    val formattedValue: String,
    val formattedReturn: String,
    val isPositiveReturn: Boolean,
    val allocationPercentageFormatted: String
)

@Immutable
data class PortfolioUiModel(
    val name: String,
    val formattedNetWorth: String,
    val formattedLiquidCash: String,
    val formattedInvested: String,
    val holdings: List<HoldingUiModel>
)
