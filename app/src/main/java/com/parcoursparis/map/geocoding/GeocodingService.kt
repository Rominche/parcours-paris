package com.parcoursparis.map.geocoding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Geocoding service interface (open source: Nominatim/Photon).
 * Exposes search with optional bounds and degrades gracefully when offline.
 */
interface GeocodingService {
    /**
     * Search for places by query. Returns empty list on network error (offline).
     * @param bounds Optional region (e.g. Paris) to restrict results
     */
    suspend fun search(query: String, bounds: BoundingBox?): List<GeocodingResult>
}

/**
 * Nominatim-based implementation. Respects 1 req/s policy via debounce in caller.
 * User-Agent is set; no API key required.
 */
class NominatimGeocodingService(
    private val baseUrl: String = "https://nominatim.openstreetmap.org",
    private val userAgent: String = "ParcoursParis/1.0"
) : GeocodingService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("User-Agent", userAgent)
                    .build()
            )
        }
        .build()

    override suspend fun search(query: String, bounds: BoundingBox?): List<GeocodingResult> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val viewbox = bounds?.toNominatimViewbox()
            val url = buildString {
                append("$baseUrl/search?q=$encoded&format=json&limit=5")
                if (!viewbox.isNullOrEmpty()) {
                    append("&bounded=1&viewbox=$viewbox")
                }
            }
            val request = Request.Builder().url(url).get().build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw GeocodingNetworkException("Service de géocodage indisponible (HTTP ${response.code})")
                    }
                    val body = response.body?.string() ?: return@withContext emptyList()
                    parseNominatimJson(body)
                }
            } catch (e: IOException) {
                throw GeocodingNetworkException("Connectez-vous pour rechercher une adresse", e)
            }
        }

    private fun parseNominatimJson(json: String): List<GeocodingResult> {
        val arr = JSONArray(json)
        val list = mutableListOf<GeocodingResult>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val lat = obj.optString("lat", "0").toDoubleOrNull() ?: continue
            val lon = obj.optString("lon", "0").toDoubleOrNull() ?: continue
            val displayName = obj.optString("display_name", "").takeIf { it.isNotBlank() }
            list.add(
                GeocodingResult(
                    label = displayName ?: "$lat, $lon",
                    latitude = lat,
                    longitude = lon,
                    displayName = displayName
                )
            )
        }
        return list
    }
}

/** Thrown when geocoding fails due to network (offline / timeout). */
class GeocodingNetworkException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
