package com.parcoursparis.map.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parcoursparis.R
import com.parcoursparis.routing.RouteResult
import com.parcoursparis.routing.RouteType
import com.parcoursparis.ui.component.ToleranceSlider

/**
 * Bottom sheet affichant ETA, distance, progression, ToleranceSlider et choix découverte/classique.
 */
@Composable
fun RouteBottomSheet(
    route: RouteResult,
    tolerancePercent: Int,
    routeProgressPercent: Int = 0,
    distanceRemainingMeters: Double = 0.0,
    hasDiscoveryRoute: Boolean = false,
    hasClassicRoute: Boolean = false,
    onToleranceChange: (Int) -> Unit,
    onRequestClassicRoute: () -> Unit,
    onRequestDiscoveryRoute: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val etaMinutes = (route.etaSeconds / 60).toInt()
    val distanceKm = route.distanceMeters / 1000.0

    ModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.route_bottom_sheet_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.route_eta_format, etaMinutes),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.route_distance_format, distanceKm),
                style = MaterialTheme.typography.bodyMedium
            )
            if (routeProgressPercent > 0 || distanceRemainingMeters > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val distanceStr = if (distanceRemainingMeters >= 1000) {
                    stringResource(R.string.route_progress_km, distanceRemainingMeters / 1000.0)
                } else {
                    stringResource(R.string.route_progress_meters, distanceRemainingMeters)
                }
                Text(
                    text = stringResource(R.string.route_progress_format, routeProgressPercent, distanceStr),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            ToleranceSlider(
                value = tolerancePercent,
                onValueChange = onToleranceChange
            )
            if (route.routeType == RouteType.DISCOVERY && hasClassicRoute) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRequestClassicRoute,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.route_button_classic))
                }
            }
            if (route.routeType == RouteType.CLASSIC && hasDiscoveryRoute) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRequestDiscoveryRoute,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.route_button_discovery))
                }
            }
        }
    }
}
