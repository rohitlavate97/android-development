package com.enterprise.financetracker.di

import com.enterprise.financetracker.core.concurrency.DispatcherProvider
import com.enterprise.financetracker.core.concurrency.StandardDispatcherProvider
import com.enterprise.financetracker.core.network.NetworkClientFactory
import com.enterprise.financetracker.core.network.TokenManager
import com.enterprise.financetracker.data.datasource.InMemoryTransactionLocalDataSource
import com.enterprise.financetracker.data.datasource.RetrofitTransactionRemoteDataSource
import com.enterprise.financetracker.data.datasource.TransactionLocalDataSource
import com.enterprise.financetracker.data.datasource.TransactionRemoteDataSource
import com.enterprise.financetracker.data.network.api.FinanceApiService
import com.enterprise.financetracker.data.repository.EnterpriseRepositories
import com.enterprise.financetracker.data.repository.ExpenseRepositoryImpl
import com.enterprise.financetracker.data.repository.PortfolioRepositoryImpl
import com.enterprise.financetracker.domain.repository.ExpenseRepository
import com.enterprise.financetracker.domain.repository.PortfolioRepository
import com.enterprise.financetracker.domain.usecase.*
import com.enterprise.financetracker.ui.viewmodels.DashboardViewModel
import com.enterprise.financetracker.ui.viewmodels.TransactionListMviViewModel
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit

val coreModule = module {
    single<DispatcherProvider> { StandardDispatcherProvider() }
}

val networkModule = module {
    single { TokenManager() }
    single {
        var apiServiceRef: FinanceApiService? = null
        val okHttpClient = NetworkClientFactory.createOkHttpClient(
            tokenManager = get(),
            apiServiceProvider = { apiServiceRef!! }
        )
        val retrofit = NetworkClientFactory.createRetrofit(okHttpClient = okHttpClient)
        val apiService = NetworkClientFactory.createFinanceApiService(retrofit)
        apiServiceRef = apiService
        apiService
    }
    singleOf(::RetrofitTransactionRemoteDataSource) bind TransactionRemoteDataSource::class
}

val dataModule = module {
    single<TransactionLocalDataSource> { InMemoryTransactionLocalDataSource() }
    singleOf(::ExpenseRepositoryImpl) bind ExpenseRepository::class
    singleOf(::PortfolioRepositoryImpl) bind PortfolioRepository::class
}

val domainModule = module {
    factoryOf(::GetTransactionsUseCase)
    factoryOf(::GetTransactionDetailUseCase)
    factoryOf(::AddTransactionUseCase)
    factoryOf(::DeleteTransactionUseCase)
    factoryOf(::GetPortfolioSummaryUseCase)
    factoryOf(::FilterTransactionsUseCase)
}

val uiModule = module {
    viewModelOf(::DashboardViewModel)
    viewModelOf(::TransactionListMviViewModel)
}

val appModules = listOf(
    coreModule,
    networkModule,
    dataModule,
    domainModule,
    uiModule
)
