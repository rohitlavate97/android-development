# Milestone 1: Kotlin Domain Model

## Title, Goal & Phase Alignment
**Goal**: Establish a pure Kotlin domain model focusing on entities, value objects, and business rules without any framework dependencies.
**Phase**: Phase 1 (Expense Tracker v1)

## Architecture & Component Blueprint
- **Pure Kotlin Domain**: The domain layer has zero Android or framework dependencies (no `android.*` or third-party libraries).
- **Entities**: `Transaction`, `Category`, `Account`, `Budget`.
- **Value Classes**: `@JvmInline value class` for type-safe IDs to prevent accidental ID swapping.
- **Sealed Interfaces**: Used for restricted class hierarchies like `TransactionType`.

## Step-by-Step Implementation Instructions
1. Create a `domain` package.
2. Define value classes for `TransactionId`, `CategoryId`, `AccountId`.
3. Create a `TransactionType` sealed interface with `Income` and `Expense` data objects.
4. Implement the `Transaction`, `Category`, `Account`, and `Budget` data classes.
5. Add validation logic inside the `init` blocks of your entities to enforce business invariants (e.g., amount > 0).

## Code Snippets & Signatures
```kotlin
@JvmInline
value class TransactionId(val value: String)

sealed interface TransactionType {
    data object Income : TransactionType
    data object Expense : TransactionType
}

data class Transaction(
    val id: TransactionId,
    val amount: BigDecimal,
    val type: TransactionType,
    val categoryId: CategoryId,
    val timestamp: Instant
) {
    init {
        require(amount > BigDecimal.ZERO) { "Transaction amount must be strictly positive" }
    }
}
```

## Deliberate Bugs to Catch & Debug
- **Bug**: Using `Double` instead of `BigDecimal` for currency, leading to precision loss.
- **Bug**: Forgetting the `@JvmInline` annotation on value classes, creating unnecessary object allocations.
- **Bug**: Missing validation in `init` blocks allowing negative expense amounts.

## Unit Testing Requirements (Given-When-Then)
- **Given** a negative amount **When** creating a `Transaction` **Then** an `IllegalArgumentException` is thrown.
- **Given** valid properties **When** creating an entity **Then** the object is successfully instantiated with the correct properties.

## Acceptance Criteria Checklist
- [ ] No `android.*` imports exist in the domain module.
- [ ] IDs are strictly typed using `@JvmInline value class`.
- [ ] Exhaustive `when` statements can be used on `TransactionType`.
- [ ] All business rules are validated upon instantiation.
- [ ] Domain unit tests pass.
