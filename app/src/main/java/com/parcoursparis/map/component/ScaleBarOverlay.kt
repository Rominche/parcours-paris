package com.parcoursparis.map.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.pow

/**
 * Échelle de la carte en fonction du niveau de zoom.
 * Affiche une barre avec la distance représentée (m ou km).
 *
 * Formule Web Mercator : mètres/pixel = 156543.03392 * cos(lat) / 2^zoom
 */
@Composable
fun ScaleBarOverlay(
    zoom: Double,
    centerLatitude: Double,
    modifier: Modifier = Modifier,
    targetWidthPx: Float = 100f
) {
    val density = LocalDensity.current
    val metersPerPixel = 156543.03392 * cos(Math.toRadians(centerLatitude)) / 2.0.pow(zoom)
    val targetMeters = (targetWidthPx * metersPerPixel).toDouble()

    val (distanceMeters, label) = when {
        targetMeters >= 5000 -> Pair(5000.0, "5 km")
        targetMeters >= 2000 -> Pair(2000.0, "2 km")
        targetMeters >= 1000 -> Pair(1000.0, "1 km")
        targetMeters >= 500 -> Pair(500.0, "500 m")
        targetMeters >= 200 -> Pair(200.0, "200 m")
        targetMeters >= 100 -> Pair(100.0, "100 m")
        targetMeters >= 50 -> Pair(50.0, "50 m")
        else -> Pair(25.0, "25 m")
    }

    val widthPx = (distanceMeters / metersPerPixel).toFloat().coerceIn(20f, 150f)
    val widthDp = with(density) { widthPx.toDp() }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Canvas(modifier = Modifier.width(widthDp).height(24.dp)) {
            val strokeWidth = 3f
            val y = 12f
            drawLine(
                color = MaterialTheme.colorScheme.onSurface,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = MaterialTheme.colorScheme.onSurface,
                start = Offset(0f, y - 4f),
                end = Offset(0f, y + 4f),
                strokeWidth = 2f
            )
            drawLine(
                color = MaterialTheme.colorScheme.onSurface,
                start = Offset(size.width, y - 4f),
                end = Offset(size.width, y + 4f),
                strokeWidth = 2f
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
