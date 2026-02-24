package com.parcoursparis.map

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.parcoursparis.MainActivity
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented test for MapScreen.
 * Verifies the map displays (MapLibreMap with MapView is present) and no crash occurs.
 * Pan/zoom fluency validated manually (NFR-P1).
 */
class MapScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mapScreen_displaysMapView() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("maplibre_map").assertExists()
    }
}
