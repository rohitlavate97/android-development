package com.expensetracker.core.database

import androidx.room.*
import com.expensetracker.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val type: String
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"]), Index(value = ["timestamp"])]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val amount: Double,
    val type: String,
    val categoryId: String,
    val accountId: String,
    val timestampEpochMillis: Long,
    val note: String?,
    val isTaxDeductible: Boolean
)

data class TransactionWithCategory(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: CategoryEntity
)

class DatabaseConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? = value?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun dateToTimestamp(date: Instant?): Long? = date?.toEpochMilliseconds()
}

@Dao
interface TransactionDao {
    @Transaction
    @Query("SELECT * FROM transactions ORDER BY timestampEpochMillis DESC")
    fun observeAllTransactions(): Flow<List<TransactionWithCategory>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): TransactionWithCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)
}

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val DATABASE_NAME = "expense_tracker_db"
    }
}

// Mappers (Phase 5: Mappers at every boundary)
fun CategoryEntity.toDomain(): Category = Category(
    id = CategoryId(id),
    name = name,
    iconName = iconName,
    colorHex = colorHex,
    type = TransactionType.valueOf(type)
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id.value,
    name = name,
    iconName = iconName,
    colorHex = colorHex,
    type = type.name
)

fun TransactionWithCategory.toDomain(): Transaction = Transaction(
    id = TransactionId(transaction.id),
    title = transaction.title,
    amount = transaction.amount,
    type = TransactionType.valueOf(transaction.type),
    category = category.toDomain(),
    accountId = AccountId(transaction.accountId),
    timestamp = Instant.fromEpochMilliseconds(transaction.timestampEpochMillis),
    note = transaction.note,
    isTaxDeductible = transaction.isTaxDeductible
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id.value,
    title = title,
    amount = amount,
    type = type.name,
    categoryId = category.id.value,
    accountId = accountId.value,
    timestampEpochMillis = timestamp.toEpochMilliseconds(),
    note = note,
    isTaxDeductible = isTaxDeductible
)
