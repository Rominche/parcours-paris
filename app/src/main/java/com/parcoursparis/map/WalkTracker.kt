package com.parcoursparis.map

import com.parcoursparis.util.haversineMeters

/**
 * Marquage automatique des rues parcourues à partir des positions GPS.
 *
 * Principe : chaque position GPS précise est rattachée à la rue la plus proche. Quand deux
 * positions consécutives tombent sur la même rue, la portion de rue entre elles est comptée
 * comme parcourue. Une rue est marquée quand une part suffisante de sa longueur a été couverte.
 * Traverser une rue perpendiculairement (à un carrefour) ne la marque donc pas.
 */
class WalkTracker(private val index: SegmentSpatialIndex) {

    companion object {
        /** Positions moins précises ignorées (m). */
        const val MAX_ACCURACY_METERS = 25f

        /** Distance max entre la position et la rue pour l'y rattacher (m). */
        const val MATCH_DISTANCE_METERS = 20.0

        /** Déplacement minimal entre deux positions prises en compte (bruit GPS à l'arrêt). */
        const val MIN_STEP_METERS = 2.0

        /** Au-delà, on considère un saut GPS (ou un trajet en transport) : on repart de zéro. */
        const val MAX_STEP_METERS = 60.0

        /** Au-delà de ~12 km/h, on n'est plus à pied (bus, voiture, vélo) : rien n'est marqué. */
        const val MAX_WALKING_SPEED_MPS = 3.5

        /** Longueur couverte nécessaire pour marquer une rue de longueur [lengthMeters]. */
        fun requiredCoverage(lengthMeters: Double): Double =
            maxOf(0.6 * lengthMeters, minOf(0.9 * lengthMeters, 20.0))
    }

    private var lastLat = Double.NaN
    private var lastLon = Double.NaN
    private var lastTimeMillis: Long? = null
    private var lastMatch: SegmentSpatialIndex.Match? = null

    /** Intervalle couvert [min, max] (m depuis le début) par rue. */
    private val coverage = HashMap<Long, DoubleArray>()
    private val marked = HashSet<Long>()

    /**
     * Traite une nouvelle position et retourne les rues à marquer comme parcourues.
     * @param timeMillis horodatage de la position (sert à écarter les trajets en véhicule)
     * @param isExplored indique si une rue est déjà marquée (elle est alors ignorée)
     */
    fun onLocation(
        lat: Double,
        lon: Double,
        accuracyMeters: Float?,
        timeMillis: Long? = null,
        isExplored: (Long) -> Boolean
    ): List<Long> {
        if (accuracyMeters != null && accuracyMeters > MAX_ACCURACY_METERS) return emptyList()

        if (!lastLat.isNaN()) {
            val step = haversineMeters(lastLat, lastLon, lat, lon)
            if (step < MIN_STEP_METERS) return emptyList()
            val previousTime = lastTimeMillis
            val seconds = if (timeMillis != null && previousTime != null) (timeMillis - previousTime) / 1000.0 else null
            val tooFast = seconds != null && seconds > 0 && step / seconds > MAX_WALKING_SPEED_MPS
            if (step > MAX_STEP_METERS || tooFast) {
                reset(lat, lon, timeMillis)
                return emptyList()
            }
        }

        val match = index.nearest(lat, lon, MATCH_DISTANCE_METERS)
        val previous = lastMatch
        lastLat = lat
        lastLon = lon
        lastTimeMillis = timeMillis
        lastMatch = match
        if (match == null || previous == null || previous.segmentId != match.segmentId) return emptyList()

        val id = match.segmentId
        if (id in marked || isExplored(id)) return emptyList()
        val range = coverage.getOrPut(id) { doubleArrayOf(previous.alongMeters, previous.alongMeters) }
        range[0] = minOf(range[0], previous.alongMeters, match.alongMeters)
        range[1] = maxOf(range[1], previous.alongMeters, match.alongMeters)
        if (range[1] - range[0] >= requiredCoverage(match.lengthMeters)) {
            marked.add(id)
            coverage.remove(id)
            return listOf(id)
        }
        return emptyList()
    }

    /** Oublie une rue marquée (ex. l'utilisateur l'a démarquée à la main). */
    fun forget(segmentId: Long) {
        marked.remove(segmentId)
        coverage.remove(segmentId)
    }

    private fun reset(lat: Double, lon: Double, timeMillis: Long?) {
        lastLat = lat
        lastLon = lon
        lastTimeMillis = timeMillis
        lastMatch = null
    }
}
