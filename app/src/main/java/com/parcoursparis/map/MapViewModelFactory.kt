package com.parcoursparis.map

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.parcoursparis.data.repository.SegmentRepository
import com.parcoursparis.map.geocoding.GeocodingService
import com.parcoursparis.routing.DiscoveryRoutingEngine

/**
 * Factory for MapViewModel to inject SegmentRepository, GeocodingService,
 * DiscoveryRoutingEngine and Application context.
 */
class MapViewModelFactory(
    private val segmentRepository: SegmentRepository,
    private val geocodingService: GeocodingService,
    private val discoveryRoutingEngine: DiscoveryRoutingEngine,
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(segmentRepository, geocodingService, discoveryRoutingEngine, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
