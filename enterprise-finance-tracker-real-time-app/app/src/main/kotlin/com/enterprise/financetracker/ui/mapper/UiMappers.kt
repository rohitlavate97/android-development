package com.enterprise.financetracker.ui.mapper

import com.enterprise.financetracker.domain.model.*
import com.enterprise.financetracker.domain.usecase.PortfolioSummary
import com.enterprise.financetracker.ui.model.*

/**
 * UI Layer Boundary Mappers.
 * Transforms pure Domain models into formatted, localized UI Presentation Models.
 * (Phase 5 Concept 7)
 */

fun Category.toUiModel(): CategoryUiModel {
    return CategoryUiModel(
        id = id.value,
        name = name,
        iconName = iconName,
        colorHex = colorHex
    )
}

fun Transaction.toUiModel(): TransactionUiModel {
    val (prefix, isPositive, typeLabel) = when (type) {
        is TransactionType.Income -> Triple("+", true, "Income")
        is TransactionType.Expense -> Triple("-", false, "Expense")
        is TransactionType.Transfer -> Triple("", true, "Transfer")
    }

    return TransactionUiModel(
        id = id.value,
        title = title,
        formattedAmount = "$prefix$${String.format("%.2f", amount)}",
        isPositive = isPositive,
        typeLabel = typeLabel,
        category = category.toUiModel(),
        accountLabel = accountId.value,
        isRecurring = isRecurring,
        note = note
    )
}

fun InvestmentHolding.toUiModel(allocation: Float): HoldingUiModel {
    val isGain = unrealizedProfitLoss >= 0
    val gainPrefix = if (isGain) "+" else ""

    return HoldingUiModel(
        ticker = ticker.value,
        name = name,
        sharesText = "$shares shares @ $${String.format("%.2f", currentMarketPrice)}",
        formattedMarketValue = "$${String.format("%.2f", currentMarketValue)}",
        returnPercentageText = "$gainPrefix${String.format("%.1f", returnPercentage)}%",
        isGain = isGain,
        allocationPercentageText = "${(allocation * 100).toInt()}%",
        rawAllocation = allocation
    )
}

fun PortfolioSummary.toUiModel(): PortfolioUiModel {
    val holdingModels = portfolio.holdings.map { holding ->
        val allocation = portfolio.calculateAllocation(holding)
        holding.toUiModel(allocation)
    }

    return PortfolioUiModel(
        name = portfolio.name,
        formattedNetWorth = "$${String.format("%.2f", netWorth)}",
        formattedCash = "$${String.format("%.2f", liquidCash)}",
        formattedInvested = "$${String.format("%.2f", investedValue)}",
        holdings = holdingModels
    )
}
