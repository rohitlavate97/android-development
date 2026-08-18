package com.enterprise.financetracker.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val baseCurrency: String,
    val isBiometricsEnabled: Boolean,
    val lastSyncTimestamp: Long
)

class UserPreferencesDataStore(
    private val context: Context
) {
    private val dataStore = context.userPreferencesDataStore

    companion object {
        private val KEY_BASE_CURRENCY = stringPreferencesKey("base_currency")
        private val KEY_BIOMETRICS_ENABLED = booleanPreferencesKey("is_biometrics_enabled")
        private val KEY_LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                baseCurrency = preferences[KEY_BASE_CURRENCY] ?: "USD",
                isBiometricsEnabled = preferences[KEY_BIOMETRICS_ENABLED] ?: true,
                lastSyncTimestamp = preferences[KEY_LAST_SYNC_TIMESTAMP] ?: 0L
            )
        }

    suspend fun setBaseCurrency(currencyCode: String) {
        dataStore.edit { preferences ->
            preferences[KEY_BASE_CURRENCY] = currencyCode
        }
    }

    suspend fun setBiometricsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_BIOMETRICS_ENABLED] = enabled
        }
    }

    suspend fun updateLastSyncTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_SYNC_TIMESTAMP] = timestamp
        }
    }
}
