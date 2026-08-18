package com.enterprise.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterprise.financetracker.domain.model.*
import com.enterprise.financetracker.ui.theme.IncomeGreen
import com.enterprise.financetracker.ui.theme.ExpenseRed
import com.enterprise.financetracker.ui.theme.TransferBlue

@Composable
fun CategoryBadge(category: Category, modifier: Modifier = Modifier) {
    val icon = when (category.iconName) {
        "restaurant" -> Icons.Default.Restaurant
        "payments" -> Icons.Default.Payments
        "shopping_cart" -> Icons.Default.ShoppingCart
        "flight" -> Icons.Default.Flight
        "home" -> Icons.Default.Home
        "trending_up" -> Icons.Default.TrendingUp
        else -> Icons.Default.Category
    }

    val iconColor = try {
        Color(android.graphics.Color.parseColor(category.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(iconColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category.name,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun AmountDisplay(amount: Double, type: TransactionType, modifier: Modifier = Modifier) {
    val (prefix, color) = when (type) {
        is TransactionType.Income -> "+$" to IncomeGreen
        is TransactionType.Expense -> "-$" to ExpenseRed
        is TransactionType.Transfer -> "$" to TransferBlue
    }

    Text(
        text = "$prefix${String.format("%.2f", amount)}",
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = modifier
    )
}

@Composable
fun TransactionCard(
    transaction: Transaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryBadge(category = transaction.category)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${transaction.category.name} • ${transaction.accountId.value}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            AmountDisplay(amount = transaction.amount, type = transaction.type)
        }
    }
}

@Composable
fun HoldingCard(
    holding: InvestmentHolding,
    allocationPercentage: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = holding.ticker.value.take(3),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = holding.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${holding.shares} shares @ $${String.format("%.2f", holding.currentMarketPrice)} (${(allocationPercentage * 100).toInt()}%)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${String.format("%.2f", holding.currentMarketValue)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                val gainColor = if (holding.unrealizedProfitLoss >= 0) IncomeGreen else ExpenseRed
                val gainPrefix = if (holding.unrealizedProfitLoss >= 0) "+" else ""
                Text(
                    text = "$gainPrefix${String.format("%.1f", holding.returnPercentage)}%",
                    color = gainColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun EmptyStateWidget(
    message: String = "No records found.",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Inbox,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
