package com.parcoursparis.map.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.parcoursparis.R
import com.parcoursparis.data.repository.ProgressStats
import com.parcoursparis.data.repository.SegmentWithExploredState

/**
 * Barre d'actions pour le marquage manuel d'un segment sélectionné sur la carte.
 * Touch targets ≥ 48dp (accessibilité).
 */
@Composable
fun SegmentSelector(
    selectedSegment: SegmentWithExploredState?,
    progressStats: ProgressStats,
    onMarkExplored: () -> Unit,
    onMarkUnexplored: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedSegment == null) return

    val isExplored = selectedSegment.isExplored
    val markExploredDesc = stringResource(R.string.segment_mark_explored_button)
    val markUnexploredDesc = stringResource(R.string.segment_mark_unexplored_button)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { testTag = "segment_selector_bar" },
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        if (isExplored) R.string.segment_selected_explored else R.string.segment_selected_unexplored
                    ),
                    style = MaterialTheme.typography.titleSmall
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.segment_dismiss_selection))
                }
            }
            Text(
                text = stringResource(
                    R.string.segment_progress_summary,
                    progressStats.exploredPercent,
                    progressStats.exploredCount,
                    progressStats.totalCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onMarkExplored,
                    enabled = !isExplored,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics {
                            testTag = "segment_mark_explored"
                            contentDescription = markExploredDesc
                        }
                ) {
                    Text(markExploredDesc)
                }
                OutlinedButton(
                    onClick = onMarkUnexplored,
                    enabled = isExplored,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics {
                            testTag = "segment_mark_unexplored"
                            contentDescription = markUnexploredDesc
                        }
                ) {
                    Text(markUnexploredDesc)
                }
            }
        }
    }
}
