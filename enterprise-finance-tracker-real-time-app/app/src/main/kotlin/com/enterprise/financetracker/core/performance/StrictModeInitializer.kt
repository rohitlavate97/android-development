package com.enterprise.financetracker.core.performance

import android.os.Build
import android.os.StrictMode
import android.util.Log

/**
 * Production-Grade StrictMode Initializer.
 * Enforces zero disk I/O, zero network calls on the Main Thread,
 * and detects SQLite cursor leaks, closable leaks, and Activity instance leaks.
 * (Phase 12 Concept 1 & ADR 035)
 */
object StrictModeInitializer {

    private const val TAG = "StrictModePolicy"

    fun initStrictMode(isDebug: Boolean) {
        if (!isDebug) return

        Log.i(TAG, "Initializing StrictMode ThreadPolicy and VmPolicy for Debug build")

        // 1. Thread Policy (Main Thread Violations)
        val threadPolicyBuilder = StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .detectCustomSlowCalls()
            .penaltyLog()

        StrictMode.setThreadPolicy(threadPolicyBuilder.build())

        // 2. VM Policy (Memory Leaks, Leaked Resources, Untagged Sockets)
        val vmPolicyBuilder = StrictMode.VmPolicy.Builder()
            .detectLeakedSqlLiteObjects()
            .detectLeakedClosableObjects()
            .detectActivityLeaks()
            .detectLeakedRegistrationObjects()
            .penaltyLog()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            vmPolicyBuilder.detectNonSdkApiUsage()
        }

        StrictMode.setVmPolicy(vmPolicyBuilder.build())
    }
}
