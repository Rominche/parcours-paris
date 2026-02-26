package com.parcoursparis

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.parcoursparis.data.GeoJsonLoader
import com.parcoursparis.data.db.AppDatabase
import com.parcoursparis.data.preferences.DataStoreUserPreferencesRepository
import com.parcoursparis.data.preferences.UserPreferencesRepository
import com.parcoursparis.data.repository.SegmentRepository
import com.parcoursparis.map.geocoding.GeocodingService
import com.parcoursparis.map.geocoding.NominatimGeocodingService
import com.parcoursparis.routing.DiscoveryRoutingEngine
import com.parcoursparis.routing.DiscoveryRoutingEngineImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import java.io.FileNotFoundException

class ParcoursParisApplication : Application() {

    private val TAG = "ParcoursParisApp"

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "parcours_paris_db"
        ).build()
    }

    val segmentRepository: SegmentRepository by lazy {
        SegmentRepository(
            segmentDao = database.segmentDao(),
            segmentVisitDao = database.segmentVisitDao()
        )
    }

    val geocodingService: GeocodingService by lazy {
        NominatimGeocodingService()
    }

    val discoveryRoutingEngine: DiscoveryRoutingEngine by lazy {
        DiscoveryRoutingEngineImpl()
    }

    val userPreferences: UserPreferencesRepository by lazy {
        DataStoreUserPreferencesRepository(applicationContext)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // MapLibre must be initialized before any MapView is created (required by SDK 12+).
        // Empty API key is valid for public tiles (e.g. demotiles.maplibre.org).
        MapLibre.getInstance(applicationContext, "", WellKnownTileServer.MapLibre)
        applicationScope.launch {
            loadSegmentsIfNeeded()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }

    private suspend fun loadSegmentsIfNeeded() {
        try {
            val segments = GeoJsonLoader.loadFromAssets(applicationContext)
            if (segments.isEmpty()) {
                Log.w(TAG, "GeoJSON loaded but no valid segments found")
            } else {
                segmentRepository.insertSegmentsIfEmpty(segments)
                Log.i(TAG, "Segments loaded successfully")
            }
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "GeoJSON file not found in assets: paris_segments.geojson", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid GeoJSON format: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error loading segments: ${e.message}", e)
        }
    }
}
