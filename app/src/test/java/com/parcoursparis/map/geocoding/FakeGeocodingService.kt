package com.parcoursparis.map.geocoding

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake GeocodingService for unit tests. Configure results or throw on next search.
 */
class FakeGeocodingService : GeocodingService {
    private val _searchCalls = MutableStateFlow<List<String>>(emptyList())
    val searchCalls = _searchCalls.asStateFlow()

    var nextResult: List<GeocodingResult> = emptyList()
    var nextDelayMs: Long = 0
    var throwOnSearch: Throwable? = null

    override suspend fun search(query: String, bounds: BoundingBox?): List<GeocodingResult> {
        _searchCalls.value = _searchCalls.value + query
        if (nextDelayMs > 0) delay(nextDelayMs)
        throwOnSearch?.let { throw it }
        return nextResult
    }
}
