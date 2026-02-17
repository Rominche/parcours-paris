package com.parcoursparis.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.parcoursparis.data.entity.Segment
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentDao {

    @Query("SELECT * FROM segment ORDER BY osm_way_id")
    fun getAll(): Flow<List<Segment>>

    @Query("SELECT * FROM segment WHERE osm_way_id = :id")
    suspend fun getById(id: Long): Segment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<Segment>)

    @Query("SELECT COUNT(*) FROM segment")
    suspend fun getCount(): Int
}
