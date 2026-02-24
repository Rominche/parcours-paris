package com.parcoursparis.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parcoursparis.ParcoursParisApplication
import com.parcoursparis.R

@Composable
fun MapScreen() {
    val repository = (LocalContext.current.applicationContext as ParcoursParisApplication).segmentRepository
    val viewModel: MapViewModel = viewModel(factory = MapViewModelFactory(repository))
    val uiState by viewModel.uiState.collectAsState()
    val mapContentDesc = stringResource(R.string.map_content_description)

    Box(modifier = Modifier.fillMaxSize()) {
        MapLibreMap(
            segments = uiState.segments,
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    testTag = "maplibre_map"
                    contentDescription = mapContentDesc
                }
        )
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
    }
}
