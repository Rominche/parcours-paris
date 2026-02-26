package com.parcoursparis.routing

import org.maplibre.android.geometry.LatLng

/**
 * Arête du graphe de routing.
 * Représente un segment entre deux nœuds (intersections).
 */
data class RoutingEdge(
    val toNodeKey: String,
    val segmentId: Long,
    val lengthMeters: Double,
    val isExplored: Boolean,
    val geometry: List<LatLng>
)

/**
 * Graphe de routing construit depuis les segments OSM.
 * Nœuds = intersections (extrémités des segments), arêtes = segments.
 */
data class RoutingGraph(
    val nodes: Map<String, LatLng>,
    val edges: Map<String, List<RoutingEdge>>
) {
    fun getNeighbors(nodeKey: String): List<RoutingEdge> = edges[nodeKey] ?: emptyList()
}
