package com.expensetracker.di

import androidx.room.Room
import com.expensetracker.core.common.DispatcherProvider
import com.expensetracker.core.common.StandardDispatcherProvider
import com.expensetracker.core.database.ExpenseDatabase
import com.expensetracker.core.datastore.UserPreferencesRepository
import com.expensetracker.core.datastore.userDataStore
import com.expensetracker.core.network.ExpenseRemoteDataSource
import com.expensetracker.core.network.FakeExpenseRemoteDataSource
import com.expensetracker.feature.analytics.AnalyticsViewModel
import com.expensetracker.feature.dashboard.DashboardViewModel
import com.expensetracker.feature.transactions.domain.*
import com.expensetracker.feature.transactions.presentation.TransactionListViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    // Core Dispatchers
    single<DispatcherProvider> { StandardDispatcherProvider() }

    // Core Database
    single {
        Room.databaseBuilder(
            androidContext(),
            ExpenseDatabase::class.java,
            ExpenseDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }
    single { get<ExpenseDatabase>().transactionDao() }
    single { get<ExpenseDatabase>().categoryDao() }

    // Core DataStore
    single { UserPreferencesRepository(androidContext().userDataStore) }

    // Core Network
    single<ExpenseRemoteDataSource> { FakeExpenseRemoteDataSource() }

    // Repository (SSOT)
    singleOf(::ExpenseRepositoryImpl) bind ExpenseRepository::class

    // Use Cases
    factoryOf(::GetTransactionsUseCase)
    factoryOf(::AddTransactionUseCase)
    factoryOf(::DeleteTransactionUseCase)
    factoryOf(::SyncTransactionsUseCase)

    // ViewModels
    viewModelOf(::DashboardViewModel)
    viewModelOf(::TransactionListViewModel)
    viewModelOf(::AnalyticsViewModel)
}
