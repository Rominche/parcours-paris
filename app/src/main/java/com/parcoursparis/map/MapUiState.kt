package com.parcoursparis.map

import com.parcoursparis.data.repository.SegmentWithExploredState
import com.parcoursparis.map.geocoding.GeocodingResult
import com.parcoursparis.routing.RouteResult
import org.maplibre.android.geometry.LatLng

/**
 * UI state for the map screen.
 * Holds segments with explored state for rendering on the map.
 */
data class MapUiState(
    val segments: List<SegmentWithExploredState> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userLocation: LatLng? = null,
    val locationPermissionGranted: Boolean = false,
    val locationPermissionDenied: Boolean = false,
    val destination: LatLng? = null,
    val searchQuery: String = "",
    val searchSuggestions: List<GeocodingResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val route: RouteResult? = null,
    val isComputingRoute: Boolean = false,
    val routeError: String? = null,
    val tolerancePercent: Int = 15,
    val showRouteBottomSheet: Boolean = false,
    val routeProgressPercent: Int = 0,
    val distanceRemainingMeters: Double = 0.0,
    val discoveryRoute: RouteResult? = null,
    val classicRoute: RouteResult? = null
)
