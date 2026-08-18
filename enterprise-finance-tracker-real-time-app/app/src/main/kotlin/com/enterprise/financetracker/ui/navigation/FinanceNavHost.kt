package com.enterprise.financetracker.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.enterprise.financetracker.domain.model.FinancialResult
import com.enterprise.financetracker.domain.model.TransactionId
import com.enterprise.financetracker.domain.usecase.GetTransactionDetailUseCase
import com.enterprise.financetracker.ui.mapper.toUiModel
import com.enterprise.financetracker.ui.model.TransactionUiModel
import com.enterprise.financetracker.ui.screens.*
import com.enterprise.financetracker.ui.viewmodels.DashboardViewModel
import com.enterprise.financetracker.ui.viewmodels.TransactionListIntent
import com.enterprise.financetracker.ui.viewmodels.TransactionListMviViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

const val DEEP_LINK_BASE_URI = "https://financetracker.enterprise.com"

@Composable
fun FinanceNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AuthGraph,
        modifier = modifier,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn() },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) + fadeOut() },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) + fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut() }
    ) {
        authGraph(navController)
        mainGraph(navController)
    }
}

fun NavGraphBuilder.authGraph(navController: NavHostController) {
    navigation<AuthGraph>(startDestination = LoginDestination) {
        composable<LoginDestination> {
            LoginRoute(
                onLoginSuccess = {
                    // Backstack management: Clear AuthGraph from backstack so back button doesn't return to login
                    navController.navigate(MainGraph) {
                        popUpTo<AuthGraph> { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

fun NavGraphBuilder.mainGraph(navController: NavHostController) {
    navigation<MainGraph>(startDestination = DashboardDestination) {
        composable<DashboardDestination> {
            val dashboardViewModel: DashboardViewModel = koinViewModel()
            DashboardRoute(
                viewModel = dashboardViewModel,
                onNavigateToTransactions = {
                    navController.navigate(TransactionListDestination) {
                        launchSingleTop = true
                    }
                },
                onNavigateToTransactionDetail = { id ->
                    navController.navigate(TransactionDetailDestination(transactionId = id)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<TransactionListDestination> {
            val transactionListViewModel: TransactionListMviViewModel = koinViewModel()
            TransactionListRoute(
                viewModel = transactionListViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { id ->
                    navController.navigate(TransactionDetailDestination(transactionId = id)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<TransactionDetailDestination>(
            deepLinks = listOf(
                navDeepLink<TransactionDetailDestination>(basePath = "$DEEP_LINK_BASE_URI/transactions")
            )
        ) { backStackEntry ->
            val destination: TransactionDetailDestination = backStackEntry.toRoute()
            val getTransactionDetailUseCase: GetTransactionDetailUseCase = koinInject()
            val transactionListViewModel: TransactionListMviViewModel = koinViewModel()

            var transactionUiModel by remember { mutableStateOf<TransactionUiModel?>(null) }

            LaunchedEffect(destination.transactionId) {
                val result = getTransactionDetailUseCase(TransactionId(destination.transactionId))
                if (result is FinancialResult.Success) {
                    transactionUiModel = result.value.toUiModel()
                }
            }

            TransactionDetailRoute(
                transaction = transactionUiModel,
                onNavigateBack = { navController.popBackStack() },
                onDeleteClick = { id ->
                    transactionListViewModel.processIntent(TransactionListIntent.DeleteTransaction(id))
                    navController.popBackStack()
                }
            )
        }
    }
}
