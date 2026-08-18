package com.expensetracker.feature.transactions.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.core.designsystem.AmountText
import com.expensetracker.core.designsystem.CategoryIconBadge
import com.expensetracker.core.model.Transaction
import com.expensetracker.core.model.TransactionId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: String,
    viewModel: TransactionListViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val transaction = (uiState as? TransactionListUiState.Content)
        ?.transactions
        ?.find { it.id.value == transactionId }

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
                        IconButton(onClick = {
                            viewModel.processIntent(TransactionListIntent.DeleteTransaction(TransactionId(transactionId)))
                            onNavigateBack()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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
                CategoryIconBadge(
                    category = transaction.category,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = transaction.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                AmountText(
                    amount = transaction.amount,
                    type = transaction.type
                )
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DetailRow(label = "Category", value = transaction.category.name)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        DetailRow(label = "Type", value = transaction.type.name)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        DetailRow(label = "Account", value = transaction.accountId.value)
                        if (!transaction.note.isNullOrBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            DetailRow(label = "Note", value = transaction.note)
                        }
                    }
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
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
