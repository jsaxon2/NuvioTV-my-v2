package com.nuvio.tv.data.repository

import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.remote.dto.trakt.TraktHistoryItemDto
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val DAY_MS = 24L * 60L * 60L * 1000L
private const val PAGE_LIMIT = 100
private const val MAX_PAGES = 5
private const val MAX_TITLES = 60

/**
 * Fork: the Recently Watched row's Trakt source. Reads the user's Trakt watch history (movies
 * and episodes) for the chosen window, newest first; a show appears once, at its latest episode.
 * Posters are left to the caller, which fills them in the same way as for Nuvio Sync.
 */
@Singleton
class TraktRecentlyWatchedSource @Inject constructor(
    private val traktAuthService: TraktAuthService,
    private val traktApi: TraktApi
) {
    /** Null when Trakt could not be reached (not signed in, offline, rate limited). */
    suspend fun fetch(windowDays: Int, nowEpochMs: Long = System.currentTimeMillis()): List<MetaPreview>? {
        val sinceEpochMs = nowEpochMs - windowDays.toLong() * DAY_MS
        val items = mutableListOf<TraktHistoryItemDto>()
        for (page in 1..MAX_PAGES) {
            val response = traktAuthService.executeAuthorizedRequest { authHeader ->
                traktApi.getRecentHistory(
                    authorization = authHeader,
                    page = page,
                    limit = PAGE_LIMIT,
                    startAt = Instant.ofEpochMilli(sinceEpochMs).toString()
                )
            } ?: return null
            if (!response.isSuccessful) return null
            val pageItems = response.body().orEmpty()
            items += pageItems
            if (pageItems.size < PAGE_LIMIT) break
            if (traktRecentlyWatchedPreviews(items, sinceEpochMs).size >= MAX_TITLES) break
        }
        return traktRecentlyWatchedPreviews(items, sinceEpochMs).take(MAX_TITLES)
    }
}

/** Movies and shows from [history] watched since [sinceEpochMs], newest first, each title once. */
internal fun traktRecentlyWatchedPreviews(history: List<TraktHistoryItemDto>, sinceEpochMs: Long): List<MetaPreview> =
    history.asSequence()
        .mapNotNull { item ->
            val watchedAtMs = item.watchedAt
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: return@mapNotNull null
            if (watchedAtMs < sinceEpochMs) return@mapNotNull null
            item.toRecentlyWatchedPreview()?.let { preview -> watchedAtMs to preview }
        }
        .sortedByDescending { (watchedAtMs, _) -> watchedAtMs }
        .distinctBy { (_, preview) -> "${preview.type}:${preview.id}" }
        .map { (_, preview) -> preview }
        .toList()

private fun TraktHistoryItemDto.toRecentlyWatchedPreview(): MetaPreview? {
    val movie = movie
    val show = show
    val (type, ids, title, year) = when {
        movie != null -> Quad(ContentType.MOVIE, movie.ids, movie.title, movie.year)
        show != null -> Quad(ContentType.SERIES, show.ids, show.title, show.year)
        else -> return null
    }
    val contentId = normalizeContentId(ids)
    if (contentId.isBlank()) return null
    return MetaPreview(
        id = contentId,
        type = type,
        name = title?.takeIf(String::isNotBlank) ?: contentId,
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = year?.toString(),
        imdbRating = null,
        genres = emptyList(),
        imdbId = ids?.imdb?.takeIf(String::isNotBlank)
    )
}

private data class Quad(
    val type: ContentType,
    val ids: com.nuvio.tv.data.remote.dto.trakt.TraktIdsDto?,
    val title: String?,
    val year: Int?
)
