package com.parcoursparis.map.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Rose des vents (N, E, S, O) : le N reste aligné sur le nord géographique quand la carte pivote
 * (en navigation, la carte suit la direction de marche).
 */
@Composable
fun CompassRoseOverlay(
    mapBearingDegrees: Float,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val northColor = MaterialTheme.colorScheme.error

    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = -mapBearingDegrees }
            .shadow(2.dp, CircleShape)
            .background(surfaceColor.copy(alpha = 0.95f), CircleShape)
            .padding(4.dp)
            .size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 2f

            drawCircle(
                color = onSurfaceColor.copy(alpha = 0.25f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5f)
            )

            val northPath = Path().apply {
                moveTo(center.x, center.y - radius)
                lineTo(center.x - 6f, center.y + 4f)
                lineTo(center.x, center.y - 2f)
                lineTo(center.x + 6f, center.y + 4f)
                close()
            }
            drawPath(northPath, northColor)

            val southPath = Path().apply {
                moveTo(center.x, center.y + radius)
                lineTo(center.x - 5f, center.y - 3f)
                lineTo(center.x, center.y + 2f)
                lineTo(center.x + 5f, center.y - 3f)
                close()
            }
            drawPath(southPath, onSurfaceColor.copy(alpha = 0.45f))
        }
        val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
        val secondaryColor = onSurfaceColor.copy(alpha = 0.7f)
        Text("N", Modifier.align(Alignment.TopCenter), style = labelStyle, color = northColor)
        Text("S", Modifier.align(Alignment.BottomCenter), style = labelStyle, color = secondaryColor)
        Text("E", Modifier.align(Alignment.CenterEnd), style = labelStyle, color = secondaryColor)
        Text("O", Modifier.align(Alignment.CenterStart), style = labelStyle, color = secondaryColor)
    }
}
