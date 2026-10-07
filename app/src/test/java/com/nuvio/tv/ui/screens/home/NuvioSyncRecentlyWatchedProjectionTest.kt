package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.model.WatchedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvioSyncRecentlyWatchedProjectionTest {

    private val now = 100L * DAY

    @Test
    fun `combines progress and watched history, newest first, one entry per title`() {
        val previews = nuvioSyncRecentlyWatchedPreviews(
            progress = listOf(
                progress("tt1", "series", "Show One", lastWatched = now - 2 * DAY, season = 1, episode = 3),
                progress("tt2", "movie", "Movie Two", lastWatched = now - 1 * DAY)
            ),
            watched = listOf(
                watched("tt1", "series", "Show One", watchedAt = now - 5 * DAY, season = 1, episode = 2),
                watched("tt3", "movie", "Movie Three", watchedAt = now - 3 * DAY)
            ),
            windowDays = 30,
            nowEpochMs = now
        )

        assertEquals(listOf("tt2", "tt1", "tt3"), previews.map { it.id })
        assertEquals(ContentType.SERIES, previews[1].type)
        assertEquals(ContentType.MOVIE, previews[0].type)
    }

    @Test
    fun `drops activity older than the window`() {
        val previews = nuvioSyncRecentlyWatchedPreviews(
            progress = listOf(progress("tt1", "series", "Old Show", lastWatched = now - 40 * DAY)),
            watched = listOf(watched("tt2", "movie", "Recent Movie", watchedAt = now - 10 * DAY)),
            windowDays = 30,
            nowEpochMs = now
        )

        assertEquals(listOf("tt2"), previews.map { it.id })
    }

    @Test
    fun `fills missing name and poster from older entries of the same title`() {
        val previews = nuvioSyncRecentlyWatchedPreviews(
            progress = listOf(progress("tt1", "series", "", lastWatched = now - 1 * DAY, poster = null)),
            watched = listOf(watched("tt1", "series", "Show One", watchedAt = now - 4 * DAY, poster = "https://img/p.jpg")),
            windowDays = 30,
            nowEpochMs = now
        )

        assertEquals(1, previews.size)
        assertEquals("Show One", previews[0].name)
        assertEquals("https://img/p.jpg", previews[0].poster)
    }

    @Test
    fun `cloud library files are left out`() {
        val previews = nuvioSyncRecentlyWatchedPreviews(
            progress = listOf(
                progress("cloud-file-1", "cloud", "home_video.mkv", lastWatched = now - 1 * DAY, season = 1, episode = 1),
                progress("tt1", "series", "Show One", lastWatched = now - 2 * DAY, season = 1, episode = 1)
            ),
            watched = emptyList(),
            windowDays = 30,
            nowEpochMs = now
        )

        assertEquals(listOf("tt1"), previews.map { it.id })
    }

    @Test
    fun `empty history gives an empty row`() {
        assertTrue(nuvioSyncRecentlyWatchedPreviews(emptyList(), emptyList(), 30, now).isEmpty())
    }

    private fun progress(
        id: String,
        type: String,
        name: String,
        lastWatched: Long,
        season: Int? = null,
        episode: Int? = null,
        poster: String? = "https://img/$id.jpg"
    ) = WatchProgress(
        contentId = id,
        contentType = type,
        name = name,
        poster = poster,
        backdrop = null,
        logo = null,
        videoId = if (season != null) "$id:$season:$episode" else id,
        season = season,
        episode = episode,
        episodeTitle = null,
        position = 600_000L,
        duration = 3_600_000L,
        lastWatched = lastWatched
    )

    private fun watched(
        id: String,
        type: String,
        title: String,
        watchedAt: Long,
        season: Int? = null,
        episode: Int? = null,
        poster: String? = null
    ) = WatchedItem(
        contentId = id,
        contentType = type,
        title = title,
        season = season,
        episode = episode,
        watchedAt = watchedAt,
        poster = poster
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
