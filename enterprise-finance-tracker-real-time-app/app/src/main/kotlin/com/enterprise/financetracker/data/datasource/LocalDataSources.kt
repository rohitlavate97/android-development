package com.enterprise.financetracker.data.datasource

import com.enterprise.financetracker.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface TransactionLocalDataSource {
    fun observeTransactions(): Flow<List<TransactionDto>>
    suspend fun getTransactionById(id: String): TransactionDto?
    suspend fun insertTransaction(dto: TransactionDto)
    suspend fun deleteTransaction(id: String)
}

class InMemoryTransactionLocalDataSource : TransactionLocalDataSource {
    private val sampleCategorySalary = CategoryDto("cat_salary", "Salary", "payments", "#4CAF50", true)
    private val sampleCategoryFood = CategoryDto("cat_food", "Food & Dining", "restaurant", "#FF5722", true)
    private val sampleCategoryShopping = CategoryDto("cat_shopping", "Electronics", "shopping_cart", "#2196F3", true)
    private val sampleCategoryHousing = CategoryDto("cat_housing", "Housing Rent", "home", "#9C27B0", true)

    private val _storage = MutableStateFlow<List<TransactionDto>>(
        listOf(
            TransactionDto("tx_1", "acc_checking", "Tech Corp Bi-weekly Paycheck", 4250.00, "INCOME", sampleCategorySalary, 1738000000000L),
            TransactionDto("tx_2", "acc_checking", "Whole Foods Organic Market", 142.80, "EXPENSE", sampleCategoryFood, 1738100000000L),
            TransactionDto("tx_3", "acc_checking", "Dell UltraSharp 4K Monitor", 499.99, "EXPENSE", sampleCategoryShopping, 1738200000000L),
            TransactionDto("tx_4", "acc_checking", "Monthly Apartment Rent", 1800.00, "EXPENSE", sampleCategoryHousing, 1738300000000L, isRecurring = true)
        )
    )

    override fun observeTransactions(): Flow<List<TransactionDto>> = _storage.asStateFlow()

    override suspend fun getTransactionById(id: String): TransactionDto? = _storage.value.find { it.id == id }

    override suspend fun insertTransaction(dto: TransactionDto) {
        _storage.update { current -> listOf(dto) + current }
    }

    override suspend fun deleteTransaction(id: String) {
        _storage.update { current -> current.filterNot { it.id == id } }
    }
}
