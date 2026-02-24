package com.parcoursparis.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcoursparis.data.repository.SegmentRepository
import com.parcoursparis.data.repository.SegmentWithExploredState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the map screen.
 * Collects segmentsWithExploredState from SegmentRepository and exposes MapUiState.
 * Flow collection in ViewModelScope avoids heavy work on main thread (NFR-P1).
 */
class MapViewModel(
    private val segmentRepository: SegmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState(isLoading = true))
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

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
}
