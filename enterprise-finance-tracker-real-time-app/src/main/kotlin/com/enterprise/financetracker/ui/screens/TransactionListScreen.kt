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
import com.enterprise.financetracker.domain.model.*
import com.enterprise.financetracker.ui.components.EmptyStateWidget
import com.enterprise.financetracker.ui.components.TransactionCard
import com.enterprise.financetracker.ui.viewmodels.TransactionListUiState
import com.enterprise.financetracker.ui.viewmodels.TransactionListViewModel

// Layer 1: Route
@Composable
fun TransactionListRoute(
    viewModel: TransactionListViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (TransactionId) -> Unit,
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
        is TransactionListUiState.Success -> {
            TransactionListScreen(
                transactions = state.filteredTransactions,
                searchQuery = state.searchQuery,
                selectedFilter = state.selectedFilter,
                onSearchQueryChange = viewModel::onSearchQueryChanged,
                onFilterSelect = viewModel::onFilterSelected,
                onNavigateBack = onNavigateBack,
                onTransactionClick = onNavigateToDetail,
                modifier = modifier
            )
        }
    }
}

// Layer 2: Stateless Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    transactions: List<Transaction>,
    searchQuery: String,
    selectedFilter: String,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelect: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onTransactionClick: (TransactionId) -> Unit,
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
                onValueChange = onSearchQueryChange,
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
                    onClick = { onFilterSelect("ALL") },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = selectedFilter == "EXPENSE",
                    onClick = { onFilterSelect("EXPENSE") },
                    label = { Text("Expenses") }
                )
                FilterChip(
                    selected = selectedFilter == "INCOME",
                    onClick = { onFilterSelect("INCOME") },
                    label = { Text("Income") }
                )
                FilterChip(
                    selected = selectedFilter == "TRANSFER",
                    onClick = { onFilterSelect("TRANSFER") },
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
}
