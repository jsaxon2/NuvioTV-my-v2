package com.nuvio.tv.core.image

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Process
import coil3.BitmapImage
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Dimension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Fork: keeps a display-size copy of artwork that is downloaded far larger than it is shown:
 * card art from hosts without sized variants (e.g. GitHub-hosted collection art) and TMDB
 * `original` images such as the 4K hero backdrops.
 *
 * Every session, Coil re-decodes the full original from its disk cache; on a weak box that is
 * hundreds of milliseconds of CPU per image and it starves scrolling. After the first
 * successful load, the bitmap as displayed is saved at that size and later requests decode
 * that copy instead. Opaque art is saved as quality-95 JPEG, which decodes far faster than
 * lossless formats and looks the same on screen; art with transparency stays lossless.
 * Sized TMDB variants are skipped: they are already close to display size.
 */
class DownsizedArtworkInterceptor(context: Context) : Interceptor {
    private val dir = File(context.cacheDir, "downsized_artwork").apply { mkdirs() }
    private val writer = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor { runnable ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                runnable.run()
            }, "DownsizedArtwork")
        }.asCoroutineDispatcher()
    )

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data as? String ?: return chain.proceed()
        val width = (chain.size.width as? Dimension.Pixels)?.px
        val height = (chain.size.height as? Dimension.Pixels)?.px
        if (!eligible(url, width, height) || !request.diskCachePolicy.readEnabled) return chain.proceed()

        val copy = File(dir, fileName(url, width!!, height!!))
        val fresh = withContext(Dispatchers.IO) {
            copy.length() > 0 && System.currentTimeMillis() - copy.lastModified() < MAX_AGE_MS
        }
        if (fresh) {
            val result = chain.withRequest(request.newBuilder().data(copy).build()).proceed()
            if (result is SuccessResult) return result
            // A damaged copy falls back to the original and is rewritten below.
        }

        val result = chain.proceed()
        val bitmap = ((result as? SuccessResult)?.image as? BitmapImage)?.bitmap
        // A cropped (fill) decode overshoots the target on one side only, and decodes can land a few
        // pixels over it; a bitmap well above the target on both sides is not a display-size copy.
        if (bitmap != null && !bitmap.isRecycled && (bitmap.width <= width * 5 / 4 || bitmap.height <= height * 5 / 4)) {
            writer.launch { save(bitmap, copy) }
        }
        return result
    }

    private fun eligible(url: String, width: Int?, height: Int?): Boolean =
        (url.startsWith("https://") || url.startsWith("http://")) &&
            (!url.startsWith("https://image.tmdb.org/") || url.contains("/original/")) &&
            !url.endsWith(".gif", ignoreCase = true) &&
            width != null && height != null && width in 1..MAX_EDGE_PX && height in 1..MAX_EDGE_PX

    private fun save(bitmap: Bitmap, target: File) {
        runCatching {
            // Hardware bitmaps can't be compressed directly; copy back to a software bitmap.
            val software = if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return
            } else {
                bitmap
            }
            val (format, quality) = when {
                !software.hasAlpha() -> Bitmap.CompressFormat.JPEG to OPAQUE_JPEG_QUALITY
                Build.VERSION.SDK_INT >= 30 -> Bitmap.CompressFormat.WEBP_LOSSLESS to 100
                else -> Bitmap.CompressFormat.PNG to 100
            }
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.outputStream().use { out -> software.compress(format, quality, out) }
            if (software !== bitmap) software.recycle()
            if (!temp.renameTo(target)) temp.delete()
        }
    }

    private fun fileName(url: String, width: Int, height: Int): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("$url|$width|$height".toByteArray())
        return digest.joinToString("") { "%02x".format(it) } + ".img"
    }

    init {
        // Copies from the first version were all lossless WebP; they are rewritten as .img.
        writer.launch { dir.listFiles { file -> file.name.endsWith(".webp") }?.forEach { it.delete() } }
    }

    private companion object {
        const val MAX_EDGE_PX = 1920
        const val OPAQUE_JPEG_QUALITY = 95
        const val MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
