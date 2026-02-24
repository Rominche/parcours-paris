package com.parcoursparis.map

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.parcoursparis.R
import com.parcoursparis.data.repository.SegmentWithExploredState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val TAG = "MapLibreMap"
private const val PARIS_LAT = 48.8566
private const val PARIS_LON = 2.3522
private const val PARIS_ZOOM = 11.5
private const val MAPLIBRE_STYLE_URL = "https://demotiles.maplibre.org/style.json"
private const val SEGMENTS_SOURCE_ID = "parcours-segments"
private const val COLOR_EXPLORED = "#4CAF50"
private const val COLOR_UNEXPLORED = "#9E9E9E"
// LOD: rues non-parcourues visibles seulement à zoom >= 12 (détail)
// En dézoom, seuls les segments explorés (verts) restent visibles → aperçu de la progression
private const val LOD_DETAIL_MIN_ZOOM = 12f

/**
 * Composable MapLibre map with pan, zoom, and colored segment layer.
 * Paris center (48.8566, 2.3522), zoom 11-12.
 * Segments: green (#4CAF50) for explored, grey (#9E9E9E) for unexplored.
 * Lifecycle: onStart/onStop/onDestroy managed via LocalLifecycleOwner.
 */
@Composable
fun MapLibreMap(
    segments: List<SegmentWithExploredState>,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val sourceRef = remember { mutableStateOf<GeoJsonSource?>(null) }
    // HIGH-1: toujours accéder à la version la plus récente des segments dans les callbacks async
    val currentSegments = rememberUpdatedState(segments)
    // HIGH-2: scope pour lancer la conversion GeoJSON hors du main thread depuis setStyle
    val scope = rememberCoroutineScope()

    AndroidView(
        modifier = modifier,
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
                    map.setStyle(Style.Builder().fromUri(MAPLIBRE_STYLE_URL)) { style ->
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(PARIS_LAT, PARIS_LON))
                            .zoom(PARIS_ZOOM)
                            .build()

                        // Source vide initialement — remplie hors main thread ci-dessous (HIGH-2)
                        val emptyGeoJson = """{"type":"FeatureCollection","features":[]}"""
                        val source = GeoJsonSource(SEGMENTS_SOURCE_ID, emptyGeoJson)
                        style.addSource(source)
                        sourceRef.value = source

                        // CRITIQUE-1 LOD: segments explorés (verts) visibles à tous niveaux de zoom
                        // CRITIQUE-1 LOD: segments non explorés (gris) uniquement à zoom >= LOD_DETAIL_MIN_ZOOM
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
                        unexploredLayer.setMinZoom(LOD_DETAIL_MIN_ZOOM)
                        style.addLayer(exploredLayer)
                        style.addLayer(unexploredLayer)

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

    LaunchedEffect(segments) {
        sourceRef.value?.let { source ->
            val geoJson = withContext(Dispatchers.Default) {
                SegmentGeoJsonConverter.toFeatureCollectionJson(segments)
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
        }
    }
}

