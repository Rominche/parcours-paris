package com.parcoursparis

import android.app.Application
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.parcoursparis.data.GeoJsonLoader
import com.parcoursparis.data.db.AppDatabase
import com.parcoursparis.data.repository.SegmentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
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
