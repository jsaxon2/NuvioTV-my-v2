@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.tracking.TrackerDisconnectNotices
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Fork: shown on Home when trackers were signed out by their services (not by the viewer). Reconnect opens the
 * tracker settings; Leave off closes it. Either way the notice is answered and doesn't come back.
 */
@Composable
internal fun TrackerDisconnectedDialogHost(onReconnect: () -> Unit) {
    val allNotices by TrackerDisconnectNotices.pending.collectAsState()
    val profileId by TrackerDisconnectNotices.activeProfile.collectAsState()
    val notices = TrackerDisconnectNotices.forProfile(allNotices, profileId)
    // Wait for the start animation to finish, then a moment more, so the alert doesn't cover it.
    val entry = com.nuvio.tv.ui.v2.profile.LocalProfileEntryState.current
    val introPlaying = entry?.pending == true
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(introPlaying) {
        if (introPlaying) {
            settled = false
        } else {
            kotlinx.coroutines.delay(AFTER_INTRO_MS)
            settled = true
        }
    }
    if (notices.isEmpty() || !settled) return
    val context = LocalContext.current
    val reconnectFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { reconnectFocus.requestFocus() } }

    val names = notices.map { it.tracker }
    val title = if (names.size == 1) {
        stringResource(R.string.tracker_disconnected_title_one, names.first())
    } else {
        stringResource(R.string.tracker_disconnected_title_many)
    }
    val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
    val dateFormat = android.text.format.DateFormat.getMediumDateFormat(context)

    NuvioDialog(
        onDismiss = { TrackerDisconnectNotices.dismissFor(profileId) },
        title = title,
        subtitle = stringResource(R.string.tracker_disconnected_subtitle),
        suppressFirstKeyUp = false
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)) {
            notices.forEach { notice ->
                val at = java.util.Date(notice.atMs)
                Text(
                    text = stringResource(
                        R.string.tracker_disconnected_line,
                        notice.tracker,
                        dateFormat.format(at),
                        timeFormat.format(at)
                    ),
                    color = NuvioTheme.colors.TextSecondary
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            Button(
                onClick = { TrackerDisconnectNotices.dismissFor(profileId) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Text(stringResource(R.string.tracker_disconnected_leave_off))
            }
            Button(
                onClick = {
                    TrackerDisconnectNotices.dismissFor(profileId)
                    onReconnect()
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(reconnectFocus)
            ) {
                Text(stringResource(R.string.tracker_disconnected_reconnect))
            }
        }
    }
}

private const val AFTER_INTRO_MS = 1_200L
