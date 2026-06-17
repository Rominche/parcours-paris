package com.parcoursparis.routing

import com.parcoursparis.data.repository.SegmentWithExploredState
import org.json.JSONArray
import org.maplibre.android.geometry.LatLng

/**
 * Réduit le jeu de segments avant construction du graphe de routing.
 * Le réseau Paris complet (~160k segments) provoque OOM si tout est chargé en mémoire.
 */
object RoutingSegmentFilter {

  /** Marges en degrés (~1° lat ≈ 111 km). Expansion progressive si aucun chemin. */
  val ROUTE_MARGINS_DEGREES = doubleArrayOf(0.004, 0.008, 0.015, 0.03, 0.06)

  fun filterForRoute(
    segments: List<SegmentWithExploredState>,
    origin: LatLng,
    destination: LatLng,
    marginDegrees: Double
  ): List<SegmentWithExploredState> {
    val minLat = minOf(origin.latitude, destination.latitude) - marginDegrees
    val maxLat = maxOf(origin.latitude, destination.latitude) + marginDegrees
    val minLon = minOf(origin.longitude, destination.longitude) - marginDegrees
    val maxLon = maxOf(origin.longitude, destination.longitude) + marginDegrees

    return segments.filter { segment ->
      segmentIntersectsBox(segment, minLat, maxLat, minLon, maxLon)
    }
  }

  private fun segmentIntersectsBox(
    segment: SegmentWithExploredState,
    minLat: Double,
    maxLat: Double,
    minLon: Double,
    maxLon: Double
  ): Boolean {
    val endpoints = quickEndpoints(segment.segment.geometry_json) ?: return false
    val (start, end) = endpoints
    return pointInBox(start, minLat, maxLat, minLon, maxLon) ||
      pointInBox(end, minLat, maxLat, minLon, maxLon)
  }

  private fun pointInBox(
    point: LatLng,
    minLat: Double,
    maxLat: Double,
    minLon: Double,
    maxLon: Double
  ): Boolean =
    point.latitude in minLat..maxLat && point.longitude in minLon..maxLon

  private fun quickEndpoints(geometryJson: String): Pair<LatLng, LatLng>? {
    return try {
      val coords = JSONArray(geometryJson)
      if (coords.length() < 2) return null
      val first = coords.getJSONArray(0)
      val last = coords.getJSONArray(coords.length() - 1)
      LatLng(first.getDouble(1), first.getDouble(0)) to
        LatLng(last.getDouble(1), last.getDouble(0))
    } catch (_: Exception) {
      null
    }
  }
}
