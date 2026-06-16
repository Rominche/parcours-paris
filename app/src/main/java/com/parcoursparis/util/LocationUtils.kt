package com.parcoursparis.util

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs

private const val MIN_UPDATE_INTERVAL_MS = 1000L
private const val MIN_UPDATE_DISTANCE_METERS = 1f

/**
 * Flow-based location provider wrapping LocationManager.
 * GPS prioritaire ; intervalle réduit pour un suivi plus réactif.
 */
fun locationFlow(context: Context): Flow<Location?> = callbackFlow {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    var bestLocation: Location? = null

    fun emitIfBetter(location: Location) {
        val current = bestLocation
        if (current == null || isBetterLocation(location, current)) {
            bestLocation = location
            trySend(location)
        }
    }

    val listener = LocationListener { location -> emitIfBetter(location) }

    val provider = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    if (provider != null) {
        try {
            @Suppress("DEPRECATION")
            var lastKnown = locationManager.getLastKnownLocation(provider)
            if (lastKnown == null && provider != LocationManager.NETWORK_PROVIDER) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            if (lastKnown == null && provider != LocationManager.GPS_PROVIDER) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (lastKnown != null) {
                emitIfBetter(lastKnown)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val request = android.location.LocationRequest.Builder(MIN_UPDATE_INTERVAL_MS)
                    .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
                    .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
                    .setMaxUpdateDelayMillis(MIN_UPDATE_INTERVAL_MS * 2)
                    .setQuality(android.location.LocationRequest.QUALITY_HIGH_ACCURACY)
                    .build()
                locationManager.requestLocationUpdates(
                    provider,
                    request,
                    ContextCompat.getMainExecutor(context),
                    listener
                )
            } else {
                @Suppress("DEPRECATION")
                locationManager.requestLocationUpdates(
                    provider,
                    MIN_UPDATE_INTERVAL_MS,
                    MIN_UPDATE_DISTANCE_METERS,
                    listener,
                    Looper.getMainLooper()
                )
            }
        } catch (_: SecurityException) {
            trySend(null)
        }
    } else {
        trySend(null)
    }

    awaitClose { locationManager.removeUpdates(listener) }
}

private fun isBetterLocation(location: Location, currentBest: Location): Boolean {
    if (location.provider == LocationManager.GPS_PROVIDER &&
        currentBest.provider == LocationManager.NETWORK_PROVIDER
    ) {
        return true
    }
    if (location.provider == LocationManager.NETWORK_PROVIDER &&
        currentBest.provider == LocationManager.GPS_PROVIDER
    ) {
        return false
    }

    val timeDelta = location.time - currentBest.time
    if (timeDelta > 10_000L) return true
    if (timeDelta < -10_000L) return false

    val accuracyDelta = (location.accuracy - currentBest.accuracy).toInt()
    if (accuracyDelta < 0) return true
    if (accuracyDelta > 0) return false
    return timeDelta > 0
}
