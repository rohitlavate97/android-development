package com.expensetracker.core.network

import com.expensetracker.core.common.Resource
import com.expensetracker.core.common.safeSuspendCall
import com.expensetracker.core.model.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.util.concurrent.TimeUnit

@Serializable
data class CategoryDto(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("icon_name") val iconName: String? = null,
    @SerialName("color_hex") val colorHex: String? = null,
    @SerialName("type") val type: String? = null
)

@Serializable
data class TransactionDto(
    @SerialName("id") val id: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("amount") val amount: Double? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("category") val category: CategoryDto? = null,
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("is_tax_deductible") val isTaxDeductible: Boolean? = null
)

interface ExpenseApiService {
    @GET("v1/transactions")
    suspend fun getTransactions(): Response<List<TransactionDto>>

    @POST("v1/transactions")
    suspend fun createTransaction(@Body transaction: TransactionDto): Response<TransactionDto>

    @DELETE("v1/transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: String): Response<Unit>
}

// Remote Data Source with defensive parsing (Phase 7)
interface ExpenseRemoteDataSource {
    suspend fun fetchTransactions(): Resource<List<Transaction>>
    suspend fun syncTransaction(transaction: Transaction): Resource<Transaction>
    suspend fun deleteTransaction(id: TransactionId): Resource<Unit>
}

class FakeExpenseRemoteDataSource : ExpenseRemoteDataSource {
    private val mockCategories = listOf(
        Category(CategoryId("cat_food"), "Food & Dining", "restaurant", "#FF5722", TransactionType.EXPENSE),
        Category(CategoryId("cat_salary"), "Salary", "payments", "#4CAF50", TransactionType.INCOME),
        Category(CategoryId("cat_tech"), "Tech & Electronics", "devices", "#2196F3", TransactionType.EXPENSE),
        Category(CategoryId("cat_travel"), "Travel", "flight", "#9C27B0", TransactionType.EXPENSE)
    )

    private val inMemoryRemoteDb = mutableListOf(
        Transaction(
            id = TransactionId("tx_1"),
            title = "Monthly Salary Deposit",
            amount = 4500.00,
            type = TransactionType.INCOME,
            category = mockCategories[1],
            accountId = AccountId("acc_main"),
            timestamp = Instant.fromEpochMilliseconds(1738000000000L),
            note = "Direct deposit tech corp"
        ),
        Transaction(
            id = TransactionId("tx_2"),
            title = "Organic Grocery Store",
            amount = 124.50,
            type = TransactionType.EXPENSE,
            category = mockCategories[0],
            accountId = AccountId("acc_main"),
            timestamp = Instant.fromEpochMilliseconds(1738100000000L),
            note = "Weekly groceries"
        ),
        Transaction(
            id = TransactionId("tx_3"),
            title = "New 4K Monitor",
            amount = 489.99,
            type = TransactionType.EXPENSE,
            category = mockCategories[2],
            accountId = AccountId("acc_main"),
            timestamp = Instant.fromEpochMilliseconds(1738200000000L),
            note = "Productivity setup"
        )
    )

    override suspend fun fetchTransactions(): Resource<List<Transaction>> = safeSuspendCall {
        inMemoryRemoteDb.toList()
    }

    override suspend fun syncTransaction(transaction: Transaction): Resource<Transaction> = safeSuspendCall {
        inMemoryRemoteDb.removeAll { it.id == transaction.id }
        inMemoryRemoteDb.add(0, transaction)
        transaction
    }

    override suspend fun deleteTransaction(id: TransactionId): Resource<Unit> = safeSuspendCall {
        inMemoryRemoteDb.removeAll { it.id == id }
        Unit
    }
}
