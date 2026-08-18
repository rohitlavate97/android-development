package com.enterprise.financetracker.data.mapper

import com.enterprise.financetracker.data.model.*
import com.enterprise.financetracker.domain.model.*
import kotlinx.datetime.Instant

/**
 * Data Layer Boundary Mappers.
 * Isolates Domain entities from volatile remote/database DTO changes.
 * (Phase 5 Concept 7 & ADR 014)
 */

fun CategoryDto.toDomain(): Category {
    return Category(
        id = CategoryId(id ?: "cat_default"),
        name = name ?: "Uncategorized",
        iconName = iconName ?: "category",
        colorHex = colorHex ?: "#78909C",
        isDefault = isDefault ?: false
    )
}

fun Category.toDto(): CategoryDto {
    return CategoryDto(
        id = id.value,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        isDefault = isDefault
    )
}

fun TransactionDto.toDomain(): Transaction {
    val transactionType = when (type?.uppercase()) {
        "INCOME" -> TransactionType.Income
        "TRANSFER" -> TransactionType.Transfer(AccountId("acc_main"))
        else -> TransactionType.Expense
    }

    return Transaction(
        id = TransactionId(id ?: "tx_temp_${System.currentTimeMillis()}"),
        accountId = AccountId(accountId ?: "acc_main"),
        title = title?.ifBlank { "Untitled Transaction" } ?: "Untitled Transaction",
        amount = (amount ?: 0.0).coerceAtLeast(0.01),
        type = transactionType,
        category = category?.toDomain() ?: Category(CategoryId("cat_gen"), "General", "category", "#78909C"),
        timestamp = Instant.fromEpochMilliseconds(timestampEpochMillis ?: System.currentTimeMillis()),
        note = note,
        tags = tags?.toSet() ?: emptySet(),
        isRecurring = isRecurring ?: false
    )
}

fun Transaction.toDto(): TransactionDto {
    val typeString = when (type) {
        is TransactionType.Income -> "INCOME"
        is TransactionType.Expense -> "EXPENSE"
        is TransactionType.Transfer -> "TRANSFER"
    }

    return TransactionDto(
        id = id.value,
        accountId = accountId.value,
        title = title,
        amount = amount,
        type = typeString,
        category = category.toDto(),
        timestampEpochMillis = timestamp.toEpochMilliseconds(),
        note = note,
        tags = tags.toList(),
        isRecurring = isRecurring
    )
}

fun InvestmentHoldingDto.toDomain(): InvestmentHolding {
    val assetClassEnum = try {
        AssetClass.valueOf(assetClass?.uppercase() ?: "EQUITY")
    } catch (e: Exception) {
        AssetClass.EQUITY
    }

    return InvestmentHolding(
        ticker = TickerSymbol(ticker ?: "UNKNOWN"),
        name = name ?: "Unknown Asset",
        shares = (shares ?: 0.0).coerceAtLeast(0.0001),
        averageBuyPrice = (averageBuyPrice ?: 1.0).coerceAtLeast(0.01),
        currentMarketPrice = (currentMarketPrice ?: 1.0).coerceAtLeast(0.0),
        assetClass = assetClassEnum
    )
}

fun PortfolioDto.toDomain(): Portfolio {
    return Portfolio(
        id = PortfolioId(id ?: "port_default"),
        name = name ?: "My Portfolio",
        holdings = holdings?.map { it.toDomain() } ?: emptyList()
    )
}
