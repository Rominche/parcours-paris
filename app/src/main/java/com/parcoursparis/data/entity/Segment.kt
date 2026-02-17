package com.parcoursparis.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import org.json.JSONArray

/**
 * Représente un segment de rue OSM (way entre intersections).
 * La géométrie est stockée en JSON (LineString coordinates).
 */
@Entity(tableName = "segment")
data class Segment(
    @PrimaryKey val osm_way_id: Long,
    val geometry_json: String
) {
    /**
     * Valide que geometry_json contient du JSON valide.
     * @throws IllegalArgumentException si le JSON est invalide
     */
    fun validateGeometry() {
        try {
            val coords = JSONArray(geometry_json)
            require(coords.length() >= 2) { "Geometry must have at least 2 coordinates" }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid geometry_json: ${e.message}", e)
        }
    }
}

/**
 * TypeConverter pour la géométrie (si besoin futur de convertir en objet Kotlin).
 * Actuellement, on garde le String brut pour simplifier le MVP.
 */
class GeometryConverters {
    @TypeConverter
    fun fromJson(value: String?): List<List<Double>>? {
        if (value == null) return null
        try {
            val coords = JSONArray(value)
            val result = mutableListOf<List<Double>>()
            for (i in 0 until coords.length()) {
                val coord = coords.getJSONArray(i)
                result.add(listOf(coord.getDouble(0), coord.getDouble(1)))
            }
            return result
        } catch (e: Exception) {
            return null
        }
    }

    @TypeConverter
    fun toJson(list: List<List<Double>>?): String? {
        if (list == null) return null
        val array = JSONArray()
        list.forEach { coord ->
            val coordArray = JSONArray()
            coordArray.put(coord[0])
            coordArray.put(coord[1])
            array.put(coordArray)
        }
        return array.toString()
    }
}
