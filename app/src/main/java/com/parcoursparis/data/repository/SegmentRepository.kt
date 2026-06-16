package com.parcoursparis.data.repository

import com.parcoursparis.data.db.dao.SegmentDao
import com.parcoursparis.data.db.dao.SegmentVisitDao
import com.parcoursparis.data.entity.Segment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Repository exposant les segments avec leur état exploré/non exploré.
 * Charge le GeoJSON au démarrage si la base est vide.
 */
class SegmentRepository(
    private val segmentDao: SegmentDao,
    private val segmentVisitDao: SegmentVisitDao
) {

    /**
     * Flow des segments avec un flag indiquant s'ils sont explorés ou non.
     * Combine les segments et les IDs explorés pour produire SegmentWithExploredState.
     * Optimisé avec distinctUntilChanged pour éviter les recalculs inutiles.
     */
    val segmentsWithExploredState: Flow<List<SegmentWithExploredState>> = combine(
        segmentDao.getAll(),
        segmentVisitDao.getAll()
    ) { segments, visits ->
        val exploredIds = visits.map { it.segment_id }.toSet()
        segments.map { segment ->
            SegmentWithExploredState(
                segment = segment,
                isExplored = segment.osm_way_id in exploredIds
            )
        }
    }.distinctUntilChanged()

    /**
     * Statistiques de progression dérivées des segments et visites.
     */
    val progressStats: Flow<ProgressStats> = combine(
        segmentDao.getAll(),
        segmentVisitDao.getAll()
    ) { segments, visits ->
        val total = segments.size
        val explored = visits.map { it.segment_id }.toSet().size
        val percent = if (total > 0) ((explored.toDouble() / total) * 100).toInt() else 0
        ProgressStats(
            exploredCount = explored,
            totalCount = total,
            exploredPercent = percent
        )
    }.distinctUntilChanged()

    /**
     * Insère les segments chargés depuis le GeoJSON dans Room.
     * À appeler au démarrage de l'app avec les segments parsés depuis assets.
     * N'insère que si la base est vide (évite rechargement à chaque démarrage).
     */
    suspend fun insertSegmentsIfEmpty(segments: List<Segment>) {
        if (segmentDao.getCount() == 0 && segments.isNotEmpty()) {
            segmentDao.insertAll(segments)
        }
    }

    /**
     * Marque un segment comme exploré (pour story 1.3+).
     */
    suspend fun markAsExplored(segmentId: Long, exploredAt: Long) {
        segmentVisitDao.insert(
            com.parcoursparis.data.entity.SegmentVisit(
                segment_id = segmentId,
                explored_at = exploredAt
            )
        )
    }

    /**
     * Marque un segment comme non exploré (pour story 1.3+).
     */
    suspend fun markAsUnexplored(segmentId: Long) {
        segmentVisitDao.delete(segmentId)
    }
}

data class SegmentWithExploredState(
    val segment: Segment,
    val isExplored: Boolean
)
