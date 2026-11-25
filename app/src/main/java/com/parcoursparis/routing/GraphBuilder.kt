package com.parcoursparis.routing

import com.parcoursparis.data.repository.SegmentWithExploredState
import com.parcoursparis.util.GeometryParser
import com.parcoursparis.util.haversineMeters
import kotlin.math.roundToLong

/**
 * Construit le graphe de routing à partir des segments (voies OSM).
 *
 * Les voies OSM ne sont pas coupées aux carrefours : deux rues qui se croisent partagent un
 * sommet situé au milieu de leur géométrie. On crée donc un nœud pour chaque sommet partagé
 * par au moins deux voies (ou répété dans une même voie), en plus des extrémités, et on coupe
 * chaque voie en arêtes entre ces nœuds. Sans ce découpage, le réseau de Paris était éclaté en
 * ~44 000 morceaux isolés et la plupart des itinéraires échouaient.
 */
object GraphBuilder {

    /** Clé entière d'un point, arrondie au micro-degré (≈ 10 cm). */
    fun pointKey(lat: Double, lon: Double): Long {
        val latQ = (lat * 1e6).roundToLong() + 90_000_000L
        val lonQ = (lon * 1e6).roundToLong() + 180_000_000L
        return (latQ shl 32) or lonQ
    }

