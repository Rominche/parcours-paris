package com.parcoursparis.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Enregistre qu'un segment a été exploré par l'utilisateur.
 * segment_id référence segment.osm_way_id.
 */
@Entity(
    tableName = "segment_visit",
    foreignKeys = [
        ForeignKey(
            entity = Segment::class,
            parentColumns = ["osm_way_id"],
            childColumns = ["segment_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("segment_id")]
)
data class SegmentVisit(
    @PrimaryKey val segment_id: Long,
    val explored_at: Long
)
