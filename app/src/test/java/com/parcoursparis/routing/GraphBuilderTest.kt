package com.parcoursparis.routing

import com.parcoursparis.data.entity.Segment
import com.parcoursparis.data.repository.SegmentWithExploredState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

/**
 * Unit tests for GraphBuilder.
 * Verifies graph construction from segments with correct nodes and edges.
 */
class GraphBuilderTest {

    @Test
    fun parseGeometry_returnsCorrectLatLngList() {
        val json = "[[2.35,48.85],[2.36,48.86]]"
        val result = GraphBuilder.parseGeometry(json)
        assertEquals(2, result.size)
        assertEquals(48.85, result[0].latitude, 1e-6)
        assertEquals(2.35, result[0].longitude, 1e-6)
        assertEquals(48.86, result[1].latitude, 1e-6)
        assertEquals(2.36, result[1].longitude, 1e-6)
    }

    @Test
    fun segmentLength_returnsPositiveDistance() {
        val geom = listOf(
            LatLng(48.85, 2.35),
            LatLng(48.86, 2.36)
        )
        val length = GraphBuilder.segmentLength(geom)
        assertTrue(length > 0)
        assertTrue(length < 2000) // ~1.5km for Paris coords
    }

    @Test
    fun build_createsGraphWithNodesAndEdges() {
        val segments = listOf(
            SegmentWithExploredState(Segment(1001L, "[[2.35,48.85],[2.36,48.86]]"), false),
            SegmentWithExploredState(Segment(1002L, "[[2.36,48.86],[2.37,48.87]]"), false)
        )
        val graph = GraphBuilder.build(segments)

        assertEquals(3, graph.nodes.size)
        assertTrue(graph.edges.isNotEmpty())

        val fromKey = GraphBuilder.nodeKey(48.85, 2.35)
        val neighbors = graph.getNeighbors(fromKey)
        assertEquals(1, neighbors.size)
        assertEquals(1001L, neighbors[0].segmentId)
        assertEquals(false, neighbors[0].isExplored)
    }

    @Test
    fun build_bidirectionalEdges() {
        val segments = listOf(
            SegmentWithExploredState(Segment(1001L, "[[2.35,48.85],[2.36,48.86]]"), true)
        )
        val graph = GraphBuilder.build(segments)

        val fromKey = GraphBuilder.nodeKey(48.85, 2.35)
        val toKey = GraphBuilder.nodeKey(48.86, 2.36)

        val fromNeighbors = graph.getNeighbors(fromKey)
        val toNeighbors = graph.getNeighbors(toKey)

        assertEquals(1, fromNeighbors.size)
        assertEquals(1, toNeighbors.size)
        assertEquals(toKey, fromNeighbors[0].toNodeKey)
        assertEquals(fromKey, toNeighbors[0].toNodeKey)
        assertTrue(fromNeighbors[0].isExplored)
    }
}
