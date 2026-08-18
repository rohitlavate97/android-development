package com.expensetracker.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.expensetracker.feature.analytics.AnalyticsScreen
import com.expensetracker.feature.analytics.AnalyticsViewModel
import com.expensetracker.feature.dashboard.DashboardRoute
import com.expensetracker.feature.dashboard.DashboardViewModel
import com.expensetracker.feature.transactions.presentation.AddTransactionScreen
import com.expensetracker.feature.transactions.presentation.TransactionDetailScreen
import com.expensetracker.feature.transactions.presentation.TransactionListRoute
import com.expensetracker.feature.transactions.presentation.TransactionListViewModel
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

// Type-Safe Routes (Phase 9 Concept 2)
@Serializable data object DashboardDestination
@Serializable data object TransactionsDestination
@Serializable data object AnalyticsDestination
@Serializable data object AddTransactionDestination
@Serializable data class TransactionDetailDestination(val id: String)

@Composable
fun ExpenseAppRoot() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    selected = currentDestination?.route?.contains("DashboardDestination") == true,
                    onClick = {
                        navController.navigate(DashboardDestination) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Transactions") },
                    label = { Text("Transactions") },
                    selected = currentDestination?.route?.contains("TransactionsDestination") == true,
                    onClick = {
                        navController.navigate(TransactionsDestination) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "Analytics") },
                    label = { Text("Analytics") },
                    selected = currentDestination?.route?.contains("AnalyticsDestination") == true,
                    onClick = {
                        navController.navigate(AnalyticsDestination) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DashboardDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<DashboardDestination> {
                val viewModel: DashboardViewModel = koinViewModel()
                DashboardRoute(
                    viewModel = viewModel,
                    onNavigateToTransactions = { navController.navigate(TransactionsDestination) },
                    onNavigateToTransactionDetail = { id ->
                        navController.navigate(TransactionDetailDestination(id))
                    }
                )
            }

            composable<TransactionsDestination> {
                val viewModel: TransactionListViewModel = koinViewModel()
                TransactionListRoute(
                    viewModel = viewModel,
                    onNavigateToDetail = { id ->
                        navController.navigate(TransactionDetailDestination(id))
                    },
                    onNavigateToAdd = {
                        navController.navigate(AddTransactionDestination)
                    }
                )
            }

            composable<AnalyticsDestination> {
                val viewModel: AnalyticsViewModel = koinViewModel()
                AnalyticsScreen(viewModel = viewModel)
            }

            composable<AddTransactionDestination> {
                val viewModel: TransactionListViewModel = koinViewModel()
                AddTransactionScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<TransactionDetailDestination> { backStackEntry ->
                val detailRoute = backStackEntry.toRoute<TransactionDetailDestination>()
                val viewModel: TransactionListViewModel = koinViewModel()
                TransactionDetailScreen(
                    transactionId = detailRoute.id,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
