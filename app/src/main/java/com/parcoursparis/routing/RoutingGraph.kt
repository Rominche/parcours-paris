package com.parcoursparis.routing

import org.maplibre.android.geometry.LatLng

/**
 * Arête du graphe de routing.
 * Représente un segment orienté avec sa longueur, son état exploré et sa géométrie.
 */
data class RoutingEdge(
    val toNodeKey: String,
    val segmentId: Long,
    val lengthMeters: Double,
    val isExplored: Boolean,
    val geometry: List<LatLng>
)

/**
 * Graphe de routing orienté.
 * Nœuds = intersections (clé = "lat,lon"), arêtes = segments avec coût.
 */
data class RoutingGraph(
    val nodes: Map<String, LatLng>,
    val edges: Map<String, List<RoutingEdge>>
) {
    /** Retourne les arêtes sortantes pour un nœud donné. */
    fun getNeighbors(nodeKey: String): Iterable<RoutingEdge> =
        edges[nodeKey] ?: emptyList()
}
