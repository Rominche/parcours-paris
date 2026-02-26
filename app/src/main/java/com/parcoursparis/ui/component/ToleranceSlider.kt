package com.parcoursparis.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parcoursparis.R

private const val MIN_TOLERANCE = 10
private const val MAX_TOLERANCE = 25

/**
 * Slider Material 3 pour la tolérance de surplus de temps (10–25 %).
 * Label : "Surplus temps max : X %"
 */
@Composable
fun ToleranceSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.tolerance_label, value),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(MIN_TOLERANCE, MAX_TOLERANCE)) },
            valueRange = MIN_TOLERANCE.toFloat()..MAX_TOLERANCE.toFloat(),
            steps = MAX_TOLERANCE - MIN_TOLERANCE - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
