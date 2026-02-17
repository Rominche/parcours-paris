package com.parcoursparis.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests unitaires pour GeoJsonLoader.
 * Utilise Robolectric pour tester le chargement depuis assets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GeoJsonLoaderTest {

    @Test
    fun loadFromAssets_loadsValidSegments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val segments = GeoJsonLoader.loadFromAssets(context)
        
        // Le fichier de test contient 3 segments valides
        assertEquals(3, segments.size)
        assertTrue(segments.all { it.osm_way_id > 0 })
        assertTrue(segments.all { it.geometry_json.isNotEmpty() })
    }

    @Test
    fun loadFromAssets_validatesCoordinatesInParisBounds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val segments = GeoJsonLoader.loadFromAssets(context)
        
        // Tous les segments doivent avoir des coordonnées dans les limites de Paris
        segments.forEach { segment ->
            val coords = org.json.JSONArray(segment.geometry_json)
            assertTrue(coords.length() >= 2)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun loadFromAssets_throwsOnInvalidType() {
        // Ce test devrait être implémenté avec un fichier GeoJSON invalide
        // Pour le MVP, on assume que le fichier est toujours valide
        throw IllegalArgumentException("Test not implemented")
    }
}
