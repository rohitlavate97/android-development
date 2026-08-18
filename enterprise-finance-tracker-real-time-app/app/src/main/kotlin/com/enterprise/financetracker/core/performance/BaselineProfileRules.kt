package com.enterprise.financetracker.core.performance

/**
 * Baseline Profile Critical User Journeys (CUJ) Contract.
 *
 * Rules to pre-compile ahead-of-time (AOT) to eliminate JIT compilation jank:
 * 1. Startup: EnterpriseFinanceApp.onCreate -> MainActivity.onCreate -> Koin bootstrapping
 * 2. Login Flow: LoginScreen render -> Auth validation -> Navigation transition
 * 3. Dashboard: DashboardScreen render -> Net worth card calculation -> HoldingCard LazyRow
 * 4. Transactions: TransactionListScreen render -> LazyColumn fling -> Filter chip selection
 * 5. Detail: TransactionDetailScreen render -> Delete intent dispatch
 *
 * (Phase 12 Concept 2 & ADR 033)
 */
object BaselineProfileJourneys {
    const val PACKAGE_NAME = "com.enterprise.financetracker"
    const val JOURNEY_STARTUP = "startup_cuj"
    const val JOURNEY_LOGIN = "login_cuj"
    const val JOURNEY_DASHBOARD_SCROLL = "dashboard_scroll_cuj"
    const val JOURNEY_TRANSACTION_LIST = "transaction_list_cuj"
}
