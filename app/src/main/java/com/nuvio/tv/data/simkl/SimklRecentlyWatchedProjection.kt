package com.nuvio.tv.data.simkl

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape

/**
 * Shows/movies from the user's Simkl library watched within the last [windowDays] days,
 * sorted most-recently-watched first. Distinct by content identity so a rewatched
 * title only appears once.
 */
fun SimklSyncSnapshot.recentlyWatchedPreviews(windowDays: Int, nowEpochMs: Long): List<MetaPreview> {
    val sinceEpochMs = nowEpochMs - windowDays.toLong() * 24L * 60L * 60L * 1000L
    return entries.asSequence()
        .mapNotNull { entry ->
            val watchedAtMs = parseSimklUtcEpochMs(entry.lastWatchedAt) ?: return@mapNotNull null
            if (watchedAtMs < sinceEpochMs) return@mapNotNull null
            entry.toRecentlyWatchedPreview()?.let { preview -> watchedAtMs to preview }
        }
        .distinctBy { (_, preview) -> "${preview.type}:${preview.id}" }
        .sortedByDescending { (watchedAtMs, _) -> watchedAtMs }
        .map { (_, preview) -> preview }
        .toList()
}

private fun SimklLibraryEntry.toRecentlyWatchedPreview(): MetaPreview? {
    val media = media ?: return null
    val contentId = media.canonicalContentId() ?: return null
    val entryType = when (mediaType) {
        SimklMediaType.MOVIES -> ContentType.MOVIE
        SimklMediaType.ANIME -> if (animeType == "movie") ContentType.MOVIE else ContentType.SERIES
        SimklMediaType.SHOWS -> ContentType.SERIES
    }
    return MetaPreview(
        id = contentId,
        type = entryType,
        name = media.title?.takeIf(String::isNotBlank) ?: contentId,
        poster = resolvedPosterUrl(),
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = media.year?.toString(),
        imdbRating = null,
        genres = emptyList(),
        imdbId = media.ids.idValue("imdb")
    )
}