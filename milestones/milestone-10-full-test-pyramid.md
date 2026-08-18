# Milestone 10: Full Test Pyramid

## Title, Goal & Phase Alignment
**Goal:** Establish a robust CI/CD-ready testing suite spanning Unit, UI, and Screenshot tests.
**Phase:** Expense Tracker v9 - Quality Assurance

## Architecture & Component Blueprint
- **Unit Tests:** JUnit 5, Fakes instead of Mocks where possible.
- **Turbine:** For testing Kotlin `Flow` emissions sequentially.
- **Compose UI Tests:** Using `createComposeRule` and semantic testing via `Modifier.testTag`.
- **Roborazzi:** JVM-based fast screenshot testing to verify UI states visually without emulators.

## Step-by-Step Implementation Instructions
1. Replace Mockito with manual Fake repositories to improve test stability.
2. Implement Turbine for ViewModel state flow testing.
3. Write Compose UI tests asserting elements exist and perform actions (clicks, scrolling).
4. Configure Roborazzi in build.gradle.
5. Record golden screenshot references for various composable states (Dark/Light mode).
6. Verify CI pipeline executes JVM tests successfully.

## Code Snippets & Signatures
```kotlin
// Turbine test example
@Test
fun `load expenses emits loading then success`() = runTest {
    viewModel.uiState.test {
        assertEquals(UiState.Loading, awaitItem())
        assertEquals(UiState.Success(expectedList), awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}

// Compose UI Test
@Test
fun `expense item displays correct amount`() {
    composeTestRule.setContent { ExpenseItem(expense) }
    composeTestRule.onNodeWithText("$50.00").assertIsDisplayed()
}

// Roborazzi Screenshot Test
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class ExpenseItemScreenshotTest {
    @Test
    fun `capture expense item light theme`() {
        captureRoboImage("screenshots/expense_item_light.png") {
            ExpenseTrackerTheme(darkTheme = false) {
                ExpenseItem(expense)
            }
        }
    }
}
```

## Deliberate Bugs to Catch & Debug
- Ignoring Coroutine test dispatchers, causing flaky or hanging state flow tests.
- Not using `testTag` and relying on brittle string lookups for UI assertions.
- Running screenshot tests on different OS environments causing pixel mismatch failures.

## Unit Testing Requirements (Given-When-Then)
- **Given** a Fake repository returning an error, **When** ViewModel loads, **Then** UI state is Error via Turbine.
- **Given** a button with a testTag, **When** clicked in ComposeTestRule, **Then** the appropriate callback is fired.
- **Given** a verified UI state, **When** a visual regression occurs, **Then** Roborazzi fails the JVM test.

## Acceptance Criteria Checklist
- [ ] Fakes used for testing ViewModels.
- [ ] Coroutine Flows tested thoroughly with Turbine.
- [ ] UI tests written using `createComposeRule` and Semantics.
- [ ] Roborazzi integrated and golden images stored in version control.
