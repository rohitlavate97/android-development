# Milestone 8: Offline-First Persistence

## Title, Goal & Phase Alignment
**Goal:** Guarantee app functionality without internet connectivity by implementing a Single Source of Truth offline-first architecture.
**Phase:** Expense Tracker v7 - Local Storage

## Architecture & Component Blueprint
- **Room Database:** Local SQLite abstraction.
- **Entities:** `@Entity` data classes representing schema.
- **DAOs:** `@Dao` interfaces exposing reactive `Flow` queries.
- **TypeConverters:** To map `java.time.Instant` to Long.
- **Preferences DataStore:** For user settings (theme, currency).
- **Repository:** Combines Room and Retrofit, exposing local data immediately while fetching remote updates.

## Step-by-Step Implementation Instructions
1. Add Room dependencies and KSP for annotation processing.
2. Define `@Entity` `ExpenseEntity` and create the `ExpenseDao` with `Flow<List<ExpenseEntity>>`.
3. Create `InstantConverter` using `@TypeConverter`.
4. Construct the `AppDatabase`.
5. Implement `OfflineFirstExpenseRepository`: observe DB `Flow`, trigger API call, save API result back to DB.
6. Write Room migration tests.

## Code Snippets & Signatures
```kotlin
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val amount: BigDecimal,
    val date: Instant
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)
}

class InstantConverter {
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()
    
    @TypeConverter
    fun toInstant(millis: Long?): Instant? = millis?.let { Instant.ofEpochMilli(it) }
}
```

## Deliberate Bugs to Catch & Debug
- Running Room database queries on the Main thread without Coroutines.
- Missing `@TypeConverter` causing compilation to fail with KSP errors.
- Returning a `List<ExpenseEntity>` from Dao instead of `Flow<List<ExpenseEntity>>`, losing reactivity.

## Unit Testing Requirements (Given-When-Then)
- **Given** new data from the API, **When** repository fetches, **Then** data is inserted into Room and UI automatically updates via Flow.
- **Given** an offline state, **When** repository is queried, **Then** cached Room data is emitted immediately.
- **Given** a schema version change, **When** Migration runs, **Then** data is preserved.

## Acceptance Criteria Checklist
- [ ] Room DB set up with appropriate Entities and DAOs.
- [ ] `Instant` mapped correctly using TypeConverters.
- [ ] Repository implements Offline-First pattern (Single Source of Truth).
- [ ] Jetpack Preferences DataStore manages local user settings.
- [ ] Schema migration tests are implemented.
