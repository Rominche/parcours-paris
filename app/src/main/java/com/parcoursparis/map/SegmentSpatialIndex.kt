package com.parcoursparis.map

import com.parcoursparis.data.repository.SegmentWithExploredState
import com.parcoursparis.util.GeometryParser
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

private const val CELL_DEGREES = 0.001
private const val METERS_PER_DEGREE = 111_320.0

/**
 * Index spatial (grille) des segments, pour trouver en quelques millisecondes la rue la plus
 * proche d'un point (tap sur la carte, position GPS) parmi les ~160 000 segments de Paris.
 */
class SegmentSpatialIndex private constructor(
    private val segmentIds: LongArray,
    private val segStart: IntArray,
    private val segEnd: IntArray,
    private val lat: DoubleArray,
    private val lon: DoubleArray,
    private val cells: HashMap<Long, IntArray>
) {

    /**
     * Rue la plus proche d'un point.
     * @property alongMeters position du point projeté, en mètres depuis le début du segment
     * @property lengthMeters longueur totale du segment
     */
    data class Match(
        val segmentId: Long,
        val distanceMeters: Double,
        val alongMeters: Double,
        val lengthMeters: Double
    )

    val size: Int get() = segmentIds.size

    fun nearest(pointLat: Double, pointLon: Double, maxDistanceMeters: Double): Match? {
        if (maxDistanceMeters <= 0) return null
        val cosLat = cos(Math.toRadians(pointLat))
        val dLatDeg = maxDistanceMeters / METERS_PER_DEGREE
        val dLonDeg = maxDistanceMeters / (METERS_PER_DEGREE * cosLat)
        val minRow = cell(pointLat - dLatDeg)
        val maxRow = cell(pointLat + dLatDeg)
        val minCol = cell(pointLon - dLonDeg)
        val maxCol = cell(pointLon + dLonDeg)
        val seen = HashSet<Int>()
        var best: Match? = null
        for (row in minRow..maxRow) {
            for (col in minCol..maxCol) {
                val candidates = cells[key(row, col)] ?: continue
                for (s in candidates) {
                    if (!seen.add(s)) continue
                    val match = project(s, pointLat, pointLon, cosLat)
                    if (match.distanceMeters <= maxDistanceMeters &&
                        (best == null || match.distanceMeters < best.distanceMeters)
                    ) {
                        best = match
                    }
                }
            }
        }
        return best
    }

    /** Projection du point sur le segment [s], en coordonnées planes locales (mètres). */
    private fun project(s: Int, pointLat: Double, pointLon: Double, cosLat: Double): Match {
        var bestDistance = Double.POSITIVE_INFINITY
        var bestAlong = 0.0
        var cumulative = 0.0
        for (i in segStart[s] until segEnd[s] - 1) {
            val ax = (lon[i] - pointLon) * cosLat * METERS_PER_DEGREE
            val ay = (lat[i] - pointLat) * METERS_PER_DEGREE
            val bx = (lon[i + 1] - pointLon) * cosLat * METERS_PER_DEGREE
            val by = (lat[i + 1] - pointLat) * METERS_PER_DEGREE
            val abx = bx - ax
            val aby = by - ay
            val len2 = abx * abx + aby * aby
            val len = sqrt(len2)
            val t = if (len2 == 0.0) 0.0 else ((-ax) * abx + (-ay) * aby) / len2
            val tc = t.coerceIn(0.0, 1.0)
            val px = ax + tc * abx
            val py = ay + tc * aby
            val d = sqrt(px * px + py * py)
            if (d < bestDistance) {
                bestDistance = d
                bestAlong = cumulative + tc * len
            }
            cumulative += len
        }
        return Match(segmentIds[s], bestDistance, bestAlong, cumulative)
    }

    companion object {
        private fun cell(value: Double): Int = floor(value / CELL_DEGREES).toInt()
        private fun key(row: Int, col: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xffffffffL)

        fun build(segments: List<SegmentWithExploredState>): SegmentSpatialIndex {
            val ids = ArrayList<Long>(segments.size)
            val starts = ArrayList<Int>(segments.size)
            val ends = ArrayList<Int>(segments.size)
            var lat = DoubleArray(maxOf(16, segments.size * 8))
            var lon = DoubleArray(lat.size)
            var count = 0
            val cellLists = HashMap<Long, IntList>()
            for (item in segments) {
                val values = GeometryParser.parseLonLat(item.segment.geometry_json) ?: continue
                val points = values.size / 2
                if (count + points > lat.size) {
                    val newSize = maxOf(lat.size * 2, count + points)
                    lat = lat.copyOf(newSize)
                    lon = lon.copyOf(newSize)
                }
                val index = ids.size
                ids.add(item.segment.osm_way_id)
                starts.add(count)
                for (p in 0 until points) {
                    lon[count + p] = values[2 * p]
                    lat[count + p] = values[2 * p + 1]
                }
                for (p in 0 until points - 1) {
                    val i = count + p
                    val r0 = cell(minOf(lat[i], lat[i + 1]))
                    val r1 = cell(maxOf(lat[i], lat[i + 1]))
                    val c0 = cell(minOf(lon[i], lon[i + 1]))
                    val c1 = cell(maxOf(lon[i], lon[i + 1]))
                    for (r in r0..r1) for (c in c0..c1) {
                        cellLists.getOrPut(key(r, c)) { IntList() }.addUnique(index)
                    }
                }
                count += points
                ends.add(count)
            }
            val cells = HashMap<Long, IntArray>(cellLists.size * 2)
            for ((k, v) in cellLists) cells[k] = v.toArray()
            return SegmentSpatialIndex(
                segmentIds = ids.toLongArray(),
                segStart = starts.toIntArray(),
                segEnd = ends.toIntArray(),
                lat = lat.copyOf(count),
                lon = lon.copyOf(count),
                cells = cells
            )
        }
    }

    private class IntList {
        private var data = IntArray(4)
        private var size = 0

        /** Les segments sont insérés dans l'ordre : un doublon est forcément le dernier élément. */
        fun addUnique(value: Int) {
            if (size > 0 && data[size - 1] == value) return
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = value
        }

        fun toArray(): IntArray = data.copyOf(size)
    }
}
