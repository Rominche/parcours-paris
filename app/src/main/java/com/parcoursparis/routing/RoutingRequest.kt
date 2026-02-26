package com.parcoursparis.routing

import org.maplibre.android.geometry.LatLng

/**
 * Requête de calcul d'itinéraire discovery.
 * Contient l'origine, la destination et la tolérance de surplus (en %).
 */
data class RoutingRequest(
    val origin: LatLng,
    val destination: LatLng,
    val tolerancePercent: Double = 15.0
)
