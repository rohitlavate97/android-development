# Milestone 2: Jetpack Compose UI Layer

## Title, Goal & Phase Alignment
**Goal**: Build a declarative UI using Jetpack Compose with unidirectional data flow and distinct UI states.
**Phase**: Phase 2 (Expense Tracker v2)

## Architecture & Component Blueprint
- **3-Layer Screen Pattern**: Route (Navigation entry) -> Screen (State hoisting) -> Components (Stateless UI).
- **Material 3 Theme**: Leveraging M3 color schemes and typography, including Dark Mode support.
- **UI States**: Loading, Content, Empty, Error handled explicitly via a sealed interface.

## Step-by-Step Implementation Instructions
1. Define a `TransactionUiState` sealed interface representing the 4 UI states.
2. Implement the Material 3 Theme using `MaterialTheme`.
3. Create stateless composable components (e.g., `TransactionItem`).
4. Build the `TransactionScreen` that takes the UI state and event callbacks.
5. Create a `TransactionRoute` that collects state from the ViewModel and passes it to the screen.
6. Use `LazyColumn` for lists and ensure items have `key` definitions.

## Code Snippets & Signatures
```kotlin
sealed interface TransactionUiState {
    data object Loading : TransactionUiState
    data class Content(val transactions: List<Transaction>) : TransactionUiState
    data object Empty : TransactionUiState
    data class Error(val message: String) : TransactionUiState
}

@Composable
fun TransactionRoute(viewModel: TransactionViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TransactionScreen(
        state = uiState,
        onTransactionClick = viewModel::onTransactionSelected
    )
}

@Composable
fun TransactionList(transactions: List<Transaction>) {
    LazyColumn {
        items(
            items = transactions,
            key = { it.id.value } // Stable key
        ) { transaction ->
            TransactionItem(transaction)
        }
    }
}
```

## Deliberate Bugs to Catch & Debug
- **Bug**: Missing the `key` parameter in `LazyColumn` items, causing UI state bugs during reordering or deletions.
- **Bug**: Passing the ViewModel directly into reusable UI components instead of hoisting state.
- **Bug**: Hardcoding colors instead of using `MaterialTheme.colorScheme`.

## Unit Testing Requirements (Given-When-Then)
- **Given** an Error state **When** rendered **Then** the error message component is displayed.
- **Given** a Content state **When** rendered **Then** the LazyColumn displays the correct number of items.

## Acceptance Criteria Checklist
- [ ] 3-layer screen pattern is strictly followed.
- [ ] 4 discrete UI states are implemented and handled visually.
- [ ] `LazyColumn` utilizes stable keys.
- [ ] Compose Previews exist for all UI states and components.
