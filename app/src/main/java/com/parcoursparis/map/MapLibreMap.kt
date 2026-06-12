package com.parcoursparis.map

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.parcoursparis.map.component.ScaleBarOverlay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.parcoursparis.R
import com.parcoursparis.data.repository.SegmentWithExploredState
import com.parcoursparis.routing.RouteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val TAG = "MapLibreMap"
private const val USER_LOCATION_SOURCE_ID = "user-location-source"
private const val USER_LOCATION_LAYER_ID = "user-location-layer"
private const val PARIS_LAT = 48.8566
private const val PARIS_LON = 2.3522
private const val PARIS_ZOOM = 11.5
private const val OSM_STYLE_URI = "asset://osm_style.json"
private const val SEGMENTS_SOURCE_ID = "parcours-segments"
private const val ROUTE_SOURCE_ID = "route-source"
private const val ROUTE_LAYER_ID = "route-layer"
private const val COLOR_EXPLORED = "#4CAF50"
private const val COLOR_UNEXPLORED = "#9E9E9E"
private const val COLOR_ROUTE_ACCENT = "#2196F3"
// LOD segments non explorés (gris) : uniquement quand Paris remplit l'écran.
// Trop dézoomé (périphérique visible) ou trop zoomé (vue « tuile » rue) → masqués.
private const val LOD_UNEXPLORED_MIN_ZOOM = 12.0f
private const val LOD_UNEXPLORED_MAX_ZOOM = 12.8f

/**
 * Composable map with OpenStreetMap tiles, pan, zoom, and colored segment layer.
 * Paris center (48.8566, 2.3522), zoom 11-12.
 * Segments: green (#4CAF50) for explored, grey (#9E9E9E) for unexplored.
 * Lifecycle: onStart/onStop/onDestroy managed via LocalLifecycleOwner.
 */
/**
 * Convertit RouteResult.geometry en GeoJSON LineString.
 */
private fun routeToGeoJsonLineString(route: RouteResult): String {
    val coords = route.geometry.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return """{"type":"LineString","coordinates":[$coords]}"""
}

/** État de la caméra pour l'échelle (zoom, latitude du centre). */
data class CameraScaleState(val zoom: Double, val centerLat: Double)

