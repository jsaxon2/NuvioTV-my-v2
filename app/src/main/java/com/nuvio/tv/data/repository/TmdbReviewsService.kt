package com.nuvio.tv.data.repository

import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbReview
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.TraktCommentReview
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val REVIEWS_CACHE_TTL_MS = 10 * 60_000L
private val HTML_TAG_REGEX = Regex("<[^>]+>")
private val EXCESS_BLANK_LINES_REGEX = Regex("\\n{3,}")

/**
 * Loads title reviews from TMDB and maps them onto the same review model the
 * Trakt comments section renders. TMDB has no per-episode reviews, so this
 * only serves movie and show level reviews.
 */
@Singleton
class TmdbReviewsService @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val tmdbService: TmdbService
) {
    private data class TimedCache(
        val pages: Map<Int, List<TraktCommentReview>>,
        val pageCount: Int,
        val itemCount: Int,
        val updatedAtMs: Long
    )

    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, TimedCache>()

    suspend fun getReviewsPage(
        meta: Meta,
        fallbackItemId: String? = null,
        fallbackItemType: String? = null,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): TraktCommentsPage {
        val mediaType = resolveMediaType(meta, fallbackItemType) ?: return emptyPage(page)
        val tmdbId = resolveTmdbId(meta, mediaType, fallbackItemId) ?: return emptyPage(page)
        val cacheKey = "$mediaType|$tmdbId"

        cacheMutex.withLock {
            if (forceRefresh) {
                cache.remove(cacheKey)
            } else {
                val cached = cache[cacheKey]
                if (
                    cached != null &&
                    System.currentTimeMillis() - cached.updatedAtMs <= REVIEWS_CACHE_TTL_MS &&
                    cached.pages.containsKey(page)
                ) {
                    return TraktCommentsPage(
                        items = cached.pages.getValue(page),
                        currentPage = page,
                        pageCount = cached.pageCount,
                        itemCount = cached.itemCount
                    )
                }
            }
        }

        val response = when (mediaType) {
            "tv" -> tmdbApi.getTvReviews(tmdbId, tmdbService.apiKey(), page = page)
            else -> tmdbApi.getMovieReviews(tmdbId, tmdbService.apiKey(), page = page)
        }
        val body = when {
            response.code() == 404 -> null
            !response.isSuccessful -> throw IllegalStateException("Failed to load TMDB reviews (${response.code()})")
            else -> response.body()
        }

        val selected = body?.results.orEmpty()
            .filter { !it.content.isNullOrBlank() }
            .map { it.toReviewModel() }
        val pageCount = body?.totalPages ?: page
        val itemCount = body?.totalResults ?: selected.size

        cacheMutex.withLock {
            val cached = cache[cacheKey]
            cache[cacheKey] = TimedCache(
                pages = cached?.pages.orEmpty() + (page to selected),
                pageCount = pageCount,
                itemCount = itemCount,
                updatedAtMs = System.currentTimeMillis()
            )
        }

        return TraktCommentsPage(
            items = selected,
            currentPage = page,
            pageCount = pageCount,
            itemCount = itemCount
        )
    }

    private fun emptyPage(page: Int) = TraktCommentsPage(
        items = emptyList(),
        currentPage = page,
        pageCount = 0,
        itemCount = 0
    )

    private fun resolveMediaType(meta: Meta, fallbackItemType: String?): String? {
        return when (meta.type) {
            ContentType.MOVIE -> "movie"
            ContentType.SERIES, ContentType.TV -> "tv"
            else -> when (
                listOf(meta.apiType, meta.rawType, fallbackItemType)
                    .firstNotNullOfOrNull { it?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
            ) {
                "movie" -> "movie"
                "series", "show", "tv" -> "tv"
                else -> null
            }
        }
    }

    private suspend fun resolveTmdbId(meta: Meta, mediaType: String, fallbackItemId: String?): Int? {
        val candidates = listOfNotNull(
            meta.id,
            meta.imdbId?.takeIf { it.isNotBlank() },
            fallbackItemId?.takeIf { it.isNotBlank() }
        ).distinct()
        for (candidate in candidates) {
            tmdbService.ensureTmdbId(candidate, mediaType)?.toIntOrNull()?.let { return it }
        }
        return null
    }
}

private fun TmdbReview.toReviewModel(): TraktCommentReview {
    val displayName = authorDetails?.name?.takeIf { it.isNotBlank() }
        ?: author?.takeIf { it.isNotBlank() }
        ?: authorDetails?.username?.takeIf { it.isNotBlank() }
        ?: "TMDB user"

    return TraktCommentReview(
        id = tmdbReviewIdToLong(id),
        authorDisplayName = displayName,
        authorUsername = authorDetails?.username?.takeIf { it.isNotBlank() },
        comment = cleanTmdbReviewContent(content),
        review = true,
        rating = authorDetails?.rating?.roundToInt(),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

/** TMDB review ids are 24-char hex object ids; the UI keys reviews by Long. */
internal fun tmdbReviewIdToLong(id: String): Long {
    return id.takeLast(15).toLongOrNull(16) ?: id.hashCode().toLong()
}

internal fun cleanTmdbReviewContent(content: String?): String {
    if (content.isNullOrBlank()) return ""
    return content
        .replace("\r\n", "\n")
        .replace(HTML_TAG_REGEX, "")
        .replace(EXCESS_BLANK_LINES_REGEX, "\n\n")
        .trim()
}
