package com.enterprise.financetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.financetracker.ui.components.EmptyStateWidget
import com.enterprise.financetracker.ui.components.TransactionCard
import com.enterprise.financetracker.ui.model.TransactionUiModel
import com.enterprise.financetracker.ui.viewmodels.TransactionListIntent
import com.enterprise.financetracker.ui.viewmodels.TransactionListMviViewModel
import com.enterprise.financetracker.ui.viewmodels.TransactionListUiState

// Layer 1: Route (MVI Intent Dispatcher)
@Composable
fun TransactionListRoute(
    viewModel: TransactionListMviViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is TransactionListUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is TransactionListUiState.Empty -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Transactions") },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                },
                modifier = modifier
            ) { innerPadding ->
                EmptyStateWidget(
                    message = "No transactions found.",
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
        is TransactionListUiState.Content -> {
            TransactionListScreen(
                transactions = state.filteredTransactions,
                searchQuery = state.searchQuery,
                selectedFilter = state.selectedFilter,
                onIntent = viewModel::processIntent,
                onNavigateBack = onNavigateBack,
                onTransactionClick = onNavigateToDetail,
                modifier = modifier
            )
        }
        is TransactionListUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// Layer 2: Stateless Screen (MVI event emitter)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    transactions: List<TransactionUiModel>,
    searchQuery: String,
    selectedFilter: String,
    onIntent: (TransactionListIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onTransactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { onIntent(TransactionListIntent.SearchQueryChanged(it)) },
                label = { Text("Search transactions...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { onIntent(TransactionListIntent.FilterSelected("ALL")) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = selectedFilter == "EXPENSE",
                    onClick = { onIntent(TransactionListIntent.FilterSelected("EXPENSE")) },
                    label = { Text("Expenses") }
                )
                FilterChip(
                    selected = selectedFilter == "INCOME",
                    onClick = { onIntent(TransactionListIntent.FilterSelected("INCOME")) },
                    label = { Text("Income") }
                )
                FilterChip(
                    selected = selectedFilter == "TRANSFER",
                    onClick = { onIntent(TransactionListIntent.FilterSelected("TRANSFER")) },
                    label = { Text("Transfers") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (transactions.isEmpty()) {
                EmptyStateWidget(message = "No matching transactions found.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(
                        items = transactions,
                        key = { it.id } // Mandatory stable key
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
}
