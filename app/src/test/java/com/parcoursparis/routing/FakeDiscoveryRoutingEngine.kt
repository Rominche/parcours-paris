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

    private val _classicComputeCalls = MutableStateFlow<List<ClassicComputeCall>>(emptyList())
    val classicComputeCalls = _classicComputeCalls.asStateFlow()

    var nextResult: RouteResult? = null
    var nextClassicResult: RouteResult? = null
    var throwOnCompute: Throwable? = null

    data class ComputeCall(
        val origin: LatLng,
        val destination: LatLng,
        val tolerancePercent: Double
    )

    data class ClassicComputeCall(
        val origin: LatLng,
        val destination: LatLng
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

    override suspend fun computeClassicRoute(
        segments: List<SegmentWithExploredState>,
        origin: LatLng,
        destination: LatLng
    ): RouteResult? {
        _classicComputeCalls.value = _classicComputeCalls.value + ClassicComputeCall(origin, destination)
        throwOnCompute?.let { throw it }
        return nextClassicResult
    }

    override suspend fun computeBothRoutes(
        segments: List<SegmentWithExploredState>,
        origin: LatLng,
        destination: LatLng,
        tolerancePercent: Double
    ): Pair<RouteResult?, RouteResult?> {
        _computeCalls.value = _computeCalls.value + ComputeCall(origin, destination, tolerancePercent)
        _classicComputeCalls.value = _classicComputeCalls.value + ClassicComputeCall(origin, destination)
        throwOnCompute?.let { throw it }
        return nextResult to nextClassicResult
    }
}
