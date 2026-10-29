package com.nuvio.tv.ui.screens.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MimeTypes
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.StreamBadge
import com.nuvio.tv.ui.components.StreamImportedBadgeChip

/**
 * Fork: what the player OSD shows top-left - resolution, HDR format and the audio line,
 * drawn with the same full-colour badges as the source list.
 */
internal data class PlayerOsdMediaInfo(
    val resolution: String?,
    val hdr: OsdHdrFormat?,
    val audio: OsdAudio?
)

internal enum class OsdHdrFormat { DOLBY_VISION, HDR10_PLUS, HDR10, HLG }

/** The audio part: a codec badge (or text when there's no badge), Atmos, channels and the route. */
internal data class OsdAudio(
    val codec: String,
    @DrawableRes val codecBadge: Int?,
    val atmos: Boolean,
    val channels: String?,
    val route: String?
)

internal fun osdResolutionLabel(width: Int, height: Int): String? {
    if (width <= 0 && height <= 0) return null
    return when {
        width >= 3200 || height >= 1800 -> "4K"
        width >= 1900 || height >= 1000 -> "1080p"
        width >= 1200 || height >= 700 -> "720p"
        else -> "SD"
    }
}

@DrawableRes
internal fun osdResolutionBadge(label: String?): Int? = when (label) {
    "4K" -> R.drawable.badge_res_2160
    "1080p" -> R.drawable.badge_res_1080
    "720p" -> R.drawable.badge_res_720
    "SD" -> R.drawable.badge_res_480
    else -> null
}

/**
 * Dolby Vision comes from the video MIME type and PQ/HLG from the colour transfer. HDR10+
 * dynamic metadata isn't in the format: a PQ stream counts as HDR10+ once the decoder has
 * returned HDR10+ metadata ([hdr10PlusSeen]). Release names are never consulted, since they
 * can list several versions.
 */
internal fun osdHdrFormat(
    mimeType: String?,
    isPq: Boolean,
    isHlg: Boolean,
    hdr10PlusSeen: Boolean = false
): OsdHdrFormat? = when {
    mimeType?.contains("dolby-vision") == true -> OsdHdrFormat.DOLBY_VISION
    isPq && hdr10PlusSeen -> OsdHdrFormat.HDR10_PLUS
    isPq -> OsdHdrFormat.HDR10
    isHlg -> OsdHdrFormat.HLG
    else -> null
}

@DrawableRes
internal fun osdHdrBadge(hdr: OsdHdrFormat?): Int? = when (hdr) {
    OsdHdrFormat.DOLBY_VISION -> R.drawable.badge_dv
    OsdHdrFormat.HDR10_PLUS -> R.drawable.badge_hdr10_plus
    OsdHdrFormat.HDR10 -> R.drawable.badge_hdr10
    OsdHdrFormat.HLG -> R.drawable.badge_hdr
    null -> null
}

