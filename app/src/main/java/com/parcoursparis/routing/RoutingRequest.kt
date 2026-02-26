package com.parcoursparis.routing

import org.maplibre.android.geometry.LatLng

/**
 * Requête de calcul d'itinéraire discovery.
 * @param origin Position de départ
 * @param destination Position d'arrivée
 * @param tolerancePercent Surplus de temps maximal accepté par rapport au chemin le plus court (défaut 15 %)
 */
data class RoutingRequest(
    val origin: LatLng,
    val destination: LatLng,
    val tolerancePercent: Double = 15.0
)
