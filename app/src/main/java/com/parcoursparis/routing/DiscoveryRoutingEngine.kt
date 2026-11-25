package com.parcoursparis.routing

import com.parcoursparis.data.repository.SegmentWithExploredState
import com.parcoursparis.util.haversineMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import kotlin.math.cos
import kotlin.math.sqrt

/** Vitesse marche ~5 km/h ≈ 1.39 m/s → ETA = distanceMeters / 1.39 */
private const val WALKING_SPEED_MPS = 1.39

/** Distance maximale entre un point (départ/arrivée) et le réseau de rues. */
internal const val MAX_SNAP_DISTANCE_METERS = 300.0

/**
 * Pénalités testées pour les rues déjà parcourues (coût = longueur × (1 + pénalité)).
 * Plus la pénalité est forte, plus l'itinéraire évite les rues connues… et s'allonge.
 * On retient la plus forte qui respecte la tolérance de temps.
 */
private val EXPLORED_PENALTIES = doubleArrayOf(0.05, 0.1, 0.2, 0.4, 0.8, 1.5, 3.0, 6.0)

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
     * Calcule l'itinéraire classique (chemin le plus court).
     * Fallback quand aucun itinéraire découverte n'est trouvé.
     */
    suspend fun computeClassicRoute(
        segments: List<SegmentWithExploredState>,
        origin: LatLng,
        destination: LatLng
    ): RouteResult?

    /** Prépare (et met en cache) le graphe pour que le premier calcul soit rapide. */
    suspend fun prepare(segments: List<SegmentWithExploredState>) {}
}

/**
 * Implémentation du moteur de routing discovery.
 *
 * - Le graphe est construit une seule fois puis réutilisé (seul l'état exploré change).
 * - Départ et arrivée sont rattachés au réseau dans la même composante connexe.
 * - Itinéraire découverte : A* où les rues parcourues sont pénalisées ; on choisit la plus
 *   forte pénalité dont le trajet reste dans la tolérance (plus court × (1 + tolérance)).
 */
class DiscoveryRoutingEngineImpl : DiscoveryRoutingEngine {

    private val graphMutex = Mutex()
    private var cachedFingerprint: Long = 0L
    private var cachedGraph: RoutingGraph? = null

    override suspend fun prepare(segments: List<SegmentWithExploredState>) {
        withContext(Dispatchers.Default) { graphFor(segments) }
    }

