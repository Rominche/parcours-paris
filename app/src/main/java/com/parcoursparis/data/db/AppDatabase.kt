package com.parcoursparis.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.parcoursparis.data.db.dao.SegmentDao
import com.parcoursparis.data.db.dao.SegmentVisitDao
import com.parcoursparis.data.entity.Segment
import com.parcoursparis.data.entity.SegmentVisit

/**
 * Base de données Room principale de l'application.
 * 
 * Version 1 : Tables segment et segment_visit pour la gestion des segments OSM et de l'exploration.
 * 
 * Stratégie de migration : Pour le MVP, les migrations seront destructives (fallbackToDestructiveMigration).
 * Pour la production, des migrations manuelles seront ajoutées si nécessaire.
 * 
 * Schémas exportés dans app/schemas/ pour traçabilité des changements.
 */
@Database(
    entities = [Segment::class, SegmentVisit::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun segmentDao(): SegmentDao
    abstract fun segmentVisitDao(): SegmentVisitDao
}
