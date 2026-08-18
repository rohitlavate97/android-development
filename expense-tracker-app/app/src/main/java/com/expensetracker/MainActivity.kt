package com.expensetracker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.expensetracker.core.designsystem.ExpenseTrackerTheme
import com.expensetracker.navigation.ExpenseAppRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Security flag: Prevent screenshots and task switcher previews of sensitive financial data (Phase 13 Concept 4)
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        setContent {
            ExpenseTrackerTheme {
                ExpenseAppRoot()
            }
        }
    }
}
