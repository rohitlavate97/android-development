package com.expensetracker.feature.transactions.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expensetracker.core.model.*
import kotlinx.datetime.Clock
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    viewModel: TransactionListViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var note by remember { mutableStateOf("") }

    val defaultCategory = Category(
        id = CategoryId("cat_food"),
        name = "Food & Dining",
        iconName = "restaurant",
        colorHex = "#FF5722",
        type = TransactionType.EXPENSE
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Transaction") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount ($)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == TransactionType.EXPENSE,
                    onClick = { type = TransactionType.EXPENSE },
                    label = { Text("Expense") }
                )
                FilterChip(
                    selected = type == TransactionType.INCOME,
                    onClick = { type = TransactionType.INCOME },
                    label = { Text("Income") }
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && amount > 0) {
                        val transaction = Transaction(
                            id = TransactionId("tx_${UUID.randomUUID()}"),
                            title = title,
                            amount = amount,
                            type = type,
                            category = defaultCategory,
                            accountId = AccountId("acc_main"),
                            timestamp = Clock.System.now(),
                            note = note.ifBlank { null }
                        )
                        viewModel.processIntent(TransactionListIntent.AddNewTransaction(transaction))
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = title.isNotBlank() && (amountText.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("Save Transaction")
            }
        }
    }
}
