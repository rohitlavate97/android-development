package com.expensetracker

import android.app.Application
import android.os.StrictMode
import androidx.work.*
import com.expensetracker.di.appModule
import com.expensetracker.worker.SyncTransactionsWorker
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

class ExpenseTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. StrictMode Discipline in Debug (Phase 12 Concept 5)
        if (BuildConfig.DEBUG) {
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

        // 2. Start Koin Dependency Injection (Phase 6 Concept 7)
        startKoin {
            if (BuildConfig.DEBUG) androidLogger()
            androidContext(this@ExpenseTrackerApp)
            modules(appModule)
        }

        // 3. Setup Periodic WorkManager Sync (Phase 3 Concept 8)
        setupBackgroundSync()
    }

    private fun setupBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncTransactionsWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncTransactionsWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
