package com.enterprise.financetracker

import android.app.Application
import android.util.Log
import com.enterprise.financetracker.core.performance.StartupPerformanceTracker
import com.enterprise.financetracker.core.performance.StrictModeInitializer
import com.enterprise.financetracker.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class EnterpriseFinanceApp : Application() {

    companion object {
        private const val TAG = "EnterpriseFinanceApp"
    }

    override fun onCreate() {
        StartupPerformanceTracker.recordAppStart()
        super.onCreate()

        // 1. Production StrictMode Enforcement
        StrictModeInitializer.initStrictMode(isDebug = BuildConfig.DEBUG)

        // 2. Koin Dependency Injection Bootstrapping
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@EnterpriseFinanceApp)
            modules(appModules)
        }

        Log.i(TAG, "Application initialized with StrictMode and Koin DI graph")
    }
}
