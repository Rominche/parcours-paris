package com.parcoursparis.data.repository

import com.parcoursparis.data.entity.Segment
import com.parcoursparis.data.entity.SegmentVisit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires pour SegmentRepository.
 * Utilise des mocks des DAOs pour vérifier la logique d'agrégation exploré/non exploré.
 */
class SegmentRepositoryTest {

    private lateinit var segmentDao: FakeSegmentDao
    private lateinit var segmentVisitDao: FakeSegmentVisitDao
    private lateinit var repository: SegmentRepository

    @Before
    fun setup() {
        segmentDao = FakeSegmentDao()
        segmentVisitDao = FakeSegmentVisitDao()
        repository = SegmentRepository(segmentDao, segmentVisitDao)
    }

    @Test
    fun segmentsWithExploredState_combinesSegmentsAndVisits() = runTest {
        val s1 = Segment(1001L, "[[2.35,48.85],[2.36,48.86]]")
        val s2 = Segment(1002L, "[[2.36,48.86],[2.37,48.87]]")
        segmentDao.insertAll(listOf(s1, s2))
        segmentVisitDao.insert(SegmentVisit(1001L, 1000L))

        val result = repository.segmentsWithExploredState.first()

        assertEquals(2, result.size)
        assertTrue(result.find { it.segment.osm_way_id == 1001L }!!.isExplored)
        assertFalse(result.find { it.segment.osm_way_id == 1002L }!!.isExplored)
    }

    @Test
    fun insertSegmentsIfEmpty_insertsWhenEmpty() = runTest {
        val segments = listOf(
            Segment(1001L, "[[2.35,48.85]]")
        )
        repository.insertSegmentsIfEmpty(segments)
        assertEquals(1, segmentDao.getCount())
    }

    @Test
    fun insertSegmentsIfEmpty_skipsWhenNotEmpty() = runTest {
        segmentDao.insertAll(listOf(Segment(999L, "[]")))
        repository.insertSegmentsIfEmpty(listOf(Segment(1001L, "[[2.35,48.85]]")))
        assertEquals(1, segmentDao.getCount())
    }

    @Test
    fun markAsExplored_insertsVisit() = runTest {
        segmentDao.insertAll(listOf(Segment(1001L, "[]")))
        repository.markAsExplored(1001L, 2000L)
        val ids = segmentVisitDao.getExploredIds()
        assertEquals(listOf(1001L), ids)
    }

    @Test
    fun markAsUnexplored_deletesVisit() = runTest {
        segmentVisitDao.insert(SegmentVisit(1001L, 1000L))
        repository.markAsUnexplored(1001L)
        assertEquals(emptyList<Long>(), segmentVisitDao.getExploredIds())
    }

    @Test
    fun progressStats_computesExploredPercent() = runTest {
        segmentDao.insertAll(
            listOf(
                Segment(1001L, "[]"),
                Segment(1002L, "[]"),
                Segment(1003L, "[]"),
                Segment(1004L, "[]")
            )
        )
        segmentVisitDao.insert(SegmentVisit(1001L, 1000L))

        val stats = repository.progressStats.first()
        assertEquals(4, stats.totalCount)
        assertEquals(1, stats.exploredCount)
        assertEquals(25, stats.exploredPercent)
    }
}
