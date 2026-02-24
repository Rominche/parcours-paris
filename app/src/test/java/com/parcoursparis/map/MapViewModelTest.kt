package com.parcoursparis.map

import androidx.test.core.app.ApplicationProvider
import com.parcoursparis.data.entity.Segment
import com.parcoursparis.data.repository.FakeSegmentDao
import com.parcoursparis.data.repository.FakeSegmentVisitDao
import com.parcoursparis.data.repository.SegmentRepository
import com.parcoursparis.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        segmentDao = FakeSegmentDao()
        segmentVisitDao = FakeSegmentVisitDao()
        repository = SegmentRepository(segmentDao, segmentVisitDao)
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        viewModel = MapViewModel(repository, app.applicationContext as android.app.Application)
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
}
