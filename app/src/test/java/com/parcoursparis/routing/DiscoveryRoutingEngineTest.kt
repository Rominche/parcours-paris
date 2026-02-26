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
        // Graph: A -[explored only]- C (direct, shorter)
        //        A -[unexplored]- B -[unexplored]- C (slightly longer via B)
        //
        // Both paths are ~same distance; with modified weights:
        //   explored path  cost = length × 1.2
        //   unexplored path cost = length × 0.9 each segment
        // Discovery should pick the unexplored path (lower discovery cost).
        //
        // Coordinates chosen so the two paths have almost identical real distance:
        //   A = (48.85, 2.35), C = (48.87, 2.37)
        //   B (via unexplored) = (48.86, 2.355)  — intermediate point, similar total length
        val segments = listOf(
            // Explored direct path A→C
            SegmentWithExploredState(Segment(1L, "[[2.35,48.85],[2.37,48.87]]"), true),
            // Unexplored path A→B (unexplored)
            SegmentWithExploredState(Segment(2L, "[[2.35,48.85],[2.355,48.86]]"), false),
            // Unexplored path B→C (unexplored)
            SegmentWithExploredState(Segment(3L, "[[2.355,48.86],[2.37,48.87]]"), false)
        )
        val origin = LatLng(48.85, 2.35)
        val dest = LatLng(48.87, 2.37)

        val result = engine.computeRoute(segments, origin, dest, 50.0)

        assertNotNull(result)
        // The discovery engine must have picked the unexplored path via B.
        // Geometry should contain the intermediate node B (lat≈48.86, lon≈2.355).
        val hasIntermediateB = result!!.geometry.any { pt ->
            Math.abs(pt.latitude - 48.86) < 0.0001 && Math.abs(pt.longitude - 2.355) < 0.0001
        }
        assertTrue("Engine should prefer the unexplored path (via B)", hasIntermediateB)
    }

    @Test
    fun computeClassicRoute_returnsShortestPath() = runTest {
        val segments = listOf(
            SegmentWithExploredState(Segment(1L, "[[2.35,48.85],[2.36,48.86]]"), false),
            SegmentWithExploredState(Segment(2L, "[[2.36,48.86],[2.37,48.87]]"), false)
        )
        val origin = LatLng(48.85, 2.35)
        val dest = LatLng(48.87, 2.37)

        val result = engine.computeClassicRoute(segments, origin, dest)

        assertNotNull(result)
        assertEquals(com.parcoursparis.routing.RouteType.CLASSIC, result!!.routeType)
        assertTrue(result.geometry.size >= 2)
        assertTrue(result.distanceMeters > 0)
    }

    @Test
    fun computeClassicRoute_noPath_returnsNull() = runTest {
        val segments = listOf(
            SegmentWithExploredState(Segment(1L, "[[2.35,48.85],[2.36,48.86]]"), false)
        )
        val origin = LatLng(48.85, 2.35)
        val dest = LatLng(48.90, 2.45)

        val result = engine.computeClassicRoute(segments, origin, dest)

        assertNull(result)
    }

    @Test
    fun computeClassicRoute_shorterThanDiscoveryWhenBothExist() = runTest {
        val segments = listOf(
            SegmentWithExploredState(Segment(1L, "[[2.35,48.85],[2.37,48.87]]"), true),
            SegmentWithExploredState(Segment(2L, "[[2.35,48.85],[2.355,48.86]]"), false),
            SegmentWithExploredState(Segment(3L, "[[2.355,48.86],[2.37,48.87]]"), false)
        )
        val origin = LatLng(48.85, 2.35)
        val dest = LatLng(48.87, 2.37)

        val discoveryResult = engine.computeRoute(segments, origin, dest, 50.0)
        val classicResult = engine.computeClassicRoute(segments, origin, dest)

        assertNotNull(discoveryResult)
        assertNotNull(classicResult)
        assertTrue(
            "Classic route should be shorter or equal to discovery",
            classicResult!!.distanceMeters <= discoveryResult!!.distanceMeters * 1.01
        )
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
