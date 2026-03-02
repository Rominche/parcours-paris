package com.parcoursparis.map

import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.parcoursparis.data.entity.Segment
import com.parcoursparis.data.preferences.FakeUserPreferencesRepository
import com.parcoursparis.data.repository.FakeSegmentDao
import com.parcoursparis.data.repository.FakeSegmentVisitDao
import com.parcoursparis.data.repository.SegmentRepository
import com.parcoursparis.map.geocoding.GeocodingNetworkException
import com.parcoursparis.map.geocoding.GeocodingResult
import com.parcoursparis.map.geocoding.FakeGeocodingService
import com.parcoursparis.routing.FakeDiscoveryRoutingEngine
import com.parcoursparis.routing.RouteResult
import com.parcoursparis.routing.RouteType
import com.parcoursparis.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager
import org.maplibre.android.geometry.LatLng

/**
 * Unit tests for MapViewModel.
 * Verifies segmentsWithExploredState is collected and exposed in MapUiState.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var segmentDao: FakeSegmentDao
    private lateinit var segmentVisitDao: FakeSegmentVisitDao
    private lateinit var repository: SegmentRepository
    private lateinit var fakeGeocoding: FakeGeocodingService
    private lateinit var fakeRoutingEngine: FakeDiscoveryRoutingEngine
    private lateinit var fakeUserPreferences: FakeUserPreferencesRepository
    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        segmentDao = FakeSegmentDao()
        segmentVisitDao = FakeSegmentVisitDao()
        repository = SegmentRepository(segmentDao, segmentVisitDao)
        fakeGeocoding = FakeGeocodingService()
        fakeRoutingEngine = FakeDiscoveryRoutingEngine()
        fakeUserPreferences = FakeUserPreferencesRepository()
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        viewModel = MapViewModel(
            repository,
            fakeGeocoding,
            fakeRoutingEngine,
            fakeUserPreferences,
            app.applicationContext as android.app.Application
        )
    }

    @Test
    fun uiState_initialLoading() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        segmentDao.insertAll(listOf(s1))

        val uiState = viewModel.uiState.first { !it.isLoading }

        assertEquals(1, uiState.segments.size)
        assertFalse(uiState.segments.first().isExplored)
        assertFalse(uiState.isLoading)
        assertEquals(null, uiState.error)
    }

    @Test
    fun uiState_exposesExploredSegments() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        val s2 = Segment(1002L, "[[2.36,48.86],[2.37,48.87]]")
        segmentDao.insertAll(listOf(s1, s2))
        segmentVisitDao.insert(com.parcoursparis.data.entity.SegmentVisit(1001L, 1000L))

        val uiState = viewModel.uiState.first { it.segments.size == 2 }

        assertTrue(uiState.segments.find { it.segment.osm_way_id == 1001L }!!.isExplored)
        assertFalse(uiState.segments.find { it.segment.osm_way_id == 1002L }!!.isExplored)
    }

    @Test
    fun onDestinationSelected_updatesDestinationInState() = runTest {
        val result = GeocodingResult("Paris, France", 48.8566, 2.3522, "Paris, France")
        viewModel.onDestinationSelected(result)

        val uiState = viewModel.uiState.value
        assertNotNull(uiState.destination)
        assertEquals(48.8566, uiState.destination!!.latitude, 1e-6)
        assertEquals(2.3522, uiState.destination!!.longitude, 1e-6)
        assertTrue(uiState.searchSuggestions.isEmpty())
    }

    @Test
    fun onSearchQuerySubmit_updatesSuggestionsWhenServiceReturnsResults() = runTest {
        fakeGeocoding.nextResult = listOf(
            GeocodingResult("Place de la Bastille, Paris", 48.8534, 2.3693, null)
        )
        viewModel.onSearchQuerySubmit("Bastille")

        val uiState = viewModel.uiState.value
        assertFalse(uiState.isSearching)
        assertEquals(1, uiState.searchSuggestions.size)
        assertEquals("Place de la Bastille, Paris", uiState.searchSuggestions[0].label)
        assertEquals(null, uiState.searchError)
    }

    @Test
    fun onSearchQuerySubmit_blankAfterSearch_cancelsPendingSearchAndClearsSuggestions() = runTest {
        fakeGeocoding.nextDelayMs = 1000
        fakeGeocoding.nextResult = listOf(
            GeocodingResult("Paris, France", 48.8566, 2.3522, null)
        )

        viewModel.onSearchQuerySubmit("Paris")
        advanceTimeBy(100)

        viewModel.onSearchQuerySubmit("")
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue(uiState.searchSuggestions.isEmpty())
        assertFalse(uiState.isSearching)
        assertEquals(null, uiState.searchError)
    }

    @Test
    fun onSearchQuerySubmit_setsSearchErrorWhenOffline() = runTest {
        fakeGeocoding.throwOnSearch = GeocodingNetworkException("Connectez-vous pour rechercher une adresse")
        viewModel.onSearchQuerySubmit("Paris")

        val uiState = viewModel.uiState.value
        assertFalse(uiState.isSearching)
        assertTrue(uiState.searchSuggestions.isEmpty())
        assertNotNull(uiState.searchError)
        assertTrue(uiState.searchError!!.contains("Connectez-vous") || uiState.searchError!!.contains("réseau"))
    }

    @Test
    fun onRequestRoute_noDestination_setsRouteError() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        segmentDao.insertAll(listOf(s1))
        viewModel.uiState.first { !it.isLoading }

        viewModel.onRequestRoute()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNull(uiState.route)
        assertEquals("Aucune destination définie", uiState.routeError)
    }

    @Test
    fun onRequestRoute_noUserLocation_setsRouteError() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        segmentDao.insertAll(listOf(s1))
        viewModel.uiState.first { !it.isLoading }
        viewModel.onDestinationSelected(GeocodingResult("Paris", 48.86, 2.36, null))

        viewModel.onRequestRoute()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNull(uiState.route)
        assertEquals("Position non disponible", uiState.routeError)
    }

    @Test
    fun onRequestRoute_noUserLocation_withParisFallback_computesRoute() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        segmentDao.insertAll(listOf(s1))
        viewModel.uiState.first { !it.isLoading }
        viewModel.onDestinationSelected(GeocodingResult("Paris", 48.86, 2.36, null))

        val routeResult = RouteResult(
            geometry = listOf(LatLng(48.8566, 2.3522), LatLng(48.86, 2.36)),
            etaSeconds = 300L,
            distanceMeters = 2500.0,
            routeType = RouteType.DISCOVERY
        )
        fakeRoutingEngine.nextResult = routeResult

        viewModel.onRequestRoute(useParisAsFallback = true)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNotNull(uiState.route)
        assertNull(uiState.routeError)
        assertTrue(uiState.usedParisAsFallback)
    }

    @Test
    fun onRequestRoute_success_updatesRoute() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        segmentDao.insertAll(listOf(s1))
        viewModel.uiState.first { !it.isLoading }
        viewModel.onDestinationSelected(GeocodingResult("Paris", 48.86, 2.36, null))

        // Simulate GPS permission grant and location emission via Robolectric ShadowLocationManager
        viewModel.onLocationPermissionResult(true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLM = Shadows.shadowOf(locationManager) as ShadowLocationManager
        val testLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.85
            longitude = 2.35
        }
        shadowLM.simulateLocation(testLocation)
        advanceUntilIdle()

        val routeResult = RouteResult(
            geometry = listOf(LatLng(48.85, 2.35), LatLng(48.86, 2.36)),
            etaSeconds = 120L,
            distanceMeters = 1500.0,
            routeType = RouteType.DISCOVERY
        )
        fakeRoutingEngine.nextResult = routeResult

        viewModel.onRequestRoute()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNotNull(uiState.route)
        assertEquals(routeResult, uiState.route)
        assertNull(uiState.routeError)
        assertFalse(uiState.isComputingRoute)
    }

    @Test
    fun onToleranceChanged_updatesStateImmediatelyAndTriggersRecalculationAfterDebounce() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        segmentDao.insertAll(listOf(s1))
        viewModel.uiState.first { !it.isLoading }
        viewModel.onDestinationSelected(GeocodingResult("Paris", 48.86, 2.36, null))
        viewModel.onLocationPermissionResult(true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLM = Shadows.shadowOf(locationManager) as ShadowLocationManager
        val testLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.85
            longitude = 2.35
        }
        shadowLM.simulateLocation(testLocation)
        advanceUntilIdle()

        fakeRoutingEngine.nextResult = RouteResult(
            geometry = listOf(LatLng(48.85, 2.35), LatLng(48.86, 2.36)),
            etaSeconds = 120L,
            distanceMeters = 1500.0,
            routeType = RouteType.DISCOVERY
        )

        // Simuler glissement rapide : 3 appels consécutifs — seul le dernier doit déclencher computeRoute
        viewModel.onToleranceChanged(18)
        viewModel.onToleranceChanged(19)
        viewModel.onToleranceChanged(20)

        // État UI mis à jour immédiatement sans attendre le debounce
        assertEquals(20, viewModel.uiState.value.tolerancePercent)

        // Laisser le debounce (300 ms) s'écouler et le calcul se terminer
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(20, uiState.tolerancePercent)
        val lastCall = fakeRoutingEngine.computeCalls.value.last()
        assertEquals(20.0, lastCall.tolerancePercent, 1e-6)
        // Un seul appel computeRoute malgré 3 changements (debounce efficace)
        assertEquals(1, fakeRoutingEngine.computeCalls.value.size)
    }

    @Test
    fun toleranceLoadedFromPreferencesOnInit() = runTest {
        // Régler la préférence AVANT de créer le ViewModel pour tester le chargement au démarrage
        fakeUserPreferences.setTolerancePercent(22)

        val viewModelWithPreset = MapViewModel(
            repository,
            fakeGeocoding,
            fakeRoutingEngine,
            fakeUserPreferences,
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .applicationContext as android.app.Application
        )
        advanceUntilIdle()

        assertEquals(22, viewModelWithPreset.uiState.value.tolerancePercent)
    }

    @Test
    fun onRequestRoute_noPath_setsRouteError() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        segmentDao.insertAll(listOf(s1))
        viewModel.uiState.first { !it.isLoading }
        viewModel.onDestinationSelected(GeocodingResult("Paris", 48.86, 2.36, null))
        viewModel.onLocationPermissionResult(true)

        fakeRoutingEngine.nextResult = null
        fakeRoutingEngine.nextClassicResult = null

        viewModel.onRequestRoute()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNull(uiState.route)
        assertEquals("Aucun chemin trouvé", uiState.routeError)
    }

    @Test
    fun onRequestRoute_discoveryFails_classicFallback_proposesClassicRoute() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        segmentDao.insertAll(listOf(s1))
        viewModel.uiState.first { !it.isLoading }
        viewModel.onDestinationSelected(GeocodingResult("Paris", 48.86, 2.36, null))
        viewModel.onLocationPermissionResult(true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLM = Shadows.shadowOf(locationManager) as ShadowLocationManager
        shadowLM.simulateLocation(Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.85
            longitude = 2.35
        })
        advanceUntilIdle()

        fakeRoutingEngine.nextResult = null
        val classicRoute = RouteResult(
            geometry = listOf(LatLng(48.85, 2.35), LatLng(48.86, 2.36)),
            etaSeconds = 90L,
            distanceMeters = 1200.0,
            routeType = RouteType.CLASSIC
        )
        fakeRoutingEngine.nextClassicResult = classicRoute

        viewModel.onRequestRoute()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNotNull(uiState.route)
        assertEquals(RouteType.CLASSIC, uiState.route!!.routeType)
        assertEquals(classicRoute, uiState.route)
        assertNull(uiState.routeError)
    }

    @Test
    fun onRequestClassicRoute_switchesToClassicWhenAvailable() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        segmentDao.insertAll(listOf(s1))
        viewModel.uiState.first { !it.isLoading }
        viewModel.onDestinationSelected(GeocodingResult("Paris", 48.86, 2.36, null))
        viewModel.onLocationPermissionResult(true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLM = Shadows.shadowOf(locationManager) as ShadowLocationManager
        shadowLM.simulateLocation(Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.85
            longitude = 2.35
        })
        advanceUntilIdle()

        val discoveryRoute = RouteResult(
            geometry = listOf(LatLng(48.85, 2.35), LatLng(48.855, 2.355), LatLng(48.86, 2.36)),
            etaSeconds = 150L,
            distanceMeters = 1800.0,
            routeType = RouteType.DISCOVERY
        )
        val classicRoute = RouteResult(
            geometry = listOf(LatLng(48.85, 2.35), LatLng(48.86, 2.36)),
            etaSeconds = 90L,
            distanceMeters = 1200.0,
            routeType = RouteType.CLASSIC
        )
        fakeRoutingEngine.nextResult = discoveryRoute
        fakeRoutingEngine.nextClassicResult = classicRoute

        viewModel.onRequestRoute()
        advanceUntilIdle()

        assertEquals(RouteType.DISCOVERY, viewModel.uiState.value.route!!.routeType)

        viewModel.onRequestClassicRoute()
        advanceUntilIdle()

        assertEquals(RouteType.CLASSIC, viewModel.uiState.value.route!!.routeType)
        assertEquals(classicRoute.distanceMeters, viewModel.uiState.value.route!!.distanceMeters, 0.01)
    }

    @Test
    fun onRequestDiscoveryRoute_switchesToDiscoveryWhenAvailable() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        segmentDao.insertAll(listOf(s1))
        viewModel.uiState.first { !it.isLoading }
        viewModel.onDestinationSelected(GeocodingResult("Paris", 48.86, 2.36, null))
        viewModel.onLocationPermissionResult(true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLM = Shadows.shadowOf(locationManager) as ShadowLocationManager
        shadowLM.simulateLocation(Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.85
            longitude = 2.35
        })
        advanceUntilIdle()

        val discoveryRoute = RouteResult(
            geometry = listOf(LatLng(48.85, 2.35), LatLng(48.855, 2.355), LatLng(48.86, 2.36)),
            etaSeconds = 150L,
            distanceMeters = 1800.0,
            routeType = RouteType.DISCOVERY
        )
        val classicRoute = RouteResult(
            geometry = listOf(LatLng(48.85, 2.35), LatLng(48.86, 2.36)),
            etaSeconds = 90L,
            distanceMeters = 1200.0,
            routeType = RouteType.CLASSIC
        )
        fakeRoutingEngine.nextResult = discoveryRoute
        fakeRoutingEngine.nextClassicResult = classicRoute

        viewModel.onRequestRoute()
        advanceUntilIdle()

        assertEquals(RouteType.DISCOVERY, viewModel.uiState.value.route!!.routeType)

        viewModel.onRequestClassicRoute()
        advanceUntilIdle()
        assertEquals(RouteType.CLASSIC, viewModel.uiState.value.route!!.routeType)

        viewModel.onRequestDiscoveryRoute()
        advanceUntilIdle()
        assertEquals(RouteType.DISCOVERY, viewModel.uiState.value.route!!.routeType)
    }

    @Test
    fun routeProgress_updatedWhenUserLocationChangesAlongRoute() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        segmentDao.insertAll(listOf(s1))
        viewModel.uiState.first { !it.isLoading }
        viewModel.onDestinationSelected(GeocodingResult("Paris", 48.87, 2.37, null))
        viewModel.onLocationPermissionResult(true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLM = Shadows.shadowOf(locationManager) as ShadowLocationManager

        val routeGeometry = listOf(
            LatLng(48.85, 2.35),
            LatLng(48.86, 2.36),
            LatLng(48.87, 2.37)
        )
        fakeRoutingEngine.nextResult = RouteResult(
            geometry = routeGeometry,
            etaSeconds = 600L,
            distanceMeters = 3000.0,
            routeType = RouteType.DISCOVERY
        )
        fakeRoutingEngine.nextClassicResult = null

        shadowLM.simulateLocation(Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.85
            longitude = 2.35
        })
        advanceUntilIdle()

        viewModel.onRequestRoute()
        advanceUntilIdle()

        var uiState = viewModel.uiState.value
        assertNotNull(uiState.route)
        assertEquals(0, uiState.routeProgressPercent)
        assertTrue(uiState.distanceRemainingMeters > 2000)

        shadowLM.simulateLocation(Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.86
            longitude = 2.36
        })
        advanceUntilIdle()

        uiState = viewModel.uiState.value
        assertTrue("Progress should increase when moving along route", uiState.routeProgressPercent > 0)
        assertTrue("Distance remaining should decrease", uiState.distanceRemainingMeters < 2000)
    }
}
