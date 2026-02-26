package com.parcoursparis.map

import com.parcoursparis.data.repository.SegmentWithExploredState
import com.parcoursparis.map.geocoding.GeocodingResult
import com.parcoursparis.routing.RouteResult
import org.maplibre.android.geometry.LatLng

/**
 * UI state for the map screen.
 * Holds segments with explored state for rendering on the map.
 * Search: suggestions, loading, error; destination for routing (story 2.2+).
 * Routing: route result, computing state, error (story 2.2).
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
    val routeError: String? = null
)
