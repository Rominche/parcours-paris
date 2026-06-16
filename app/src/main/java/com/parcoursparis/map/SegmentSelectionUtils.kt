package com.parcoursparis.map

import com.parcoursparis.data.repository.SegmentWithExploredState
import com.parcoursparis.util.haversineMeters
import org.json.JSONArray
import org.json.JSONException
import org.maplibre.android.geometry.LatLng
import kotlin.math.cos
import kotlin.math.pow

/**
 * Utilitaires pour la sélection de segments sur la carte (tap → segment le plus proche).
 */
object SegmentSelectionUtils {

    const val DEFAULT_TOUCH_TARGET_DP = 48f

    /**
     * Estime la tolérance en mètres pour un tap carte, à partir du zoom et de la latitude.
     * Correspond à environ [touchTargetDp] points de densité [density] (cible accessibilité 48dp).
     */
    fun touchToleranceMeters(
        zoom: Double,
        latitude: Double,
        touchTargetDp: Float = DEFAULT_TOUCH_TARGET_DP,
        density: Float = 2.75f
    ): Double {
        if (zoom <= 0) return 30.0
        val metersPerPixel = 156543.03392 * cos(Math.toRadians(latitude)) / 2.0.pow(zoom)
        return touchTargetDp * density * metersPerPixel
    }

    /**
     * Retourne le segment le plus proche du point tapé, ou null si aucun dans la tolérance.
     */
    fun findNearestSegment(
        tapLat: Double,
        tapLon: Double,
        segments: List<SegmentWithExploredState>,
        maxDistanceMeters: Double
    ): SegmentWithExploredState? {
        if (segments.isEmpty() || maxDistanceMeters <= 0) return null
        val marginDegrees = maxDistanceMeters / 111_000.0 * 1.5
        val minLat = tapLat - marginDegrees
        val maxLat = tapLat + marginDegrees
        val minLon = tapLon - marginDegrees
        val maxLon = tapLon + marginDegrees
        var best: SegmentWithExploredState? = null
        var bestDistance = Double.POSITIVE_INFINITY
        for (item in segments) {
            val coords = parseCoordinates(item.segment.geometry_json) ?: continue
            if (!coords.any { (lon, lat) -> lat in minLat..maxLat && lon in minLon..maxLon }) {
                continue
            }
            val distance = minDistanceToPolylineMeters(tapLat, tapLon, coords)
            if (distance <= maxDistanceMeters && distance < bestDistance) {
                bestDistance = distance
                best = item
            }
        }
        return best
    }

    internal fun parseCoordinates(geometryJson: String): List<Pair<Double, Double>>? {
        return try {
            val array = JSONArray(geometryJson)
            if (array.length() < 2) return null
            buildList {
                for (i in 0 until array.length()) {
                    val coord = array.getJSONArray(i)
                    add(coord.getDouble(0) to coord.getDouble(1)) // lon, lat
                }
            }
        } catch (_: JSONException) {
            null
        }
    }

    internal fun minDistanceToPolylineMeters(
        tapLat: Double,
        tapLon: Double,
        coordinates: List<Pair<Double, Double>>
    ): Double {
        if (coordinates.size < 2) return Double.POSITIVE_INFINITY
        val point = LatLng(tapLat, tapLon)
        var minDistance = Double.POSITIVE_INFINITY
        for (i in 0 until coordinates.size - 1) {
            val (lonA, latA) = coordinates[i]
            val (lonB, latB) = coordinates[i + 1]
            val projected = projectPointOnSegment(point, LatLng(latA, lonA), LatLng(latB, lonB))
            val d = haversineMeters(tapLat, tapLon, projected.latitude, projected.longitude)
            if (d < minDistance) minDistance = d
        }
        return minDistance
    }

    private fun projectPointOnSegment(p: LatLng, a: LatLng, b: LatLng): LatLng {
        val apLat = p.latitude - a.latitude
        val apLon = p.longitude - a.longitude
        val abLat = b.latitude - a.latitude
        val abLon = b.longitude - a.longitude
        val abDot = abLat * abLat + abLon * abLon
        if (abDot == 0.0) return a
        var t = (apLat * abLat + apLon * abLon) / abDot
        t = t.coerceIn(0.0, 1.0)
        return LatLng(a.latitude + t * abLat, a.longitude + t * abLon)
    }
}