internal fun osdAudioCodec(mimeType: String?): String? = when (mimeType) {
    MimeTypes.AUDIO_TRUEHD -> "TrueHD"
    MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
    MimeTypes.AUDIO_DTS_X -> "DTS:X"
    MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
    MimeTypes.AUDIO_DTS -> "DTS"
    MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3"
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

/** DTS-HD MA vs HRA isn't in the MIME type; [dtsHdMa] comes from the bitstream. */
@DrawableRes
internal fun osdAudioCodecBadge(mimeType: String?, dtsHdMa: Boolean = false): Int? = when (mimeType) {
    MimeTypes.AUDIO_TRUEHD -> R.drawable.badge_truehd
    MimeTypes.AUDIO_DTS_X -> R.drawable.badge_dts_x
    MimeTypes.AUDIO_DTS_HD -> if (dtsHdMa) R.drawable.badge_dts_hd_ma else R.drawable.badge_dts_hd
    MimeTypes.AUDIO_DTS -> R.drawable.badge_dts
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> R.drawable.badge_ddp
    MimeTypes.AUDIO_AC3 -> R.drawable.badge_dd
    else -> null
}

/** E-AC-3 JOC is Atmos by definition (the extractor reads it from the bitstream); TrueHD says so in its major sync. */
internal fun osdIsAtmos(mimeType: String?, trueHdAtmos: Boolean = false): Boolean = when (mimeType) {
    MimeTypes.AUDIO_E_AC3_JOC -> true
    MimeTypes.AUDIO_TRUEHD -> trueHdAtmos
    else -> false
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

@DrawableRes
internal fun osdChannelBadge(channels: String?): Int? = when (channels) {
    "7.1" -> R.drawable.badge_ch_7_1
    "5.1" -> R.drawable.badge_ch_5_1
    else -> null
}

internal fun osdAudioRoute(route: AudioRoutingMode?): String? = when (route) {
    AudioRoutingMode.PASSTHROUGH_IEC, AudioRoutingMode.PASSTHROUGH_DIRECT -> "Passthrough"
    AudioRoutingMode.TRANSCODE_AC3 -> "AC-3 transcode"
    AudioRoutingMode.PCM -> "PCM"
    null -> null
}

internal fun osdAudio(
    mimeType: String?,
    channelCount: Int,
    route: AudioRoutingMode?,
    trueHdAtmos: Boolean = false,
    dtsHd: Boolean = false,
    dtsHdMa: Boolean = false,
    dtsX: Boolean = false
): OsdAudio? {
    // Matroska labels every DTS track plain DTS; the bitstream says which one it really is.
    val mime = when {
        mimeType != MimeTypes.AUDIO_DTS && mimeType != MimeTypes.AUDIO_DTS_HD -> mimeType
        dtsX -> MimeTypes.AUDIO_DTS_X
        dtsHd || dtsHdMa -> MimeTypes.AUDIO_DTS_HD
        else -> mimeType
    }
    val codec = osdAudioCodec(mime) ?: return null
    return OsdAudio(
        codec = codec,
        codecBadge = osdAudioCodecBadge(mime, dtsHdMa),
        atmos = osdIsAtmos(mime, trueHdAtmos),
        channels = osdChannelLayout(channelCount),
        route = osdAudioRoute(route)
    )
}

/** Text form of the audio part, e.g. "E-AC-3 Atmos 5.1 · Passthrough" (used for accessibility and tests). */
internal fun OsdAudio.describe(): String =
    listOfNotNull(
        listOfNotNull(codec, "Atmos".takeIf { atmos }, channels).joinToString(" "),
        route
    ).joinToString(" · ")

/** Each badge position in the OSD line; the user's Fusion badge set can supply its artwork. */
internal enum class OsdBadgeSlot { RESOLUTION, HDR, CODEC, ATMOS, CHANNELS, ROUTE }

/**
 * Candidate words for each slot, spelled the way release names spell them, most typical first.
 * Each is tried against the user's Fusion badge rules until one yields a badge.
 */
internal fun PlayerOsdMediaInfo.fusionLookupTokens(): Map<OsdBadgeSlot, List<String>> = buildMap {
    when (resolution) {
        "4K" -> listOf("2160p", "4K", "UHD")
        "1080p" -> listOf("1080p")
        "720p" -> listOf("720p")
        "SD" -> listOf("480p", "576p", "SD")
        else -> null
    }?.let { put(OsdBadgeSlot.RESOLUTION, it) }
    when (hdr) {
        OsdHdrFormat.DOLBY_VISION -> listOf("DV", "DoVi", "Dolby.Vision")
        OsdHdrFormat.HDR10_PLUS -> listOf("HDR10+", "HDR10Plus")
        OsdHdrFormat.HDR10 -> listOf("HDR10")
        OsdHdrFormat.HLG -> listOf("HLG", "HDR")
        null -> null
    }?.let { put(OsdBadgeSlot.HDR, it) }
    audio?.let { a ->
        when {
            a.codecBadge == R.drawable.badge_dts_hd_ma -> listOf("DTS-HD.MA", "DTS-HD")
            a.codec == "E-AC-3" -> listOf("DDP", "DD+", "EAC3")
            a.codec == "AC-3" -> listOf("AC3", "DD")
            a.codec == "DTS:X" -> listOf("DTS-X", "DTS.X")
            a.codec == "PCM" -> listOf("LPCM", "PCM")
            else -> listOf(a.codec)
        }.let { put(OsdBadgeSlot.CODEC, it) }
        if (a.atmos) put(OsdBadgeSlot.ATMOS, listOf("Atmos"))
        a.channels?.takeIf { it.contains('.') }?.let { put(OsdBadgeSlot.CHANNELS, listOf(it)) }
        when (a.route) {
            "Passthrough" -> listOf("Passthrough")
            // A PCM track already shows its PCM badge as the codec.
            "PCM" -> listOf("PCM", "LPCM").takeIf { a.codec != "PCM" }
            else -> null
        }?.let { put(OsdBadgeSlot.ROUTE, it) }
    }
}

private val OsdTextColor = Color.White.copy(alpha = 0.92f)
private val OsdMutedColor = Color.White.copy(alpha = 0.85f)

@Composable
private fun OsdText(text: String, bold: Boolean = false, italic: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = if (italic) 15.sp else 12.sp,
            fontWeight = when {
                italic -> FontWeight.ExtraBold
                bold -> FontWeight.SemiBold
                else -> FontWeight.Normal
            },
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
        ),
        color = if (italic) OsdMutedColor else OsdTextColor
    )
}

