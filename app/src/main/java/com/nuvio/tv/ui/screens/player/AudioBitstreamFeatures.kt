package com.nuvio.tv.ui.screens.player

import android.util.Log
import androidx.media3.common.MimeTypes
import java.nio.ByteBuffer

/**
 * Fork: what the audio bitstream itself declares, for the OSD: TrueHD Atmos, and which DTS
 * family member is playing. The MIME type doesn't say (Matroska labels every DTS track plain
 * "DTS"), and release names can list several tracks, so the audio renderer feeds every buffer
 * it reads (on its way to the decoder, or to the receiver in passthrough) through [inspect]
 * until the answer is known.
 */
internal object AudioBitstreamFeatures {
    /** The TrueHD major sync declares a 16-channel (Atmos) presentation. */
    @Volatile
    var trueHdAtmos: Boolean = false
        private set

    /** The DTS stream carries a DTS-HD extension substream. */
    @Volatile
    var dtsHd: Boolean = false
        private set

    /** The DTS-HD substream carries the XLL (lossless, Master Audio) extension. */
    @Volatile
    var dtsHdMa: Boolean = false
        private set

    /** The DTS-HD lossless stream carries the DTS:X object extension. */
    @Volatile
    var dtsX: Boolean = false
        private set

    @Volatile
    private var mimeType: String? = null
    private var trueHdDecided = false
    private var dtsBuffersLeft = 0

    /** A new audio format starts unknown. */
    fun onFormat(sampleMimeType: String?) {
        mimeType = sampleMimeType
        trueHdAtmos = false
        trueHdDecided = false
        dtsHd = false
        dtsHdMa = false
        dtsX = false
        dtsBuffersLeft = DTS_SCAN_BUFFERS
    }

    fun inspect(data: ByteBuffer, from: Int, to: Int) {
        when (mimeType) {
            MimeTypes.AUDIO_TRUEHD -> if (!trueHdDecided) {
                trueHdMajorSyncAtmos(data, from, to)?.let {
                    trueHdAtmos = it
                    trueHdDecided = true
                    Log.i(TAG, "TrueHD atmos=$it")
                }
            }
            MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_X -> if (dtsBuffersLeft > 0) {
                // Every DTS-HD frame repeats its extension syncs, so a few buffers settle it; stopping
                // there also keeps a chance match in compressed audio from flipping the answer later.
                dtsBuffersLeft--
                val found = dtsExtensions(data, from, to)
                if (DTS_SYNC_SUBSTREAM in found) dtsHd = true
                if (DTS_SYNC_XLL in found) dtsHdMa = true
                if (DTS_SYNC_X in found) dtsX = true
                if (dtsBuffersLeft == 0) Log.i(TAG, "DTS hd=$dtsHd ma=$dtsHdMa x=$dtsX")
            }
        }
    }

    private const val TAG = "AudioBitstream"
    private const val DTS_SCAN_BUFFERS = 24
}

internal const val DTS_SYNC_SUBSTREAM = 0x64582025
internal const val DTS_SYNC_XLL = 0x41A29547
internal const val DTS_SYNC_X = 0x02000850
internal const val DTS_SYNC_X_IMAX = 0xF14000D0.toInt()

/**
 * Atmos flag of the first TrueHD major sync in [from, to), or null when there is none. The major
 * sync is format_sync F8726FBA, format_info (4 bytes), signature B752, flags, reserved, peak data
 * rate, a byte holding the substream count, then substream_info, whose top bit marks the
 * 16-channel presentation that carries Atmos.
 */
internal fun trueHdMajorSyncAtmos(data: ByteBuffer, from: Int, to: Int): Boolean? {
    var i = from
    while (i + 18 <= to) {
        if (data.get(i) == 0xF8.toByte() && data.get(i + 1) == 0x72.toByte() &&
            data.get(i + 2) == 0x6F.toByte() && data.get(i + 3) == 0xBA.toByte() &&
            data.get(i + 8) == 0xB7.toByte() && data.get(i + 9) == 0x52.toByte()
        ) {
            return (data.get(i + 17).toInt() and 0x80) != 0
        }
        i++
    }
    return null
}

/**
 * The DTS extension sync words in [from, to): the DTS-HD substream header, the XLL (lossless)
 * extension, and the DTS:X object extension (IMAX Enhanced reports as DTS:X too).
 */
internal fun dtsExtensions(data: ByteBuffer, from: Int, to: Int): Set<Int> {
    val found = HashSet<Int>(4)
    var word = 0
    for (i in from until to) {
        word = (word shl 8) or (data.get(i).toInt() and 0xFF)
        if (i - from < 3) continue
        when (word) {
            DTS_SYNC_SUBSTREAM, DTS_SYNC_XLL, DTS_SYNC_X -> found += word
            DTS_SYNC_X_IMAX -> found += DTS_SYNC_X
        }
    }
    return found
}
