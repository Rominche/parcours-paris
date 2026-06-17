package com.parcoursparis.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.parcoursparis.ParcoursParisApplication
import com.parcoursparis.R
import com.parcoursparis.map.geocoding.BoundingBox
import com.parcoursparis.map.geocoding.GeocodingNetworkException
import com.parcoursparis.map.geocoding.GeocodingResult
import com.parcoursparis.map.geocoding.GeocodingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PARIS_BOUNDS = BoundingBox(
    minLon = 2.2,
    minLat = 48.8,
    maxLon = 2.4,
    maxLat = 48.92
)

/** Format: "destLat,destLng" or "destLat,destLng,origLat,origLng" */
const val ADDRESS_SEARCH_RESULT = "address_search_result"
const val ADDRESS_SEARCH_RESULT_LABEL = "address_search_result_label"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressSearchScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = context.applicationContext as ParcoursParisApplication
    val geocodingService: GeocodingService = appContext.geocodingService
    val scope = rememberCoroutineScope()

    var departureQuery by remember { mutableStateOf("") }
    var destinationQuery by remember { mutableStateOf("") }
    var departureSuggestions by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var destinationSuggestions by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var departureSelected by remember { mutableStateOf<GeocodingResult?>(null) }
    var destinationSelected by remember { mutableStateOf<GeocodingResult?>(null) }
    var isSearchingDeparture by remember { mutableStateOf(false) }
    var isSearchingDestination by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var activeField by remember { mutableStateOf<String?>(null) } // "departure" ou "destination"
    val offlineMessage = stringResource(R.string.search_offline_message)

    var departureSearchJob by remember { mutableStateOf<Job?>(null) }
    var destinationSearchJob by remember { mutableStateOf<Job?>(null) }

    fun searchDeparture(query: String) {
        departureSearchJob?.cancel()
        if (query.isBlank()) {
            departureSuggestions = emptyList()
            isSearchingDeparture = false
            return
        }
        departureSearchJob = scope.launch {
            isSearchingDeparture = true
            searchError = null
            delay(300)
            try {
                val results = geocodingService.search(query, PARIS_BOUNDS)
                departureSuggestions = results
            } catch (e: GeocodingNetworkException) {
                searchError = e.message ?: offlineMessage
                departureSuggestions = emptyList()
            } catch (e: Exception) {
                searchError = e.message ?: "Erreur de recherche"
                departureSuggestions = emptyList()
            }
            isSearchingDeparture = false
        }
    }

    fun searchDestination(query: String) {
        destinationSearchJob?.cancel()
        if (query.isBlank()) {
            destinationSuggestions = emptyList()
            isSearchingDestination = false
            return
        }
        destinationSearchJob = scope.launch {
            isSearchingDestination = true
            searchError = null
            delay(300)
            try {
                val results = geocodingService.search(query, PARIS_BOUNDS)
                destinationSuggestions = results
            } catch (e: GeocodingNetworkException) {
                searchError = e.message ?: offlineMessage
                destinationSuggestions = emptyList()
            } catch (e: Exception) {
                searchError = e.message ?: "Erreur de recherche"
                destinationSuggestions = emptyList()
            }
            isSearchingDestination = false
        }
    }

    fun onConfirm() {
        val dest = destinationSelected ?: return
        val origin = departureSelected?.toLatLng()
        val result = if (origin != null) {
            "${dest.latitude},${dest.longitude},${origin.latitude},${origin.longitude}"
        } else {
            "${dest.latitude},${dest.longitude}"
        }
        navController.previousBackStackEntry?.savedStateHandle?.apply {
            set(ADDRESS_SEARCH_RESULT, result)
            set(ADDRESS_SEARCH_RESULT_LABEL, dest.label)
        }
        navController.popBackStack()
    }

    val canConfirm = destinationSelected != null

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.address_search_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = departureSelected?.label ?: departureQuery,
                onValueChange = {
                    departureSelected = null
                    departureQuery = it
                    searchDeparture(it)
                    activeField = "departure"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                placeholder = { Text(stringResource(R.string.address_search_departure_placeholder)) },
                leadingIcon = {
                    if (isSearchingDeparture) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                singleLine = true,
                readOnly = departureSelected != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            if (activeField == "departure" && departureSuggestions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(departureSuggestions) { _, result ->
                        Text(
                            text = result.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    departureSelected = result
                                    departureQuery = result.label
                                    departureSuggestions = emptyList()
                                    activeField = null
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                    }
                }
            }

            OutlinedTextField(
                value = destinationSelected?.label ?: destinationQuery,
                onValueChange = {
                    destinationSelected = null
                    destinationQuery = it
                    searchDestination(it)
                    activeField = "destination"
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.address_search_destination_placeholder)) },
                leadingIcon = {
                    if (isSearchingDestination) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                singleLine = true,
                readOnly = destinationSelected != null,
                isError = searchError != null,
                supportingText = if (searchError != null) {
                    {
                        Text(
                            text = if (searchError!!.contains("Connectez-vous") || searchError!!.contains("réseau")) offlineMessage else searchError!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    errorBorderColor = MaterialTheme.colorScheme.error
                )
            )
            if (activeField == "destination" && destinationSuggestions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(destinationSuggestions) { _, result ->
                        Text(
                            text = result.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    destinationSelected = result
                                    destinationQuery = result.label
                                    destinationSuggestions = emptyList()
                                    activeField = null
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f))

            Button(
                onClick = { onConfirm() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                enabled = canConfirm
            ) {
                Text(stringResource(R.string.address_search_confirm))
            }
        }
    }
}
