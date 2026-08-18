package com.enterprise.financetracker.database

import com.enterprise.financetracker.data.local.entity.CategoryEntity
import com.enterprise.financetracker.data.local.entity.TransactionEntity
import com.enterprise.financetracker.data.local.entity.TransactionWithCategory
import com.enterprise.financetracker.data.mapper.toDomain
import com.enterprise.financetracker.data.mapper.toEntity
import com.enterprise.financetracker.domain.model.*
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.Test

class EntityMapperTest {

    @Test
    fun given_transaction_with_category_relation_when_mapped_to_domain_then_types_match() {
        val relation = TransactionWithCategory(
            transaction = TransactionEntity(
                id = "tx_ent_1",
                accountId = "acc_chase",
                categoryId = "cat_tech",
                title = "Keyboard Purchase",
                amount = 150.00,
                type = "EXPENSE",
                timestampEpochMillis = 1738000000000L,
                note = "Mechanical",
                tags = "hardware,office",
                isRecurring = false
            ),
            category = CategoryEntity(
                id = "cat_tech",
                name = "Technology",
                iconName = "computer",
                colorHex = "#2196F3",
                isDefault = true
            )
        )

        val domain = relation.toDomain()

        assertThat(domain.id.value).isEqualTo("tx_ent_1")
        assertThat(domain.title).isEqualTo("Keyboard Purchase")
        assertThat(domain.type).isEqualTo(TransactionType.Expense)
        assertThat(domain.category.name).isEqualTo("Technology")
        assertThat(domain.tags).containsExactly("hardware", "office")
    }

    @Test
    fun given_domain_model_when_mapped_to_entity_then_fields_match() {
        val domain = Transaction(
            id = TransactionId("tx_dom_1"),
            accountId = AccountId("acc_main"),
            title = "Monthly Gym Membership",
            amount = 60.00,
            type = TransactionType.Expense,
            category = Category(CategoryId("cat_health"), "Health", "fitness_center", "#4CAF50"),
            timestamp = Instant.fromEpochMilliseconds(1738000000000L),
            tags = setOf("subscription", "fitness"),
            isRecurring = true
        )

        val entity = domain.toEntity()

        assertThat(entity.id).isEqualTo("tx_dom_1")
        assertThat(entity.categoryId).isEqualTo("cat_health")
        assertThat(entity.tags).isEqualTo("subscription,fitness")
        assertThat(entity.isRecurring).isTrue()
    }
}
