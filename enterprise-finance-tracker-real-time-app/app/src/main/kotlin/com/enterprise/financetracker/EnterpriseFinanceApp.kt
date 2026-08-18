package com.enterprise.financetracker

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.enterprise.financetracker.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Enterprise Application Root Class.
 * Responsible for process-level initialization, StrictMode policy configuration,
 * and Koin Dependency Injection graph initialization. (Phase 6 Concept 7 & Stage 6)
 */
class EnterpriseFinanceApp : Application() {

    companion object {
        private const val TAG = "EnterpriseFinanceApp"
        var isAppInitialized: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application onCreate: Initializing process ${android.os.Process.myPid()}")

        setupStrictMode()
        setupDependencyInjection()

        isAppInitialized = true
        Log.i(TAG, "Application initialization complete.")
    }

    private fun setupStrictMode() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Enabling StrictMode policies for debug build")
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }

    private fun setupDependencyInjection() {
        Log.d(TAG, "Starting Koin Dependency Injection container")
        startKoin {
            if (BuildConfig.DEBUG) androidLogger()
            androidContext(this@EnterpriseFinanceApp)
            modules(appModules)
        }
    }
}
