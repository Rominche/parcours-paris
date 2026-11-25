package com.parcoursparis.util

/**
 * Lecture rapide d'une géométrie LineString stockée en JSON (`[[lon,lat],[lon,lat],...]`).
 *
 * Évite org.json, trop lent pour les ~160 000 segments de Paris et indisponible
 * dans les tests unitaires JVM.
 */
object GeometryParser {

    /**
     * Retourne les coordonnées entrelacées `[lon0, lat0, lon1, lat1, ...]`,
     * ou null si le texte n'est pas une liste de paires de nombres (au moins 2 points).
     */
    fun parseLonLat(json: String): DoubleArray? {
        var values = DoubleArray(16)
        var count = 0
        var i = 0
        val n = json.length
        while (i < n) {
            val c = json[i]
            if (c == '-' || c == '+' || c == '.' || c in '0'..'9') {
                val start = i
                i++
                while (i < n) {
                    val d = json[i]
                    if (d in '0'..'9' || d == '.' || d == 'e' || d == 'E' || d == '-' || d == '+') i++ else break
                }
                val value = json.substring(start, i).toDoubleOrNull() ?: return null
                if (count == values.size) values = values.copyOf(values.size * 2)
                values[count++] = value
            } else {
                i++
            }
        }
        if (count < 4 || count % 2 != 0) return null
        return if (count == values.size) values else values.copyOf(count)
    }
}
