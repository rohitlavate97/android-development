package com.expensetracker.feature.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.expensetracker.core.common.DispatcherProvider
import com.expensetracker.core.designsystem.PrimaryGreen
import com.expensetracker.core.model.CategorySpending
import com.expensetracker.core.model.TransactionType
import com.expensetracker.feature.transactions.domain.GetTransactionsUseCase
import kotlinx.coroutines.flow.*

data class AnalyticsUiState(
    val totalExpense: Double = 0.0,
    val averageTransaction: Double = 0.0,
    val categoryBreakdown: List<CategorySpending> = emptyList(),
    val monthlyBudget: Double = 3000.0
) {
    val budgetProgress: Float
        get() = if (monthlyBudget > 0) (totalExpense / monthlyBudget).toFloat().coerceIn(0f, 1f) else 0f
}

class AnalyticsViewModel(
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    val uiState: StateFlow<AnalyticsUiState> = getTransactionsUseCase()
        .map { transactions ->
            val expenseTx = transactions.filter { it.type == TransactionType.EXPENSE }
            val totalExpense = expenseTx.sumOf { it.amount }
            val avg = if (expenseTx.isNotEmpty()) totalExpense / expenseTx.size else 0.0

            val catMap = expenseTx.groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.amount } }

            val breakdown = catMap.map { (cat, amount) ->
                CategorySpending(
                    category = cat,
                    totalAmount = amount,
                    percentage = if (totalExpense > 0) (amount / totalExpense).toFloat() else 0f
                )
            }.sortedByDescending { it.totalAmount }

            AnalyticsUiState(
                totalExpense = totalExpense,
                averageTransaction = avg,
                categoryBreakdown = breakdown
            )
        }
        .flowOn(dispatchers.default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AnalyticsUiState()
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Spending Analytics") })
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Budget Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Monthly Budget Progress", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("$${String.format("%.2f", state.totalExpense)} spent", fontWeight = FontWeight.Bold)
                            Text("Limit: $${state.monthlyBudget.toInt()}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.budgetProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = if (state.budgetProgress > 0.9f) MaterialTheme.colorScheme.error else PrimaryGreen
                        )
                    }
                }
            }

            // Key Metrics
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Avg / Expense",
                        value = "$${String.format("%.2f", state.averageTransaction)}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Total Categories",
                        value = "${state.categoryBreakdown.size}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Text("Category Spending Distribution", style = MaterialTheme.typography.titleLarge)
            }

            items(state.categoryBreakdown) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.category.name, fontWeight = FontWeight.Medium)
                        Text(
                            text = "$${String.format("%.2f", item.totalAmount)} (${(item.percentage * 100).toInt()}%)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
