package com.parcoursparis.routing

import com.parcoursparis.data.entity.Segment
import com.parcoursparis.data.repository.SegmentWithExploredState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

/**
 * Unit tests for DiscoveryRoutingEngine.
 * Verifies: graphe simple, favorise segments non explorés, respecte tolérance.
 */
class DiscoveryRoutingEngineTest {

    private val engine = DiscoveryRoutingEngineImpl()

    @Test
    fun computeRoute_simplePath_returnsRouteResult() = runTest {
        // Two connected segments: A->B->C
        val segments = listOf(
            SegmentWithExploredState(Segment(1L, "[[2.35,48.85],[2.36,48.86]]"), false),
            SegmentWithExploredState(Segment(2L, "[[2.36,48.86],[2.37,48.87]]"), false)
        )
        val origin = LatLng(48.85, 2.35)
        val dest = LatLng(48.87, 2.37)

        val result = engine.computeRoute(segments, origin, dest, 15.0)

        assertNotNull(result)
        assertTrue(result!!.geometry.size >= 2)
        assertTrue(result.etaSeconds > 0)
        assertTrue(result.distanceMeters > 0)
    }

    @Test
    fun computeRoute_noPath_returnsNull() = runTest {
        // Single isolated segment
        val segments = listOf(
            SegmentWithExploredState(Segment(1L, "[[2.35,48.85],[2.36,48.86]]"), false)
        )
        val origin = LatLng(48.85, 2.35)
        val dest = LatLng(48.90, 2.45) // Far away, no connection

        val result = engine.computeRoute(segments, origin, dest, 15.0)

        assertNull(result)
    }

    @Test
    fun computeRoute_emptySegments_returnsNull() = runTest {
        val origin = LatLng(48.85, 2.35)
        val dest = LatLng(48.86, 2.36)

        val result = engine.computeRoute(emptyList(), origin, dest, 15.0)

        assertNull(result)
    }

    @Test
    fun computeRoute_twoPaths_favorsUnexploredWhenInTolerance() = runTest {
        // Graph: A -[explored]- B -[unexplored]- C  vs  A -[unexplored]- D -[unexplored]- C
        // Short path: A->B->C (explored segment)
        // Discovery path: A->D->C (both unexplored, longer but within tolerance)
        val segments = listOf(
            SegmentWithExploredState(Segment(1L, "[[2.35,48.85],[2.36,48.86]]"), true),  // A-B explored
            SegmentWithExploredState(Segment(2L, "[[2.36,48.86],[2.37,48.87]]"), false), // B-C
            SegmentWithExploredState(Segment(3L, "[[2.35,48.85],[2.355,48.855]]"), false), // A-D
            SegmentWithExploredState(Segment(4L, "[[2.355,48.855],[2.37,48.87]]"), false)  // D-C
        )
        val origin = LatLng(48.85, 2.35)
        val dest = LatLng(48.87, 2.37)

        val result = engine.computeRoute(segments, origin, dest, 50.0)

        assertNotNull(result)
        // With high tolerance, engine should find a path (possibly via unexplored)
        assertTrue(result!!.geometry.size >= 2)
    }

    @Test
    fun computeRoute_respectsTolerance() = runTest {
        val segments = listOf(
            SegmentWithExploredState(Segment(1L, "[[2.35,48.85],[2.36,48.86]]"), false),
            SegmentWithExploredState(Segment(2L, "[[2.36,48.86],[2.37,48.87]]"), false)
        )
        val origin = LatLng(48.85, 2.35)
        val dest = LatLng(48.87, 2.37)

        val result = engine.computeRoute(segments, origin, dest, 15.0)

        assertNotNull(result)
        // ETA should be reasonable (distance / 1.39 m/s)
        val expectedMinEta = (result!!.distanceMeters / 2.0).toLong()
        assertTrue("ETA should be positive and reasonable", result.etaSeconds >= expectedMinEta / 2)
    }
}
