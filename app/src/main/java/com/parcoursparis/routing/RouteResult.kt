package com.parcoursparis.routing

import org.maplibre.android.geometry.LatLng

/**
 * Résultat d'un calcul d'itinéraire discovery.
 * Contient la géométrie (liste de points), l'ETA en secondes et la distance en mètres.
 */
data class RouteResult(
    val geometry: List<LatLng>,
    val etaSeconds: Long,
    val distanceMeters: Double
)
