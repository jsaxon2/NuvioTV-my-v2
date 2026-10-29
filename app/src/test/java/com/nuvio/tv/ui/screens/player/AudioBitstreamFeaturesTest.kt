package com.nuvio.tv.ui.screens.player

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class AudioBitstreamFeaturesTest {

    /** A TrueHD access unit with a major sync, [substreamInfo] at byte 17 of the sync. */
    private fun trueHdUnit(substreamInfo: Int): ByteBuffer {
        val bytes = byteArrayOf(
            0x51, 0x2C, 0x00, 0x00,                       // access unit header
            0xF8.toByte(), 0x72, 0x6F, 0xBA.toByte(),    // format_sync
            0x00, 0x00, 0x00, 0x00,                       // format_info
            0xB7.toByte(), 0x52,                          // signature
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,           // flags, reserved, peak data rate
            0x40,                                         // substreams
            substreamInfo.toByte(),
            0x00, 0x00
        )
        return ByteBuffer.wrap(bytes)
    }

    @Test fun `truehd major sync reports the 16-channel presentation bit`() {
        val atmos = trueHdUnit(0x84)
        assertEquals(true, trueHdMajorSyncAtmos(atmos, 0, atmos.limit()))
        val plain = trueHdUnit(0x04)
        assertEquals(false, trueHdMajorSyncAtmos(plain, 0, plain.limit()))
        val noSync = ByteBuffer.wrap(ByteArray(40))
        assertNull(trueHdMajorSyncAtmos(noSync, 0, noSync.limit()))
    }

    @Test fun `dts extensions are found by their sync words`() {
        val x = ByteBuffer.wrap(byteArrayOf(
            0x7F, 0xFE.toByte(), 0x80.toByte(), 0x01, 0x00,           // core
            0x64, 0x58, 0x20, 0x25, 0x00,                            // DTS-HD substream
            0x41, 0xA2.toByte(), 0x95.toByte(), 0x47, 0x00,          // XLL
            0x02, 0x00, 0x08, 0x50                                   // DTS:X
        ))
        assertEquals(setOf(DTS_SYNC_SUBSTREAM, DTS_SYNC_XLL, DTS_SYNC_X), dtsExtensions(x, 0, x.limit()))
        val core = ByteBuffer.wrap(byteArrayOf(0x7F, 0xFE.toByte(), 0x80.toByte(), 0x01, 0x00, 0x00))
        assertTrue(dtsExtensions(core, 0, core.limit()).isEmpty())
    }

    @Test fun `a plain matroska dts track reports what its bitstream carries`() {
        AudioBitstreamFeatures.onFormat(MimeTypes.AUDIO_DTS)
        val ma = ByteBuffer.wrap(byteArrayOf(0x64, 0x58, 0x20, 0x25, 0x41, 0xA2.toByte(), 0x95.toByte(), 0x47))
        AudioBitstreamFeatures.inspect(ma, 0, ma.limit())
        assertTrue(AudioBitstreamFeatures.dtsHd && AudioBitstreamFeatures.dtsHdMa)
        assertFalse(AudioBitstreamFeatures.dtsX)
        assertEquals("DTS-HD", osdAudio(MimeTypes.AUDIO_DTS, 8, null, dtsHd = true, dtsHdMa = true)!!.codec)
        assertEquals("DTS:X", osdAudio(MimeTypes.AUDIO_DTS, 8, null, dtsHd = true, dtsHdMa = true, dtsX = true)!!.codec)
        assertEquals("DTS", osdAudio(MimeTypes.AUDIO_DTS, 6, null)!!.codec)
    }

    @Test fun `features follow the current format and keep the buffer untouched`() {
        AudioBitstreamFeatures.onFormat(MimeTypes.AUDIO_TRUEHD)
        val unit = trueHdUnit(0x84)
        AudioBitstreamFeatures.inspect(unit, 0, unit.limit())
        assertTrue(AudioBitstreamFeatures.trueHdAtmos)
        assertEquals(0, unit.position())
        AudioBitstreamFeatures.onFormat(MimeTypes.AUDIO_E_AC3)
        assertFalse(AudioBitstreamFeatures.trueHdAtmos)
    }
}
