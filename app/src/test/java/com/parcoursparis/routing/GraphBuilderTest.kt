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

class GraphBuilderTest {

    private fun seg(id: Long, json: String, explored: Boolean = false) =
        SegmentWithExploredState(Segment(id, json), explored)

    /**
     * Deux rues en croix qui se coupent au milieu de leur géométrie (cas OSM réel) :
     * le carrefour doit devenir un nœud, sinon aucun chemin ne relie les deux rues.
     */
    private val crossingStreets = listOf(
        seg(1L, "[[2.340,48.850],[2.345,48.850],[2.350,48.850]]"), // est-ouest
        seg(2L, "[[2.345,48.845],[2.345,48.850],[2.345,48.855]]")  // nord-sud, croise en (2.345,48.850)
    )

    @Test
    fun build_splitsWaysAtSharedInteriorVertex() {
        val graph = GraphBuilder.build(crossingStreets)
        assertEquals(5, graph.nodeCount) // 4 extrémités + 1 carrefour
        assertEquals(4, graph.edgeCount) // chaque rue coupée en deux
        assertEquals(1, graph.component.distinct().size)
    }

    @Test
    fun computeRoute_crossesIntersectionInsideWays() = runTest {
        val engine = DiscoveryRoutingEngineImpl()
        val result = engine.computeClassicRoute(
            crossingStreets,
            origin = LatLng(48.850, 2.340),
            destination = LatLng(48.855, 2.345)
        )
        assertNotNull(result)
        // Ouest → carrefour (~367 m) → nord (~556 m)
        assertTrue(result!!.distanceMeters in 850.0..1000.0)
    }

    @Test
    fun computeRoute_doesNotSnapToIsolatedFragment() = runTest {
        val segments = crossingStreets + seg(3L, "[[2.3401,48.8501],[2.3402,48.8502]]")
        val engine = DiscoveryRoutingEngineImpl()
        val result = engine.computeClassicRoute(
            segments,
            origin = LatLng(48.8501, 2.3401), // pile sur le fragment isolé
            destination = LatLng(48.855, 2.345)
        )
        assertNotNull("Le départ doit être rattaché au réseau principal", result)
    }

    @Test
    fun computeRoute_pointFarFromNetwork_returnsNull() = runTest {
        val engine = DiscoveryRoutingEngineImpl()
        val result = engine.computeClassicRoute(
            crossingStreets,
            origin = LatLng(48.850, 2.340),
            destination = LatLng(48.900, 2.450)
        )
        assertNull(result)
    }

    @Test
    fun computeRoute_staysWithinTolerance() = runTest {
        // Chemin direct exploré A→C, détour non exploré A→B→C bien plus long (+60 %).
        val segments = listOf(
            seg(1L, "[[2.350,48.850],[2.360,48.850]]", explored = true),
            seg(2L, "[[2.350,48.850],[2.355,48.854]]"),
            seg(3L, "[[2.355,48.854],[2.360,48.850]]")
        )
        val engine = DiscoveryRoutingEngineImpl()
        val origin = LatLng(48.850, 2.350)
        val dest = LatLng(48.850, 2.360)
        val classic = engine.computeClassicRoute(segments, origin, dest)!!
        val strict = engine.computeRoute(segments, origin, dest, 15.0)!!
        assertEquals(classic.distanceMeters, strict.distanceMeters, 1.0)
        val loose = engine.computeRoute(segments, origin, dest, 100.0)!!
        assertTrue(loose.distanceMeters > classic.distanceMeters * 1.3)
    }
}
