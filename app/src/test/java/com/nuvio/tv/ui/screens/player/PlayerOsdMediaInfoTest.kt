package com.nuvio.tv.ui.screens.player

import androidx.media3.common.MimeTypes
import com.nuvio.tv.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerOsdMediaInfoTest {
    @Test fun `resolution labels follow the frame size`() {
        assertEquals("4K", osdResolutionLabel(3840, 2160))
        assertEquals("4K", osdResolutionLabel(3840, 1600)) // scope UHD
        assertEquals("1080p", osdResolutionLabel(1920, 1080))
        assertEquals("1080p", osdResolutionLabel(1920, 800)) // scope HD
        assertEquals("720p", osdResolutionLabel(1280, 720))
        assertEquals("SD", osdResolutionLabel(720, 480))
        assertNull(osdResolutionLabel(0, 0))
    }

    @Test fun `hdr format prefers dolby vision then hdr10 plus then pq then hlg`() {
        assertEquals(OsdHdrFormat.DOLBY_VISION, osdHdrFormat("video/dolby-vision", isPq = true, isHlg = false, hdr10PlusSeen = true))
        // HDR10+ only once the decoder has returned HDR10+ metadata.
        assertEquals(OsdHdrFormat.HDR10_PLUS, osdHdrFormat("video/hevc", isPq = true, isHlg = false, hdr10PlusSeen = true))
        assertEquals(OsdHdrFormat.HDR10, osdHdrFormat("video/hevc", isPq = true, isHlg = false))
        assertEquals(OsdHdrFormat.HLG, osdHdrFormat("video/hevc", isPq = false, isHlg = true))
        assertNull(osdHdrFormat("video/avc", isPq = false, isHlg = false, hdr10PlusSeen = true))
    }

    @Test fun `audio shows codec, atmos, layout and the audio route`() {
        assertEquals("E-AC-3 5.1 · Passthrough", osdAudio(MimeTypes.AUDIO_E_AC3, 6, AudioRoutingMode.PASSTHROUGH_IEC)!!.describe())
        assertEquals("E-AC-3 Atmos 5.1 · Passthrough", osdAudio(MimeTypes.AUDIO_E_AC3_JOC, 6, AudioRoutingMode.PASSTHROUGH_IEC)!!.describe())
        assertEquals("TrueHD Atmos 7.1 · Passthrough", osdAudio(MimeTypes.AUDIO_TRUEHD, 8, AudioRoutingMode.PASSTHROUGH_DIRECT, trueHdAtmos = true)!!.describe())
        assertEquals("TrueHD 7.1 · Passthrough", osdAudio(MimeTypes.AUDIO_TRUEHD, 8, AudioRoutingMode.PASSTHROUGH_DIRECT)!!.describe())
        assertEquals("DTS 5.1 · AC-3 transcode", osdAudio(MimeTypes.AUDIO_DTS, 6, AudioRoutingMode.TRANSCODE_AC3)!!.describe())
        assertEquals("AAC 2.0 · PCM", osdAudio(MimeTypes.AUDIO_AAC, 2, AudioRoutingMode.PCM)!!.describe())
        assertEquals("AAC 2.0", osdAudio(MimeTypes.AUDIO_AAC, 2, null)!!.describe())
        assertNull(osdAudio(null, 2, null))
    }

    @Test fun `badges match the source list artwork`() {
        assertEquals(R.drawable.badge_res_2160, osdResolutionBadge("4K"))
        assertEquals(R.drawable.badge_res_1080, osdResolutionBadge("1080p"))
        assertEquals(R.drawable.badge_dv, osdHdrBadge(OsdHdrFormat.DOLBY_VISION))
        assertEquals(R.drawable.badge_hdr10_plus, osdHdrBadge(OsdHdrFormat.HDR10_PLUS))
        assertEquals(R.drawable.badge_truehd, osdAudioCodecBadge(MimeTypes.AUDIO_TRUEHD))
        assertEquals(R.drawable.badge_ddp, osdAudioCodecBadge(MimeTypes.AUDIO_E_AC3_JOC))
        assertEquals(R.drawable.badge_dts_hd_ma, osdAudioCodecBadge(MimeTypes.AUDIO_DTS_HD, dtsHdMa = true))
        assertEquals(R.drawable.badge_dts_hd, osdAudioCodecBadge(MimeTypes.AUDIO_DTS_HD))
        assertNull(osdAudioCodecBadge(MimeTypes.AUDIO_AAC))
        assertEquals(R.drawable.badge_ch_7_1, osdChannelBadge("7.1"))
        assertNull(osdChannelBadge("2.0"))
    }

    @Test fun `fusion lookup tokens use release name spellings for each slot`() {
        val info = PlayerOsdMediaInfo(
            resolution = "4K",
            hdr = OsdHdrFormat.DOLBY_VISION,
            audio = osdAudio(MimeTypes.AUDIO_TRUEHD, 8, AudioRoutingMode.PASSTHROUGH_DIRECT, trueHdAtmos = true)
        )
        assertEquals(
            mapOf(
                OsdBadgeSlot.RESOLUTION to listOf("2160p", "4K", "UHD"),
                OsdBadgeSlot.HDR to listOf("DV", "DoVi", "Dolby.Vision"),
                OsdBadgeSlot.CODEC to listOf("TrueHD"),
                OsdBadgeSlot.ATMOS to listOf("Atmos"),
                OsdBadgeSlot.CHANNELS to listOf("7.1"),
                OsdBadgeSlot.ROUTE to listOf("Passthrough")
            ),
            info.fusionLookupTokens()
        )
        val ddp = PlayerOsdMediaInfo(null, null, osdAudio(MimeTypes.AUDIO_E_AC3, 6, null)).fusionLookupTokens()
        assertEquals(listOf("DDP", "DD+", "EAC3"), ddp[OsdBadgeSlot.CODEC])
        assertEquals(listOf("5.1"), ddp[OsdBadgeSlot.CHANNELS])
        assertNull(ddp[OsdBadgeSlot.ROUTE])
    }

    @Test fun `aac stereo decoded to pcm looks up codec, channels and route`() {
        val aac = PlayerOsdMediaInfo(null, null, osdAudio(MimeTypes.AUDIO_AAC, 2, AudioRoutingMode.PCM)).fusionLookupTokens()
        assertEquals(listOf("AAC"), aac[OsdBadgeSlot.CODEC])
        assertEquals(listOf("2.0"), aac[OsdBadgeSlot.CHANNELS])
        assertEquals(listOf("PCM", "LPCM"), aac[OsdBadgeSlot.ROUTE])
        // A PCM track doesn't repeat its PCM badge for the route.
        val pcm = PlayerOsdMediaInfo(null, null, osdAudio(MimeTypes.AUDIO_RAW, 2, AudioRoutingMode.PCM)).fusionLookupTokens()
        assertEquals(listOf("LPCM", "PCM"), pcm[OsdBadgeSlot.CODEC])
        assertNull(pcm[OsdBadgeSlot.ROUTE])
    }
}
