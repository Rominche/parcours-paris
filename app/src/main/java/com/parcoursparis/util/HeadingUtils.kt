package com.parcoursparis.util

/** Vitesse à partir de laquelle on se fie au cap GPS plutôt qu'à la boussole (m/s ≈ 2,5 km/h). */
const val MIN_SPEED_FOR_GPS_COURSE_MPS = 0.7f

object HeadingUtils {

    /** Ramène un angle dans [0, 360). */
    fun normalize(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

    /** Plus petit écart signé de [from] vers [to], dans [-180, 180]. */
    fun delta(from: Double, to: Double): Double {
        var d = normalize(to) - normalize(from)
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    /** Lissage circulaire (gère le passage 359° → 0°). */
    fun smooth(current: Double?, target: Double, factor: Double): Double =
        if (current == null) normalize(target) else normalize(current + delta(current, target) * factor)

    /**
     * Cap à afficher pour le pointeur de position :
     * - en marche, le cap GPS (direction réelle du déplacement) ;
     * - à l'arrêt ou au ralenti, la boussole du téléphone (vers où il pointe) ;
     * - null si aucune source n'est disponible (le pointeur est alors masqué).
     */
    fun chooseHeading(
        gpsCourseDegrees: Double?,
        compassHeadingDegrees: Double?,
        magneticDeclinationDegrees: Double = 0.0
    ): Double? = when {
        gpsCourseDegrees != null -> normalize(gpsCourseDegrees)
        compassHeadingDegrees != null -> normalize(compassHeadingDegrees + magneticDeclinationDegrees)
        else -> null
    }
}
