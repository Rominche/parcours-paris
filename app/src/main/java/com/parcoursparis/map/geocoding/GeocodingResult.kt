package com.parcoursparis.map.geocoding

import org.maplibre.android.geometry.LatLng

/**
 * Result of a geocoding search (e.g. Nominatim).
 * Holds display label and coordinates for map/selection.
 */
data class GeocodingResult(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val displayName: String? = null
) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)
}
