package com.parcoursparis.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

private val TOLERANCE_PERCENT_KEY = intPreferencesKey("tolerance_percent")
private const val DEFAULT_TOLERANCE = 15

/**
 * Implémentation DataStore pour UserPreferencesRepository.
 */
class DataStoreUserPreferencesRepository(private val context: Context) : UserPreferencesRepository {

    override val tolerancePercent: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[TOLERANCE_PERCENT_KEY] ?: DEFAULT_TOLERANCE
    }

    override suspend fun setTolerancePercent(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[TOLERANCE_PERCENT_KEY] = value.coerceIn(10, 25)
        }
    }
}
