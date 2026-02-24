package com.parcoursparis.map

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcoursparis.data.repository.SegmentRepository
import com.parcoursparis.data.repository.SegmentWithExploredState
import com.parcoursparis.util.locationFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/**
 * ViewModel for the map screen.
 * Collects segmentsWithExploredState from SegmentRepository and exposes MapUiState.
 * Flow collection in ViewModelScope avoids heavy work on main thread (NFR-P1).
 */
class MapViewModel(
    private val segmentRepository: SegmentRepository,
    private val appContext: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState(isLoading = true))
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null

    init {
        viewModelScope.launch {
            segmentRepository.segmentsWithExploredState
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Unknown error"
                        )
                    }
                }
                .collect { segments ->
                    _uiState.update {
                        it.copy(
                            segments = segments,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    /**
     * Called when the user responds to the location permission request.
     * When granted, starts collecting GPS updates and updating userLocation.
     */
    fun onLocationPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                locationPermissionGranted = granted,
                locationPermissionDenied = !granted
            )
        }
        if (granted) {
            locationJob?.cancel()
            locationJob = viewModelScope.launch {
                locationFlow(appContext)
                    .catch { _uiState.update { it.copy(userLocation = null) } }
                    .collect { location ->
                        _uiState.update { state ->
                            state.copy(
                                userLocation = location?.let {
                                    LatLng(it.latitude, it.longitude)
                                }
                            )
                        }
                    }
            }
        }
    }
}
