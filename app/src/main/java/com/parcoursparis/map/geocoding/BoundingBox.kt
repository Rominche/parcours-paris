package com.parcoursparis.map.geocoding

/**
 * Geographic bounds (e.g. Paris area) for limiting geocoding results.
 * Nominatim viewbox format: minLon, minLat, maxLon, maxLat.
 */
data class BoundingBox(
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double
) {
    fun toNominatimViewbox(): String = "$minLon,$minLat,$maxLon,$maxLat"
}
