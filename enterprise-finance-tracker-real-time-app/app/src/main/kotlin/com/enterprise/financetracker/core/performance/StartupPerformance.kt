package com.enterprise.financetracker.core.performance

import android.app.Activity
import android.os.SystemClock
import android.util.Log

/**
 * Startup Optimization & TTFD (Time To Full Display) Metrics Tracker.
 * (Phase 12 Concept 4 & ADR 033)
 */
object StartupPerformanceTracker {

    private const val TAG = "StartupMetrics"
    private var appStartTimeMillis: Long = 0L
    private var isFullyDrawnReported: Boolean = false

    fun recordAppStart() {
        appStartTimeMillis = SystemClock.uptimeMillis()
        Log.i(TAG, "Application startup initiated at uptime: $appStartTimeMillis ms")
    }

    /**
     * Called when the critical initial UI state (Dashboard data) has been completely rendered.
     * Triggers Android OS reportFullyDrawn() for Google Play Vitals and Macrobenchmark tracking.
     */
    fun reportFullyDrawn(activity: Activity) {
        if (isFullyDrawnReported) return

        val duration = SystemClock.uptimeMillis() - appStartTimeMillis
        Log.i(TAG, "⚡ Time To Full Display (TTFD) achieved in $duration ms")

        try {
            activity.reportFullyDrawn()
            isFullyDrawnReported = true
        } catch (e: Exception) {
            Log.w(TAG, "reportFullyDrawn not supported on this platform: ${e.message}")
        }
    }
}
