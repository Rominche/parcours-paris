package com.parcoursparis.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.parcoursparis.map.ADDRESS_SEARCH_RESULT
import com.parcoursparis.map.ADDRESS_SEARCH_RESULT_LABEL
import com.parcoursparis.map.component.CompassRoseOverlay
import com.parcoursparis.map.component.RouteBottomSheet
import com.parcoursparis.map.component.SegmentSelector
import com.parcoursparis.navigation.NavRoutes
import org.maplibre.android.geometry.LatLng
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parcoursparis.ParcoursParisApplication
import com.parcoursparis.R

@Composable
fun MapScreen(navController: NavController) {
    val context = LocalContext.current
    val displayDensity = context.resources.displayMetrics.density
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
    val selectedSegment = uiState.selectedSegmentId?.let { id ->
        uiState.segments.find { it.segment.osm_way_id == id }
    }
    var showRationaleDialog by remember { mutableStateOf(false) }
    val destinationClearDesc = stringResource(R.string.destination_clear_button)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(granted)
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
            mapBearing = uiState.mapBearing,
            navigationMode = uiState.route != null,
            selectedSegmentId = uiState.selectedSegmentId,
            segmentSelectionEnabled = uiState.isSegmentSelectionEnabled,
            onMapClick = { latLng, zoom ->
                viewModel.onMapTap(latLng, zoom, displayDensity)
            },
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(NavRoutes.ADDRESS_SEARCH) }
                    .background(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = if (uiState.destination != null) {
                        uiState.searchQuery.ifBlank {
                            stringResource(R.string.address_search_button)
                        }
                    } else {
                        stringResource(R.string.address_search_button)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.destination != null) {
                    IconButton(
                        onClick = { viewModel.onClearDestination() },
                        modifier = Modifier.semantics {
                            contentDescription = destinationClearDesc
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            val backStackEntry = navController.currentBackStackEntry
                ?: navController.getBackStackEntry(NavRoutes.MAP)
            backStackEntry.savedStateHandle.getStateFlow(ADDRESS_SEARCH_RESULT, null)
                .collect { result: String? ->
                    result?.let { str ->
                        try {
                            val parts = str.split(",")
                            if (parts.size >= 2) {
                                val dest = LatLng(parts[0].toDouble(), parts[1].toDouble())
                                val origin = if (parts.size >= 4) {
                                    LatLng(parts[2].toDouble(), parts[3].toDouble())
                                } else null
                                val label = backStackEntry.savedStateHandle
                                    .get<String>(ADDRESS_SEARCH_RESULT_LABEL).orEmpty()
                                viewModel.onAddressSearchResult(dest, origin, label)
                            }
                        } catch (_: NumberFormatException) {
                            // Coordonnées invalides — ignorer sans crasher
                        } finally {
                            backStackEntry.savedStateHandle.remove<String>(ADDRESS_SEARCH_RESULT)
                            backStackEntry.savedStateHandle.remove<String>(ADDRESS_SEARCH_RESULT_LABEL)
                        }
                    }
                }
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
        if (uiState.isComputingRoute && uiState.segments.isEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.route_error_segments_loading),
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.routeError!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (uiState.routeError == stringResource(R.string.route_error_no_position)) {
                        TextButton(
                            onClick = { viewModel.onRequestRoute(useParisAsFallback = true) }
                        ) {
                            Text(
                                text = stringResource(R.string.route_error_use_paris_fallback),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
        val compassBottomPadding = if (uiState.destination != null) 88.dp else 16.dp
        CompassRoseOverlay(
            mapBearingDegrees = uiState.mapBearing.toFloat(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = compassBottomPadding)
        )
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
                onStopRoute = viewModel::onStopRoute,
                onDismissRequest = viewModel::onDismissRouteBottomSheet
            )
        }
        if (uiState.isSegmentSelectionEnabled && selectedSegment != null) {
            SegmentSelector(
                selectedSegment = selectedSegment,
                progressStats = uiState.progressStats,
                onMarkExplored = viewModel::onMarkSelectedExplored,
                onMarkUnexplored = viewModel::onMarkSelectedUnexplored,
                onDismiss = viewModel::onClearSegmentSelection,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