@Composable
fun MapLibreMap(
    segments: List<SegmentWithExploredState>,
    userLocation: org.maplibre.android.geometry.LatLng? = null,
    route: RouteResult? = null,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val sourceRef = remember { mutableStateOf<GeoJsonSource?>(null) }
    val routeSourceRef = remember { mutableStateOf<GeoJsonSource?>(null) }
    val locationSourceRef = remember { mutableStateOf<GeoJsonSource?>(null) }
    var cameraScaleState by remember {
        mutableStateOf<CameraScaleState?>(CameraScaleState(PARIS_ZOOM.toDouble(), PARIS_LAT.toDouble()))
    }
    // HIGH-1: toujours accéder à la version la plus récente des segments dans les callbacks async
    val currentSegments = rememberUpdatedState(segments)
    // Même pattern pour userLocation : accès à la dernière valeur dans le callback setStyle
    val currentUserLocation = rememberUpdatedState(userLocation)
    val currentRoute = rememberUpdatedState(route)
    // HIGH-2: scope pour lancer la conversion GeoJSON hors du main thread depuis setStyle
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                mapViewState.value = this
                addOnDidFailLoadingMapListener { errorMessage ->
                    Log.e(TAG, "Style load failed: $errorMessage")
                    Toast.makeText(ctx, ctx.getString(R.string.map_load_error), Toast.LENGTH_LONG).show()
                }
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    onStart()
                }
                getMapAsync { map ->
                    map.addOnCameraIdleListener {
                        val pos = map.cameraPosition
                        cameraScaleState = CameraScaleState(
                            zoom = pos.zoom.toDouble(),
                            centerLat = pos.target?.latitude ?: PARIS_LAT
                        )
                    }
                    map.setStyle(Style.Builder().fromUri(OSM_STYLE_URI)) { style ->
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(PARIS_LAT, PARIS_LON))
                            .zoom(PARIS_ZOOM)
                            .build()

                        // Source vide initialement — remplie hors main thread ci-dessous (HIGH-2)
                        val emptyGeoJson = """{"type":"FeatureCollection","features":[]}"""
                        val source = GeoJsonSource(SEGMENTS_SOURCE_ID, emptyGeoJson)
                        style.addSource(source)
                        sourceRef.value = source

                        // Segments explorés (verts) : tous niveaux de zoom
                        // Segments non explorés (gris) : bande de zoom « Paris plein écran »
                        val exploredLayer = LineLayer("parcours-segments-explored", SEGMENTS_SOURCE_ID)
                            .withFilter(Expression.eq(Expression.get("isExplored"), Expression.literal(true)))
                            .withProperties(
                                PropertyFactory.lineColor(android.graphics.Color.parseColor(COLOR_EXPLORED)),
                                PropertyFactory.lineWidth(3f)
                            )
                        val unexploredLayer = LineLayer("parcours-segments-unexplored", SEGMENTS_SOURCE_ID)
                            .withFilter(Expression.eq(Expression.get("isExplored"), Expression.literal(false)))
                            .withProperties(
                                PropertyFactory.lineColor(android.graphics.Color.parseColor(COLOR_UNEXPLORED)),
                                PropertyFactory.lineWidth(2f)
                            )
                        unexploredLayer.setMinZoom(LOD_UNEXPLORED_MIN_ZOOM)
                        unexploredLayer.setMaxZoom(LOD_UNEXPLORED_MAX_ZOOM)
                        style.addLayer(exploredLayer)
                        style.addLayer(unexploredLayer)

                        // Route: GeoJsonSource + LineLayer (above segments, below userLocation)
                        val routeSource = GeoJsonSource(ROUTE_SOURCE_ID, """{"type":"FeatureCollection","features":[]}""")
                        style.addSource(routeSource)
                        routeSourceRef.value = routeSource
                        currentRoute.value?.let { r ->
                            routeSource.setGeoJson(
                                """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":${routeToGeoJsonLineString(r)},"properties":{}}]}"""
                            )
                        }
                        val routeLayer = LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID)
                            .withProperties(
                                PropertyFactory.lineColor(android.graphics.Color.parseColor(COLOR_ROUTE_ACCENT)),
                                PropertyFactory.lineWidth(4f)
                            )
                        style.addLayer(routeLayer)

                        // User location: GeoJsonSource + CircleLayer for GPS dot
                        val locationSource = GeoJsonSource(
                            USER_LOCATION_SOURCE_ID,
                            """{"type":"FeatureCollection","features":[]}"""
                        )
                        style.addSource(locationSource)
                        locationSourceRef.value = locationSource
                        // Appliquer immédiatement si une position GPS était déjà connue avant
                        // le chargement du style (évite la race condition au démarrage)
                        currentUserLocation.value?.let { loc ->
                            locationSource.setGeoJson(
                                """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${loc.longitude},${loc.latitude}]},"properties":{}}]}"""
                            )
                        }
                        val locationLayer = CircleLayer(
                            USER_LOCATION_LAYER_ID,
                            USER_LOCATION_SOURCE_ID
                        ).withProperties(
                            PropertyFactory.circleRadius(8f),
                            PropertyFactory.circleColor(
                                android.graphics.Color.parseColor("#2196F3")
                            ),
                            PropertyFactory.circleStrokeWidth(2f),
                            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE)
                        )
                        style.addLayer(locationLayer)

                        // HIGH-1 + HIGH-2: conversion hors main thread avec données les plus récentes
                        scope.launch {
                            val geoJson = withContext(Dispatchers.Default) {
                                SegmentGeoJsonConverter.toFeatureCollectionJson(currentSegments.value)
                            }
                            source.setGeoJson(geoJson)
                        }
                    }
                }
            }
        }
    )
        cameraScaleState?.let { state ->
            ScaleBarOverlay(
                zoom = state.zoom,
                centerLatitude = state.centerLat,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            )
        }
    }

    LaunchedEffect(segments) {
        sourceRef.value?.let { source ->
            val geoJson = withContext(Dispatchers.Default) {
                SegmentGeoJsonConverter.toFeatureCollectionJson(segments)
            }
            source.setGeoJson(geoJson)
        }
    }

    LaunchedEffect(userLocation) {
        locationSourceRef.value?.let { source ->
            val geoJson = if (userLocation != null) {
                """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${userLocation.longitude},${userLocation.latitude}]},"properties":{}}]}"""
            } else {
                """{"type":"FeatureCollection","features":[]}"""
            }
            source.setGeoJson(geoJson)
        }
    }

    LaunchedEffect(route) {
        routeSourceRef.value?.let { source ->
            val geoJson = if (route != null) {
                """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":${routeToGeoJsonLineString(route)},"properties":{}}]}"""
            } else {
                """{"type":"FeatureCollection","features":[]}"""
            }
            source.setGeoJson(geoJson)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            mapViewState.value?.let { mapView ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> {}
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapViewState.value?.let { mapView ->
                mapView.onStop()
                mapView.onDestroy()
            }
            mapViewState.value = null
            sourceRef.value = null
            routeSourceRef.value = null
            locationSourceRef.value = null
        }
    }
}

