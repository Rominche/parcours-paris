package com.parcoursparis.map

import com.parcoursparis.data.entity.Segment
import com.parcoursparis.data.repository.SegmentWithExploredState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for SegmentGeoJsonConverter.
 * Uses Robolectric for org.json (Android SDK) support.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SegmentGeoJsonConverterTest {

    @Test
    fun toFeatureCollectionJson_emptyList_returnsValidFeatureCollection() {
        val json = SegmentGeoJsonConverter.toFeatureCollectionJson(emptyList())
        val obj = JSONObject(json)
        assertEquals("FeatureCollection", obj.getString("type"))
        assertTrue(obj.getJSONArray("features").length() == 0)
    }

    @Test
    fun toFeatureCollectionJson_singleSegment_hasCorrectStructure() {
        val segments = listOf(
            SegmentWithExploredState(
                segment = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]"),
                isExplored = true
            )
        )
        val json = SegmentGeoJsonConverter.toFeatureCollectionJson(segments)
        val obj = JSONObject(json)
        assertEquals("FeatureCollection", obj.getString("type"))
        val features = obj.getJSONArray("features")
        assertEquals(1, features.length())
        val feature = features.getJSONObject(0)
        assertEquals("Feature", feature.getString("type"))
        assertEquals(true, feature.getJSONObject("properties").getBoolean("isExplored"))
        assertEquals("LineString", feature.getJSONObject("geometry").getString("type"))
    }

    @Test
    fun toFeatureCollectionJson_exploredAndUnexplored() {
        val segments = listOf(
            SegmentWithExploredState(Segment(1001L, "[[2.35,48.85],[2.36,48.86]]"), true),
            SegmentWithExploredState(Segment(1002L, "[[2.36,48.86],[2.37,48.87]]"), false)
        )
        val json = SegmentGeoJsonConverter.toFeatureCollectionJson(segments)
        val obj = JSONObject(json)
        val features = obj.getJSONArray("features")
        assertEquals(2, features.length())
        assertEquals(true, features.getJSONObject(0).getJSONObject("properties").getBoolean("isExplored"))
        assertEquals(false, features.getJSONObject(1).getJSONObject("properties").getBoolean("isExplored"))
    }
}