/**
 * [fusion] holds the user's Fusion badge for each slot that has one. Anything without one is
 * written out as text (resolution in bold italic, HDR as the grey-tinted logo), never as the
 * built-in colour badges.
 */
@Composable
internal fun PlayerOsdMediaInfoBar(
    info: PlayerOsdMediaInfo,
    modifier: Modifier = Modifier,
    fusion: Map<OsdBadgeSlot, StreamBadge> = emptyMap()
) {
    // Same dark pill as the clock, so the line stays readable over bright scenes.
    Row(
        modifier = modifier
            .background(Color(0xE61A1C22), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        info.resolution?.let { res ->
            fusion[OsdBadgeSlot.RESOLUTION]?.let { StreamImportedBadgeChip(it) } ?: OsdText(res, italic = true)
        }
        info.hdr?.let { hdr ->
            fusion[OsdBadgeSlot.HDR]?.let { StreamImportedBadgeChip(it) } ?: osdHdrBadge(hdr)?.let { res ->
                // The Dolby Vision logo carries small "VISION" lettering that blurs when tiny.
                Image(
                    painter = painterResource(res),
                    contentDescription = hdr.name,
                    contentScale = ContentScale.FillHeight,
                    colorFilter = ColorFilter.tint(OsdMutedColor),
                    modifier = Modifier.height(if (hdr == OsdHdrFormat.DOLBY_VISION) 20.dp else 15.dp)
                )
            }
        }
        info.audio?.let { audio ->
            val codecBadge = fusion[OsdBadgeSlot.CODEC]
            val atmosBadge = fusion[OsdBadgeSlot.ATMOS].takeIf { audio.atmos }
            val channelBadge = fusion[OsdBadgeSlot.CHANNELS]
            val routeBadge = fusion[OsdBadgeSlot.ROUTE]
            if (codecBadge != null) StreamImportedBadgeChip(codecBadge)
            if (atmosBadge != null) StreamImportedBadgeChip(atmosBadge)
            if (channelBadge != null) StreamImportedBadgeChip(channelBadge)
            // Whatever has no badge is written out, then the route: "TrueHD 7.1 · Passthrough".
            val text = listOfNotNull(
                audio.codec.takeIf { codecBadge == null },
                "Atmos".takeIf { audio.atmos && atmosBadge == null },
                audio.channels.takeIf { channelBadge == null }
            ).joinToString(" ")
            val route = audio.route.takeIf { routeBadge == null && !(audio.route == "PCM" && audio.codec == "PCM") }
            val line = listOfNotNull(text.takeIf { it.isNotBlank() }, route).joinToString(" · ")
            if (line.isNotBlank()) {
                OsdText(if (text.isBlank()) "· $line" else line)
            }
            if (routeBadge != null) StreamImportedBadgeChip(routeBadge)
        }
    }
}
