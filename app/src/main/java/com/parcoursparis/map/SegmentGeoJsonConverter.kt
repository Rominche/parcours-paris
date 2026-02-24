package com.parcoursparis.map

import android.util.Log
import com.parcoursparis.data.repository.SegmentWithExploredState
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Converts SegmentWithExploredState list to GeoJSON FeatureCollection string.
 * Used by MapLibre GeoJsonSource for segment layer rendering.
 * Format: FeatureCollection with LineString features, properties.isExplored.
 */
object SegmentGeoJsonConverter {

    private const val TAG = "SegmentGeoJsonConverter"
    private const val TYPE_FEATURE_COLLECTION = "FeatureCollection"
    private const val TYPE_FEATURE = "Feature"
    private const val TYPE_LINESTRING = "LineString"
    private const val PROP_IS_EXPLORED = "isExplored"

    /**
     * Builds a GeoJSON FeatureCollection JSON string from segments.
     * Each feature has geometry LineString (from segment.geometry_json) and property isExplored.
     * Segments with malformed geometry are skipped individually (log warning) without crashing the batch.
     */
    fun toFeatureCollectionJson(segments: List<SegmentWithExploredState>): String {
        val features = JSONArray()
        for (item in segments) {
            try {
                val coords = JSONArray(item.segment.geometry_json)
                if (coords.length() < 2) continue

                val geometry = JSONObject().apply {
                    put("type", TYPE_LINESTRING)
                    put("coordinates", coords)
                }
                val properties = JSONObject().apply {
                    put(PROP_IS_EXPLORED, item.isExplored)
                }
                val feature = JSONObject().apply {
                    put("type", TYPE_FEATURE)
                    put("geometry", geometry)
                    put("properties", properties)
                }
                features.put(feature)
            } catch (e: JSONException) {
                Log.w(TAG, "Segment ${item.segment.osm_way_id} ignoré — geometry invalide: ${e.message}")
            }
        }
        return JSONObject().apply {
            put("type", TYPE_FEATURE_COLLECTION)
            put("features", features)
        }.toString()
    }
}
