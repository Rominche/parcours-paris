package com.parcoursparis.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/** Variation minimale (degrés) pour émettre un nouveau cap boussole. */
private const val MIN_HEADING_CHANGE_DEGREES = 2.0

/** Lissage de la boussole (0 = figé, 1 = brut). */
private const val COMPASS_SMOOTHING = 0.25

/**
 * Cap magnétique du téléphone (degrés, 0 = nord magnétique), via le capteur de rotation.
 * Tient compte de l'orientation de l'écran. Flux vide si l'appareil n'a pas de boussole.
 * À collecter uniquement quand l'écran est visible (économie de batterie).
 */
fun compassHeadingFlow(context: Context): Flow<Double> {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        ?: return emptyFlow()
    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: return emptyFlow()

    return callbackFlow {
        val rotationMatrix = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothed: Double? = null
        var lastSent: Double? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val (axisX, axisY) = when (displayRotation(context)) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remapped)
                SensorManager.getOrientation(remapped, orientation)
                val azimuth = Math.toDegrees(orientation[0].toDouble())
                val next = HeadingUtils.smooth(smoothed, azimuth, COMPASS_SMOOTHING)
                smoothed = next
                val previous = lastSent
                if (previous == null || kotlin.math.abs(HeadingUtils.delta(previous, next)) >= MIN_HEADING_CHANGE_DEGREES) {
                    lastSent = next
                    trySend(next)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}

@Suppress("DEPRECATION")
private fun displayRotation(context: Context): Int = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.rotation ?: Surface.ROTATION_0
    } else {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }
} catch (_: UnsupportedOperationException) {
    // Contexte non visuel (ex. Application) : on suppose le portrait.
    Surface.ROTATION_0
}
