package com.parcoursparis.routing

import org.maplibre.android.geometry.LatLng

/**
 * Graphe de routing piéton, stocké dans des tableaux primitifs (≈240 000 nœuds pour Paris).
 *
 * - Nœuds : intersections (sommet partagé par plusieurs voies OSM) et extrémités de voies.
 * - Arêtes : portion de voie OSM entre deux nœuds consécutifs, parcourable dans les deux sens.
 *   Une voie OSM (segment de l'app) peut donc produire plusieurs arêtes : c'est ce découpage
 *   aux carrefours qui rend le réseau connexe.
 * - Demi-arête : `edge * 2` = sens from→to, `edge * 2 + 1` = sens to→from.
 */
class RoutingGraph(
    val nodeLat: DoubleArray,
    val nodeLon: DoubleArray,
    /** Identifiant de composante connexe de chaque nœud. */
    val component: IntArray,
    val edgeFrom: IntArray,
    val edgeTo: IntArray,
    val edgeLength: DoubleArray,
    /** osm_way_id du segment d'origine de chaque arête. */
    val edgeSegmentId: LongArray,
    /** Index (inclus) du premier et du dernier point de l'arête dans [coordLat]/[coordLon]. */
    val edgeGeomStart: IntArray,
    val edgeGeomEnd: IntArray,
    val coordLat: DoubleArray,
    val coordLon: DoubleArray,
    /** Liste d'adjacence compacte : demi-arêtes sortantes du nœud n = adjHalfEdge[adjStart[n] until adjStart[n+1]]. */
    val adjStart: IntArray,
    val adjHalfEdge: IntArray
) {
    val nodeCount: Int get() = nodeLat.size
    val edgeCount: Int get() = edgeFrom.size

    /** Nœud d'arrivée d'une demi-arête. */
    fun target(halfEdge: Int): Int {
        val e = halfEdge shr 1
        return if (halfEdge and 1 == 0) edgeTo[e] else edgeFrom[e]
    }

    /** Nœud de départ d'une demi-arête. */
    fun source(halfEdge: Int): Int {
        val e = halfEdge shr 1
        return if (halfEdge and 1 == 0) edgeFrom[e] else edgeTo[e]
    }

    /** Géométrie d'une demi-arête dans son sens de parcours. */
    fun halfEdgeGeometry(halfEdge: Int): List<LatLng> {
        val e = halfEdge shr 1
        val start = edgeGeomStart[e]
        val end = edgeGeomEnd[e]
        val points = ArrayList<LatLng>(end - start + 1)
        if (halfEdge and 1 == 0) {
            for (i in start..end) points.add(LatLng(coordLat[i], coordLon[i]))
        } else {
            for (i in end downTo start) points.add(LatLng(coordLat[i], coordLon[i]))
        }
        return points
    }
}
