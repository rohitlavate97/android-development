package com.enterprise.financetracker

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Launcher Activity for Enterprise Finance Tracker.
 * Demonstrates Android Activity Lifecycle mechanics and state preservation.
 * (Phase 3 Concept 2 & Stage 2)
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val KEY_SESSION_COUNTER = "key_session_counter"
    }

    private var sessionCounter: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: Activity created. savedInstanceState is ${if (savedInstanceState == null) "NULL (Fresh Launch)" else "NON-NULL (Restored from Death/Rotation)"}")

        if (savedInstanceState != null) {
            sessionCounter = savedInstanceState.getInt(KEY_SESSION_COUNTER, 0)
            Log.d(TAG, "Restored sessionCounter: $sessionCounter")
        }

        // Programmatic fallback UI before Jetpack Compose integration in Stage 3
        val textView = TextView(this).apply {
            text = "${getString(R.string.welcome_message)}\n\nStatus: ${getString(R.string.status_ready)}\nSession Restorations: $sessionCounter"
            textSize = 18f
            setPadding(48, 96, 48, 48)
        }
        setContentView(textView)
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart: Activity becoming visible to user")
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume: Activity in foreground and interactive")
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause: Activity losing focus (e.g. dialog, notification shade, split screen)")
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop: Activity no longer visible")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        sessionCounter++
        outState.putInt(KEY_SESSION_COUNTER, sessionCounter)
        Log.i(TAG, "onSaveInstanceState: Saving transient state (sessionCounter = $sessionCounter) before potential process kill")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy: Activity tearing down (isFinishing = $isFinishing)")
    }
}
