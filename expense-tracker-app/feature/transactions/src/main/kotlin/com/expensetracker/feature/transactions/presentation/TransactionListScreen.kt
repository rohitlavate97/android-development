package com.expensetracker.feature.transactions.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.core.designsystem.*
import com.expensetracker.core.model.Transaction
import com.expensetracker.core.model.TransactionType

// Layer 1: Route (Collects ViewModel, wires callbacks)
@Composable
fun TransactionListRoute(
    viewModel: TransactionListViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TransactionListScreen(
        state = uiState,
        onIntent = viewModel::processIntent,
        onTransactionClick = onNavigateToDetail,
        onAddClick = onNavigateToAdd,
        modifier = modifier
    )
}

// Layer 2: Stateless Screen (100% Previewable & Testable)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    state: TransactionListUiState,
    onIntent: (TransactionListIntent) -> Unit,
    onTransactionClick: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                actions = {
                    IconButton(onClick = { onIntent(TransactionListIntent.Refresh) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state) {
                is TransactionListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is TransactionListUiState.Empty -> {
                    EmptyStateWidget(
                        message = "No transactions found. Tap + to add one!",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is TransactionListUiState.Error -> {
                    ErrorStateWidget(
                        message = state.message,
                        onRetry = { onIntent(TransactionListIntent.Refresh) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is TransactionListUiState.Content -> {
                    TransactionListContent(
                        content = state,
                        onFilterSelected = { onIntent(TransactionListIntent.FilterByType(it)) },
                        onTransactionClick = onTransactionClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// Layer 3: Content & Components
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionListContent(
    content: TransactionListUiState.Content,
    onFilterSelected: (TransactionType?) -> Unit,
    onTransactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = content.selectedFilter == null,
                onClick = { onFilterSelected(null) },
                label = { Text("All") }
            )
            FilterChip(
                selected = content.selectedFilter == TransactionType.EXPENSE,
                onClick = { onFilterSelected(TransactionType.EXPENSE) },
                label = { Text("Expenses") }
            )
            FilterChip(
                selected = content.selectedFilter == TransactionType.INCOME,
                onClick = { onFilterSelected(TransactionType.INCOME) },
                label = { Text("Income") }
            )
        }

        // Lazy List with stable key (Phase 4 Concept 7)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(
                items = content.filteredTransactions,
                key = { it.id.value } // Mandatory stable key
            ) { transaction ->
                ExpenseCard(
                    transaction = transaction,
                    onClick = { onTransactionClick(transaction.id.value) }
                )
            }
        }
    }
}
