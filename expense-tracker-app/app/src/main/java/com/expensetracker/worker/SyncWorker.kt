package com.expensetracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.expensetracker.core.common.Resource
import com.expensetracker.feature.transactions.domain.SyncTransactionsUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background WorkManager job ensuring guaranteed sync when network is unmetered.
 * (Phase 3 Concept 8)
 */
class SyncTransactionsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val syncTransactionsUseCase: SyncTransactionsUseCase by inject()

    override suspend fun doWork(): Result {
        return when (syncTransactionsUseCase()) {
            is Resource.Success -> Result.success()
            is Resource.Error -> if (runAttemptCount < 3) Result.retry() else Result.failure()
            Resource.Loading -> Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "SyncTransactionsWork"
    }
}