    override suspend fun computeRoute(
        segments: List<SegmentWithExploredState>,
        origin: LatLng,
        destination: LatLng,
        tolerancePercent: Double
    ): RouteResult? = withContext(Dispatchers.Default) {
        val graph = graphFor(segments)
        val (originNode, destNode) = snap(graph, origin, destination) ?: return@withContext null
        val search = AStar(graph, destNode)

        val shortest = search.run(originNode, null, 0.0) ?: return@withContext null
        val maxAllowed = shortest.realDistance * (1 + tolerancePercent / 100.0)

        val explored = exploredEdges(graph, segments)
        var best = shortest
        if (explored.any { it }) {
            // Recherche dichotomique de la plus forte pénalité respectant la tolérance
            // (la longueur du trajet croît avec la pénalité).
            var low = 0
            var high = EXPLORED_PENALTIES.size - 1
            while (low <= high) {
                val mid = (low + high) / 2
                val path = search.run(originNode, explored, EXPLORED_PENALTIES[mid])
                if (path != null && path.realDistance <= maxAllowed + 1e-6) {
                    best = path
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
        }
        buildResult(graph, best.halfEdges, origin, destination, RouteType.DISCOVERY)
    }

    override suspend fun computeClassicRoute(
        segments: List<SegmentWithExploredState>,
        origin: LatLng,
        destination: LatLng
    ): RouteResult? = withContext(Dispatchers.Default) {
        val graph = graphFor(segments)
        val (originNode, destNode) = snap(graph, origin, destination) ?: return@withContext null
        val path = AStar(graph, destNode).run(originNode, null, 0.0) ?: return@withContext null
        buildResult(graph, path.halfEdges, origin, destination, RouteType.CLASSIC)
    }

    private suspend fun graphFor(segments: List<SegmentWithExploredState>): RoutingGraph =
        graphMutex.withLock {
            val fingerprint = fingerprint(segments)
            val cached = cachedGraph
            if (cached != null && fingerprint == cachedFingerprint) return cached
            val graph = GraphBuilder.build(segments)
            cachedGraph = graph
            cachedFingerprint = fingerprint
            graph
        }

    /** Empreinte de la liste des segments (indépendante de l'état exploré). */
    private fun fingerprint(segments: List<SegmentWithExploredState>): Long {
        var h = 1125899906842597L
        for (s in segments) {
            h = 31 * h + s.segment.osm_way_id
            h = 31 * h + s.segment.geometry_json.length
        }
        return 31 * h + segments.size
    }

    private fun exploredEdges(graph: RoutingGraph, segments: List<SegmentWithExploredState>): BooleanArray {
        val exploredIds = HashSet<Long>()
        for (s in segments) if (s.isExplored) exploredIds.add(s.segment.osm_way_id)
        return BooleanArray(graph.edgeCount) { graph.edgeSegmentId[it] in exploredIds }
    }

    /**
     * Rattache départ et arrivée au nœud le plus proche, en imposant qu'ils soient dans la même
     * composante connexe (sinon aucun chemin n'existe). Retourne null si l'un des deux points
     * est à plus de [MAX_SNAP_DISTANCE_METERS] du réseau.
     */
    private fun snap(graph: RoutingGraph, origin: LatLng, destination: LatLng): Pair<Int, Int>? {
        val fromOrigin = nearestPerComponent(graph, origin)
        val fromDest = nearestPerComponent(graph, destination)
        var best: Pair<Int, Int>? = null
        var bestScore = Double.POSITIVE_INFINITY
        for ((comp, o) in fromOrigin) {
            val d = fromDest[comp] ?: continue
            val score = o.second + d.second
            if (score < bestScore) {
                bestScore = score
                best = o.first to d.first
            }
        }
        return best
    }

    /** Pour chaque composante à portée, le nœud le plus proche du point et sa distance. */
    private fun nearestPerComponent(graph: RoutingGraph, point: LatLng): Map<Int, Pair<Int, Double>> {
        val result = HashMap<Int, Pair<Int, Double>>()
        val cosLat = cos(Math.toRadians(point.latitude))
        val maxDeg = MAX_SNAP_DISTANCE_METERS / 111_320.0
        for (n in 0 until graph.nodeCount) {
            val dLat = graph.nodeLat[n] - point.latitude
            if (dLat > maxDeg || dLat < -maxDeg) continue
            val dLon = (graph.nodeLon[n] - point.longitude) * cosLat
            if (dLon > maxDeg || dLon < -maxDeg) continue
            val dist = sqrt(dLat * dLat + dLon * dLon) * 111_320.0
            if (dist > MAX_SNAP_DISTANCE_METERS) continue
            val comp = graph.component[n]
            val current = result[comp]
            if (current == null || dist < current.second) result[comp] = n to dist
        }
        return result
    }

    private fun buildResult(
        graph: RoutingGraph,
        halfEdges: IntArray,
        origin: LatLng,
        destination: LatLng,
        type: RouteType
    ): RouteResult {
        val segmentGeometry = ArrayList<LatLng>()
        var pathLength = 0.0
        for (h in halfEdges) {
            val geom = graph.halfEdgeGeometry(h)
            if (segmentGeometry.isEmpty()) segmentGeometry.addAll(geom) else segmentGeometry.addAll(geom.drop(1))
            pathLength += graph.edgeLength[h shr 1]
        }
        val first = segmentGeometry.firstOrNull()
        val last = segmentGeometry.lastOrNull()
        val distanceMeters =
            (if (first != null) haversineMeters(origin.latitude, origin.longitude, first.latitude, first.longitude) else 0.0) +
                pathLength +
                (if (last != null) haversineMeters(last.latitude, last.longitude, destination.latitude, destination.longitude) else 0.0)
        return RouteResult(
            geometry = listOf(origin) + segmentGeometry + destination,
            etaSeconds = (distanceMeters / WALKING_SPEED_MPS).toLong(),
            distanceMeters = distanceMeters,
            routeType = type
        )
    }
}

/** Chemin trouvé : demi-arêtes dans l'ordre et longueur réelle. */
internal class GraphPath(val halfEdges: IntArray, val realDistance: Double)

/**
 * A* vers une destination fixe. Coût d'une arête = longueur × (1 + pénalité si explorée).
 * L'heuristique (distance à vol d'oiseau, légèrement minorée) reste admissible car coût ≥ longueur.
 */
internal class AStar(private val graph: RoutingGraph, private val dest: Int) {
    private val n = graph.nodeCount
    private val heuristic = DoubleArray(n).also { h ->
        val destLat = graph.nodeLat[dest]
        val destLon = graph.nodeLon[dest]
        val cosLat = cos(Math.toRadians(destLat))
        for (i in 0 until n) {
            val dLat = graph.nodeLat[i] - destLat
            val dLon = (graph.nodeLon[i] - destLon) * cosLat
            h[i] = sqrt(dLat * dLat + dLon * dLon) * 111_000.0 * 0.99
        }
    }

    fun run(origin: Int, explored: BooleanArray?, penalty: Double): GraphPath? {
        val cost = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val real = DoubleArray(n)
        val prev = IntArray(n) { -1 }
        val closed = BooleanArray(n)
        val heap = NodeHeap()
        cost[origin] = 0.0
        heap.push(origin, heuristic[origin])
        while (heap.isNotEmpty()) {
            val u = heap.popNode()
            if (closed[u]) continue
            closed[u] = true
            if (u == dest) break
            for (a in graph.adjStart[u] until graph.adjStart[u + 1]) {
                val h = graph.adjHalfEdge[a]
                val v = graph.target(h)
                if (closed[v]) continue
                val e = h shr 1
                val length = graph.edgeLength[e]
                val weight = if (explored != null && explored[e]) length * (1 + penalty) else length
                val newCost = cost[u] + weight
                if (newCost < cost[v]) {
                    cost[v] = newCost
                    real[v] = real[u] + length
                    prev[v] = h
                    heap.push(v, newCost + heuristic[v])
                }
            }
        }
        if (cost[dest] == Double.POSITIVE_INFINITY) return null
        val edges = ArrayList<Int>()
        var current = dest
        while (current != origin) {
            val h = prev[current]
            edges.add(h)
            current = graph.source(h)
        }
        edges.reverse()
        return GraphPath(edges.toIntArray(), real[dest])
    }
}

/** Tas binaire minimal (nœud, priorité) avec suppression paresseuse. */
private class NodeHeap {
    private var nodes = IntArray(1024)
    private var keys = DoubleArray(1024)
    private var size = 0

    fun isNotEmpty() = size > 0

    fun push(node: Int, key: Double) {
        if (size == nodes.size) {
            nodes = nodes.copyOf(size * 2)
            keys = keys.copyOf(size * 2)
        }
        var i = size++
        while (i > 0) {
            val parent = (i - 1) / 2
            if (keys[parent] <= key) break
            nodes[i] = nodes[parent]
            keys[i] = keys[parent]
            i = parent
        }
        nodes[i] = node
        keys[i] = key
    }

    fun popNode(): Int {
        val top = nodes[0]
        size--
        if (size > 0) {
            val node = nodes[size]
            val key = keys[size]
            var i = 0
            while (true) {
                val left = 2 * i + 1
                if (left >= size) break
                val right = left + 1
                val child = if (right < size && keys[right] < keys[left]) right else left
                if (keys[child] >= key) break
                nodes[i] = nodes[child]
                keys[i] = keys[child]
                i = child
            }
            nodes[i] = node
            keys[i] = key
        }
        return top
    }
}
