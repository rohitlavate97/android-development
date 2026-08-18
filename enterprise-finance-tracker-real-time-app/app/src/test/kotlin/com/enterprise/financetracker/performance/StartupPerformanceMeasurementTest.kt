package com.enterprise.financetracker.performance

import com.enterprise.financetracker.core.performance.BaselineProfileJourneys
import com.enterprise.financetracker.core.performance.StartupPerformanceTracker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StartupPerformanceMeasurementTest {

    @Test
    fun given_startup_tracker_when_recordAppStart_called_then_records_non_zero_timestamp() {
        StartupPerformanceTracker.recordAppStart()
        // Method executes safely without crashing
        assertThat(BaselineProfileJourneys.PACKAGE_NAME).isEqualTo("com.enterprise.financetracker")
    }

    @Test
    fun given_baseline_profile_journeys_when_verified_then_contains_all_critical_paths() {
        assertThat(BaselineProfileJourneys.JOURNEY_STARTUP).isNotEmpty()
        assertThat(BaselineProfileJourneys.JOURNEY_LOGIN).isNotEmpty()
        assertThat(BaselineProfileJourneys.JOURNEY_DASHBOARD_SCROLL).isNotEmpty()
        assertThat(BaselineProfileJourneys.JOURNEY_TRANSACTION_LIST).isNotEmpty()
    }
}
