package com.parcoursparis.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.parcoursparis.data.entity.Segment
import com.parcoursparis.data.entity.SegmentVisit
import com.parcoursparis.data.preferences.FakeUserPreferencesRepository
import com.parcoursparis.data.repository.FakeSegmentDao
import com.parcoursparis.data.repository.FakeSegmentVisitDao
import com.parcoursparis.data.repository.SegmentRepository
import com.parcoursparis.map.geocoding.FakeGeocodingService
import com.parcoursparis.routing.FakeDiscoveryRoutingEngine
import com.parcoursparis.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MapViewModelSegmentSelectionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var segmentDao: FakeSegmentDao
    private lateinit var segmentVisitDao: FakeSegmentVisitDao
    private lateinit var repository: SegmentRepository
    private lateinit var viewModel: MapViewModel

    private val segmentNearTap = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
    private val segmentFar = Segment(1002L, "[[2.50,48.95],[2.51,48.96]]")

    @Before
    fun setup() {
        segmentDao = FakeSegmentDao()
        segmentVisitDao = FakeSegmentVisitDao()
        repository = SegmentRepository(segmentDao, segmentVisitDao)
        val app = ApplicationProvider.getApplicationContext<Context>()
        viewModel = MapViewModel(
            repository,
            FakeGeocodingService(),
            FakeDiscoveryRoutingEngine(),
            FakeUserPreferencesRepository(),
            app.applicationContext as android.app.Application
        )
    }

    private suspend fun seedSegments() {
        segmentDao.insertAll(listOf(segmentNearTap, segmentFar))
    }

    @Test
    fun onMapTap_selectsNearestSegmentWhenSelectionEnabled() = runTest {
        seedSegments()
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isLoading }
        assertTrue(state.isSegmentSelectionEnabled)

        viewModel.onMapTap(LatLng(48.855, 2.355), zoom = 14.0, displayDensity = 2.75f)
        advanceUntilIdle()

        assertEquals(1001L, viewModel.uiState.value.selectedSegmentId)
    }

    @Test
    fun onMapTap_clearsSelectionWhenNoSegmentNearby() = runTest {
        seedSegments()
        advanceUntilIdle()
        viewModel.onMapTap(LatLng(48.855, 2.355), zoom = 14.0, displayDensity = 2.75f)
        advanceUntilIdle()
        assertEquals(1001L, viewModel.uiState.value.selectedSegmentId)

        viewModel.onMapTap(LatLng(49.0, 2.8), zoom = 14.0, displayDensity = 2.75f)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedSegmentId)
    }

    @Test
    fun onMarkSelectedExplored_updatesRepositoryAndUiState() = runTest {
        seedSegments()
        advanceUntilIdle()
        viewModel.onMapTap(LatLng(48.855, 2.355), zoom = 14.0, displayDensity = 2.75f)
        viewModel.onMarkSelectedExplored()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val segment = state.segments.find { it.segment.osm_way_id == 1001L }
        assertNotNull(segment)
        assertTrue(segment!!.isExplored)
        assertEquals(1, state.progressStats.exploredCount)
        assertEquals(2, state.progressStats.totalCount)
        assertEquals(50, state.progressStats.exploredPercent)
    }

    @Test
    fun onMarkSelectedUnexplored_removesVisit() = runTest {
        seedSegments()
        segmentVisitDao.insert(SegmentVisit(1001L, 1000L))
        advanceUntilIdle()
        viewModel.onMapTap(LatLng(48.855, 2.355), zoom = 14.0, displayDensity = 2.75f)
        viewModel.onMarkSelectedUnexplored()
        advanceUntilIdle()

        val segment = viewModel.uiState.value.segments.find { it.segment.osm_way_id == 1001L }
        assertFalse(segment!!.isExplored)
        assertEquals(0, viewModel.uiState.value.progressStats.exploredCount)
    }

    @Test
    fun destinationDisablesSegmentSelection() = runTest {
        seedSegments()
        advanceUntilIdle()
        viewModel.onAddressSearchResult(LatLng(48.86, 2.36), null, autoComputeRoute = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSegmentSelectionEnabled)
        assertNull(state.selectedSegmentId)

        viewModel.onMapTap(LatLng(48.855, 2.355), zoom = 14.0, displayDensity = 2.75f)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedSegmentId)
    }

    @Test
    fun onClearDestination_reEnablesSegmentSelection() = runTest {
        seedSegments()
        advanceUntilIdle()
        viewModel.onAddressSearchResult(LatLng(48.86, 2.36), null, autoComputeRoute = false)
        viewModel.onClearDestination()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSegmentSelectionEnabled)
        viewModel.onMapTap(LatLng(48.855, 2.355), zoom = 14.0, displayDensity = 2.75f)
        advanceUntilIdle()
        assertEquals(1001L, viewModel.uiState.value.selectedSegmentId)
    }
}