    fun build(segments: List<SegmentWithExploredState>): RoutingGraph {
        // 1. Lecture des géométries dans des tableaux globaux.
        val segCount = segments.size
        val segStart = IntArray(segCount)
        val segEnd = IntArray(segCount) // exclusif
        var coordLat = DoubleArray(maxOf(16, segCount * 8))
        var coordLon = DoubleArray(coordLat.size)
        var coordCount = 0
        for (s in 0 until segCount) {
            segStart[s] = coordCount
            val values = GeometryParser.parseLonLat(segments[s].segment.geometry_json)
            if (values != null) {
                val points = values.size / 2
                if (coordCount + points > coordLat.size) {
                    val newSize = maxOf(coordLat.size * 2, coordCount + points)
                    coordLat = coordLat.copyOf(newSize)
                    coordLon = coordLon.copyOf(newSize)
                }
                for (p in 0 until points) {
                    coordLon[coordCount] = values[2 * p]
                    coordLat[coordCount] = values[2 * p + 1]
                    coordCount++
                }
            }
            segEnd[s] = coordCount
        }

        // 2. Sommets qui deviennent des nœuds : extrémités + sommets vus au moins deux fois.
        //    Les extrémités sont comptées deux fois pour être toujours retenues.
        var occurrences = LongArray(coordCount + 2 * segCount)
        var occCount = 0
        for (s in 0 until segCount) {
            val start = segStart[s]
            val end = segEnd[s]
            if (end - start < 2) continue
            for (i in start until end) {
                val key = pointKey(coordLat[i], coordLon[i])
                occurrences[occCount++] = key
                if (i == start || i == end - 1) occurrences[occCount++] = key
            }
        }
        occurrences = occurrences.copyOf(occCount)
        occurrences.sort()
        var nodeKeys = LongArray(occCount)
        var nodeCount = 0
        var i = 0
        while (i < occCount) {
            var j = i + 1
            while (j < occCount && occurrences[j] == occurrences[i]) j++
            if (j - i >= 2) nodeKeys[nodeCount++] = occurrences[i]
            i = j
        }
        nodeKeys = nodeKeys.copyOf(nodeCount)
        val nodeLat = DoubleArray(nodeCount)
        val nodeLon = DoubleArray(nodeCount)

        // 3. Découpage des voies en arêtes entre nœuds consécutifs.
        var edgeFrom = IntArray(maxOf(16, segCount * 2))
        var edgeTo = IntArray(edgeFrom.size)
        var edgeLength = DoubleArray(edgeFrom.size)
        var edgeSegmentId = LongArray(edgeFrom.size)
        var edgeGeomStart = IntArray(edgeFrom.size)
        var edgeGeomEnd = IntArray(edgeFrom.size)
        var edgeCount = 0
        for (s in 0 until segCount) {
            val start = segStart[s]
            val end = segEnd[s]
            if (end - start < 2) continue
            val segmentId = segments[s].segment.osm_way_id
            var lastNode = -1
            var lastIndex = start
            var length = 0.0
            for (k in start until end) {
                if (k > start) {
                    length += haversineMeters(coordLat[k - 1], coordLon[k - 1], coordLat[k], coordLon[k])
                }
                val node = nodeKeys.binarySearch(pointKey(coordLat[k], coordLon[k]))
                if (node < 0) continue
                nodeLat[node] = coordLat[k]
                nodeLon[node] = coordLon[k]
                if (lastNode >= 0 && node != lastNode && length > 0.0) {
                    if (edgeCount == edgeFrom.size) {
                        val newSize = edgeFrom.size * 2
                        edgeFrom = edgeFrom.copyOf(newSize)
                        edgeTo = edgeTo.copyOf(newSize)
                        edgeLength = edgeLength.copyOf(newSize)
                        edgeSegmentId = edgeSegmentId.copyOf(newSize)
                        edgeGeomStart = edgeGeomStart.copyOf(newSize)
                        edgeGeomEnd = edgeGeomEnd.copyOf(newSize)
                    }
                    edgeFrom[edgeCount] = lastNode
                    edgeTo[edgeCount] = node
                    edgeLength[edgeCount] = length
                    edgeSegmentId[edgeCount] = segmentId
                    edgeGeomStart[edgeCount] = lastIndex
                    edgeGeomEnd[edgeCount] = k
                    edgeCount++
                }
                lastNode = node
                lastIndex = k
                length = 0.0
            }
        }

        // 4. Liste d'adjacence compacte.
        val adjStart = IntArray(nodeCount + 1)
        for (e in 0 until edgeCount) {
            adjStart[edgeFrom[e] + 1]++
            adjStart[edgeTo[e] + 1]++
        }
        for (n in 0 until nodeCount) adjStart[n + 1] += adjStart[n]
        val fill = adjStart.copyOf(nodeCount)
        val adjHalfEdge = IntArray(edgeCount * 2)
        for (e in 0 until edgeCount) {
            adjHalfEdge[fill[edgeFrom[e]]++] = e * 2
            adjHalfEdge[fill[edgeTo[e]]++] = e * 2 + 1
        }

        // 5. Composantes connexes (parcours en largeur).
        val component = IntArray(nodeCount) { -1 }
        val queue = IntArray(nodeCount)
        var componentId = 0
        for (startNode in 0 until nodeCount) {
            if (component[startNode] >= 0) continue
            var head = 0
            var tail = 0
            queue[tail++] = startNode
            component[startNode] = componentId
            while (head < tail) {
                val u = queue[head++]
                for (a in adjStart[u] until adjStart[u + 1]) {
                    val h = adjHalfEdge[a]
                    val e = h shr 1
                    val v = if (h and 1 == 0) edgeTo[e] else edgeFrom[e]
                    if (component[v] < 0) {
                        component[v] = componentId
                        queue[tail++] = v
                    }
                }
            }
            componentId++
        }

        return RoutingGraph(
            nodeLat = nodeLat,
            nodeLon = nodeLon,
            component = component,
            edgeFrom = edgeFrom.copyOf(edgeCount),
            edgeTo = edgeTo.copyOf(edgeCount),
            edgeLength = edgeLength.copyOf(edgeCount),
            edgeSegmentId = edgeSegmentId.copyOf(edgeCount),
            edgeGeomStart = edgeGeomStart.copyOf(edgeCount),
            edgeGeomEnd = edgeGeomEnd.copyOf(edgeCount),
            coordLat = coordLat.copyOf(coordCount),
            coordLon = coordLon.copyOf(coordCount),
            adjStart = adjStart,
            adjHalfEdge = adjHalfEdge
        )
    }
}
