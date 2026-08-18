package com.expensetracker.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.Transaction
import com.expensetracker.core.model.TransactionType

// Palette
val PrimaryGreen = Color(0xFF00897B)
val SecondaryTeal = Color(0xFF4DB6AC)
val AccentIncome = Color(0xFF2E7D32)
val AccentExpense = Color(0xFFD32F2F)
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val LightBackground = Color(0xFFF8F9FA)
val LightSurface = Color(0xFFFFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    secondary = SecondaryTeal,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    secondary = SecondaryTeal,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
        ),
        content = content
    )
}

// Reusable Atomic UI Components (Phase 4: Component Layer)

@Composable
fun CategoryIconBadge(category: Category, modifier: Modifier = Modifier) {
    val icon = when (category.iconName) {
        "restaurant" -> Icons.Default.Restaurant
        "payments" -> Icons.Default.Payments
        "devices" -> Icons.Default.Devices
        "flight" -> Icons.Default.Flight
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
fun AmountText(amount: Double, type: TransactionType, modifier: Modifier = Modifier) {
    val prefix = when (type) {
        TransactionType.INCOME -> "+$"
        TransactionType.EXPENSE -> "-$"
        TransactionType.TRANSFER -> "$"
    }
    val textColor = when (type) {
        TransactionType.INCOME -> AccentIncome
        TransactionType.EXPENSE -> AccentExpense
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = "$prefix${String.format("%.2f", amount)}",
        color = textColor,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = modifier
    )
}

@Composable
fun ExpenseCard(
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
            CategoryIconBadge(category = transaction.category)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = transaction.category.name,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
            AmountText(amount = transaction.amount, type = transaction.type)
        }
    }
}

@Composable
fun EmptyStateWidget(
    message: String = "No transactions recorded yet.",
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
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun ErrorStateWidget(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
