package com.parcoursparis.map

import com.parcoursparis.data.entity.Segment
import com.parcoursparis.data.repository.SegmentWithExploredState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SegmentSelectionUtilsTest {

    private val segmentA = SegmentWithExploredState(
        segment = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]"),
        isExplored = false
    )
    private val segmentB = SegmentWithExploredState(
        segment = Segment(1002L, "[[2.40,48.90],[2.41,48.91]]"),
        isExplored = true
    )

    @Test
    fun findNearestSegment_returnsClosestWithinTolerance() {
        val result = SegmentSelectionUtils.findNearestSegment(
            tapLat = 48.855,
            tapLon = 2.355,
            segments = listOf(segmentA, segmentB),
            maxDistanceMeters = 500.0
        )
        assertNotNull(result)
        assertEquals(1001L, result!!.segment.osm_way_id)
    }

    @Test
    fun findNearestSegment_returnsNullWhenTooFar() {
        val result = SegmentSelectionUtils.findNearestSegment(
            tapLat = 48.855,
            tapLon = 2.355,
            segments = listOf(segmentB),
            maxDistanceMeters = 10.0
        )
        assertNull(result)
    }

    @Test
    fun findNearestSegment_returnsNullForEmptyList() {
        val result = SegmentSelectionUtils.findNearestSegment(
            tapLat = 48.85,
            tapLon = 2.35,
            segments = emptyList(),
            maxDistanceMeters = 100.0
        )
        assertNull(result)
    }

    @Test
    fun touchToleranceMeters_increasesWhenZoomingOut() {
        val zoomedIn = SegmentSelectionUtils.touchToleranceMeters(15.0, 48.85)
        val zoomedOut = SegmentSelectionUtils.touchToleranceMeters(12.0, 48.85)
        assertTrue(zoomedOut > zoomedIn)
    }

    @Test
    fun parseCoordinates_returnsNullForInvalidJson() {
        assertNull(SegmentSelectionUtils.parseCoordinates("not-json"))
    }
}
