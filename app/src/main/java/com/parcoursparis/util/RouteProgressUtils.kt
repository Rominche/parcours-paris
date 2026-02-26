package com.parcoursparis.util

import org.maplibre.android.geometry.LatLng

/**
 * Utilitaires pour le suivi de progression le long d'un itinéraire.
 * Projection d'un point GPS sur une polyligne et calcul de la distance restante.
 */
object RouteProgressUtils {

    /**
     * Projette le point sur la polyligne et retourne l'index du segment
     * (0 = premier segment [geometry[0], geometry[1]], etc.).
     * Retourne -1 si la géométrie est vide ou invalide (< 2 points).
     */
    fun projectPointOnPolyline(point: LatLng, geometry: List<LatLng>): Int {
        if (geometry.size < 2) return -1
        var bestIndex = 0
        var bestDistanceMeters = Double.POSITIVE_INFINITY
        for (i in 0 until geometry.size - 1) {
            val a = geometry[i]
            val b = geometry[i + 1]
            val projected = projectPointOnSegment(point, a, b)
            val d = haversineMeters(point.latitude, point.longitude, projected.latitude, projected.longitude)
            if (d < bestDistanceMeters) {
                bestDistanceMeters = d
                bestIndex = i
            }
        }
        return bestIndex
    }

    /**
     * Calcule la distance restante en mètres depuis le point projeté sur le segment
     * jusqu'à la destination (dernier point de la géométrie).
     *
     * @param geometry Géométrie de l'itinéraire
     * @param segmentIndex Index du segment sur lequel le point est projeté
     * @param point Position actuelle (sera projetée sur le segment pour précision)
     */
    fun distanceRemaining(geometry: List<LatLng>, segmentIndex: Int, point: LatLng): Double {
        if (geometry.size < 2) return 0.0
        if (segmentIndex < 0 || segmentIndex >= geometry.size - 1) return 0.0

        val projected = if (segmentIndex < geometry.size - 1) {
            projectPointOnSegment(point, geometry[segmentIndex], geometry[segmentIndex + 1])
        } else {
            geometry.last()
        }

        var remaining = 0.0
        remaining += haversineMeters(
            projected.latitude, projected.longitude,
            geometry[segmentIndex + 1].latitude, geometry[segmentIndex + 1].longitude
        )
        for (i in segmentIndex + 1 until geometry.size - 1) {
            remaining += haversineMeters(
                geometry[i].latitude, geometry[i].longitude,
                geometry[i + 1].latitude, geometry[i + 1].longitude
            )
        }
        return remaining
    }

    /**
     * Projette le point P sur le segment [A, B].
     * Formule : t = dot(AP, AB) / dot(AB, AB), point = A + t*(B-A), t clampé [0,1].
     * Approximation plane acceptable pour Paris (petites distances).
     */
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
