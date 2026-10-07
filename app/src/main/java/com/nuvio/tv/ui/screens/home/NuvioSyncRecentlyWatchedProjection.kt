package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.model.WatchedItem

private const val DAY_MS = 24L * 60L * 60L * 1000L

/**
 * Shows/movies from Nuvio Sync history watched within the last [windowDays] days, sorted
 * most-recently-watched first. Combines in-progress entries ([progress]) with completed
 * episodes/movies ([watched]); a show appears once, at the time of its latest activity.
 */
internal fun nuvioSyncRecentlyWatchedPreviews(
    progress: List<WatchProgress>,
    watched: List<WatchedItem>,
    windowDays: Int,
    nowEpochMs: Long
): List<MetaPreview> {
    val sinceEpochMs = nowEpochMs - windowDays.toLong() * DAY_MS
    val activity = progress.map { item ->
        RecentActivity(item.contentId, item.contentType, item.name, item.poster, null, item.lastWatched)
    } + watched.map { item ->
        RecentActivity(item.contentId, item.contentType, item.title, item.poster, item.releaseInfo, item.watchedAt)
    }
    return activity
        .filter { it.contentId.isNotBlank() && it.atEpochMs >= sinceEpochMs }
        .groupBy { "${it.type}:${it.contentId}" }
        .values
        .map { group ->
            val newestFirst = group.sortedByDescending { it.atEpochMs }
            val latest = newestFirst.first()
            latest.atEpochMs to MetaPreview(
                id = latest.contentId,
                type = latest.type,
                name = newestFirst.firstNotNullOfOrNull { it.name?.takeIf(String::isNotBlank) } ?: latest.contentId,
                poster = newestFirst.firstNotNullOfOrNull { it.poster?.takeIf(String::isNotBlank) },
                posterShape = PosterShape.POSTER,
                background = null,
                logo = null,
                description = null,
                releaseInfo = newestFirst.firstNotNullOfOrNull { it.releaseInfo?.takeIf(String::isNotBlank) },
                imdbRating = null,
                genres = emptyList(),
                imdbId = latest.contentId.takeIf { it.startsWith("tt") }
            )
        }
        .sortedByDescending { (atEpochMs, _) -> atEpochMs }
        .map { (_, preview) -> preview }
}

private class RecentActivity(
    val contentId: String,
    rawType: String,
    val name: String?,
    val poster: String?,
    val releaseInfo: String?,
    val atEpochMs: Long
) {
    // History only holds movies and episodes; anything that isn't a movie belongs to a show.
    val type: ContentType =
        if (ContentType.fromString(rawType) == ContentType.MOVIE) ContentType.MOVIE else ContentType.SERIES
}
