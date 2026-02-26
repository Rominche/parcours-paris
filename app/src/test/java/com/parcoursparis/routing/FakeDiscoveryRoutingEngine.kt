package com.parcoursparis.routing

import com.parcoursparis.data.repository.SegmentWithExploredState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng

/**
 * Fake DiscoveryRoutingEngine pour les tests unitaires.
 * Configure le résultat ou l'exception à retourner.
 */
class FakeDiscoveryRoutingEngine : DiscoveryRoutingEngine {
    private val _computeCalls = MutableStateFlow<List<ComputeCall>>(emptyList())
    val computeCalls = _computeCalls.asStateFlow()

    var nextResult: RouteResult? = null
    var throwOnCompute: Throwable? = null

    data class ComputeCall(
        val origin: LatLng,
        val destination: LatLng,
        val tolerancePercent: Double
    )

    override suspend fun computeRoute(
        segments: List<SegmentWithExploredState>,
        origin: LatLng,
        destination: LatLng,
        tolerancePercent: Double
    ): RouteResult? {
        _computeCalls.value = _computeCalls.value + ComputeCall(origin, destination, tolerancePercent)
        throwOnCompute?.let { throw it }
        return nextResult
    }
}
