package com.enterprise.financetracker.di

import com.enterprise.financetracker.core.concurrency.DispatcherProvider
import com.enterprise.financetracker.domain.repository.ExpenseRepository
import com.enterprise.financetracker.domain.repository.PortfolioRepository
import com.enterprise.financetracker.domain.usecase.GetTransactionsUseCase
import com.enterprise.financetracker.ui.viewmodels.DashboardViewModel
import com.enterprise.financetracker.ui.viewmodels.TransactionListMviViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get

class AppModuleCheckTest : KoinTest {

    @Before
    fun setUp() {
        startKoin {
            modules(appModules)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun given_appModules_when_resolving_core_dependencies_then_all_singletons_are_instantiated() {
        val dispatchers: DispatcherProvider = get()
        assertThat(dispatchers).isNotNull()

        val expenseRepository: ExpenseRepository = get()
        assertThat(expenseRepository).isNotNull()

        val portfolioRepository: PortfolioRepository = get()
        assertThat(portfolioRepository).isNotNull()
    }

    @Test
    fun given_appModules_when_resolving_use_cases_then_all_factories_are_instantiated() {
        val useCase1: GetTransactionsUseCase = get()
        val useCase2: GetTransactionsUseCase = get()

        assertThat(useCase1).isNotNull()
        assertThat(useCase2).isNotNull()
        // Factory produces new instances per request (Phase 6 Concept 2)
        assertThat(useCase1).isNotSameInstanceAs(useCase2)
    }

    @Test
    fun given_appModules_when_resolving_view_models_then_graph_resolves_all_parameters() {
        val dashboardViewModel: DashboardViewModel = get()
        assertThat(dashboardViewModel).isNotNull()

        val transactionListMviViewModel: TransactionListMviViewModel = get()
        assertThat(transactionListMviViewModel).isNotNull()
    }
}
