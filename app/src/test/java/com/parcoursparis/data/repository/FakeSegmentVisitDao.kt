package com.parcoursparis.data.repository

import com.parcoursparis.data.db.dao.SegmentVisitDao
import com.parcoursparis.data.entity.SegmentVisit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake SegmentVisitDao pour les tests unitaires.
 * Implémente le comportement de Room avec OnConflictStrategy.REPLACE et tri par explored_at DESC.
 */
class FakeSegmentVisitDao : SegmentVisitDao {
    private val _visits = MutableStateFlow<List<SegmentVisit>>(emptyList())
    
    override fun getAll() = _visits.asStateFlow()

    /**
     * Implémente REPLACE strategy: si une visite existe déjà (même segment_id), elle est remplacée.
     * Le résultat est trié par explored_at DESC comme dans le vrai DAO.
     */
    override suspend fun insert(visit: SegmentVisit) {
        val filtered = _visits.value.filter { it.segment_id != visit.segment_id }
        _visits.value = (filtered + visit).sortedByDescending { it.explored_at }
    }

    override suspend fun delete(segmentId: Long) {
        _visits.value = _visits.value.filter { it.segment_id != segmentId }
    }

    override suspend fun getExploredIds() = _visits.value.map { it.segment_id }
}
