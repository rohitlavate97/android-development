package com.enterprise.financetracker

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.enterprise.financetracker.ui.navigation.FinanceNavHost
import com.enterprise.financetracker.ui.theme.EnterpriseFinanceTheme

/**
 * Launcher Activity hosting Type-Safe Jetpack Compose Navigation.
 * (Phase 9 & Stage 9)
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: Hosting Type-Safe Navigation-Compose NavHost")

        setContent {
            EnterpriseFinanceTheme {
                val navController = rememberNavController()
                FinanceNavHost(navController = navController)
            }
        }
    }
}
