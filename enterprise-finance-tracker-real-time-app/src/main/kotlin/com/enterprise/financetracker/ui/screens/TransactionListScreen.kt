package com.enterprise.financetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enterprise.financetracker.domain.model.*
import com.enterprise.financetracker.ui.components.EmptyStateWidget
import com.enterprise.financetracker.ui.components.TransactionCard

// Layer 1: Route
@Composable
fun TransactionListRoute(
    transactions: List<Transaction>,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (TransactionId) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf<String?>("ALL") }

    val filteredList = remember(transactions, searchQuery, selectedFilter) {
        transactions.filter { tx ->
            val matchesSearch = tx.title.contains(searchQuery, ignoreCase = true) ||
                    tx.category.name.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "EXPENSE" -> tx.type is TransactionType.Expense
                "INCOME" -> tx.type is TransactionType.Income
                "TRANSFER" -> tx.type is TransactionType.Transfer
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    TransactionListScreen(
        transactions = filteredList,
        searchQuery = searchQuery,
        selectedFilter = selectedFilter,
        onSearchQueryChange = { searchQuery = it },
        onFilterSelect = { selectedFilter = it },
        onNavigateBack = onNavigateBack,
        onTransactionClick = onNavigateToDetail,
        modifier = modifier
    )
}

// Layer 2: Stateless Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    transactions: List<Transaction>,
    searchQuery: String,
    selectedFilter: String?,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelect: (String?) -> Unit,
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
