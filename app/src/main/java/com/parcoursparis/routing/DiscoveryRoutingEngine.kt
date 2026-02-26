package com.parcoursparis.routing

import com.parcoursparis.data.repository.SegmentWithExploredState
import com.parcoursparis.util.haversineMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import java.util.PriorityQueue

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

    /**
     * Calcule l'itinéraire classique (chemin le plus court, Dijkstra sans pondération).
     * Fallback quand aucun itinéraire découverte n'est trouvé.
     */
    suspend fun computeClassicRoute(
        segments: List<SegmentWithExploredState>,
        origin: LatLng,
        destination: LatLng
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
        val originKey = findNearestNode(graph, origin) ?: return@withContext null
        val destKey = findNearestNode(graph, destination) ?: return@withContext null

        val shortestDistance = dijkstraShortest(graph, originKey, destKey)
            ?: return@withContext null
        val maxAllowedDistance = shortestDistance * (1 + tolerancePercent / 100)

        val path = dijkstraDiscovery(graph, originKey, destKey, maxAllowedDistance)
            ?: return@withContext null

        val segmentGeometry = buildGeometryFromPath(path)
        val geometry = listOf(origin) + segmentGeometry + destination
        val firstNode = segmentGeometry.firstOrNull()
        val lastNode = segmentGeometry.lastOrNull()
        val distanceMeters =
            (if (firstNode != null) haversineMeters(origin.latitude, origin.longitude, firstNode.latitude, firstNode.longitude) else 0.0) +
                path.sumOf { it.lengthMeters } +
                (if (lastNode != null) haversineMeters(lastNode.latitude, lastNode.longitude, destination.latitude, destination.longitude) else 0.0)
        val etaSeconds = (distanceMeters / WALKING_SPEED_MPS).toLong()

        RouteResult(
            geometry = geometry,
            etaSeconds = etaSeconds,
            distanceMeters = distanceMeters,
            routeType = RouteType.DISCOVERY
        )
    }

    override suspend fun computeClassicRoute(
        segments: List<SegmentWithExploredState>,
        origin: LatLng,
        destination: LatLng
    ): RouteResult? = withContext(Dispatchers.Default) {
        val graph = GraphBuilder.build(segments)
        val originKey = findNearestNode(graph, origin) ?: return@withContext null
        val destKey = findNearestNode(graph, destination) ?: return@withContext null

        val path = dijkstraShortestPath(graph, originKey, destKey) ?: return@withContext null
        val segmentGeometry = buildGeometryFromPath(path)
        val geometry = listOf(origin) + segmentGeometry + destination
        val firstNode = segmentGeometry.firstOrNull()
        val lastNode = segmentGeometry.lastOrNull()
        val distanceMeters =
            (if (firstNode != null) haversineMeters(origin.latitude, origin.longitude, firstNode.latitude, firstNode.longitude) else 0.0) +
                path.sumOf { it.lengthMeters } +
                (if (lastNode != null) haversineMeters(lastNode.latitude, lastNode.longitude, destination.latitude, destination.longitude) else 0.0)
        val etaSeconds = (distanceMeters / WALKING_SPEED_MPS).toLong()

        RouteResult(
            geometry = geometry,
            etaSeconds = etaSeconds,
            distanceMeters = distanceMeters,
            routeType = RouteType.CLASSIC
        )
    }

    private fun findNearestNode(graph: RoutingGraph, point: LatLng): String? {
        if (graph.nodes.isEmpty()) return null
        return graph.nodes.keys.minByOrNull { key ->
            val node = graph.nodes[key]!!
            haversineMeters(point.latitude, point.longitude, node.latitude, node.longitude)
        }
    }

    /**
     * Dijkstra classique (coût = longueur réelle) pour calculer le chemin le plus court.
     * Retourne la distance totale.
     */
    private fun dijkstraShortest(graph: RoutingGraph, origin: String, dest: String): Double? {
        val (_, dist) = dijkstraShortestWithPrevious(graph, origin, dest)
        return dist[dest]
    }

    /**
     * Dijkstra classique qui retourne le chemin (liste d'arêtes).
     */
    private fun dijkstraShortestPath(graph: RoutingGraph, origin: String, dest: String): List<RoutingEdge>? {
        val (previous, _) = dijkstraShortestWithPrevious(graph, origin, dest)
        return reconstructPath(previous, origin, dest)
    }

    private fun dijkstraShortestWithPrevious(
        graph: RoutingGraph,
        origin: String,
        dest: String
    ): Pair<Map<String, RoutingEdge?>, Map<String, Double>> {
        val dist = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        val previous = mutableMapOf<String, RoutingEdge?>()
        dist[origin] = 0.0
        previous[origin] = null
        val pq = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        pq.add(origin to 0.0)

        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll()
            if (d > dist.getValue(u)) continue
            if (u == dest) break

            for (edge in graph.getNeighbors(u)) {
                val alt = d + edge.lengthMeters
                if (alt < dist.getOrDefault(edge.toNodeKey, Double.POSITIVE_INFINITY)) {
                    dist[edge.toNodeKey] = alt
                    previous[edge.toNodeKey] = edge
                    pq.add(edge.toNodeKey to alt)
                }
            }
        }
        return previous to dist
    }

    /**
     * Dijkstra discovery : poids modifiés (exploré × 1.2, non exploré × 0.9),
     * prune quand distance réelle > maxAllowedDistance.
     * Utilise un PriorityQueue et un map `previous` pour reconstruire le chemin.
     */
    private fun dijkstraDiscovery(
        graph: RoutingGraph,
        origin: String,
        dest: String,
        maxAllowedDistance: Double
    ): List<RoutingEdge>? {
        data class NodeState(val nodeKey: String, val discoveryCost: Double, val realDistance: Double)

        val bestCost = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        val bestReal = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        val previous = mutableMapOf<String, RoutingEdge?>()

        bestCost[origin] = 0.0
        bestReal[origin] = 0.0
        previous[origin] = null

        val pq = PriorityQueue<NodeState>(compareBy { it.discoveryCost })
        pq.add(NodeState(origin, 0.0, 0.0))

        while (pq.isNotEmpty()) {
            val (u, dCost, uDist) = pq.poll()
            if (dCost > bestCost.getValue(u)) continue
            if (uDist > maxAllowedDistance) continue
            if (u == dest) break

            for (edge in graph.getNeighbors(u)) {
                val weight = if (edge.isExplored) EXPLORED_WEIGHT else UNEXPLORED_WEIGHT
                val newCost = dCost + edge.lengthMeters * weight
                val newRealDist = uDist + edge.lengthMeters
                if (newRealDist > maxAllowedDistance) continue
                if (newCost >= bestCost.getOrDefault(edge.toNodeKey, Double.POSITIVE_INFINITY)) continue

                bestCost[edge.toNodeKey] = newCost
                bestReal[edge.toNodeKey] = newRealDist
                previous[edge.toNodeKey] = edge
                pq.add(NodeState(edge.toNodeKey, newCost, newRealDist))
            }
        }

        if (!previous.containsKey(dest) && dest != origin) return null
        return reconstructPath(previous, origin, dest)
    }

    /** Reconstruit la liste d'arêtes depuis la map `previous`. */
    private fun reconstructPath(previous: Map<String, RoutingEdge?>, origin: String, dest: String): List<RoutingEdge>? {
        val path = mutableListOf<RoutingEdge>()
        var current = dest
        while (current != origin) {
            val edge = previous[current] ?: return null
            path.add(0, edge)
            // geometry.first() encodes the from-node (same key format as GraphBuilder.nodeKey)
            val first = edge.geometry.first()
            current = "%.6f,%.6f".format(first.latitude, first.longitude)
        }
        return path
    }

    private fun buildGeometryFromPath(path: List<RoutingEdge>): List<LatLng> {
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
