package com.enterprise.financetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.financetracker.ui.components.EmptyStateWidget
import com.enterprise.financetracker.ui.components.HoldingCard
import com.enterprise.financetracker.ui.components.TransactionCard
import com.enterprise.financetracker.ui.model.PortfolioUiModel
import com.enterprise.financetracker.ui.model.TransactionUiModel
import com.enterprise.financetracker.ui.viewmodels.DashboardUiState
import com.enterprise.financetracker.ui.viewmodels.DashboardViewModel

// Layer 1: Route
@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToTransactionDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is DashboardUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is DashboardUiState.Content -> {
            DashboardScreen(
                portfolio = state.portfolio,
                recentTransactions = state.recentTransactions,
                onViewAllTransactions = onNavigateToTransactions,
                onTransactionClick = onNavigateToTransactionDetail,
                modifier = modifier
            )
        }
        is DashboardUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// Layer 2: Stateless Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    portfolio: PortfolioUiModel,
    recentTransactions: List<TransactionUiModel>,
    onViewAllTransactions: () -> Unit,
    onTransactionClick: (String) -> Unit,
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
                            text = portfolio.formattedNetWorth,
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
                                Text(portfolio.formattedCash, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Investments (Live)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                Text(portfolio.formattedInvested, fontWeight = FontWeight.SemiBold)
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
                    key = { it.ticker }
                ) { holding ->
                    HoldingCard(holding = holding)
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
                    key = { it.id }
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
