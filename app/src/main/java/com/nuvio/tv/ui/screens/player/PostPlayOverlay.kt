@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.ui.theme.NuvioMotion

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.Cancel
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.ui.util.localizeEpisodeTitle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.focus.onFocusChanged
import com.nuvio.tv.domain.model.VisualStyle
import com.nuvio.tv.ui.v2.appearance.LocalV2Appearance
import com.nuvio.tv.ui.v2.components.GlassRole
import com.nuvio.tv.ui.v2.components.nuvioGlass
import com.nuvio.tv.ui.v2.components.nuvioV2Focus
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.nuvio.tv.R
import com.nuvio.tv.ui.util.BlurTransformation

@Composable
fun PostPlayOverlay(
    mode: PostPlayMode?,
    controlsVisible: Boolean,
    blurUnwatchedEpisodes: Boolean,
    nextEpisodeFocusRequester: FocusRequester,
    progressBarFocusRequester: FocusRequester?,
    leftFocusRequester: FocusRequester?,
    onPlayNext: () -> Unit,
    onCancelNext: () -> Unit,
    onContinueStillWatching: () -> Unit,
    onDismissStillWatching: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAutoPlay = mode is PostPlayMode.AutoPlay
    val isAutoPlayPlayable = (mode as? PostPlayMode.AutoPlay)?.nextEpisode?.hasAired == true

    var placedFocused by remember(isAutoPlay, controlsVisible) { mutableStateOf(false) }

    val transition = updateTransition(targetState = mode, label = "PostPlayMode")
    // Fork: the up-next card keeps its last content while it slides away.
    var lastMode by remember { mutableStateOf(mode) }
    if (mode != null) lastMode = mode
    val shownMode = mode ?: lastMode

    transition.AnimatedVisibility(
        visible = { it != null },
        enter = slideInHorizontally(animationSpec = tween(260), initialOffsetX = { it / 2 }) +
            fadeIn(animationSpec = tween(220)),
        exit = slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { it / 2 }) +
            fadeOut(animationSpec = tween(160)),
        modifier = modifier,
    ) {
        if (shownMode is PostPlayMode.AutoPlay) {
            UpNextCard(
                mode = shownMode,
                blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                autoFocus = !controlsVisible,
                playFocusRequester = nextEpisodeFocusRequester,
                progressBarFocusRequester = progressBarFocusRequester,
                leftFocusRequester = leftFocusRequester,
                onPlayNow = onPlayNext,
                onCancel = onCancelNext,
            )
        } else {
        val onCardClick: () -> Unit = when {
            isAutoPlay && isAutoPlayPlayable -> onPlayNext
            mode is PostPlayMode.StillWatching -> onContinueStillWatching
            else -> ({})
        }
        Card(
            onClick = onCardClick,
            shape = CardDefaults.shape(shape = RoundedCornerShape(14.dp)),
            colors = CardDefaults.colors(
                containerColor = Color(0xE3191919),
                focusedContainerColor = Color(0xE3191919),
            ),
            border = CardDefaults.border(
                border = Border(
                    border = BorderStroke(NuvioTheme.spacing.hairline, Color.White.copy(alpha = 0.16f)),
                    shape = RoundedCornerShape(14.dp),
                ),
                focusedBorder = Border(
                    border = BorderStroke(NuvioTheme.spacing.xxs, Color.White),
                    shape = RoundedCornerShape(14.dp),
                ),
            ),
            scale = CardDefaults.scale(focusedScale = 1f),
            modifier = Modifier
                .width(420.dp)
                .focusRequester(nextEpisodeFocusRequester)
                .onPlaced {
                    if (isAutoPlay && !controlsVisible && !placedFocused) {
                        placedFocused = true
                        runCatching { nextEpisodeFocusRequester.requestFocus() }
                    }
                }
                .then(
                    if (progressBarFocusRequester != null || leftFocusRequester != null) {
                        Modifier.focusProperties {
                            progressBarFocusRequester?.let { down = it }
                            leftFocusRequester?.let { left = it }
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            transition.AnimatedContent(
                transitionSpec = {
                    fadeIn(animationSpec = tween(NuvioMotion.tokens.durations.fast)) togetherWith
                        fadeOut(animationSpec = tween(120))
                },
                contentKey = { it?.let { current -> current::class } },
            ) { current ->
                when (current) {
                    is PostPlayMode.AutoPlay -> AutoPlayBody(
                        mode = current,
                        blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                    )
                    is PostPlayMode.StillWatching -> StillWatchingBody(
                        mode = current,
                        blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                        onContinue = onContinueStillWatching,
                        onDismiss = onDismissStillWatching,
                    )
                    null -> Unit
                }
            }
        }
        }
    }
}

/**
 * Fork: the up-next card. Thumbnail on the left; the episode, its title and the countdown to the end of the
 * playing episode on the right, over Cancel and Play now. Play now is focused when the card comes up.
 */
@Composable
private fun UpNextCard(
    mode: PostPlayMode.AutoPlay,
    blurUnwatchedEpisodes: Boolean,
    autoFocus: Boolean,
    playFocusRequester: FocusRequester,
    progressBarFocusRequester: FocusRequester?,
    leftFocusRequester: FocusRequester?,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    val nextEpisode = mode.nextEpisode
    val isPlayable = nextEpisode.hasAired
    val cancelFocusRequester = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val v2 = LocalV2Appearance.current != null
    val shape = RoundedCornerShape(if (v2) 20.dp else 16.dp)
    Row(
        modifier = Modifier
            .width(if (v2) 430.dp else 390.dp)
            .then(
                if (v2) {
                    Modifier.nuvioGlass(GlassRole.PANEL, shape = shape)
                } else {
                    Modifier
                        .clip(shape)
                        .background(Color(0xF2202020))
                        .border(BorderStroke(NuvioTheme.spacing.hairline, Color.White.copy(alpha = 0.10f)), shape)
                }
            )
            .padding(if (v2) 14.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NextEpisodeThumbnail(
            thumbnail = nextEpisode.thumbnail,
            contentDescription = stringResource(R.string.cd_next_episode_thumbnail),
            blurred = blurUnwatchedEpisodes,
            width = if (v2) 144.dp else 160.dp,
            height = if (v2) 81.dp else 90.dp,
            corner = if (v2) 10.dp else 6.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val code = if (nextEpisode.isOtherType) null else stringResource(
                R.string.season_episode_format, nextEpisode.season, nextEpisode.episode
            )
            val topLine = nextEpisode.mixName?.let { stringResource(R.string.scout_mixes_next_in, it) }
                ?: listOfNotNull(nextEpisode.showName, code).joinToString(" \u00B7 ").ifBlank { null }
            topLine?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = nextEpisode.title.localizeEpisodeTitle(LocalContext.current),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            val status = when {
                nextEpisode.mixName != null -> mode.countdownSec?.let {
                    pluralStringResource(R.plurals.scout_mixes_starts_in, it, it)
                }
                !isPlayable && !nextEpisode.unairedMessage.isNullOrBlank() -> nextEpisode.unairedMessage
                mode.searching -> stringResource(R.string.next_episode_finding_source)
                mode.countdownSec != null -> stringResource(R.string.next_episode_playing_in, mode.countdownSec)
                else -> null
            }
            Text(
                text = status.orEmpty(),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpNextButton(
                    label = stringResource(R.string.next_episode_cancel),
                    filled = false,
                    v2 = v2,
                    onClick = onCancel,
                    modifier = Modifier
                        .focusRequester(cancelFocusRequester)
                        .focusProperties {
                            progressBarFocusRequester?.let { down = it }
                            if (isRtl) {
                                left = playFocusRequester
                                leftFocusRequester?.let { right = it }
                            } else {
                                right = playFocusRequester
                                leftFocusRequester?.let { left = it }
                            }
                        },
                )
                if (isPlayable) {
                    UpNextButton(
                        label = stringResource(R.string.next_episode_play_now),
                        filled = true,
                        v2 = v2,
                        onClick = onPlayNow,
                        modifier = Modifier
                            .focusRequester(playFocusRequester)
                            .focusProperties {
                                progressBarFocusRequester?.let { down = it }
                                if (isRtl) right = cancelFocusRequester else left = cancelFocusRequester
                            }
                            .onPlaced {
                                if (autoFocus && !focusedOnce) {
                                    focusedOnce = true
                                    runCatching { playFocusRequester.requestFocus() }
                                }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpNextButton(
    label: String,
    filled: Boolean,
    v2: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (v2) {
        V2UpNextButton(label = label, primary = filled, onClick = onClick, modifier = modifier)
        return
    }
    val pill = RoundedCornerShape(10.dp)
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape = pill),
        colors = CardDefaults.colors(
            containerColor = if (filled) Color.White else Color(0xFF3A3A3A),
            focusedContainerColor = if (filled) Color.White else Color(0xFF4A4A4A),
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioTheme.colors.FocusRing),
                shape = pill,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        modifier = modifier,
    ) {
        Text(
            text = label.uppercase(),
            maxLines = 1,
            softWrap = false,
            color = if (filled) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AutoPlayBody(
    mode: PostPlayMode.AutoPlay,
    blurUnwatchedEpisodes: Boolean,
) {
    val nextEpisode = mode.nextEpisode
    val isPlayable = nextEpisode.hasAired
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NextEpisodeThumbnail(
            thumbnail = nextEpisode.thumbnail,
            contentDescription = stringResource(R.string.cd_next_episode_thumbnail),
            blurred = blurUnwatchedEpisodes,
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = nextEpisode.mixName?.let { stringResource(R.string.scout_mixes_next_in, it) }
                    ?: stringResource(R.string.next_episode_label),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
            Text(
                text = nextEpisodeDisplayLabel(nextEpisode),
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            val statusText = when {
                nextEpisode.mixName != null -> mode.countdownSec?.let {
                    pluralStringResource(R.plurals.scout_mixes_starts_in, it, it)
                }
                !isPlayable && !nextEpisode.unairedMessage.isNullOrBlank() -> nextEpisode.unairedMessage
                mode.searching -> stringResource(R.string.next_episode_finding_source)
                !mode.sourceName.isNullOrBlank() && mode.countdownSec != null ->
                    stringResource(R.string.next_episode_playing_via, mode.sourceName, mode.countdownSec)
                else -> null
            }
            if (statusText != null) {
                Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
                NextEpisodeStatusLine(text = statusText)
            }
        }

        Row(
            modifier = Modifier
                .padding(start = NuvioTheme.spacing.sm)
                .clip(CircleShape)
                .border(
                    BorderStroke(NuvioTheme.spacing.hairline, Color.White.copy(alpha = 0.2f)),
                    CircleShape,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (isPlayable) Color.White else Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (isPlayable) {
                    stringResource(R.string.next_episode_play)
                } else {
                    stringResource(R.string.next_episode_unaired)
                },
                color = if (isPlayable) Color.White else Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 3.dp),
            )
        }
    }
}

@Composable
private fun StillWatchingBody(
    mode: PostPlayMode.StillWatching,
    blurUnwatchedEpisodes: Boolean,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val continueFocusRequester = remember { FocusRequester() }
    val exitFocusRequester = remember { FocusRequester() }
    var continueFocused by remember { mutableStateOf(false) }

    val nextEpisode = mode.nextEpisode
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (nextEpisode.thumbnail != null) {
            NextEpisodeThumbnail(
                thumbnail = nextEpisode.thumbnail,
                contentDescription = stringResource(R.string.cd_next_episode_thumbnail),
                blurred = blurUnwatchedEpisodes,
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.still_watching_title),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
            Text(
                text = nextEpisodeDisplayLabel(nextEpisode),
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            if (mode.countdownSec != null) {
                Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
                NextEpisodeStatusLine(
                    text = stringResource(R.string.still_watching_countdown, mode.countdownSec),
                )
            }
        }
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Row(
            modifier = Modifier.padding(start = NuvioTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PostPlayPillButton(
                icon = Icons.Default.PlayArrow,
                iconTint = Color.White,
                label = stringResource(R.string.still_watching_continue),
                textColor = Color.White,
                onClick = onContinue,
                focusRequester = continueFocusRequester,
                modifier = Modifier
                    .onPlaced {
                        if (!continueFocused) {
                            continueFocused = true
                            runCatching { continueFocusRequester.requestFocus() }
                        }
                    }
                    .focusProperties {
                        if (isRtl) left = exitFocusRequester else right = exitFocusRequester
                        if (isRtl) right = Cancel else left = Cancel
                    },
            )
            PostPlayPillButton(
                icon = Icons.Default.Close,
                iconTint = Color.White.copy(alpha = 0.65f),
                label = stringResource(R.string.still_watching_exit),
                textColor = Color.White.copy(alpha = 0.72f),
                onClick = onDismiss,
                focusRequester = exitFocusRequester,
                modifier = Modifier.focusProperties {
                    if (isRtl) right = continueFocusRequester else left = continueFocusRequester
                    if (isRtl) left = Cancel else right = Cancel
                },
            )
        }
    }
}

@Composable
private fun PostPlayPillButton(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    textColor: Color,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape = CircleShape),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.15f),
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(NuvioTheme.spacing.hairline, Color.White.copy(alpha = 0.2f)),
                shape = CircleShape,
            ),
            focusedBorder = Border(
                border = BorderStroke(NuvioTheme.spacing.xxs, Color.White),
                shape = CircleShape,
            ),
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
        modifier = modifier.focusRequester(focusRequester),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 3.dp),
            )
        }
    }
}

/**
 * Fork: the V2 player's button look (as on its transport row): glass with the V2 focus edge; Play now is the
 * solid light button unless the look is Cinematic Glass, where every button is glass.
 */
@Composable
private fun V2UpNextButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    val solid = primary && LocalV2Appearance.current?.visualStyle != VisualStyle.CINEMATIC_GLASS
    Button(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .onFocusChanged { focused = it.isFocused }
            .nuvioV2Focus(focused, shape, hardwareShadow = false)
            .nuvioGlass(GlassRole.CONTROL, focused, shape),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        shape = ButtonDefaults.shape(shape),
        colors = ButtonDefaults.colors(
            containerColor = if (solid) Color(0xE8E8F1FF) else Color.Transparent,
            focusedContainerColor = if (solid) Color(0xFFE8F1FF) else Color.Transparent,
            contentColor = if (solid) Color(0xFF071323) else Color.White,
            focusedContentColor = if (solid) Color(0xFF071323) else Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Icon(
            imageVector = if (primary) Icons.Default.PlayArrow else Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun NextEpisodeThumbnail(
    thumbnail: String?,
    contentDescription: String?,
    blurred: Boolean,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 112.dp,
    height: androidx.compose.ui.unit.Dp = 64.dp,
    corner: androidx.compose.ui.unit.Dp = 9.dp,
) {
    val context = LocalContext.current
    val imageRequest = remember(context, thumbnail, blurred) {
        ImageRequest.Builder(context)
            .data(thumbnail)
            .crossfade(true)
            .apply {
                if (blurred) transformations(BlurTransformation())
            }
            .build()
    }
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(corner)),
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)),
                    ),
                ),
        )
    }
}

@Composable
private fun NextEpisodeStatusLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.78f),
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun nextEpisodeDisplayLabel(nextEpisode: NextEpisodeInfo): String {
    if (nextEpisode.isOtherType) return nextEpisode.title
    val context = LocalContext.current
    val code = stringResource(
        R.string.season_episode_format,
        nextEpisode.season,
        nextEpisode.episode,
    )
    val localizedTitle = nextEpisode.title.localizeEpisodeTitle(context)
    return listOfNotNull(nextEpisode.showName, code, localizedTitle).joinToString(" • ")
}
