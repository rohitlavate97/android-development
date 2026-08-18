package com.expensetracker.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.expensetracker.core.model.CurrencyCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val selectedCurrency: CurrencyCode = CurrencyCode.USD,
    val isDarkMode: Boolean = false,
    val isBiometricsEnabled: Boolean = false,
    val lastSyncEpochMillis: Long = 0L
)

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {
    private object PreferencesKeys {
        val CURRENCY = stringPreferencesKey("currency")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val BIOMETRICS = booleanPreferencesKey("biometrics")
        val LAST_SYNC = longPreferencesKey("last_sync")
    }

    val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val currencyStr = preferences[PreferencesKeys.CURRENCY] ?: CurrencyCode.USD.name
            val currency = try { CurrencyCode.valueOf(currencyStr) } catch (e: Exception) { CurrencyCode.USD }
            val isDarkMode = preferences[PreferencesKeys.DARK_MODE] ?: false
            val isBiometrics = preferences[PreferencesKeys.BIOMETRICS] ?: false
            val lastSync = preferences[PreferencesKeys.LAST_SYNC] ?: 0L

            UserPreferences(
                selectedCurrency = currency,
                isDarkMode = isDarkMode,
                isBiometricsEnabled = isBiometrics,
                lastSyncEpochMillis = lastSync
            )
        }

    suspend fun setCurrency(currency: CurrencyCode) {
        dataStore.edit { it[PreferencesKeys.CURRENCY] = currency.name }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DARK_MODE] = enabled }
    }

    suspend fun setBiometricsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.BIOMETRICS] = enabled }
    }

    suspend fun updateLastSync(epochMillis: Long) {
        dataStore.edit { it[PreferencesKeys.LAST_SYNC] = epochMillis }
    }
}
