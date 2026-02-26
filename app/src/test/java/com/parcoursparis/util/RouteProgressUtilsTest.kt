package com.parcoursparis.util

import com.parcoursparis.util.RouteProgressUtils.distanceRemaining
import com.parcoursparis.util.RouteProgressUtils.projectPointOnPolyline
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.android.geometry.LatLng

/**
 * Unit tests for RouteProgressUtils.
 * Verifies: projection point on segment, distance remaining.
 */
class RouteProgressUtilsTest {

    @Test
    fun projectPointOnPolyline_pointAtStart_returns0() {
        val geometry = listOf(
            LatLng(48.85, 2.35),
            LatLng(48.86, 2.36),
            LatLng(48.87, 2.37)
        )
        val point = LatLng(48.85, 2.35)
        val index = projectPointOnPolyline(point, geometry)
        assertEquals(0, index)
    }

    @Test
    fun projectPointOnPolyline_pointAtEnd_returnsLastSegmentIndex() {
        val geometry = listOf(
            LatLng(48.85, 2.35),
            LatLng(48.86, 2.36),
            LatLng(48.87, 2.37)
        )
        val point = LatLng(48.87, 2.37)
        val index = projectPointOnPolyline(point, geometry)
        assertEquals(1, index)
    }

    @Test
    fun projectPointOnPolyline_pointOnMiddleSegment_returnsCorrectIndex() {
        val geometry = listOf(
            LatLng(48.85, 2.35),
            LatLng(48.855, 2.355),
            LatLng(48.86, 2.36),
            LatLng(48.87, 2.37)
        )
        val point = LatLng(48.855, 2.355)
        val index = projectPointOnPolyline(point, geometry)
        assertEquals(0, index)
    }

    @Test
    fun distanceRemaining_atStart_returnsFullDistance() {
        val geometry = listOf(
            LatLng(48.85, 2.35),
            LatLng(48.86, 2.36),
            LatLng(48.87, 2.37)
        )
        val remaining = distanceRemaining(geometry, 0, geometry[0])
        val fullDistance = haversineMeters(48.85, 2.35, 48.86, 2.36) +
            haversineMeters(48.86, 2.36, 48.87, 2.37)
        assertEquals(fullDistance, remaining, 1.0)
    }

    @Test
    fun distanceRemaining_atEnd_returnsZero() {
        val geometry = listOf(
            LatLng(48.85, 2.35),
            LatLng(48.86, 2.36),
            LatLng(48.87, 2.37)
        )
        val remaining = distanceRemaining(geometry, 1, geometry[2])
        assertEquals(0.0, remaining, 0.1)
    }

    @Test
    fun distanceRemaining_atMiddleSegment_returnsPartialDistance() {
        val geometry = listOf(
            LatLng(48.85, 2.35),
            LatLng(48.86, 2.36),
            LatLng(48.87, 2.37)
        )
        val remaining = distanceRemaining(geometry, 1, geometry[1])
        val expected = haversineMeters(48.86, 2.36, 48.87, 2.37)
        assertEquals(expected, remaining, 1.0)
    }

    @Test
    fun projectPointOnPolyline_emptyGeometry_returnsMinusOne() {
        val geometry = emptyList<LatLng>()
        val point = LatLng(48.85, 2.35)
        val index = projectPointOnPolyline(point, geometry)
        assertEquals(-1, index)
    }

    @Test
    fun projectPointOnPolyline_singlePointGeometry_returnsMinusOne() {
        val geometry = listOf(LatLng(48.85, 2.35))
        val point = LatLng(48.85, 2.35)
        val index = projectPointOnPolyline(point, geometry)
        assertEquals(-1, index)
    }
}
