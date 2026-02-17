package com.parcoursparis.data

import android.content.Context
import android.util.Log
import com.parcoursparis.data.entity.Segment
import org.json.JSONObject
import java.io.IOException

/**
 * Charge et parse le fichier GeoJSON des segments Paris depuis assets.
 * Format attendu : FeatureCollection avec Feature contenant properties.osm_way_id
 * et geometry de type LineString.
 */
object GeoJsonLoader {

    private const val TAG = "GeoJsonLoader"
    private const val PARIS_MIN_LAT = 48.8
    private const val PARIS_MAX_LAT = 48.9
    private const val PARIS_MIN_LON = 2.2
    private const val PARIS_MAX_LON = 2.5

    /**
     * Charge paris_segments.geojson depuis assets et retourne la liste des Segment.
     * @throws IOException si le fichier est absent ou illisible
     * @throws IllegalArgumentException si le format GeoJSON est invalide
     */
    fun loadFromAssets(context: Context): List<Segment> {
        context.assets.open("paris_segments.geojson").bufferedReader().use { reader ->
            val json = JSONObject(reader.readText())
            
            // Validation du type FeatureCollection
            if (json.optString("type") != "FeatureCollection") {
                throw IllegalArgumentException("GeoJSON type must be FeatureCollection, got: ${json.optString("type")}")
            }
            
            val features = json.getJSONArray("features")
            val segments = mutableListOf<Segment>()
            
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                
                // Validation des properties
                if (!feature.has("properties")) {
                    Log.w(TAG, "Feature $i missing properties, skipping")
                    continue
                }
                
                val props = feature.getJSONObject("properties")
                val osmWayId = props.optLong("osm_way_id", -1L)
                
                if (osmWayId <= 0) {
                    Log.w(TAG, "Feature $i has invalid osm_way_id: $osmWayId, skipping")
                    continue
                }
                
                // Validation de la geometry
                if (!feature.has("geometry")) {
                    Log.w(TAG, "Feature $i missing geometry, skipping")
                    continue
                }
                
                val geometry = feature.getJSONObject("geometry")
                val geometryType = geometry.optString("type")
                
                if (geometryType != "LineString") {
                    Log.w(TAG, "Feature $i has invalid geometry type: $geometryType, skipping")
                    continue
                }
                
                val coordinates = geometry.getJSONArray("coordinates")
                
                if (coordinates.length() < 2) {
                    Log.w(TAG, "Feature $i has less than 2 coordinates, skipping")
                    continue
                }
                
                // Validation basique des coordonnées (dans la zone de Paris)
                var validCoordinates = true
                for (j in 0 until coordinates.length()) {
                    val coord = coordinates.getJSONArray(j)
                    if (coord.length() < 2) {
                        validCoordinates = false
                        break
                    }
                    val lon = coord.getDouble(0)
                    val lat = coord.getDouble(1)
                    
                    if (lon < PARIS_MIN_LON || lon > PARIS_MAX_LON || 
                        lat < PARIS_MIN_LAT || lat > PARIS_MAX_LAT) {
                        Log.w(TAG, "Feature $i has coordinates outside Paris bounds, skipping")
                        validCoordinates = false
                        break
                    }
                }
                
                if (!validCoordinates) continue
                
                val geometryJson = coordinates.toString()
                segments.add(Segment(osm_way_id = osmWayId, geometry_json = geometryJson))
            }
            
            Log.i(TAG, "Loaded ${segments.size} valid segments from GeoJSON")
            return segments
        }
    }
}
