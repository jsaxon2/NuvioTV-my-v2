package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * How opaque the player menus are (0 = fully clear, 1 = solid). The video keeps
 * playing behind them, so this is a balance between see-through and readable text.
 */
internal const val PLAYER_GLASS_ALPHA = 0.58f

/** Dimming behind the Episodes / Sources / Subtitle style side panels. */
internal const val PLAYER_GLASS_SCRIM_ALPHA = 0.2f

/**
 * Translucent "smoked glass" for player menus, matching the V2 glass palette.
 * Live blur isn't possible here: decoded video is drawn on its own surface that
 * the UI can't sample, so the glass is a tinted, edge-lit translucent panel.
 */
@Composable
internal fun Modifier.playerGlass(shape: Shape): Modifier {
    val accent = NuvioTheme.colors.Primary
    val fill = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFF1B1E24).copy(alpha = PLAYER_GLASS_ALPHA),
                Color(0xFF080A0F).copy(alpha = (PLAYER_GLASS_ALPHA + 0.08f).coerceAtMost(1f))
            )
        )
    }
    val edge = remember {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.06f),
                Color.Black.copy(alpha = 0.3f)
            )
        )
    }
    return clip(shape)
        .background(fill, shape)
        .background(accent.copy(alpha = 0.06f), shape)
        .border(1.dp, edge, shape)
}
