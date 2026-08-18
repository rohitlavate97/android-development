# Milestone 9: Type-Safe Navigation

## Title, Goal & Phase Alignment
**Goal:** Implement a scalable, type-safe navigation graph using Jetpack Compose Navigation.
**Phase:** Expense Tracker v8 - Routing & Deep Linking

## Architecture & Component Blueprint
- **Compose Navigation:** Core routing mechanism.
- **@Serializable Routes:** Kotlinx.serialization objects/classes defining navigation targets.
- **Nested Graphs:** Logical grouping of related screens (e.g., Auth Graph, Home Graph).
- **SavedStateHandle:** Passing complex arguments safely via ViewModel.
- **Deep Links:** URI intent resolution pointing directly to screens.

## Step-by-Step Implementation Instructions
1. Migrate from string-based routes to Type-Safe routes using `@Serializable`.
2. Setup the `NavHost` with nested `navigation<Route>` blocks.
3. Add a bottom navigation bar synced with the NavController's current backstack entry.
4. Update `ViewModel` to extract typed arguments from `SavedStateHandle.toRoute()`.
5. Declare an intent filter in `AndroidManifest.xml` for `expensetracker://transaction/{id}` and attach `deepLinks` to the composable destination.

## Code Snippets & Signatures
```kotlin
@Serializable
data object HomeGraph

@Serializable
data class TransactionDetail(val transactionId: String)

NavHost(navController = navController, startDestination = HomeGraph) {
    navigation<HomeGraph>(startDestination = TransactionList) {
        composable<TransactionList> {
            TransactionListScreen(
                onNavigateToDetail = { id -> navController.navigate(TransactionDetail(id)) }
            )
        }
        composable<TransactionDetail>(
            deepLinks = listOf(navDeepLink<TransactionDetail>(basePath = "expensetracker://transaction"))
        ) { backStackEntry ->
            val detailRoute = backStackEntry.toRoute<TransactionDetail>()
            TransactionDetailScreen(id = detailRoute.transactionId)
        }
    }
}
```

## Deliberate Bugs to Catch & Debug
- Forgetting `@Serializable` on a route class, causing runtime crashes when NavHost tries to register it.
- Passing non-parcelable complex objects via navigation instead of IDs.
- Mismatch between AndroidManifest schema and NavHost deepLink URI pattern.

## Unit Testing Requirements (Given-When-Then)
- **Given** the Transaction List screen, **When** an item is clicked, **Then** NavController navigates to `TransactionDetail` with correct arguments.
- **Given** an intent matching `expensetracker://transaction/123`, **When** the app opens, **Then** the TransactionDetail screen is displayed.

## Acceptance Criteria Checklist
- [ ] Navigation is 100% type-safe with Kotlinx Serialization.
- [ ] Bottom Navigation updates state correctly when navigating.
- [ ] Nested NavGraphs properly separate app flows.
- [ ] Complex data passing relies on `SavedStateHandle.toRoute()`.
- [ ] Deep links open specific app screens.
