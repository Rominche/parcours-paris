package com.parcoursparis.routing

import com.parcoursparis.data.entity.Segment
import com.parcoursparis.data.repository.SegmentWithExploredState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoutingSegmentFilterTest {

    private val segments = listOf(
        SegmentWithExploredState(Segment(1L, "[[2.35,48.85],[2.36,48.86]]"), false),
        SegmentWithExploredState(Segment(2L, "[[2.36,48.86],[2.37,48.87]]"), false)
    )
    private val origin = LatLng(48.85, 2.35)
    private val destination = LatLng(48.87, 2.37)

    @Test
    fun filterForRoute_includesNearbySegments() {
        val filtered = RoutingSegmentFilter.filterForRoute(
            segments = segments,
            origin = origin,
            destination = destination,
            marginDegrees = 0.004
        )
        assertEquals(2, filtered.size)
        val graph = GraphBuilder.build(filtered)
        assertTrue(graph.nodes.size >= 3)
    }

    @Test
    fun computeBothRoutes_returnsClassicAndDiscovery() = runTest {
        val engine = DiscoveryRoutingEngineImpl()
        val (discovery, classic) = engine.computeBothRoutes(
            segments = segments,
            origin = origin,
            destination = destination,
            tolerancePercent = 15.0
        )
        assertNotNull(classic)
        assertNotNull(discovery)
    }
}
