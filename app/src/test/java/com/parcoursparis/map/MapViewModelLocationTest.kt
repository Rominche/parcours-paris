package com.parcoursparis.map

import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.parcoursparis.data.entity.Segment
import com.parcoursparis.data.repository.FakeSegmentDao
import com.parcoursparis.data.repository.FakeSegmentVisitDao
import com.parcoursparis.data.repository.SegmentRepository
import com.parcoursparis.map.geocoding.FakeGeocodingService
import com.parcoursparis.routing.FakeDiscoveryRoutingEngine
import com.parcoursparis.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MapViewModelLocationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var segmentDao: FakeSegmentDao
    private lateinit var segmentVisitDao: FakeSegmentVisitDao
    private lateinit var repository: SegmentRepository
    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        segmentDao = FakeSegmentDao()
        segmentVisitDao = FakeSegmentVisitDao()
        repository = SegmentRepository(segmentDao, segmentVisitDao)
        val context = ApplicationProvider.getApplicationContext<Context>()
        viewModel = MapViewModel(
            repository,
            FakeGeocodingService(),
            FakeDiscoveryRoutingEngine(),
            context.applicationContext as android.app.Application
        )
        runBlocking {
            segmentDao.insertAll(listOf(Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")))
        }
    }

    @Test
    fun onLocationPermissionResult_false_setsDeniedAndDoesNotStartCollection() = runTest {
        viewModel.onLocationPermissionResult(false)
        advanceUntilIdle()

        val uiState = viewModel.uiState.first()
        assertFalse(uiState.locationPermissionGranted)
        assertTrue(uiState.locationPermissionDenied)
        assertNull(uiState.userLocation)
    }

    @Test
    fun onLocationPermissionResult_true_setsGrantedAndStartsCollection() = runTest {
        viewModel.onLocationPermissionResult(true)
        advanceUntilIdle()

        val uiState = viewModel.uiState.first()
        assertTrue(uiState.locationPermissionGranted)
        assertFalse(uiState.locationPermissionDenied)
    }

    @Test
    fun onLocationPermissionResult_calledTwice_doesNotCrashAndKeepsGrantedState() = runTest {
        viewModel.onLocationPermissionResult(true)
        advanceUntilIdle()
        viewModel.onLocationPermissionResult(true)
        advanceUntilIdle()

        val uiState = viewModel.uiState.first()
        assertTrue(uiState.locationPermissionGranted)
        assertFalse(uiState.locationPermissionDenied)
    }

    @Test
    fun onLocationPermissionResult_true_userLocationUpdatedWhenLocationReceived() = runTest {
        viewModel.onLocationPermissionResult(true)
        advanceUntilIdle()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocationManager = Shadows.shadowOf(locationManager) as ShadowLocationManager
        val testLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 48.8566
            longitude = 2.3522
        }
        shadowLocationManager.simulateLocation(testLocation)
        advanceUntilIdle()

        val uiState = viewModel.uiState.first()
        assertNotNull(uiState.userLocation)
        assertEquals(48.8566, uiState.userLocation!!.latitude, 0.0001)
        assertEquals(2.3522, uiState.userLocation!!.longitude, 0.0001)
    }
}
