package com.enterprise.financetracker.performance

import androidx.compose.runtime.Immutable
import com.enterprise.financetracker.ui.model.CategoryUiModel
import com.enterprise.financetracker.ui.model.HoldingUiModel
import com.enterprise.financetracker.ui.model.PortfolioUiModel
import com.enterprise.financetracker.ui.model.TransactionUiModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StabilityAndImmutabilityTest {

    @Test
    fun given_ui_models_when_checked_for_stability_annotations_then_all_have_Immutable_annotation() {
        val modelClasses = listOf(
            CategoryUiModel::class.java,
            TransactionUiModel::class.java,
            HoldingUiModel::class.java,
            PortfolioUiModel::class.java
        )

        for (clazz in modelClasses) {
            val hasImmutableAnnotation = clazz.isAnnotationPresent(Immutable::class.java)
            assertThat(hasImmutableAnnotation).isTrue()
        }
    }

    @Test
    fun given_two_identical_TransactionUiModels_when_compared_then_equals_returns_true() {
        val modelA = TransactionUiModel(
            id = "tx_100",
            title = "AWS Hosting",
            formattedAmount = "-$120.00",
            isPositive = false,
            typeLabel = "Expense",
            category = CategoryUiModel("cat_cloud", "Cloud", "cloud", "#2196F3"),
            accountLabel = "acc_main",
            isRecurring = true,
            note = "Monthly server bill"
        )

        val modelB = TransactionUiModel(
            id = "tx_100",
            title = "AWS Hosting",
            formattedAmount = "-$120.00",
            isPositive = false,
            typeLabel = "Expense",
            category = CategoryUiModel("cat_cloud", "Cloud", "cloud", "#2196F3"),
            accountLabel = "acc_main",
            isRecurring = true,
            note = "Monthly server bill"
        )

        assertThat(modelA).isEqualTo(modelB)
        assertThat(modelA.hashCode()).isEqualTo(modelB.hashCode())
    }
}
