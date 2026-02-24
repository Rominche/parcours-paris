package com.parcoursparis.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.parcoursparis.data.repository.SegmentRepository

/**
 * Factory for MapViewModel to inject SegmentRepository.
 */
class MapViewModelFactory(
    private val segmentRepository: SegmentRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(segmentRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
