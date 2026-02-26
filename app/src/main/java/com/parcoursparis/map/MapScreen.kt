package com.parcoursparis.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.parcoursparis.map.component.RouteBottomSheet
import com.parcoursparis.map.component.SearchBar
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parcoursparis.ParcoursParisApplication
import com.parcoursparis.R

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext as ParcoursParisApplication
    val repository = appContext.segmentRepository
    val viewModel: MapViewModel = viewModel(
        factory = MapViewModelFactory(
            repository,
            appContext.geocodingService,
            appContext.discoveryRoutingEngine,
            appContext.userPreferences,
            appContext
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    val mapContentDesc = stringResource(R.string.map_content_description)
    val routeComputeButtonDesc = stringResource(R.string.route_compute_button)
    var showRationaleDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(granted)
    }

    LaunchedEffect(uiState.searchQuery) {
        delay(300)
        viewModel.onSearchQuerySubmit(uiState.searchQuery)
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.onLocationPermissionResult(true)
        } else {
            showRationaleDialog = true
        }
    }

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = {
                showRationaleDialog = false
                viewModel.onLocationPermissionResult(false)
            },
            text = {
                Text(
                    text = stringResource(R.string.location_permission_rationale),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }) {
                    Text(stringResource(R.string.permission_dialog_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    viewModel.onLocationPermissionResult(false)
                }) {
                    Text(stringResource(R.string.permission_dialog_deny_button))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapLibreMap(
            segments = uiState.segments,
            userLocation = uiState.userLocation,
            route = uiState.route,
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    testTag = "maplibre_map"
                    contentDescription = mapContentDesc
                }
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                suggestions = uiState.searchSuggestions,
                isSearching = uiState.isSearching,
                searchError = uiState.searchError,
                onSuggestionSelected = viewModel::onDestinationSelected
            )
        }
        if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.map_segments_error),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (uiState.locationPermissionDenied) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.location_permission_denied),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (uiState.routeError != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.routeError!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (uiState.destination != null) {
            FloatingActionButton(
                onClick = { viewModel.onRequestRoute() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .semantics {
                        testTag = "route_compute_fab"
                        contentDescription = routeComputeButtonDesc
                    },
                content = {
                    if (uiState.isComputingRoute) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = routeComputeButtonDesc
                        )
                    }
                }
            )
        }
        if (uiState.route != null && uiState.showRouteBottomSheet) {
            RouteBottomSheet(
                route = uiState.route!!,
                tolerancePercent = uiState.tolerancePercent,
                routeProgressPercent = uiState.routeProgressPercent,
                distanceRemainingMeters = uiState.distanceRemainingMeters,
                hasDiscoveryRoute = uiState.discoveryRoute != null,
                hasClassicRoute = uiState.classicRoute != null,
                onToleranceChange = viewModel::onToleranceChanged,
                onRequestClassicRoute = viewModel::onRequestClassicRoute,
                onRequestDiscoveryRoute = viewModel::onRequestDiscoveryRoute,
                onDismissRequest = viewModel::onDismissRouteBottomSheet
            )
        }
    }
}
