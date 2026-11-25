package com.parcoursparis.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeometryParserTest {

    @Test
    fun parse_lineString() {
        val values = GeometryParser.parseLonLat("[[2.35,48.85],[-0.5,4.2e1]]")
        assertArrayEquals(doubleArrayOf(2.35, 48.85, -0.5, 42.0), values, 1e-12)
    }

    @Test
    fun parse_invalid_returnsNull() {
        assertNull(GeometryParser.parseLonLat("[[2.35,48.85]]"))
        assertNull(GeometryParser.parseLonLat("[[2.35,48.85],[2.36]]"))
        assertNull(GeometryParser.parseLonLat("not json"))
    }
}
