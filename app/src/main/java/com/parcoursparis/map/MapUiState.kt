package com.parcoursparis.map

import com.parcoursparis.data.repository.SegmentWithExploredState
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
    val locationPermissionDenied: Boolean = false
)
