package com.expensetracker.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.core.designsystem.*

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToTransactionDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardScreen(
        state = uiState,
        onViewAllClick = onNavigateToTransactions,
        onTransactionClick = onNavigateToTransactionDetail,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onViewAllClick: () -> Unit,
    onTransactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Dashboard") }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        when (state) {
            is DashboardUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is DashboardUiState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Balance Card
                    item {
                        BalanceSummaryCard(
                            balance = state.totalBalance,
                            income = state.monthlyIncome,
                            expense = state.monthlyExpense
                        )
                    }

                    // Spending by Category
                    if (state.topSpendingCategories.isNotEmpty()) {
                        item {
                            Text(
                                text = "Top Spending Categories",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(state.topSpendingCategories) { item ->
                            CategorySpendingRow(item = item)
                        }
                    }

                    // Recent Transactions Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent Transactions", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = onViewAllClick) {
                                Text("View All")
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }

                    if (state.recentTransactions.isEmpty()) {
                        item {
                            EmptyStateWidget(message = "No recent transactions found.")
                        }
                    } else {
                        items(
                            items = state.recentTransactions,
                            key = { it.id.value }
                        ) { transaction ->
                            ExpenseCard(
                                transaction = transaction,
                                onClick = { onTransactionClick(transaction.id.value) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BalanceSummaryCard(balance: Double, income: Double, expense: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Total Net Balance",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Text(
                text = "$${String.format("%.2f", balance)}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = AccentIncome,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text("Income", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                        Text("+$${String.format("%.2f", income)}", fontWeight = FontWeight.SemiBold, color = AccentIncome)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = AccentExpense,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text("Expense", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                        Text("-$${String.format("%.2f", expense)}", fontWeight = FontWeight.SemiBold, color = AccentExpense)
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySpendingRow(item: com.expensetracker.core.model.CategorySpending) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(item.category.name, fontWeight = FontWeight.Medium)
            Text("$${String.format("%.2f", item.totalAmount)} (${(item.percentage * 100).toInt()}%)")
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { item.percentage },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = PrimaryGreen,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
