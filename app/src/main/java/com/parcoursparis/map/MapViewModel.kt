package com.parcoursparis.map

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcoursparis.data.repository.SegmentRepository
import com.parcoursparis.map.geocoding.BoundingBox
import com.parcoursparis.routing.DiscoveryRoutingEngine
import com.parcoursparis.map.geocoding.GeocodingNetworkException
import com.parcoursparis.map.geocoding.GeocodingResult
import com.parcoursparis.map.geocoding.GeocodingService
import com.parcoursparis.util.locationFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

/** Paris area bounds for geocoding (Nominatim viewbox). */
private val PARIS_BOUNDS = BoundingBox(
    minLon = 2.2,
    minLat = 48.8,
    maxLon = 2.4,
    maxLat = 48.92
)

/**
 * ViewModel for the map screen.
 * Collects segmentsWithExploredState from SegmentRepository and exposes MapUiState.
 * Geocoding: debounced search, destination selection, offline handling.
 */
class MapViewModel(
    private val segmentRepository: SegmentRepository,
    private val geocodingService: GeocodingService,
    private val discoveryRoutingEngine: DiscoveryRoutingEngine,
    private val appContext: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState(isLoading = true))
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var searchJob: Job? = null

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

    /** Updates search query; debounced search is triggered from UI (LaunchedEffect). */
    fun onSearchQueryChange(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                searchError = null,
                searchSuggestions = if (query.isBlank()) emptyList() else it.searchSuggestions
            )
        }
    }

    /**
     * Performs geocoding search (call after debounce). Updates suggestions or searchError.
     */
    fun onSearchQuerySubmit(query: String) {
        if (query.isBlank()) {
            searchJob?.cancel()
            _uiState.update {
                it.copy(searchSuggestions = emptyList(), isSearching = false, searchError = null)
            }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            try {
                val results = geocodingService.search(query, PARIS_BOUNDS)
                _uiState.update {
                    it.copy(
                        searchSuggestions = results,
                        isSearching = false,
                        searchError = null
                    )
                }
            } catch (e: GeocodingNetworkException) {
                _uiState.update {
                    it.copy(
                        searchSuggestions = emptyList(),
                        isSearching = false,
                        searchError = e.message ?: "Connectez-vous pour rechercher une adresse"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        searchSuggestions = emptyList(),
                        isSearching = false,
                        searchError = e.message ?: "Erreur de recherche"
                    )
                }
            }
        }
    }

    /** Called when user selects a suggestion; sets destination and clears suggestions. */
    fun onDestinationSelected(result: GeocodingResult) {
        _uiState.update {
            it.copy(
                destination = result.toLatLng(),
                searchSuggestions = emptyList(),
                searchQuery = result.label,
                searchError = null
            )
        }
    }

    /** Clears destination and search state (e.g. when clearing the field). */
    fun onClearDestination() {
        _uiState.update {
            it.copy(
                destination = null,
                searchSuggestions = emptyList(),
                searchError = null,
                route = null,
                routeError = null
            )
        }
    }

    /**
     * Demande le calcul d'un itinéraire discovery (origine = position utilisateur, destination).
     * Gère : pas de position, pas de destination, aucun chemin trouvé.
     */
    fun onRequestRoute() {
        val dest = _uiState.value.destination
        val origin = _uiState.value.userLocation
        val segments = _uiState.value.segments

        if (dest == null) {
            _uiState.update { it.copy(routeError = "Aucune destination définie") }
            return
        }
        if (origin == null) {
            _uiState.update { it.copy(routeError = "Position non disponible") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isComputingRoute = true, routeError = null, route = null)
            }
            try {
                val result = discoveryRoutingEngine.computeRoute(
                    segments = segments,
                    origin = origin,
                    destination = dest,
                    tolerancePercent = 15.0
                )
                _uiState.update {
                    it.copy(
                        isComputingRoute = false,
                        route = result,
                        routeError = if (result == null) "Aucun chemin trouvé" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isComputingRoute = false,
                        route = null,
                        routeError = e.message ?: "Erreur de calcul d'itinéraire"
                    )
                }
            }
        }
    }
}
