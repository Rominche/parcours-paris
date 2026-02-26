package com.parcoursparis.data.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake UserPreferencesRepository pour les tests unitaires.
 */
class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val _tolerancePercent = MutableStateFlow(15)
    override val tolerancePercent = _tolerancePercent.asStateFlow()

    override suspend fun setTolerancePercent(value: Int) {
        _tolerancePercent.value = value.coerceIn(10, 25)
    }
}
