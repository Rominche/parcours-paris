package com.parcoursparis.data.repository

/**
 * Statistiques de progression recalculées à chaque marquage manuel.
 */
data class ProgressStats(
    val exploredCount: Int,
    val totalCount: Int,
    val exploredPercent: Int
) {
    companion object {
        val EMPTY = ProgressStats(0, 0, 0)
    }
}
