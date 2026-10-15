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
        assertEquals(OsdHdrFormat.DOLBY_VISION, osdHdrFormat("video/dolby-vision", isPq = true, isHlg = false, sourceName = "HDR10+"))
        assertEquals(OsdHdrFormat.HDR10_PLUS, osdHdrFormat("video/hevc", isPq = true, isHlg = false, sourceName = "Movie 2160p HDR10+ DV"))
        assertEquals(OsdHdrFormat.HDR10_PLUS, osdHdrFormat("video/hevc", isPq = true, isHlg = false, sourceName = "movie.hdr10plus.mkv"))
        assertEquals(OsdHdrFormat.HDR10, osdHdrFormat("video/hevc", isPq = true, isHlg = false, sourceName = "Movie 2160p HDR"))
        assertEquals(OsdHdrFormat.HLG, osdHdrFormat("video/hevc", isPq = false, isHlg = true, sourceName = null))
        assertNull(osdHdrFormat("video/avc", isPq = false, isHlg = false, sourceName = "HDR10+"))
    }

    @Test fun `audio shows codec, atmos, layout and the audio route`() {
        assertEquals("E-AC-3 5.1 · Passthrough", osdAudio(MimeTypes.AUDIO_E_AC3, 6, AudioRoutingMode.PASSTHROUGH_IEC, null)!!.describe())
        assertEquals("E-AC-3 Atmos 5.1 · Passthrough", osdAudio(MimeTypes.AUDIO_E_AC3_JOC, 6, AudioRoutingMode.PASSTHROUGH_IEC, null)!!.describe())
        assertEquals("TrueHD Atmos 7.1 · Passthrough", osdAudio(MimeTypes.AUDIO_TRUEHD, 8, AudioRoutingMode.PASSTHROUGH_DIRECT, "Movie.2160p.TrueHD.Atmos.7.1")!!.describe())
        assertEquals("TrueHD 7.1 · Passthrough", osdAudio(MimeTypes.AUDIO_TRUEHD, 8, AudioRoutingMode.PASSTHROUGH_DIRECT, "Movie.TrueHD.7.1")!!.describe())
        assertEquals("DTS 5.1 · AC-3 transcode", osdAudio(MimeTypes.AUDIO_DTS, 6, AudioRoutingMode.TRANSCODE_AC3, null)!!.describe())
        assertEquals("AAC 2.0 · PCM", osdAudio(MimeTypes.AUDIO_AAC, 2, AudioRoutingMode.PCM, null)!!.describe())
        assertEquals("AAC 2.0", osdAudio(MimeTypes.AUDIO_AAC, 2, null, null)!!.describe())
        assertNull(osdAudio(null, 2, null, null))
    }

    @Test fun `badges match the source list artwork`() {
        assertEquals(R.drawable.badge_res_2160, osdResolutionBadge("4K"))
        assertEquals(R.drawable.badge_res_1080, osdResolutionBadge("1080p"))
        assertEquals(R.drawable.badge_dv, osdHdrBadge(OsdHdrFormat.DOLBY_VISION))
        assertEquals(R.drawable.badge_hdr10_plus, osdHdrBadge(OsdHdrFormat.HDR10_PLUS))
        assertEquals(R.drawable.badge_truehd, osdAudioCodecBadge(MimeTypes.AUDIO_TRUEHD, null))
        assertEquals(R.drawable.badge_ddp, osdAudioCodecBadge(MimeTypes.AUDIO_E_AC3_JOC, null))
        assertEquals(R.drawable.badge_dts_hd_ma, osdAudioCodecBadge(MimeTypes.AUDIO_DTS_HD, "Movie.DTS-HD.MA.7.1"))
        assertEquals(R.drawable.badge_dts_hd, osdAudioCodecBadge(MimeTypes.AUDIO_DTS_HD, "Movie.DTS-HD.HRA"))
        assertNull(osdAudioCodecBadge(MimeTypes.AUDIO_AAC, null))
        assertEquals(R.drawable.badge_ch_7_1, osdChannelBadge("7.1"))
        assertNull(osdChannelBadge("2.0"))
    }

    @Test fun `fusion lookup tokens use release name spellings for each slot`() {
        val info = PlayerOsdMediaInfo(
            resolution = "4K",
            hdr = OsdHdrFormat.DOLBY_VISION,
            audio = osdAudio(MimeTypes.AUDIO_TRUEHD, 8, AudioRoutingMode.PASSTHROUGH_DIRECT, "TrueHD.Atmos.7.1")
        )
        assertEquals(
            mapOf(
                OsdBadgeSlot.RESOLUTION to listOf("2160p", "4K", "UHD"),
                OsdBadgeSlot.HDR to listOf("DV", "DoVi", "Dolby.Vision"),
                OsdBadgeSlot.CODEC to listOf("TrueHD"),
                OsdBadgeSlot.ATMOS to listOf("Atmos"),
                OsdBadgeSlot.CHANNELS to listOf("7.1")
            ),
            info.fusionLookupTokens()
        )
        val ddp = PlayerOsdMediaInfo(null, null, osdAudio(MimeTypes.AUDIO_E_AC3, 6, null, null)).fusionLookupTokens()
        assertEquals(listOf("DDP", "DD+", "EAC3"), ddp[OsdBadgeSlot.CODEC])
        assertEquals(listOf("5.1"), ddp[OsdBadgeSlot.CHANNELS])
    }
}
