package com.nuvio.tv.ui.screens.player

import androidx.media3.common.MimeTypes
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

    @Test fun `audio line shows codec, layout and where it is decoded`() {
        assertEquals("E-AC-3 5.1 · receiver", osdAudioLine(MimeTypes.AUDIO_E_AC3, 6, AudioRoutingMode.PASSTHROUGH_IEC))
        assertEquals("TrueHD 7.1 · receiver", osdAudioLine(MimeTypes.AUDIO_TRUEHD, 8, AudioRoutingMode.PASSTHROUGH_DIRECT))
        assertEquals("DTS 5.1 · AC-3 to receiver", osdAudioLine(MimeTypes.AUDIO_DTS, 6, AudioRoutingMode.TRANSCODE_AC3))
        assertEquals("AAC 2.0 · decoded", osdAudioLine(MimeTypes.AUDIO_AAC, 2, AudioRoutingMode.PCM))
        assertEquals("AAC 2.0", osdAudioLine(MimeTypes.AUDIO_AAC, 2, null))
        assertNull(osdAudioLine(null, 2, null))
    }
}
