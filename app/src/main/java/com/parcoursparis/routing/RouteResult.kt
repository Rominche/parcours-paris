package com.parcoursparis.routing

import org.maplibre.android.geometry.LatLng

/**
 * Type d'itinéraire : découverte (pondéré) ou classique (plus court).
 */
enum class RouteType {
    DISCOVERY,
    CLASSIC
}

/**
 * Résultat d'un calcul d'itinéraire discovery ou classique.
 * Contient la géométrie (liste de points), l'ETA en secondes et la distance en mètres.
 */
data class RouteResult(
    val geometry: List<LatLng>,
    val etaSeconds: Long,
    val distanceMeters: Double,
    val routeType: RouteType = RouteType.DISCOVERY
)
