package com.parcoursparis.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadingUtilsTest {

    @Test
    fun walking_usesGpsCourse() {
        assertEquals(90.0, HeadingUtils.chooseHeading(90.0, 10.0, 1.5)!!, 1e-9)
    }

    @Test
    fun standingStill_usesCompassWithDeclination() {
        assertEquals(11.5, HeadingUtils.chooseHeading(null, 10.0, 1.5)!!, 1e-9)
        assertEquals(359.0, HeadingUtils.chooseHeading(null, -1.0)!!, 1e-9)
    }

    @Test
    fun noSource_hidesPointer() {
        assertNull(HeadingUtils.chooseHeading(null, null))
    }

    @Test
    fun smooth_crossesNorthTheShortWay() {
        // De 350° vers 10° : on passe par 0°, pas par 180°.
        assertEquals(0.0, HeadingUtils.smooth(350.0, 10.0, 0.5), 1e-9)
        assertEquals(-20.0, HeadingUtils.delta(10.0, 350.0), 1e-9)
    }
}
