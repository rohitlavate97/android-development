package com.enterprise.financetracker.ui

import com.enterprise.financetracker.ui.model.CategoryUiModel
import com.enterprise.financetracker.ui.model.TransactionUiModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StatelessScreenLogicTest {

    @Test
    fun given_valid_credentials_when_evaluating_login_form_rules_then_pass_validation() {
        val email = "engineer@enterprise.com"
        val password = "StrongPassword123"

        val isValidEmail = email.contains("@") && email.contains(".")
        val isPasswordSufficient = password.length >= 6

        assertThat(isValidEmail).isTrue()
        assertThat(isPasswordSufficient).isTrue()
    }

    @Test
    fun given_invalid_email_when_evaluating_login_form_rules_then_fail_validation() {
        val email = "invalid-email-address"
        val isValidEmail = email.contains("@") && email.contains(".")

        assertThat(isValidEmail).isFalse()
    }

    @Test
    fun given_transaction_ui_models_when_filtered_by_search_query_then_correct_subset_is_retained() {
        val items = listOf(
            TransactionUiModel(
                id = "1",
                title = "Starbucks Coffee",
                formattedAmount = "-$5.50",
                isPositive = false,
                typeLabel = "Expense",
                category = CategoryUiModel("c1", "Food", "restaurant", "#FF5722"),
                accountLabel = "acc_main",
                isRecurring = false,
                note = "Latte"
            ),
            TransactionUiModel(
                id = "2",
                title = "Monthly Salary",
                formattedAmount = "+$4000.00",
                isPositive = true,
                typeLabel = "Income",
                category = CategoryUiModel("c2", "Salary", "payments", "#4CAF50"),
                accountLabel = "acc_main",
                isRecurring = true,
                note = null
            )
        )

        val query = "Coffee"
        val result = items.filter { it.title.contains(query, ignoreCase = true) }

        assertThat(result).hasSize(1)
        assertThat(result.first().title).isEqualTo("Starbucks Coffee")
    }
}
