package com.parcoursparis.map

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcoursparis.data.preferences.UserPreferencesRepository
import com.parcoursparis.data.repository.SegmentRepository
import com.parcoursparis.map.geocoding.BoundingBox
import com.parcoursparis.map.geocoding.GeocodingNetworkException
import com.parcoursparis.map.geocoding.GeocodingResult
import com.parcoursparis.map.geocoding.GeocodingService
import com.parcoursparis.routing.DiscoveryRoutingEngine
import com.parcoursparis.routing.RoutingRequest
import com.parcoursparis.util.RouteProgressUtils
import com.parcoursparis.util.bearingDegrees
import com.parcoursparis.util.haversineMeters
import com.parcoursparis.util.locationFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

/** Centre de Paris — fallback quand la position GPS n'est pas disponible (émulateur, intérieur). */
private val PARIS_CENTER = LatLng(48.8566, 2.3522)

/**
 * ViewModel for the map screen.
 * Collects segmentsWithExploredState from SegmentRepository and exposes MapUiState.
 * Flow collection in ViewModelScope avoids heavy work on main thread (NFR-P1).
 */
class MapViewModel(
    private val segmentRepository: SegmentRepository,
    private val geocodingService: GeocodingService,
    private val discoveryRoutingEngine: DiscoveryRoutingEngine,
    private val userPreferences: UserPreferencesRepository,
    private val appContext: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState(isLoading = true))
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var searchJob: Job? = null
    private var computeRouteJob: Job? = null
    private var toleranceDebounceJob: Job? = null
    private var segmentSelectionJob: Job? = null
    private var smoothedBearing: Double = 0.0

    init {
        viewModelScope.launch {
            userPreferences.tolerancePercent.collect { tolerance ->
                _uiState.update { it.copy(tolerancePercent = tolerance) }
            }
        }
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
        viewModelScope.launch {
            segmentRepository.progressStats.collect { stats ->
                _uiState.update { it.copy(progressStats = stats) }
            }
        }
    }

    private fun updateSegmentSelectionEnabled(state: MapUiState): MapUiState {
        val enabled = state.destination == null && state.route == null
        return if (enabled) {
            state.copy(isSegmentSelectionEnabled = true)
        } else {
            state.copy(
                isSegmentSelectionEnabled = false,
                selectedSegmentId = null
            )
        }
    }

    /**
     * Appelé au tap sur la carte : sélectionne le segment le plus proche si le marquage est actif.
     * La recherche s'exécute hors thread principal pour rester fluide sur mobile.
     */
    fun onMapTap(latLng: LatLng, zoom: Double, displayDensity: Float) {
        val state = _uiState.value
        if (!state.isSegmentSelectionEnabled || state.segments.isEmpty()) return
        val segments = state.segments
        val tolerance = SegmentSelectionUtils.touchToleranceMeters(
            zoom = zoom,
            latitude = latLng.latitude,
            density = displayDensity
        )
        segmentSelectionJob?.cancel()
        segmentSelectionJob = viewModelScope.launch {
            val nearest = SegmentSelectionUtils.findNearestSegment(
                tapLat = latLng.latitude,
                tapLon = latLng.longitude,
                segments = segments,
                maxDistanceMeters = tolerance
            )
            _uiState.update {
                it.copy(selectedSegmentId = nearest?.segment?.osm_way_id)
            }
        }
    }

    fun onClearSegmentSelection() {
        _uiState.update { it.copy(selectedSegmentId = null) }
    }

    fun onMarkSelectedExplored() {
        val segmentId = _uiState.value.selectedSegmentId ?: return
        viewModelScope.launch {
            segmentRepository.markAsExplored(segmentId, System.currentTimeMillis())
        }
    }

    fun onMarkSelectedUnexplored() {
        val segmentId = _uiState.value.selectedSegmentId ?: return
        viewModelScope.launch {
            segmentRepository.markAsUnexplored(segmentId)
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
                            val latLng = location?.let { LatLng(it.latitude, it.longitude) }
                            val bearing = if (state.route != null && location != null && latLng != null) {
                                computeNavigationBearing(
                                    previous = state.userLocation,
                                    current = latLng,
                                    location = location
                                )
                            } else {
                                0.0
                            }
                            val updated = state.copy(
                                userLocation = latLng,
                                mapBearing = bearing
                            )
                            updateRouteProgress(updated)
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
            updateSegmentSelectionEnabled(
                it.copy(
                    destination = result.toLatLng(),
                    searchSuggestions = emptyList(),
                    searchQuery = result.label,
                    searchError = null
                )
            )
        }
    }

    /** Clears destination and search state (e.g. when clearing the field). */
    fun onClearDestination() {
        _uiState.update {
            updateSegmentSelectionEnabled(
                it.copy(
                    destination = null,
                    originOverride = null,
                    searchSuggestions = emptyList(),
                    searchError = null,
                    route = null,
                    discoveryRoute = null,
                    classicRoute = null,
                    routeError = null,
                    showRouteBottomSheet = false,
                    usedParisAsFallback = false
                )
            )
        }
    }

    /** Appelé quand l'utilisateur revient de la page de recherche avec destination (et optionnellement origine). */
    fun onAddressSearchResult(destination: LatLng, originOverride: LatLng?) {
        _uiState.update {
            updateSegmentSelectionEnabled(
                it.copy(
                    destination = destination,
                    originOverride = originOverride,
                    searchQuery = "",
                    searchSuggestions = emptyList(),
                    searchError = null
                )
            )
        }
    }

    /** Bascule vers l'itinéraire classique (plus rapide) si disponible. */
    fun onRequestClassicRoute() {
        val classic = _uiState.value.classicRoute
        if (classic != null) {
            _uiState.update {
                updateSegmentSelectionEnabled(
                    updateRouteProgress(
                        it.copy(
                            route = classic,
                            routeProgressPercent = 0,
                            distanceRemainingMeters = classic.distanceMeters
                        )
                    )
                )
            }
        }
    }

    /** Bascule vers l'itinéraire découverte si disponible. */
    fun onRequestDiscoveryRoute() {
        val discovery = _uiState.value.discoveryRoute
        if (discovery != null) {
            _uiState.update {
                updateSegmentSelectionEnabled(
                    updateRouteProgress(
                        it.copy(
                            route = discovery,
                            routeProgressPercent = 0,
                            distanceRemainingMeters = discovery.distanceMeters
                        )
                    )
                )
            }
        }
    }

    /** Arrête le trajet en cours et retire l'itinéraire de la carte. */
    fun onStopRoute() {
        computeRouteJob?.cancel()
        toleranceDebounceJob?.cancel()
        _uiState.update {
            updateSegmentSelectionEnabled(
                it.copy(
                    destination = null,
                    originOverride = null,
                    searchQuery = "",
                    searchSuggestions = emptyList(),
                    searchError = null,
                    route = null,
                    discoveryRoute = null,
                    classicRoute = null,
                    routeError = null,
                    showRouteBottomSheet = false,
                    routeProgressPercent = 0,
                    distanceRemainingMeters = 0.0,
                    usedParisAsFallback = false,
                    isComputingRoute = false,
                    mapBearing = 0.0
                )
            )
        }
        smoothedBearing = 0.0
    }

    /** Appelé quand l'utilisateur ferme le bottom sheet manuellement. */
    fun onDismissRouteBottomSheet() {
        onStopRoute()
    }

    /**
     * Demande le calcul d'un itinéraire discovery (origine = position utilisateur, destination).
     * Gère : pas de position, pas de destination, aucun chemin trouvé.
     * @param useParisAsFallback si true et position absente, utilise le centre de Paris (émulateur/démo)
     */
    fun onRequestRoute(useParisAsFallback: Boolean = false) {
        val state = _uiState.value
        val dest = state.destination
        var origin = state.originOverride ?: state.userLocation
        val segments = state.segments
        val useFallback = useParisAsFallback || (origin == null && state.usedParisAsFallback)

        if (dest == null) {
            _uiState.update { it.copy(routeError = "Aucune destination définie") }
            return
        }
        if (origin == null) {
            if (useFallback) {
                origin = PARIS_CENTER
            } else {
                _uiState.update { it.copy(routeError = "Position non disponible") }
                return
            }
        }

        val tolerance = _uiState.value.tolerancePercent.toDouble()
        val request = RoutingRequest(origin = origin, destination = dest, tolerancePercent = tolerance)

        computeRouteJob?.cancel()
        computeRouteJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isComputingRoute = true, routeError = null, route = null)
            }
            try {
                val (discoveryResult, classicResult) = coroutineScope {
                    val discoveryDeferred = async {
                        discoveryRoutingEngine.computeRoute(
                            segments = segments,
                            origin = request.origin,
                            destination = request.destination,
                            tolerancePercent = request.tolerancePercent
                        )
                    }
                    val classicDeferred = async {
                        discoveryRoutingEngine.computeClassicRoute(
                            segments = segments,
                            origin = request.origin,
                            destination = request.destination
                        )
                    }
                    discoveryDeferred.await() to classicDeferred.await()
                }
                val result = discoveryResult ?: classicResult
                if (result != null) {
                    _uiState.update {
                        updateSegmentSelectionEnabled(
                            updateRouteProgress(
                                it.copy(
                                    isComputingRoute = false,
                                    route = result,
                                    discoveryRoute = discoveryResult,
                                    classicRoute = classicResult,
                                    routeError = null,
                                    showRouteBottomSheet = true,
                                    routeProgressPercent = 0,
                                    distanceRemainingMeters = result.distanceMeters,
                                    usedParisAsFallback = useFallback
                                )
                            )
                        )
                    }
                } else {
                    _uiState.update {
                        updateSegmentSelectionEnabled(
                            it.copy(
                                isComputingRoute = false,
                                route = null,
                                discoveryRoute = null,
                                classicRoute = null,
                                routeError = "Aucun chemin trouvé"
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isComputingRoute = false) }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isComputingRoute = false,
                        route = null,
                        discoveryRoute = null,
                        classicRoute = null,
                        routeError = e.message ?: "Erreur de calcul d'itinéraire"
                    )
                }
            }
        }
    }

    /**
     * Met à jour la progression le long de l'itinéraire quand route != null et userLocation != null.
     * En cas de perte GPS (userLocation = null), la route reste affichée, progression inchangée.
     */
    private fun updateRouteProgress(state: MapUiState): MapUiState {
        val route = state.route
        val userLocation = state.userLocation
        if (route == null || userLocation == null || route.geometry.size < 2) {
            return state
        }
        val segmentIndex = RouteProgressUtils.projectPointOnPolyline(userLocation, route.geometry)
        if (segmentIndex < 0) return state
        val remaining = RouteProgressUtils.distanceRemaining(route.geometry, segmentIndex, userLocation)
        val total = route.distanceMeters
        val progressPercent = if (total > 0) {
            val traveled = total - remaining
            ((traveled / total) * 100).toInt().coerceIn(0, 100)
        } else 0
        return state.copy(
            routeProgressPercent = progressPercent,
            distanceRemainingMeters = remaining
        )
    }

    /**
     * Appelé quand l'utilisateur ajuste la tolérance dans le bottom sheet.
     * Debounce 300 ms pour éviter de lancer Dijkstra à chaque tick du slider.
     * Annule tout calcul et debounce précédents avant d'en lancer un nouveau.
     */
    fun onToleranceChanged(newValue: Int) {
        val clamped = newValue.coerceIn(10, 25)
        _uiState.update { it.copy(tolerancePercent = clamped) }
        computeRouteJob?.cancel()
        toleranceDebounceJob?.cancel()
        toleranceDebounceJob = viewModelScope.launch {
            delay(300L)
            userPreferences.setTolerancePercent(clamped)
            onRequestRoute()
        }
    }

    private fun computeNavigationBearing(
        previous: LatLng?,
        current: LatLng,
        location: android.location.Location
    ): Double {
        val rawBearing = when {
            location.hasBearing() && location.hasSpeed() && location.speed >= 0.5f ->
                location.bearing.toDouble()
            previous != null -> {
                val movedMeters = haversineMeters(
                    previous.latitude,
                    previous.longitude,
                    current.latitude,
                    current.longitude
                )
                if (movedMeters >= 3.0) {
                    bearingDegrees(
                        previous.latitude,
                        previous.longitude,
                        current.latitude,
                        current.longitude
                    )
                } else {
                    smoothedBearing
                }
            }
            else -> smoothedBearing
        }
        smoothedBearing = smoothBearing(smoothedBearing, rawBearing)
        return smoothedBearing
    }

    private fun smoothBearing(current: Double, target: Double): Double {
        var diff = target - current
        while (diff > 180.0) diff -= 360.0
        while (diff < -180.0) diff += 360.0
        return (current + diff * 0.35 + 360.0) % 360.0
    }
}
