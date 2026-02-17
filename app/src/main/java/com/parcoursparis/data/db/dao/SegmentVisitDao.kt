package com.parcoursparis.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.parcoursparis.data.entity.SegmentVisit
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentVisitDao {

    @Query("SELECT * FROM segment_visit ORDER BY explored_at DESC")
    fun getAll(): Flow<List<SegmentVisit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(visit: SegmentVisit)

    @Query("DELETE FROM segment_visit WHERE segment_id = :segmentId")
    suspend fun delete(segmentId: Long)

    @Query("SELECT segment_id FROM segment_visit")
    suspend fun getExploredIds(): List<Long>
}
