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

/**
 * Flow-based location provider wrapping LocationManager.
 * Emits Location? on each GPS update; null when unavailable or permission denied.
 * Uses callbackFlow for integration with Coroutines/Flow.
 */
fun locationFlow(context: Context): Flow<Location?> = callbackFlow {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val listener = LocationListener { location -> trySend(location) }

    val provider = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    if (provider != null) {
        try {
            // Émet immédiatement la dernière position connue (évite "Position non disponible" au démarrage)
            @Suppress("DEPRECATION")
            var lastKnown = locationManager.getLastKnownLocation(provider)
            if (lastKnown == null && provider != LocationManager.NETWORK_PROVIDER) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            if (lastKnown == null && provider != LocationManager.GPS_PROVIDER) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (lastKnown != null) {
                trySend(lastKnown)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val request = android.location.LocationRequest.Builder(2000L)
                    .setMinUpdateDistanceMeters(5f)
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
                    2000L,
                    5f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        } catch (e: SecurityException) {
            trySend(null)
        }
    } else {
        trySend(null)
    }

    awaitClose { locationManager.removeUpdates(listener) }
}
