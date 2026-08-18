# PHASE 8 — LOCAL PERSISTENCE (Week 12)

**Objective:** Implement offline-first local storage, reactive database queries, and robust cache management using Room and DataStore.
**Why this phase matters:** Mobile apps must work seamlessly offline or in poor network conditions. Modern Android architecture treats the local database as the Single Source of Truth: the UI observes the local database, and network responses write to the database. Poor persistence design leads to data inconsistency, UI flickering, and catastrophic migration crashes on app update.
**Prerequisites:** Phase 1 (Kotlin), Phase 2 (Coroutines & Flow), Phase 5 (App Architecture), Phase 6 (Dependency Injection), Phase 7 (Networking).
**Project deliverable:** Expense Tracker v7 — Room database with reactive Flow queries, DataStore settings repository, and Single Source of Truth (SSOT) data pipeline.
**Concepts covered:** 9 total, each with the full 13-step teaching sequence.

---

## Concept 1: Storage Tool Selection on Android

### 1. What is it?
Android provides multiple discrete persistence mechanisms tailored to different data shapes: Jetpack DataStore (key-value/typed), Room (relational SQLite), App-Private Files (binary), and Android Keystore (cryptographic).

### 2. Why does it exist?
Because forcing the wrong data shape into the wrong tool causes catastrophic performance issues. Putting binary images into SQLite bloats the database, and putting complex relational data into a key-value store makes it impossible to query efficiently. 

### 3. Mental model
Think of data storage like a kitchen:
- **Room (Pantry):** For bulk, structured ingredients you need to organize and search (relational data, lists of expenses).
- **DataStore (Fridge door):** For quick access to small, frequently used items (user preferences, feature flags).
- **Filesystem (Chest freezer):** For large, bulky items (images, PDFs, binary blobs).
- **Keystore (Wall Safe):** For highly sensitive items (auth tokens, encryption keys).

### 4. How it works
- **Jetpack DataStore:** Replaces legacy `SharedPreferences`. Uses Coroutines and Flow for fully asynchronous, transactional reads/writes without blocking the UI thread.
- **Room:** An abstraction layer over Android's built-in SQLite database (conceptually similar to Spring Data JPA/Hibernate, but heavily optimized for mobile memory constraints).
- **Files:** Written to standard Linux directories scoped to your app's sandbox (`Context.filesDir`).
- **In-Memory `StateFlow`:** Lives in a Singleton component for session-scoped data that shouldn't survive process death.

### 5. Code
Instead of one tool, you inject specific tools for specific data:
```kotlin
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences> // For simple flags
)

class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao // For relational data
)

class ReceiptImageRepository @Inject constructor(
    @ApplicationContext private val context: Context // For file I/O
) {
    fun saveImage(bytes: ByteArray, filename: String) {
        File(context.filesDir, filename).writeBytes(bytes)
    }
}
```

### 6. Production usage
In an Expense Tracker, the user's `isDarkModeEnabled` goes into DataStore. The `Expense` list goes into Room. The receipt photo goes into the Filesystem (with the filename stored as a String column in the Room database).

### 7. Common mistakes
❌ **Wrong:** Storing a JSON-serialized list of 10,000 expenses into `SharedPreferences` or `DataStore`. It must all be loaded into memory at once to parse.
✅ **Right:** Storing those 10,000 expenses in Room, where you can query `LIMIT 50 OFFSET 0` efficiently.

### 8. Debugging
Use **Android Studio -> View -> Tool Windows -> Device File Explorer**.
Navigate to `data/data/com.your.package/` to inspect `files/`, `databases/`, and `datastore/` directly on an emulator.

### 9. Testing
Unit test Repositories by mocking the underlying Dao or DataStore interface. Do not test the storage tools themselves in unit tests.

### 10. Exercise
Map the following data requirements to the correct tool:
1. OAuth 2.0 Refresh Token
2. 500 cached Categories from an API
3. User's choice of "Start Week on Monday vs Sunday"
4. A PDF export of a monthly report

### 11. Deliberate failure
Try to save a `ByteArray` of a 5MB image into a Room database column. Observe the cursor window allocation crashes when trying to query that table.

### 12. Interview questions
- *Junior:* "What's the difference between Room and DataStore?"
- *Mid:* "Why does Google recommend DataStore over `SharedPreferences` for new code?" *(Note: `SharedPreferences` itself is not formally `@Deprecated` in the Android SDK — it still compiles without warnings — but Google's own architecture guidance has recommended against it since DataStore's stable release, because of the ANR/data-consistency issues covered in Concept 2.)*
- *Senior:* "How would you design storage for an offline-first app that handles both structured text data and heavy media files?"

### 13. Checkpoint
Do you understand that there is no "one size fits all" database on Android, and your architecture must actively split data across the filesystem, SQLite, and key-value stores?

---

## Concept 2: Jetpack DataStore Deep Dive

### 1. What is it?
A data storage solution that allows you to store key-value pairs (Preferences DataStore) or typed objects (Proto DataStore) asynchronously, consistently, and transactionally using Kotlin Coroutines and Flow.

