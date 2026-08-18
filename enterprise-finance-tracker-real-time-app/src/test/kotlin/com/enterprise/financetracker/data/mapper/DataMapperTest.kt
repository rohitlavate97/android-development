package com.enterprise.financetracker.data.mapper

import com.enterprise.financetracker.data.model.CategoryDto
import com.enterprise.financetracker.data.model.TransactionDto
import com.enterprise.financetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DataMapperTest {

    @Test
    fun given_incomplete_dto_when_mapped_to_domain_then_apply_defensive_fallbacks() {
        val incompleteDto = TransactionDto(
            id = null,
            title = null,
            amount = -50.0, // Negative amount from buggy server
            type = "UNKNOWN_TYPE",
            category = null,
            timestampEpochMillis = null
        )

        val domainModel = incompleteDto.toDomain()

        assertThat(domainModel.title).isEqualTo("Untitled Transaction")
        assertThat(domainModel.amount).isEqualTo(0.01) // Coerced positive
        assertThat(domainModel.type).isEqualTo(TransactionType.Expense)
        assertThat(domainModel.category.name).isEqualTo("General")
    }

    @Test
    fun given_valid_domain_model_when_mapped_to_dto_then_match_serialization_structure() {
        val dto = TransactionDto(
            id = "tx_99",
            accountId = "acc_main",
            title = "Cloud Server Subscription",
            amount = 45.00,
            type = "EXPENSE",
            category = CategoryDto("cat_tech", "Tech", "devices", "#2196F3", false),
            timestampEpochMillis = 1738000000000L
        )

        val domain = dto.toDomain()
        val mappedBackDto = domain.toDto()

        assertThat(mappedBackDto.id).isEqualTo("tx_99")
        assertThat(mappedBackDto.title).isEqualTo("Cloud Server Subscription")
        assertThat(mappedBackDto.type).isEqualTo("EXPENSE")
        assertThat(mappedBackDto.amount).isEqualTo(45.00)
    }
}
