package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MimeTypes
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R

/** Fork: what the player OSD shows top-left - resolution, HDR format and the audio line. */
internal data class PlayerOsdMediaInfo(
    val resolution: String?,
    val hdr: OsdHdrFormat?,
    val audio: String?
)

internal enum class OsdHdrFormat { DOLBY_VISION, HDR10_PLUS, HDR10, HLG }

internal fun osdResolutionLabel(width: Int, height: Int): String? {
    if (width <= 0 && height <= 0) return null
    return when {
        width >= 3200 || height >= 1800 -> "4K"
        width >= 1900 || height >= 1000 -> "1080p"
        width >= 1200 || height >= 700 -> "720p"
        else -> "SD"
    }
}

/**
 * Dolby Vision comes from the video MIME type and PQ/HLG from the colour transfer. HDR10+
 * dynamic metadata isn't visible in the format, so a PQ stream counts as HDR10+ when the
 * source name says so (the same signal the source badges use).
 */
internal fun osdHdrFormat(mimeType: String?, isPq: Boolean, isHlg: Boolean, sourceName: String?): OsdHdrFormat? = when {
    mimeType?.contains("dolby-vision") == true -> OsdHdrFormat.DOLBY_VISION
    isPq && sourceName.mentionsHdr10Plus() -> OsdHdrFormat.HDR10_PLUS
    isPq -> OsdHdrFormat.HDR10
    isHlg -> OsdHdrFormat.HLG
    else -> null
}

private fun String?.mentionsHdr10Plus(): Boolean =
    this != null && Regex("""hdr\s*10\s*(\+|plus)""", RegexOption.IGNORE_CASE).containsMatchIn(this)

internal fun osdAudioCodec(mimeType: String?): String? = when (mimeType) {
    MimeTypes.AUDIO_TRUEHD -> "TrueHD"
    MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
    MimeTypes.AUDIO_DTS_X -> "DTS:X"
    MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
    MimeTypes.AUDIO_DTS -> "DTS"
    MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3 Atmos"
    MimeTypes.AUDIO_E_AC3 -> "E-AC-3"
    MimeTypes.AUDIO_AC3 -> "AC-3"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_VORBIS -> "Vorbis"
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.AUDIO_RAW -> "PCM"
    null -> null
    else -> mimeType.substringAfter('/').uppercase()
}

internal fun osdChannelLayout(channelCount: Int): String? = when (channelCount) {
    1 -> "Mono"
    2 -> "2.0"
    3 -> "2.1"
    6 -> "5.1"
    7 -> "6.1"
    8 -> "7.1"
    in 4..Int.MAX_VALUE -> "${channelCount}ch"
    else -> null
}

/** "E-AC-3 5.1 · receiver": codec + layout, then where the sound is decoded. */
internal fun osdAudioLine(mimeType: String?, channelCount: Int, route: AudioRoutingMode?): String? {
    val codec = osdAudioCodec(mimeType) ?: return null
    val layout = osdChannelLayout(channelCount)
    val where = when (route) {
        AudioRoutingMode.PASSTHROUGH_IEC, AudioRoutingMode.PASSTHROUGH_DIRECT -> "receiver"
        AudioRoutingMode.TRANSCODE_AC3 -> "AC-3 to receiver"
        AudioRoutingMode.PCM -> "decoded"
        null -> null
    }
    return listOfNotNull(listOfNotNull(codec, layout).joinToString(" "), where).joinToString(" · ")
}

private val OsdInfoColor = Color.White.copy(alpha = 0.62f)

@Composable
internal fun PlayerOsdMediaInfoBar(info: PlayerOsdMediaInfo, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        info.resolution?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic
                ),
                color = OsdInfoColor
            )
        }
        info.hdr?.let { hdr ->
            val badge = when (hdr) {
                OsdHdrFormat.DOLBY_VISION -> R.drawable.badge_dv
                OsdHdrFormat.HDR10_PLUS -> R.drawable.badge_hdr10_plus
                OsdHdrFormat.HDR10 -> R.drawable.badge_hdr10
                OsdHdrFormat.HLG -> R.drawable.badge_hdr
            }
            Image(
                painter = painterResource(badge),
                contentDescription = hdr.name,
                contentScale = ContentScale.FillHeight,
                colorFilter = ColorFilter.tint(OsdInfoColor),
                modifier = Modifier.height(13.dp)
            )
        }
        info.audio?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
