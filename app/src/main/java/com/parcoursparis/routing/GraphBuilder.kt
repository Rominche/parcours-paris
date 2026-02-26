package com.parcoursparis.routing

import com.parcoursparis.data.repository.SegmentWithExploredState
import com.parcoursparis.util.haversineMeters
import org.json.JSONArray
import org.maplibre.android.geometry.LatLng

/**
 * Construit un graphe de routing depuis les segments avec état exploré.
 * Nœuds = intersections (extrémités des segments), arêtes = segments avec longueur et état.
 */
object GraphBuilder {

    /**
     * Clé unique pour un nœud (arrondi à 6 décimales pour éviter doublons flottants).
     */
    fun nodeKey(lat: Double, lon: Double): String = "%.6f,%.6f".format(lat, lon)

    /**
     * Parse geometry_json (format GeoJSON LineString: [[lon,lat],[lon,lat],...]) en liste de LatLng.
     */
    fun parseGeometry(geometryJson: String): List<LatLng> {
        val coords = JSONArray(geometryJson)
        val result = mutableListOf<LatLng>()
        for (i in 0 until coords.length()) {
            val coord = coords.getJSONArray(i)
            val lon = coord.getDouble(0)
            val lat = coord.getDouble(1)
            result.add(LatLng(lat, lon))
        }
        return result
    }

    /**
     * Calcule la longueur d'une polyligne en mètres (Haversine).
     */
    fun segmentLength(geometry: List<LatLng>): Double {
        if (geometry.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until geometry.size - 1) {
            total += haversineMeters(
                geometry[i].latitude, geometry[i].longitude,
                geometry[i + 1].latitude, geometry[i + 1].longitude
            )
        }
        return total
    }

    /**
     * Construit le graphe depuis la liste des segments avec état exploré.
     */
    fun build(segments: List<SegmentWithExploredState>): RoutingGraph {
        val nodes = mutableMapOf<String, LatLng>()
        val edges = mutableMapOf<String, MutableList<RoutingEdge>>()

        for (swe in segments) {
            val segment = swe.segment
            val geometry = parseGeometry(segment.geometry_json)
            if (geometry.size < 2) continue

            val length = segmentLength(geometry)
            val from = geometry.first()
            val to = geometry.last()
            val fromKey = nodeKey(from.latitude, from.longitude)
            val toKey = nodeKey(to.latitude, to.longitude)

            nodes[fromKey] = from
            nodes[toKey] = to

            val edge = RoutingEdge(
                toNodeKey = toKey,
                segmentId = segment.osm_way_id,
                lengthMeters = length,
                isExplored = swe.isExplored,
                geometry = geometry
            )
            edges.getOrPut(fromKey) { mutableListOf() }.add(edge)

            val reverseEdge = RoutingEdge(
                toNodeKey = fromKey,
                segmentId = segment.osm_way_id,
                lengthMeters = length,
                isExplored = swe.isExplored,
                geometry = geometry.reversed()
            )
            edges.getOrPut(toKey) { mutableListOf() }.add(reverseEdge)
        }

        return RoutingGraph(nodes = nodes, edges = edges)
    }
}
