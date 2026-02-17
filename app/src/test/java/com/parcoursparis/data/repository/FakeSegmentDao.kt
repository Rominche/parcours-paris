package com.parcoursparis.data.repository

import com.parcoursparis.data.db.dao.SegmentDao
import com.parcoursparis.data.entity.Segment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake SegmentDao pour les tests unitaires.
 * Implémente le comportement de Room avec OnConflictStrategy.REPLACE.
 */
class FakeSegmentDao : SegmentDao {
    private val _segments = MutableStateFlow<List<Segment>>(emptyList())
    override fun getAll() = _segments.asStateFlow()

    override suspend fun getById(id: Long) = _segments.value.find { it.osm_way_id == id }

    /**
     * Implémente REPLACE strategy: si un segment existe déjà (même osm_way_id), il est remplacé.
     */
    override suspend fun insertAll(segments: List<Segment>) {
        val newSegmentsMap = segments.associateBy { it.osm_way_id }
        val existingSegments = _segments.value.filter { it.osm_way_id !in newSegmentsMap }
        _segments.value = existingSegments + segments
    }

    override suspend fun getCount() = _segments.value.size
}
