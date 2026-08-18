package com.enterprise.financetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterprise.financetracker.domain.model.Transaction
import com.enterprise.financetracker.ui.components.AmountDisplay
import com.enterprise.financetracker.ui.components.CategoryBadge

// Layer 1: Route
@Composable
fun TransactionDetailRoute(
    transaction: Transaction?,
    onNavigateBack: () -> Unit,
    onDeleteClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    TransactionDetailScreen(
        transaction = transaction,
        onNavigateBack = onNavigateBack,
        onDeleteClick = { if (transaction != null) onDeleteClick(transaction) },
        modifier = modifier
    )
}

// Layer 2: Stateless Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transaction: Transaction?,
    onNavigateBack: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (transaction != null) {
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (transaction != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CategoryBadge(
                    category = transaction.category,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = transaction.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                AmountDisplay(
                    amount = transaction.amount,
                    type = transaction.type
                )
                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DetailItem(label = "Category", value = transaction.category.name)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        DetailItem(label = "Account", value = transaction.accountId.value)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        DetailItem(
                            label = "Type",
                            value = when (transaction.type) {
                                is com.enterprise.financetracker.domain.model.TransactionType.Income -> "Income"
                                is com.enterprise.financetracker.domain.model.TransactionType.Expense -> "Expense"
                                is com.enterprise.financetracker.domain.model.TransactionType.Transfer -> "Transfer"
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        DetailItem(label = "Recurring", value = if (transaction.isRecurring) "Yes (Monthly)" else "No")
                        if (!transaction.note.isNullOrBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            DetailItem(label = "Note", value = transaction.note)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Done")
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Transaction not found.")
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
