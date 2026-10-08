package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.v2.appearance.LocalV2Appearance
import com.nuvio.tv.ui.v2.components.NuvioActionPill

@Composable
internal fun ShuffleButton(
    active: Boolean,
    pending: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit
) {
    val focusModifier = Modifier
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .focusProperties { up = FocusRequester.Cancel }
        .onFocusChanged { if (it.isFocused) onFocused() }
    if (LocalV2Appearance.current != null) {
        // Fork: match the V2 hero actions (Library, Trailer), which use the V2 action pill.
        NuvioActionPill(onClick = { if (!pending) onClick() }, modifier = focusModifier) {
            Icon(if (active) Icons.Default.Stop else Icons.Default.Shuffle, null, Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (active) R.string.shuffle_stop else R.string.random_episode_title),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
        return
    }
    Button(
        onClick = { if (!pending) onClick() },
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties { up = FocusRequester.Cancel }
            .onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (active) Icons.Default.Stop else Icons.Default.Shuffle, null, Modifier.size(18.dp))
            Text(stringResource(if (active) R.string.shuffle_stop else R.string.random_episode_title))
        }
    }
}
