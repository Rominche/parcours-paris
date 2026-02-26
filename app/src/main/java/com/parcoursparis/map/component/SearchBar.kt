package com.parcoursparis.map.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.parcoursparis.R
import com.parcoursparis.map.geocoding.GeocodingResult

/**
 * Search bar overlay for the map: Material 3 OutlinedTextField with 16dp padding.
 * Shows suggestions list below; emits query changes and selection to ViewModel.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<GeocodingResult>,
    isSearching: Boolean,
    searchError: String?,
    onSuggestionSelected: (GeocodingResult) -> Unit,
    modifier: Modifier = Modifier,
    placeholderResId: Int = R.string.search_placeholder,
    offlineMessageResId: Int = R.string.search_offline_message
) {
    val placeholder = stringResource(placeholderResId)
    val offlineMessage = stringResource(offlineMessageResId)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    testTag = "search_bar"
                    contentDescription = placeholder
                },
            placeholder = { Text(placeholder) },
            leadingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                }
            },
            singleLine = true,
            isError = searchError != null,
            supportingText = if (searchError != null) {
                {
                    Text(
                        text = if (searchError.contains("Connectez-vous") || searchError.contains("réseau")) offlineMessage else searchError,
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
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            if (suggestions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .semantics { testTag = "search_suggestions" },
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(suggestions) { _, result ->
                        Text(
                            text = result.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionSelected(result) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}
