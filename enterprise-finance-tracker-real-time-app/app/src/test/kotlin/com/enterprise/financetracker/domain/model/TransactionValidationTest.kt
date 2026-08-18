package com.enterprise.financetracker.domain.model

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.Assert.assertThrows
import org.junit.Test

class TransactionValidationTest {

    private val sampleCategory = Category(
        id = CategoryId("cat_dining"),
        name = "Dining",
        iconName = "restaurant",
        colorHex = "#FF5722"
    )

    @Test
    fun given_negative_amount_when_instantiating_transaction_then_throw_IllegalArgumentException() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Transaction(
                id = TransactionId("tx_invalid"),
                accountId = AccountId("acc_checking"),
                title = "Invalid Transaction",
                amount = -45.0, // Negative amount
                type = TransactionType.Expense,
                category = sampleCategory,
                timestamp = Instant.fromEpochMilliseconds(1738000000000L)
            )
        }

        assertThat(exception).hasMessageThat().contains("Transaction amount must be strictly positive")
    }

    @Test
    fun given_blank_title_when_instantiating_transaction_then_throw_IllegalArgumentException() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Transaction(
                id = TransactionId("tx_blank"),
                accountId = AccountId("acc_checking"),
                title = "   ", // Blank title
                amount = 25.0,
                type = TransactionType.Expense,
                category = sampleCategory,
                timestamp = Instant.fromEpochMilliseconds(1738000000000L)
            )
        }

        assertThat(exception).hasMessageThat().contains("Transaction title cannot be blank")
    }

    @Test
    fun given_valid_expense_when_checking_net_balance_impact_then_return_negative_amount() {
        val expense = Transaction(
            id = TransactionId("tx_valid_expense"),
            accountId = AccountId("acc_checking"),
            title = "Dinner with Friends",
            amount = 65.50,
            type = TransactionType.Expense,
            category = sampleCategory,
            timestamp = Instant.fromEpochMilliseconds(1738000000000L)
        )

        assertThat(expense.netBalanceImpact).isEqualTo(-65.50)
    }

    @Test
    fun given_valid_income_when_checking_net_balance_impact_then_return_positive_amount() {
        val income = Transaction(
            id = TransactionId("tx_valid_income"),
            accountId = AccountId("acc_checking"),
            title = "Salary Bonus",
            amount = 1200.00,
            type = TransactionType.Income,
            category = sampleCategory,
            timestamp = Instant.fromEpochMilliseconds(1738000000000L)
        )

        assertThat(income.netBalanceImpact).isEqualTo(1200.00)
    }

    @Test
    fun given_valid_budget_when_spent_exceeds_limit_then_isOverBudget_is_true() {
        val budget = Budget(
            categoryId = CategoryId("cat_dining"),
            monthlyLimit = 500.0,
            currentSpent = 550.0
        )

        assertThat(budget.isOverBudget).isTrue()
        assertThat(budget.progress).isEqualTo(1.0f)
        assertThat(budget.remainingBalance).isEqualTo(0.0)
    }
}