### 2. Why does it exist?
The legacy `SharedPreferences` API was a notorious cause of ANRs (Application Not Responding crashes). Calling `sharedPrefs.edit().commit()` synchronously blocked the thread, and `sharedPrefs.edit().apply()` silently queued disk writes that could still freeze the UI during Activity lifecycle transitions.

### 3. Mental model
`SharedPreferences` is like a synchronous sticky note on your desk—you stop working to write it. `DataStore` is an asynchronous message queue—you dispatch a request to write it and immediately resume working, while the system handles the disk I/O safely in the background.

### 4. How it works
DataStore uses `DataStore<Preferences>` with predefined keys. Reads are exposed as a `Flow<Preferences>` that emits a new value every time the underlying file changes. Writes use a suspend function `edit { mutablePreferences -> ... }` which ensures thread-safe transactional updates.

### 5. Code
```kotlin
// 1. Define the DataStore (typically in a module or extension property)
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    // 2. Define strongly typed keys
    private val THEME_KEY = booleanPreferencesKey("dark_theme")

    // 3. Expose as a Flow (Read)
    val isDarkMode: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[THEME_KEY] ?: false // Default to false
        }

    // 4. Suspend function to write
    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[THEME_KEY] = enabled
        }
    }
}
```

### 6. Production usage
Managing user sessions, feature flags, UI toggle states (like dark mode, list vs. grid view), and storing simple authentication tokens securely (often combined with encryption at the storage layer).

