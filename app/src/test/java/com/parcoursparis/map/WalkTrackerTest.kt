package com.parcoursparis.map

import com.parcoursparis.data.entity.Segment
import com.parcoursparis.data.repository.SegmentWithExploredState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkTrackerTest {

    // Rue est-ouest de ~730 m et rue nord-sud de ~1,1 km qui la croise en son milieu.
    private val eastWest = SegmentWithExploredState(Segment(1L, "[[2.340,48.850],[2.345,48.850],[2.350,48.850]]"), false)
    private val northSouth = SegmentWithExploredState(Segment(2L, "[[2.345,48.845],[2.345,48.850],[2.345,48.855]]"), false)
    private val index = SegmentSpatialIndex.build(listOf(eastWest, northSouth))

    /** Positions tous les ~7 m de (lat, lonFrom) à (lat, lonTo). */
    private fun walkEastWest(tracker: WalkTracker, lat: Double, lonFrom: Double, lonTo: Double): List<Long> {
        val marked = mutableListOf<Long>()
        var lon = lonFrom
        while (lon <= lonTo) {
            marked += tracker.onLocation(lat, lon, 8f) { false }
            lon += 0.0001
        }
        return marked
    }

    @Test
    fun walkingMostOfStreet_marksIt() {
        val tracker = WalkTracker(index)
        val marked = walkEastWest(tracker, 48.85003, 2.3400, 2.3480)
        assertEquals(listOf(1L), marked)
    }

    @Test
    fun walkingSmallPartOfLongStreet_doesNotMarkIt() {
        val tracker = WalkTracker(index)
        assertTrue(walkEastWest(tracker, 48.85003, 2.3400, 2.3420).isEmpty())
    }

    @Test
    fun crossingStreet_doesNotMarkIt() {
        val tracker = WalkTracker(index)
        val marked = mutableListOf<Long>()
        // Marche sur la rue nord-sud, en traversant la rue est-ouest.
        var lat = 48.8490
        while (lat <= 48.8510) {
            marked += tracker.onLocation(lat, 2.34503, 8f) { false }
            lat += 0.00007
        }
        assertTrue(1L !in marked)
    }

    @Test
    fun inaccurateOrJumpingPositions_areIgnored() {
        val tracker = WalkTracker(index)
        val marked = mutableListOf<Long>()
        var lon = 2.3400
        while (lon <= 2.3480) {
            marked += tracker.onLocation(48.85003, lon, 80f) { false } // précision trop faible
            lon += 0.0001
        }
        lon = 2.3400
        while (lon <= 2.3480) {
            marked += tracker.onLocation(48.85003, lon, 8f) { false }
            lon += 0.001 // sauts de ~73 m
        }
        assertTrue(marked.isEmpty())
    }

    @Test
    fun movingAtVehicleSpeed_doesNotMarkStreet() {
        val tracker = WalkTracker(index)
        val marked = mutableListOf<Long>()
        var lon = 2.3400
        var time = 0L
        while (lon <= 2.3480) {
            marked += tracker.onLocation(48.85003, lon, 8f, time) { false }
            lon += 0.0001 // ~7 m par seconde ≈ 26 km/h
            time += 1000
        }
        assertTrue(marked.isEmpty())
    }

    @Test
    fun walkingWithTimestamps_marksStreet() {
        val tracker = WalkTracker(index)
        val marked = mutableListOf<Long>()
        var lon = 2.3400
        var time = 0L
        while (lon <= 2.3480) {
            marked += tracker.onLocation(48.85003, lon, 8f, time) { false }
            lon += 0.0001 // ~7 m toutes les 5 s ≈ 5 km/h
            time += 5000
        }
        assertEquals(listOf(1L), marked)
    }

    @Test
    fun alreadyExploredStreet_isNotMarkedAgain() {
        val tracker = WalkTracker(index)
        val marked = mutableListOf<Long>()
        var lon = 2.3400
        while (lon <= 2.3480) {
            marked += tracker.onLocation(48.85003, lon, 8f) { it == 1L }
            lon += 0.0001
        }
        assertTrue(marked.isEmpty())
    }

    @Test
    fun spatialIndex_findsNearestStreet() {
        val match = index.nearest(48.8501, 2.3420, 30.0)
        assertEquals(1L, match?.segmentId)
        assertEquals(null, index.nearest(48.860, 2.360, 30.0))
    }
}
