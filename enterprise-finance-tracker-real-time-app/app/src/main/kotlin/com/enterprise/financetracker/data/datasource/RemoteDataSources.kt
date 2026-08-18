package com.enterprise.financetracker.data.datasource

import com.enterprise.financetracker.core.network.safeApiCall
import com.enterprise.financetracker.data.network.api.FinanceApiService
import com.enterprise.financetracker.data.network.model.NetworkTransactionDto
import com.enterprise.financetracker.domain.model.FinancialResult

interface TransactionRemoteDataSource {
    suspend fun fetchTransactions(): FinancialResult<List<NetworkTransactionDto>>
    suspend fun fetchTransactionById(id: String): FinancialResult<NetworkTransactionDto>
    suspend fun createTransaction(dto: NetworkTransactionDto): FinancialResult<NetworkTransactionDto>
    suspend fun deleteTransaction(id: String): FinancialResult<Unit>
}

class RetrofitTransactionRemoteDataSource(
    private val apiService: FinanceApiService
) : TransactionRemoteDataSource {

    override suspend fun fetchTransactions(): FinancialResult<List<NetworkTransactionDto>> {
        return safeApiCall {
            apiService.getTransactions()
        }
    }

    override suspend fun fetchTransactionById(id: String): FinancialResult<NetworkTransactionDto> {
        return safeApiCall {
            apiService.getTransactionById(id)
        }
    }

    override suspend fun createTransaction(dto: NetworkTransactionDto): FinancialResult<NetworkTransactionDto> {
        return safeApiCall {
            apiService.createTransaction(dto)
        }
    }

    override suspend fun deleteTransaction(id: String): FinancialResult<Unit> {
        return safeApiCall {
            apiService.deleteTransaction(id)
            Unit
        }
    }
}