> **[Extension] Note:** There is no official Jetpack "EncryptedDataStore" artifact analogous to `androidx.security.crypto.EncryptedSharedPreferences` — if you need encrypted key-value storage today, you either (a) keep using `EncryptedSharedPreferences` specifically for small sensitive values like tokens (still the standard, Google-blessed turnkey option, and what Phase 7's token-refresh `Authenticator` example relies on), or (b) layer your own encryption (e.g. via Tink, the library `androidx.security.crypto` itself wraps) on top of a Proto DataStore's serializer before bytes hit disk. Don't take "Encrypted DataStore" as a real off-the-shelf class name if you see it referenced elsewhere.

### 7. Common mistakes
❌ **Wrong:** Trying to read DataStore synchronously on the Main Thread using `runBlocking`. This defeats the entire purpose of DataStore and will crash or freeze your app.
```kotlin
// NEVER DO THIS
val isDark = runBlocking { dataStore.data.first()[THEME_KEY] }
```
✅ **Right:** Collecting the Flow in a ViewModel and updating a `StateFlow` for the UI to observe.

### 8. Debugging
Since DataStore uses Kotlin Coroutines, debugging involves standard Flow debugging (`onEach { Log.d(...) }`). Unlike SharedPreferences, DataStore files are stored as `.preferences_pb` protocol buffers, making them unreadable in plain text via the Device File Explorer.

### 9. Testing
Testing DataStore requires injecting a test instance backed by a temporary file:
```kotlin
val testDataStore = PreferenceDataStoreFactory.create(
    scope = TestScope(),
    produceFile = { File(testContext.filesDir, "test.preferences_pb") }
)
```

### 10. Exercise
Implement a `UserDataStore` that tracks whether the user has seen the onboarding screen (`has_seen_onboarding` Boolean flag). Write a ViewModel that observes this and exposes a `StateFlow<Boolean>`.

### 11. Deliberate failure
Attempt to call `dataStore.edit { }` from inside a synchronous function without a CoroutineScope. The compiler will immediately block you because `edit` is a `suspend` function.

### 12. Interview questions
- *Junior:* "How do you read a value from Preferences DataStore?"
- *Mid:* "Explain the difference between `Preferences DataStore` and `Proto DataStore`."
- *Senior:* "How does DataStore solve the threading and data consistency issues inherent in SharedPreferences?"

### 13. Checkpoint
Can you explain why DataStore reads are exposed as a `Flow` instead of returning a simple synchronous value?

---

## Concept 3: Room Database Fundamentals

### 1. What is it?
Room is an ORM (Object-Relational Mapping) library for Android that abstracts away raw SQLite commands. It is heavily inspired by Retrofit and Spring Data JPA.

### 2. Why does it exist?
Writing raw SQLite `Cursor` parsing is highly error-prone, verbose, and fails at runtime. Room provides **compile-time verification** of SQL queries. If you misspell a column name in a query, the app will not compile.

### 3. Mental model
If Retrofit maps JSON from a network API into Kotlin objects, Room maps SQLite rows from a local database into Kotlin objects. 
- `@Entity` = Database Table
- `@Dao` = SQL Query Interface (Spring Data `@Repository`)
- `@Database` = The Database Connection / Configuration

### 4. How it works
Room uses KSP (Kotlin Symbol Processing). At compile time, it reads your interfaces and generates the actual Java/Kotlin classes that execute `SQLiteOpenHelper` logic, manage thread safety, and map cursors to objects.

> **[Extension] Room + KSP schema export config nuance:** `MigrationTestHelper` (used in Concept 9) needs exported schema JSON files to know what earlier versions of your database looked like. Under the older `kapt` toolchain, you configured this via `android { defaultConfig { javaCompileOptions { annotationProcessorOptions { arg("room.schemaLocation", "$projectDir/schemas") } } } }`. Under KSP, that block is silently ignored — KSP arguments are configured separately via `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` at the top level of `build.gradle.kts`. A common migration-from-kapt bug is leaving the old `javaCompileOptions` block in place after switching Room to KSP: the build succeeds, but no schema JSON files are ever written, and `MigrationTestHelper`-based tests fail with a confusing "cannot find schema file" error instead of a build error.

### 5. Code
```kotlin
// 1. Entity (Table)
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val description: String,
    val timestamp: Instant // Requires a TypeConverter!
)

// 2. DAO (Data Access Object)
@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getExpenseById(expenseId: String): ExpenseEntity?
}

// 3. TypeConverters (Translating non-primitives)
class DateConverters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()
    
    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
}

// 4. Database definition
@Database(entities = [ExpenseEntity::class], version = 1)
@TypeConverters(DateConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
}
```

### 6. Production usage
The core persistence layer of modern Android apps. It acts as the local cache in an Offline-First / Single Source of Truth architecture.

### 7. Common mistakes
❌ **Wrong:** Leaking Room `@Entity` classes up to the UI layer. 
✅ **Right:** Mapping the `@Entity` to a pure Domain model (e.g., `Expense`) in the Repository layer before passing it to the ViewModel.

### 8. Debugging
Use the **App Inspection -> Database Inspector** tool in Android Studio while the app is running. You can view tables, run custom SQL commands live, and see updates in real-time.

### 9. Testing
Test DAOs using an in-memory database that clears itself after the process dies:
```kotlin
val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
```

### 10. Exercise
Create a Room database for `CategoryEntity`. Add a DAO with an `@Insert` and a `@Delete`. Write a query to fetch a category by its name.

### 11. Deliberate failure
Add a new property `val categoryId: String` to `ExpenseEntity` but DO NOT update the `@Database(version = 1)`. Run the app. It will crash with an `IllegalStateException` complaining about schema mismatch.

### 12. Interview questions
- *Junior:* "What are the three main components of Room?"
- *Mid:* "How do you store a custom object, like a `LocalDate` or an Enum, in a Room database?"
- *Senior:* "Explain the purpose of Room's `TypeConverters` and why they are necessary given SQLite's type affinity."

### 13. Checkpoint
Can you identify the difference between an `@Entity` class and a standard domain data class?

---

## Concept 4: Reactive Database Queries with Kotlin Flow

### 1. What is it?
Room DAOs can return `Flow<T>` instead of plain data. When returned as a Flow, Room automatically continuously monitors the SQLite table for any insertions, updates, or deletions, and emits the latest data.

### 2. Why does it exist?
In the old days, if you added an item to a database, you had to manually fire an event or callback to tell the UI to refresh. Reactive queries establish a **Single Source of Truth (SSOT)**. The UI simply observes the DB. If a background worker syncs new data from the network and saves it to Room, the UI updates instantly without any manual coordination.

### 3. Mental model
Imagine an Excel spreadsheet. A synchronous query is like copying data from cell A to cell B—it's a one-time snapshot. A reactive Flow query is like writing `=A1` in cell B. Whenever cell A changes, cell B updates automatically.

### 4. How it works
Under the hood, Room registers an `InvalidationTracker`. When a transaction completes that modifies a table watched by an active query, Room re-runs the SQL query on a background thread and emits the new cursor results down the Flow.

### 5. Code
```kotlin
@Dao
interface ExpenseDao {
    // ❌ Synchronous: one-time snapshot
    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesSnapshot(): List<ExpenseEntity>

    // ✅ Reactive: continuous stream. Note: NO 'suspend' keyword!
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun observeAllExpenses(): Flow<List<ExpenseEntity>>
    
    @Insert
    suspend fun insert(expense: ExpenseEntity)
}

// In Repository:
class ExpenseRepository(private val dao: ExpenseDao) {
    // Map the Entity to the Domain model reactively
    val expenses: Flow<List<Expense>> = dao.observeAllExpenses().map { entities ->
        entities.map { it.toDomain() }
    }
}
```

### 6. Production usage
Populating a `LazyColumn` (RecyclerView) in the UI. The UI collects the flow. Any time an expense is added, deleted, or synced from the API into Room, the list animates automatically.

### 7. Common mistakes
❌ **Wrong:** Using `suspend` with `Flow` in the DAO:
```kotlin
// INVALID: Room will throw a compile error
@Query("...")
suspend fun observeExpenses(): Flow<List<ExpenseEntity>>
```
*Why?* `Flow` already represents an asynchronous stream. Returning the Flow object itself is fast and doesn't block, so the function shouldn't be suspended. The suspension happens when you *collect* it.

### 8. Debugging
If a UI isn't updating automatically after an insert:
1. Ensure the UI is actively collecting the Flow (`collectAsStateWithLifecycle()`).
2. Ensure you didn't accidentally use the synchronous list `suspend fun getExpenses()`.
3. Check Database Inspector to verify the row actually inserted.

### 9. Testing
Use the `Turbine` testing library:
```kotlin
dao.observeAllExpenses().test {
    val initial = awaitItem()
    assertTrue(initial.isEmpty())
    
    dao.insert(ExpenseEntity(..., amountCents = 100))
    
    val updated = awaitItem()
    assertEquals(1, updated.size)
}
```

### 10. Exercise
Write a DAO query to observe the sum of all expenses dynamically: `SELECT SUM(amount_cents) FROM expenses`. Return it as a `Flow<Long>`.

### 11. Deliberate failure
Cancel the CoroutineScope collecting the flow, then insert a new item into the database. Observe that the flow stops emitting because its lifecycle ended.

### 12. Interview questions
- *Junior:* "Why doesn't a Room function returning a `Flow` use the `suspend` keyword?"
- *Mid:* "How does Room know when to emit a new value to a `Flow`?"
- *Senior:* "Explain the concept of Single Source of Truth in the context of network requests, Room, and the UI layer."

### 13. Checkpoint
Can you explain why reactive queries eliminate the need for an Event Bus or UI refresh callbacks?

---

## Concept 5: Entity Relations & Transactions

### 1. What is it?
Room's mechanism for handling SQL table relationships (1-to-1, 1-to-Many, Many-to-Many) using POJOs (Plain Old Java Objects) and the `@Relation` / `@Embedded` annotations, rather than deep object graphs.

### 2. Why does it exist?
Traditional ORMs (like Hibernate) use "lazy loading," magically fetching related data when you access a property (e.g., `account.expenses`). On Android, doing implicit database I/O on the main thread causes UI stuttering. Room deliberately forbids lazy loading. You must explicitly query the relationships you want.

### 3. Mental model
Instead of objects pointing to other objects recursively, Room makes you define specific "View Models" for the database. If you want an Account and its Expenses, you don't put a `List<Expense>` inside the `AccountEntity`. You create a distinct class `AccountWithExpenses` specifically meant to hold the results of a JOIN.

### 4. How it works
- `@Embedded`: Flattens a nested Kotlin object into the same SQL table.
- `@Relation`: Automatically executes a second SQL query under the hood to fetch child records that match a parent key.
- `@Transaction`: Ensures that the parent query and the subsequent child queries happen atomically without interference.

### 5. Code
```kotlin
// 1. Entities
@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val categoryId: String, // Foreign Key concept
    val amount: Long
)

// 2. The Relation POJO (NOT an Entity!)
data class CategoryWithExpenses(
    @Embedded val category: CategoryEntity,
    @Relation(
        parentColumn = "id",        // ID in CategoryEntity
        entityColumn = "categoryId" // ID in ExpenseEntity
    )
    val expenses: List<ExpenseEntity>
)

// 3. DAO
@Dao
interface CategoryDao {
    // Requires @Transaction because Room executes two queries: 
    // 1. Get Categories. 2. Get Expenses for those Categories.
    @Transaction 
    @Query("SELECT * FROM categories")
    fun observeCategoriesWithExpenses(): Flow<List<CategoryWithExpenses>>
}
```

### 6. Production usage
Displaying a dashboard where you show a list of Expense Categories, and inside each category row, a nested list of the specific expenses. 

### 7. Common mistakes
❌ **Wrong:** Putting a `@Relation` property directly inside an `@Entity` class.
✅ **Right:** Keeping `@Entity` classes completely flat (matching the SQLite schema exactly) and creating separate data classes for relations.

### 8. Debugging
If your `@Relation` list is coming back empty but data exists in the database, double-check your `parentColumn` and `entityColumn` names. Room matches these exactly, and silent failures occur if the keys don't align.

### 9. Testing
Insert a Category, insert three Expenses referencing that Category's ID. Query the relation class and assert that `categoryWithExpenses.expenses.size == 3`.

### 10. Exercise
Implement an `AccountEntity` and an `ExpenseEntity`. Write a relation `AccountWithExpenses` and ensure the DAO returns it correctly using `@Transaction`.

### 11. Deliberate failure
Remove the `@Transaction` annotation from the Dao method. The project will still compile, but Lint will output a severe warning: `The return value includes a POJO with a @Relation. It is usually desired to annotate this method with @Transaction to avoid possibility of inconsistent results.`

### 12. Interview questions
- *Junior:* "What is the difference between `@Embedded` and `@Relation`?"
- *Mid:* "Why does Room require the `@Transaction` annotation when returning a POJO containing a `@Relation`?"
- *Senior:* "How do you enforce Foreign Key constraints in Room, and how does `onDelete = CASCADE` impact related entities?"

### 13. Checkpoint
Do you understand why Room forces you to create new, separate classes to handle relationships instead of allowing infinite object nesting inside your Entities?


---

## 6. Single Source of Truth (SSOT) & Network-Bound Resource

### 1. What is it
The **Single Source of Truth (SSOT)** pattern states that a particular state or data element should be stored in exactly one place, and all consumers read from that single location. In offline-first Android apps, the local Room database is the SSOT. The **Network-Bound Resource** pattern describes the synchronization pipeline: the UI strictly observes the local database, while the repository fetches from the network and saves *only* to the local database, never passing network models directly to the UI.

### 2. Why does it exist
To prevent "split brain" architecture. If your UI sometimes shows data from a network response and sometimes from the database, the app will flicker, show duplicate loading spinners, or crash if offline. SSOT guarantees that the UI always renders exactly what is on disk, ensuring immediate startup (cache display) and seamless background updates.

### 3. Mental model
Think of a **water reservoir (Database)** supplying a **city (UI)**. 
Rain **(Network API)** doesn't fall directly onto people's kitchen sinks; it falls into the reservoir. The city's plumbing is permanently connected to the reservoir. When it rains, the reservoir level rises, and the sinks get more water automatically. If there's a drought (offline), the sinks still run using whatever water is left in the reservoir.

### 4. How it works
1. The **ViewModel** requests a `Flow<List<DomainModel>>` from the **Repository**.
2. The **Repository** returns a Flow directly from the **Room DAO**. This Flow emits the current cache immediately.
3. The **ViewModel** launches a background coroutine calling `repository.refresh()`.
4. `refresh()` fetches data from the **Remote API (Retrofit)**.
5. If successful, `refresh()` transforms the DTOs into Entities and saves them to **Room**.
6. Because Room tables are observed via `Flow`, the database change automatically triggers an emission to the ViewModel, which updates the UI.

### 5. Code
```kotlin
// 1. The DAO observing the SSOT
@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun observeAllExpenses(): Flow<List<ExpenseEntity>> // Never suspend for Flow!

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)
}

// 2. The Repository enforcing the pattern
class ExpenseRepositoryImpl(
    private val api: ExpenseApi,
    private val dao: ExpenseDao
) : ExpenseRepository {

    // The UI ONLY reads from this stream
    override fun getExpensesStream(): Flow<List<Expense>> {
        return dao.observeAllExpenses().map { entities ->
            entities.map { it.toDomain() } // Map Entity -> Domain model
        }
    }

    // The Network ONLY writes to the database
    override suspend fun refreshExpenses() {
        try {
            val remoteData = api.fetchExpenses() // DTOs
            val entities = remoteData.map { it.toEntity() }
            dao.insertAll(entities) 
            // We do NOT return the data here!
        } catch (e: IOException) {
            // Log or notify, but UI still shows cached data
            Log.e("Sync", "Offline, showing cache", e)
        }
    }
}
```

### 6. Production usage
Every modern app (Twitter, Instagram, Gmail). When you open Gmail, you instantly see your cached emails. In the background, it fetches new emails, writes them to SQLite, and the UI dynamically inserts the new rows without blocking your scrolling.

### 7. Common mistakes
**The Split-Brain Mistake (Wrong way):**
```kotlin
// BAD: UI doesn't know whether to show 'localFlow' or the result of 'fetchRemote'
fun getExpenses(): Flow<List<Expense>> = flow {
    emit(dao.getAll()) // Local
    val remote = api.fetch()
    dao.insert(remote)
    emit(remote) // BAD: Bypassing SSOT!
}
```

### 8. Debugging
If the UI doesn't update after a network call:
1. Verify the Retrofit call succeeded (check Logcat/Network Profiler).
2. Verify Room `insert` actually replaced/added rows (App Inspection -> Database Inspector).
3. Verify the DAO method returns a `Flow` and the ViewModel is actively collecting it.

### 9. Testing
Test that the repository handles network failures gracefully by emitting local data, and that network successes correctly map and insert to the DAO.

### 10. Exercise
Refactor a naive "fetch from API and return to UI" repository into an SSOT pattern. Create an `observe()` method that returns `Flow` from Room, and a separate `sync()` method that fetches from the mock API and inserts to Room.

### 11. Deliberate failure
Make the DAO `observe()` function return `List<ExpenseEntity>` instead of `Flow<List<ExpenseEntity>>`, and try to wire it up in the ViewModel. Observe how the UI requires manual pulling to update after a sync, destroying the reactive paradigm.

### 12. Interview questions
- **Junior:** What is the Single Source of Truth in Android architecture?
- **Mid:** Explain the data flow when a user opens an offline-first app and later regains connectivity.
- **Senior:** If your network returns a paginated list of 50 items out of 1000, how do you merge this into your local Room SSOT without wiping out the other 950 items?

### 13. Checkpoint
You understand SSOT when you can confidently say: "My UI never cares if the phone is offline. It just renders whatever is in SQLite. Syncing is an entirely separate background process."

---

## 7. Cache Invalidation, Synchronization & Optimistic Updates

### 1. What is it
**Cache Invalidation** determines when local data is too old and must be re-fetched. **Synchronization** coordinates background network calls. **Optimistic Updates** modify the local UI immediately (assuming network success) before the server confirms the change, making the app feel instantly responsive.

### 2. Why does it exist
If you only ever read from cache, data becomes stale. If you fetch from the network on every screen load, you waste bandwidth and battery. Optimistic updates exist because users hate waiting 500ms for a network round-trip just to see an "Expense Added" checkmark.

### 3. Mental model
**Optimistic Update:** It's like paying with a credit card at a restaurant. You sign the receipt and leave immediately (Optimistic UI update). You assume the bank will settle the transaction later. If the transaction eventually bounces (Network Error), the restaurant has to hunt you down (UI Rollback / Error State).

### 4. How it works
- **Staleness TTL (Time-To-Live):** Store a timestamp in DataStore when a sync completes. Next time, check `currentTime - lastSyncTime > 5_MINUTES`.
- **Optimistic Update:** 
  1. User clicks "Save".
  2. Insert item to Room instantly with `sync_status = PENDING`.
  3. UI updates instantly via Flow.
  4. Make API call.
  5. On success, update Room row to `sync_status = SYNCED`. On failure, delete row or mark `sync_status = ERROR` (showing a retry button).

### 5. Code
```kotlin
// Optimistic Update Implementation
enum class SyncStatus { PENDING, SYNCED, ERROR }

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val amount: Double,
    val syncStatus: SyncStatus
)

class ExpenseRepositoryImpl(...) {
    suspend fun addExpenseOptimistically(expense: Expense) {
        val tempId = UUID.randomUUID().toString()
        val pendingEntity = expense.toEntity(id = tempId, status = SyncStatus.PENDING)
        
        // 1. Immediate local write (UI updates instantly)
        dao.insert(pendingEntity)

        try {
            // 2. Network call
            val serverId = api.createExpense(expense.toDto())
            
            // 3. Confirm success
            dao.updateStatusAndId(oldId = tempId, newId = serverId, status = SyncStatus.SYNCED)
        } catch (e: Exception) {
            // 4. Rollback or mark error
            dao.updateStatus(id = tempId, status = SyncStatus.ERROR)
        }
    }
}
```

### 6. Production usage
WhatsApp messaging. When you send a message, it appears in the chat bubble immediately with a "clock" icon (Pending). Once it hits the server, it gets a "single tick" (Synced). If airplane mode is on, it stays as a clock until reconnected.

### 7. Common mistakes
Updating the local database *after* waiting for the network call.
```kotlin
// WRONG: UI blocks and shows spinner while waiting for network
suspend fun addExpense(expense: Expense) {
    val result = api.post(expense) // User waits...
    dao.insert(result)             // UI finally updates
}
```

### 8. Debugging
If optimistic updates result in duplicates, check your IDs. Often, local items use temporary UUIDs, and the server generates its own database IDs. Ensure you use an `upsert` or delete the local temporary row when the server row arrives.

### 9. Testing
Mock the network API to throw an `IOException`. Call the optimistic insert function. Verify that the local database briefly holds the item, and then correctly reflects the `ERROR` state after the network failure.

### 10. Exercise
Implement a "Favorite" toggle for an expense. When clicked, instantly toggle the star icon locally, fire the API call in the background, and revert the local star if the API returns a 500 Server Error.

### 11. Deliberate failure
Forget to catch exceptions during the network call of an optimistic update. The app crashes, the local DB remains in a permanently `PENDING` state, and the user cannot recover.

### 12. Interview questions
- **Junior:** Why do apps cache data instead of fetching it live every time?
- **Mid:** Explain the concept of an Optimistic Update. What are the risks?
- **Senior:** How would you design a robust background sync system for an app that needs to upload offline-created records when the app is completely closed? (Hint: WorkManager).

### 13. Checkpoint
You understand optimistic updates when you realize the UI is just reflecting the local database state (`PENDING` vs `SYNCED`), and the network layer is just silently managing those status flags.

---

## 8. Room Migrations & Schema Evolution

### 1. What is it
When you change the structure of a Room `@Entity` (e.g., adding a column), the underlying SQLite schema changes. **Migrations** are scripts or automated rules that tell SQLite how to transform the old database table into the new one without deleting the user's existing data.

### 2. Why does it exist
If you update an app on the Play Store with a new database schema and don't provide a migration path, Room will crash with `IllegalStateException: Room cannot verify the data integrity`. If you use `fallbackToDestructiveMigration()`, Room will drop all tables and recreate them—wiping out all user data.

### 3. Mental model
Imagine you manage a physical library (SQLite). You decide to add a "Genre" label to every book's index card (Schema update).
- **Destructive Migration:** Burn down the library and build a new one. (Fast, but disastrous).
- **Migration Script:** Go through every single existing index card and add a blank space for "Genre". (Safe, preserves books).

### 4. How it works
- Increase the `@Database(version = 2)` number.
- **AutoMigration:** Room automatically generates SQLite `ALTER TABLE` scripts for simple additions (new columns, new tables).
- **Manual Migration:** Provide a `Migration` object executing raw SQLite for complex changes (renaming columns, changing data types).

### 5. Code
These are **two alternative** ways to handle the exact same schema change — pick one per version transition. Declaring both an `AutoMigration` and a manual `Migration` for the *same* `(from, to)` pair is a real conflict Room will reject at runtime with a duplicate-migration error, so never combine them for one version bump.

```kotlin
// Changing the Entity
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: Int,
    val amount: Double,
    // Added field in v2
    @ColumnInfo(defaultValue = "0") val isTaxDeductible: Boolean 
)

// --- OPTION A: AutoMigration (Preferred for simple additions like this one) ---
@Database(
    entities = [ExpenseEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class AppDatabase : RoomDatabase()

Room.databaseBuilder(context, AppDatabase::class.java, "app.db").build()
// Nothing else needed — Room reads the @ColumnInfo(defaultValue) and
// generates the ALTER TABLE for you because it's just a simple additive column.

// --- OPTION B: Manual Migration (Required for renames, type changes, splits/merges) ---
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN isTaxDeductible INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [ExpenseEntity::class], version = 2) // no autoMigrations here
abstract class AppDatabase : RoomDatabase()

Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
    .addMigrations(MIGRATION_1_2)
    .build()
```

### 6. Production usage
Every production app that stores data locally goes through dozens of schema versions over its lifetime. Shipping without proper migration handling is one of the most common causes of 1-star reviews ("Update erased all my saved data!").

### 7. Common mistakes
- **Relying on `fallbackToDestructiveMigration()` in production.** This is acceptable *only* if your app is a 100% online app where the DB is strictly a cache and data can be re-downloaded perfectly. For user-generated local data, it's catastrophic.
- **Forgetting `defaultValue`** when adding a `NOT NULL` column. SQLite needs to know what value to assign to existing rows.

### 8. Debugging
If the app crashes on launch after an update:
1. Read the Logcat. Room provides very explicit errors: `Expected: TableInfo{...} Found: TableInfo{...}`. Compare the two to see exactly which column or constraint doesn't match.
2. Ensure you exported the schemas (`room.schemaLocation`).

### 9. Testing
Use `MigrationTestHelper`. It requires you to export your DB schema as JSON files. It creates a v1 DB, allows you to insert test data, runs your migration to v2, and allows you to assert the data survived.

### 10. Exercise
Add a `receiptImageUrl` column to your `ExpenseEntity`. Increment the database version to 2. Implement an `AutoMigration` to handle the schema change. Run the app and verify existing expenses don't disappear.

### 11. Deliberate failure
Add a new column `notes: String` to an Entity, bump the version to 2, but do not provide an AutoMigration or Migration script. Do not use destructive migration. Run the app over a previous install. Watch the instant crash on launch.

### 12. Interview questions
- **Junior:** What happens if you change a Room entity but forget to update the database version?
- **Mid:** When is it acceptable to use `fallbackToDestructiveMigration()`?
- **Senior:** You need to rename a column in SQLite, but SQLite doesn't support `ALTER TABLE RENAME COLUMN` in older versions. How do you write a manual Room migration for this? (Answer: Create a new temp table, copy data, drop old table, rename temp table).

### 13. Checkpoint
You understand Room Migrations when you treat changing a `@Entity` property not just as a Kotlin refactor, but as a rigid structural change that requires explicit SQL conversion rules.

---

## 9. Testing Local Persistence & Schema Migrations

### 1. What is it
Testing the data layer involves verifying that DAOs execute queries correctly, Room databases survive schema upgrades without data loss, and repositories correctly merge network and local data streams.

### 2. Why does it exist
SQL syntax errors inside `@Query` are caught at compile-time by Room, but logic errors (e.g., sorting descending instead of ascending, or writing a bad `WHERE` clause) are not. Migration failures lead to catastrophic user data loss, making automated migration tests critical.

### 3. Mental model
Testing persistence is like **running a fire drill in an empty building**. 
Instead of risking the real, permanent database on the physical disk, you spin up a temporary "In-Memory" building. You start fires (insert data), test the sprinklers (run queries), and when the test is over, the building vanishes completely.

### 4. How it works
- **In-Memory Database:** Use `Room.inMemoryDatabaseBuilder()`. It creates an SQLite instance entirely in RAM. It is instantly fast and destroyed the moment the process ends.
- **Turbine:** A testing library by CashApp designed specifically for testing Kotlin `Flow`. It allows you to assert sequential emissions (`awaitItem()`).
- **MigrationTestHelper:** Reads the generated Room JSON schema files to accurately recreate older database versions for testing.

### 5. Code
```kotlin
// 1. DAO Test
@RunWith(AndroidJUnit4::class)
class ExpenseDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ExpenseDao

    @Before
    fun setup() {
        // Ephemeral in-memory DB
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build() // Allowed ONLY in tests
        dao = db.expenseDao()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun observeExpenses_emitsNewDataOnInsert() = runTest {
        // Using Turbine to test Flow
        dao.observeAllExpenses().test {
            // Initial state is empty
            assertTrue(awaitItem().isEmpty())

            // Insert item
            val expense = ExpenseEntity("1", 100.0)
            dao.insert(expense)

            // Flow automatically emits new list!
            val updatedList = awaitItem()
            assertEquals(1, updatedList.size)
            assertEquals(100.0, updatedList[0].amount)
        }
    }
}
```

### 6. Production usage
All stable Android projects maintain a robust suite of DAO tests. Because DAOs don't have complex dependencies (they just need SQLite), they are highly reliable and rarely flake, providing excellent ROI for test coverage.

### 7. Common mistakes
- **Testing against a real on-disk DB.** Tests will interfere with each other because data persists between test runs.
- **Forgetting `.allowMainThreadQueries()` in the test builder.** Coroutine test dispatchers often run on a single thread, causing Room to throw exceptions unless explicitly told main-thread access is fine for testing.

### 8. Debugging
If a Turbine flow test hangs indefinitely, it means you called `awaitItem()` but the `Flow` never emitted. Ensure your Room DAO method actually returns `Flow` and that the insertion logic fired successfully.

### 9. Testing
(The entire concept is about testing!)

### 10. Exercise
Write an instrumented test for your `ExpenseDao`. Test a query that retrieves expenses for a specific date range. Insert three expenses on different dates, run the query, and assert that only the correct expenses are returned.

### 11. Deliberate failure
Run a DAO test without `Room.inMemoryDatabaseBuilder()` (use `Room.databaseBuilder()` instead). Run the test twice. Observe how the second test fails because the data from the first test is still lingering on the device's disk.

### 12. Interview questions
- **Junior:** How do you test a Room DAO without permanently modifying the app's real database?
- **Mid:** How do you assert multiple sequential emissions from a Room `Flow` in a unit test?
- **Senior:** Explain how `MigrationTestHelper` works under the hood. Why does it require `room.schemaLocation` JSON files?

### 13. Checkpoint
You understand persistence testing when you can effortlessly spin up an in-memory database, insert mock entities, and verify complex SQL queries and reactive Flow emissions.

---

## Phase 8 Project — Expense Tracker v7 (Offline-First Persistence)

**Goal:** Transform the Expense Tracker into an offline-first app powered by Room and DataStore.

**Requirements:**
1. **DataStore Settings Repository:**
   - Store user preferences: `selectedCurrency` (USD/EUR/INR), `themeMode` (LIGHT/DARK/SYSTEM), and `lastSyncTimestamp`.
2. **Room Database Implementation:**
   - `ExpenseEntity` and `CategoryEntity` tables with foreign keys and index on `date`.
   - `ExpenseDao` with reactive `observeExpenses(): Flow<List<ExpenseEntity>>` and CRUD `suspend` functions.
   - TypeConverters for `Instant` and `CategoryType`.
3. **Offline-First Repository (SSOT):**
   - `ExpenseRepositoryImpl` returning `Flow<List<Expense>>` directly from Room.
   - `refreshExpenses()` function that fetches from the remote API, inserts into Room in a single transaction, and handles network errors gracefully without disturbing the UI flow.
4. **Migration & Unit Testing:**
   - Add a new field `isTaxDeductible: Boolean` to `ExpenseEntity` and write a Migration from v1 to v2.
   - Unit tests for `ExpenseDao` using `inMemoryDatabaseBuilder`.
   - Migration test using `MigrationTestHelper`.

---

## Phase 8 Checkpoint

Answer without looking:
1. Draw or trace the exact data flow for: "User opens the app while in Airplane mode, views existing expenses, connects to Wi-Fi, and the screen automatically updates with new remote expenses" — explaining why the UI does not flicker or show duplicate spinners.
2. Why is marking a Room DAO function that returns `Flow<List<T>>` with the `suspend` keyword an error?
3. What is the disaster scenario that occurs if an app ships to the Google Play Store with `fallbackToDestructiveMigration()` enabled and a missing migration script?
4. How does `PreferencesDataStore` guarantee safety against Android Main-Thread ANRs compared to legacy `SharedPreferences.apply()` or `.commit()`?
5. Why should in-memory databases (`Room.inMemoryDatabaseBuilder`) be used for unit tests rather than testing against real SQLite on a physical disk?

---

## Complete SQL / JPA / Backend → Android Room & DataStore Translation Table

| Backend / JPA / SQL Concept | Android Room / DataStore Equivalent | Notes |
|---|---|---|
| `@Entity` / `@Table` (Hibernate) | `@Entity(tableName = "expenses")` | SQLite table definition |
| `@Id` / `@GeneratedValue` | `@PrimaryKey(autoGenerate = true)` | Primary key specification |
| Spring Data `@Query("SELECT...")` | Room `@Query("SELECT...")` | Compile-time SQL validation |
| Hibernate `@OneToMany` / `@ManyToOne` | `@Relation` + `@Embedded` (in Room) | Non-blocking relationship queries |
| `@Transactional` (Spring) | `@Transaction` (Room DAO method) | Atomic SQLite transaction |
| Liquibase / Flyway migrations | Room `Migration(from, to)` / `AutoMigration` | Structured schema upgrades |
| `application.properties` / Spring Cloud Config | Jetpack DataStore (Preferences / Proto) | Async reactive configuration store |
| H2 In-Memory DB (for unit tests) | `Room.inMemoryDatabaseBuilder()` | Ephemeral DB destroyed when process ends |
