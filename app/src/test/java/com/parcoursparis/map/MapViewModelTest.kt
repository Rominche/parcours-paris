package com.parcoursparis.map

import androidx.test.core.app.ApplicationProvider
import com.parcoursparis.data.entity.Segment
import com.parcoursparis.data.repository.FakeSegmentDao
import com.parcoursparis.data.repository.FakeSegmentVisitDao
import com.parcoursparis.data.repository.SegmentRepository
import com.parcoursparis.map.geocoding.GeocodingNetworkException
import com.parcoursparis.map.geocoding.GeocodingResult
import com.parcoursparis.map.geocoding.FakeGeocodingService
import com.parcoursparis.routing.FakeDiscoveryRoutingEngine
import com.parcoursparis.routing.RouteResult
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
import org.maplibre.android.geometry.LatLng
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for MapViewModel.
 * Verifies segments, geocoding search, destination selection, and offline handling.
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
    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        segmentDao = FakeSegmentDao()
        segmentVisitDao = FakeSegmentVisitDao()
        repository = SegmentRepository(segmentDao, segmentVisitDao)
        fakeGeocoding = FakeGeocodingService()
        fakeRoutingEngine = FakeDiscoveryRoutingEngine()
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        viewModel = MapViewModel(
            repository,
            fakeGeocoding,
            fakeRoutingEngine,
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
    fun onRequestRoute_success_updatesRoute() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        segmentDao.insertAll(listOf(s1))
        val uiStateWithSegments = viewModel.uiState.first { !it.isLoading }
        viewModel.onDestinationSelected(GeocodingResult("Paris", 48.86, 2.36, null))
        viewModel.onLocationPermissionResult(true)

        val routeResult = RouteResult(
            geometry = listOf(LatLng(48.85, 2.35), LatLng(48.86, 2.36)),
            etaSeconds = 120L,
            distanceMeters = 1500.0
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
    fun onRequestRoute_noPath_setsRouteError() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        segmentDao.insertAll(listOf(s1))
        viewModel.uiState.first { !it.isLoading }
        viewModel.onDestinationSelected(GeocodingResult("Paris", 48.86, 2.36, null))
        viewModel.onLocationPermissionResult(true)

        fakeRoutingEngine.nextResult = null

        viewModel.onRequestRoute()
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNull(uiState.route)
        assertEquals("Aucun chemin trouvé", uiState.routeError)
    }
}
