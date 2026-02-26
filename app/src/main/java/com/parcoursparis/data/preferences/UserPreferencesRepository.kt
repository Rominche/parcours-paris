package com.parcoursparis.data.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Repository pour les préférences utilisateur (tolérance itinéraire, etc.).
 * Persistance via DataStore Preferences.
 */
interface UserPreferencesRepository {
    /** Valeur de tolérance (10–25 %). */
    val tolerancePercent: Flow<Int>

    /** Sauvegarde la tolérance. */
    suspend fun setTolerancePercent(value: Int)
}
