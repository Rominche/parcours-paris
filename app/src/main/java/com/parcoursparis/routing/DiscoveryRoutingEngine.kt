package com.parcoursparis.routing

import com.parcoursparis.data.repository.SegmentWithExploredState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng

/** Vitesse marche ~5 km/h ≈ 1.39 m/s → ETA = distanceMeters / 1.39 */
private const val WALKING_SPEED_MPS = 1.39

/** Coût multiplicatif pour segment exploré (favorise les non explorés). */
private const val EXPLORED_WEIGHT = 1.2

/** Coût multiplicatif pour segment non exploré. */
private const val UNEXPLORED_WEIGHT = 0.9

/**
 * Moteur de routing orienté découverte.
 * Calcule un itinéraire A→B privilégiant les segments non explorés,
 * avec un surplus de temps maîtrisé (tolérance par défaut 15 %).
 */
interface DiscoveryRoutingEngine {
    suspend fun computeRoute(
        segments: List<SegmentWithExploredState>,
        origin: LatLng,
        destination: LatLng,
        tolerancePercent: Double = 15.0
    ): RouteResult?
}

/**
 * Implémentation du moteur de routing discovery.
 * Dijkstra avec poids modifiés : exploré = coût × 1.2, non exploré = coût × 0.9.
 * Contrainte : coût total max = shortest × (1 + tolerance/100).
 */
class DiscoveryRoutingEngineImpl : DiscoveryRoutingEngine {

    override suspend fun computeRoute(
        segments: List<SegmentWithExploredState>,
        origin: LatLng,
        destination: LatLng,
        tolerancePercent: Double
    ): RouteResult? = withContext(Dispatchers.Default) {
        val graph = GraphBuilder.build(segments)
        val originKey = findNearestNode(graph, origin) ?: return null
        val destKey = findNearestNode(graph, destination) ?: return null

        val shortestDistance = dijkstraShortest(graph, originKey, destKey) ?: return null
        val maxAllowedDistance = shortestDistance * (1 + tolerancePercent / 100)

        val path = dijkstraDiscovery(graph, originKey, destKey, maxAllowedDistance) ?: return null

        val segmentGeometry = buildGeometryFromPath(graph, path)
        val geometry = listOf(origin) + segmentGeometry + destination
        val firstNode = segmentGeometry.firstOrNull()
        val lastNode = segmentGeometry.lastOrNull()
        val distanceMeters = (if (firstNode != null) haversineMeters(origin.latitude, origin.longitude, firstNode.latitude, firstNode.longitude) else 0.0) +
            path.sumOf { it.lengthMeters } +
            (if (lastNode != null) haversineMeters(lastNode.latitude, lastNode.longitude, destination.latitude, destination.longitude) else 0.0)
        val etaSeconds = (distanceMeters / WALKING_SPEED_MPS).toLong()

        RouteResult(
            geometry = geometry,
            etaSeconds = etaSeconds,
            distanceMeters = distanceMeters
        )
    }

    private fun findNearestNode(graph: RoutingGraph, point: LatLng): String? {
        if (graph.nodes.isEmpty()) return null
        return graph.nodes.keys.minByOrNull { key ->
            val node = graph.nodes[key]!!
            haversineMeters(point.latitude, point.longitude, node.latitude, node.longitude)
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).pow(2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2).pow(2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return 6_371_000.0 * c
    }

    /** Dijkstra avec coût = longueur (chemin le plus court). */
    private fun dijkstraShortest(graph: RoutingGraph, origin: String, dest: String): Double? {
        val dist = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        dist[origin] = 0.0
        val pq = mutableListOf<Pair<String, Double>>()
        pq.add(origin to 0.0)

        while (pq.isNotEmpty()) {
            pq.sortBy { it.second }
            val (u, d) = pq.removeAt(0)
            if (d > dist.getValue(u)) continue
            if (u == dest) return d

            for (edge in graph.getNeighbors(u)) {
                val alt = d + edge.lengthMeters
                if (alt < dist.getOrDefault(edge.toNodeKey, Double.POSITIVE_INFINITY)) {
                    dist[edge.toNodeKey] = alt
                    pq.add(edge.toNodeKey to alt)
                }
            }
        }
        return null
    }

    /**
     * DijkstraDiscovery : poids modifiés (exploré × 1.2, non exploré × 0.9),
     * prune quand distance réelle > maxAllowedDistance.
     * Retourne la liste des arêtes du chemin (pour reconstruire la géométrie).
     */
    private fun dijkstraDiscovery(
        graph: RoutingGraph,
        origin: String,
        dest: String,
        maxAllowedDistance: Double
    ): List<RoutingEdge>? {
        data class State(val nodeKey: String, val discoveryCost: Double, val realDistance: Double, val path: List<RoutingEdge>)

        val bestCost = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        bestCost[origin] = 0.0
        val pq = mutableListOf<State>()
        pq.add(State(origin, 0.0, 0.0, emptyList()))
        var bestPath: List<RoutingEdge>? = null

        while (pq.isNotEmpty()) {
            pq.sortBy { it.discoveryCost }
            val (u, dCost, uDist, path) = pq.removeAt(0)
            if (dCost > bestCost.getValue(u)) continue
            if (uDist > maxAllowedDistance) continue
            if (u == dest) {
                bestPath = path
                break
            }

            for (edge in graph.getNeighbors(u)) {
                val weight = if (edge.isExplored) EXPLORED_WEIGHT else UNEXPLORED_WEIGHT
                val edgeCost = edge.lengthMeters * weight
                val newCost = dCost + edgeCost
                val newRealDist = uDist + edge.lengthMeters
                if (newRealDist > maxAllowedDistance) continue
                if (newCost >= bestCost.getOrDefault(edge.toNodeKey, Double.POSITIVE_INFINITY)) continue

                bestCost[edge.toNodeKey] = newCost
                pq.add(State(edge.toNodeKey, newCost, newRealDist, path + edge))
            }
        }

        return bestPath
    }

    private fun buildGeometryFromPath(graph: RoutingGraph, path: List<RoutingEdge>): List<LatLng> {
        if (path.isEmpty()) return emptyList()
        val result = mutableListOf<LatLng>()
        for (edge in path) {
            val geom = edge.geometry
            if (result.isEmpty()) {
                result.addAll(geom)
            } else {
                result.addAll(geom.drop(1))
            }
        }
        return result
    }
}
