package com.enterprise.financetracker.data.mapper

import com.enterprise.financetracker.data.local.entity.CategoryEntity
import com.enterprise.financetracker.data.local.entity.TransactionEntity
import com.enterprise.financetracker.data.local.entity.TransactionWithCategory
import com.enterprise.financetracker.data.network.model.NetworkCategoryDto
import com.enterprise.financetracker.data.network.model.NetworkTransactionDto
import com.enterprise.financetracker.domain.model.*
import kotlinx.datetime.Instant

/**
 * Persistence Boundary Mappers: Entities <-> Domain Models
 * (Phase 8 Concept 5)
 */

fun CategoryEntity.toDomain(): Category {
    return Category(
        id = CategoryId(id),
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        isDefault = isDefault
    )
}

fun Category.toEntity(): CategoryEntity {
    return CategoryEntity(
        id = id.value,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        isDefault = isDefault
    )
}

fun TransactionWithCategory.toDomain(): Transaction {
    val transactionType = when (transaction.type.uppercase()) {
        "INCOME" -> TransactionType.Income
        "TRANSFER" -> TransactionType.Transfer(AccountId("acc_main"))
        else -> TransactionType.Expense
    }

    return Transaction(
        id = TransactionId(transaction.id),
        accountId = AccountId(transaction.accountId),
        title = transaction.title,
        amount = transaction.amount,
        type = transactionType,
        category = category.toDomain(),
        timestamp = Instant.fromEpochMilliseconds(transaction.timestampEpochMillis),
        note = transaction.note,
        tags = if (transaction.tags.isBlank()) emptySet() else transaction.tags.split(",").toSet(),
        isRecurring = transaction.isRecurring
    )
}

fun Transaction.toEntity(): TransactionEntity {
    val typeString = when (type) {
        is TransactionType.Income -> "INCOME"
        is TransactionType.Expense -> "EXPENSE"
        is TransactionType.Transfer -> "TRANSFER"
    }

    return TransactionEntity(
        id = id.value,
        accountId = accountId.value,
        categoryId = category.id.value,
        title = title,
        amount = amount,
        type = typeString,
        timestampEpochMillis = timestamp.toEpochMilliseconds(),
        note = note,
        tags = tags.joinToString(","),
        isRecurring = isRecurring
    )
}

fun NetworkTransactionDto.toEntity(): Pair<TransactionEntity, CategoryEntity> {
    val catId = category?.id ?: "cat_default"
    val categoryEntity = CategoryEntity(
        id = catId,
        name = category?.name ?: "General",
        iconName = category?.iconName ?: "category",
        colorHex = category?.colorHex ?: "#78909C",
        isDefault = category?.isDefault ?: false
    )

    val transactionEntity = TransactionEntity(
        id = id ?: "tx_temp_${System.currentTimeMillis()}",
        accountId = accountId ?: "acc_main",
        categoryId = catId,
        title = title ?: "Untitled Transaction",
        amount = (amount ?: 0.0).coerceAtLeast(0.01),
        type = type ?: "EXPENSE",
        timestampEpochMillis = timestampEpochMillis ?: System.currentTimeMillis(),
        note = note,
        tags = tags?.joinToString(",") ?: "",
        isRecurring = isRecurring ?: false
    )

    return Pair(transactionEntity, categoryEntity)
}
