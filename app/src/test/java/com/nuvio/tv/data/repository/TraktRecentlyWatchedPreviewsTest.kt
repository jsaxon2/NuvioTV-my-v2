package com.nuvio.tv.data.repository

import com.nuvio.tv.data.remote.dto.trakt.TraktEpisodeDto
import com.nuvio.tv.data.remote.dto.trakt.TraktHistoryItemDto
import com.nuvio.tv.data.remote.dto.trakt.TraktIdsDto
import com.nuvio.tv.data.remote.dto.trakt.TraktMovieDto
import com.nuvio.tv.data.remote.dto.trakt.TraktShowDto
import com.nuvio.tv.domain.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TraktRecentlyWatchedPreviewsTest {

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun episode(showImdb: String, title: String, at: String) = TraktHistoryItemDto(
        watchedAt = at,
        show = TraktShowDto(title = title, year = 2020, ids = TraktIdsDto(imdb = showImdb)),
        episode = TraktEpisodeDto()
    )

    private fun movie(imdb: String?, tmdb: Int?, title: String, at: String) = TraktHistoryItemDto(
        watchedAt = at,
        movie = TraktMovieDto(title = title, year = 2021, ids = TraktIdsDto(imdb = imdb, tmdb = tmdb))
    )

    @Test
    fun `newest first, one entry per show, movies and shows typed`() {
        val history = listOf(
            episode("tt100", "Show A", "2026-10-20T10:00:00.000Z"),
            movie("tt200", null, "Movie B", "2026-10-20T12:00:00.000Z"),
            episode("tt100", "Show A", "2026-10-20T14:00:00.000Z")
        )

        val previews = traktRecentlyWatchedPreviews(history, sinceEpochMs = 0L)

        assertEquals(listOf("tt100", "tt200"), previews.map { it.id })
        assertEquals(listOf(ContentType.SERIES, ContentType.MOVIE), previews.map { it.type })
        assertEquals("Show A", previews[0].name)
    }

    @Test
    fun `entries before the window and without ids are dropped`() {
        val history = listOf(
            movie("tt300", null, "Old", "2026-09-01T00:00:00.000Z"),
            movie(null, null, "No ids", "2026-10-20T00:00:00.000Z"),
            movie(null, 42, "TMDB only", "2026-10-20T00:00:00.000Z")
        )

        val previews = traktRecentlyWatchedPreviews(history, sinceEpochMs = ms("2026-10-01T00:00:00Z"))

        assertEquals(listOf("tmdb:42"), previews.map { it.id })
    }
}
