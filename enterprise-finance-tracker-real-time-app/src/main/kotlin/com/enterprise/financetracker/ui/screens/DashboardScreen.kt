package com.enterprise.financetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterprise.financetracker.domain.model.*
import com.enterprise.financetracker.ui.components.EmptyStateWidget
import com.enterprise.financetracker.ui.components.HoldingCard
import com.enterprise.financetracker.ui.components.TransactionCard

// Layer 1: Route
@Composable
fun DashboardRoute(
    portfolio: Portfolio,
    transactions: List<Transaction>,
    onNavigateToTransactions: () -> Unit,
    onNavigateToTransactionDetail: (TransactionId) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalBalance = 12450.00
    val totalNetWorth = totalBalance + portfolio.totalPortfolioValue

    DashboardScreen(
        netWorth = totalNetWorth,
        cashBalance = totalBalance,
        portfolio = portfolio,
        recentTransactions = transactions.take(5),
        onViewAllTransactions = onNavigateToTransactions,
        onTransactionClick = onNavigateToTransactionDetail,
        modifier = modifier
    )
}

// Layer 2: Stateless Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    netWorth: Double,
    cashBalance: Double,
    portfolio: Portfolio,
    recentTransactions: List<Transaction>,
    onViewAllTransactions: () -> Unit,
    onTransactionClick: (TransactionId) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enterprise Wealth") },
                actions = {
                    IconButton(onClick = onViewAllTransactions) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "Transactions")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Net Worth Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Total Net Worth",
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "$${String.format("%.2f", netWorth)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Liquid Cash", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                Text("$${String.format("%.2f", cashBalance)}", fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Investments", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                Text("$${String.format("%.2f", portfolio.totalPortfolioValue)}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Portfolio Holdings Section
            item {
                Text(
                    text = "Portfolio Holdings (${portfolio.name})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (portfolio.holdings.isEmpty()) {
                item {
                    EmptyStateWidget(message = "No investment holdings recorded.")
                }
            } else {
                items(
                    items = portfolio.holdings,
                    key = { it.ticker.value }
                ) { holding ->
                    HoldingCard(
                        holding = holding,
                        allocationPercentage = portfolio.calculateAllocation(holding)
                    )
                }
            }

            // Recent Transactions Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Transactions", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onViewAllTransactions) {
                        Text("View All")
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            if (recentTransactions.isEmpty()) {
                item {
                    EmptyStateWidget(message = "No recent transactions found.")
                }
            } else {
                items(
                    items = recentTransactions,
                    key = { it.id.value } // Mandatory stable key
                ) { transaction ->
                    TransactionCard(
                        transaction = transaction,
                        onClick = { onTransactionClick(transaction.id) }
                    )
                }
            }
        }
    }
}
