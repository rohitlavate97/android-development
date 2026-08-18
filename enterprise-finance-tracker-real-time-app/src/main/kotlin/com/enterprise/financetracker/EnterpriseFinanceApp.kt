package com.enterprise.financetracker

import android.app.Application
import android.os.StrictMode
import android.util.Log

/**
 * Enterprise Application Root Class.
 * Responsible for process-level initialization, StrictMode policy configuration,
 * and global exception handler hooks. (Phase 3 Concept 2 & Stage 2)
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
}
