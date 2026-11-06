package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CwEpisodesLeftTest {

    private val show = summary(
        episode(1, 1, "2020-01-01"),
        episode(1, 2, "2020-01-08"),
        episode(2, 1, "2021-01-01"),
        episode(2, 2, "2021-01-08"),
        episode(2, 3, "2021-01-15"),
        episode(2, 4, "2099-01-01")
    )

    @Test
    fun `counts the current episode and every aired one after it`() {
        assertEquals(4, show.episodesLeftFrom(1, 2))
        assertEquals(1, show.episodesLeftFrom(2, 3))
    }

    @Test
    fun `undated placeholder episodes after the last dated one are not counted`() {
        // Chicago Med S12: E5 aired, E6 and E7 dated later, E8 and E9 listed as TBA without a date.
        val placeholders = summary(
            episode(12, 4, "2026-10-29"),
            episode(12, 5, "2026-11-05"),
            episode(12, 6, "2099-11-12"),
            episode(12, 7, "2099-11-19"),
            episode(12, 8, null),
            episode(12, 9, null)
        )
        assertEquals(1, placeholders.episodesLeftFrom(12, 5))
    }

    @Test
    fun `a list without any dates keeps every episode`() {
        val undated = summary(episode(1, 1, null), episode(1, 2, null), episode(1, 3, null))
        assertEquals(2, undated.episodesLeftFrom(1, 2))
    }

    @Test
    fun `episodes not aired yet are not counted`() {
        assertNull(show.episodesLeftFrom(2, 4))
    }

    @Test
    fun `specials and duplicate entries do not change the count`() {
        val withExtras = summary(
            episode(0, 1, "2019-01-01"),
            episode(1, 1, "2020-01-01"),
            episode(1, 1, "2020-01-01"),
            episode(1, 2, "2020-01-08")
        )
        assertEquals(2, withExtras.episodesLeftFrom(1, 1))
    }

    @Test
    fun `next up cards and in progress cards get the count, films and shuffle cards do not`() {
        val items = listOf(
            ContinueWatchingItem.InProgress(progress("series", 2, 1)),
            ContinueWatchingItem.NextUp(nextUp(2, 2)),
            ContinueWatchingItem.InProgress(progress("movie", null, null)),
            ContinueWatchingItem.NextUp(nextUp(2, 2), shufflePlayback = true)
        )

        val result = items.withEpisodesLeft { _, _ -> show.episodeKeys() }

        assertEquals(3, (result[0] as ContinueWatchingItem.InProgress).episodesLeft)
        assertEquals(2, (result[1] as ContinueWatchingItem.NextUp).episodesLeft)
        assertNull((result[2] as ContinueWatchingItem.InProgress).episodesLeft)
        assertNull((result[3] as ContinueWatchingItem.NextUp).episodesLeft)
    }

    @Test
    fun `a show whose episode list is not loaded yet shows no count`() {
        val items = listOf(ContinueWatchingItem.NextUp(nextUp(2, 2)))
        val result = items.withEpisodesLeft { _, _ -> null }
        assertNull((result[0] as ContinueWatchingItem.NextUp).episodesLeft)
    }

    @Test
    fun `a show pushed out of the meta cache keeps its count`() {
        val items = listOf(
            ContinueWatchingItem.InProgress(progress("series", 2, 1)),
            ContinueWatchingItem.InProgress(progress("movie", null, null))
        )
        val loaded = keptEpisodeLists(emptyMap(), items) { _, _ -> show.episodeKeys() }
        val afterEviction = keptEpisodeLists(loaded, items) { _, _ -> null }

        assertSame(loaded, afterEviction)
        assertEquals(setOf("tt-show"), afterEviction.keys)
        assertEquals(3, (items.withEpisodesLeft(afterEviction)[0] as ContinueWatchingItem.InProgress).episodesLeft)
    }

    @Test
    fun `only shows on the row without a list are looked up, and lists for shows that left are dropped`() {
        val other = ContinueWatchingItem.NextUp(nextUp(1, 1).copy(contentId = "tt-other"))
        val items = listOf(
            ContinueWatchingItem.NextUp(nextUp(2, 2)),
            ContinueWatchingItem.InProgress(progress("series", 2, 1)),
            other,
            ContinueWatchingItem.InProgress(progress("movie", null, null)).let {
                it.copy(progress = it.progress.copy(contentId = "tt-film"))
            }
        )
        val lists = mapOf("tt-show" to show.episodeKeys(), "tt-gone" to show.episodeKeys())

        assertEquals(listOf<ContinueWatchingItem>(other), items.missingEpisodeLists(lists))
        assertEquals(setOf("tt-show"), keptEpisodeLists(lists, items) { _, _ -> null }.keys)
    }

    private fun summary(vararg videos: CwVideoSummary) = CwMetaSummary(
        id = "tt-show",
        name = "Show",
        poster = null,
        backdropUrl = null,
        logo = null,
        description = null,
        genres = emptyList(),
        releaseInfo = null,
        imdbRating = null,
        language = null,
        country = null,
        videos = videos.toList()
    )

    private fun episode(season: Int, ep: Int, released: String?) = CwVideoSummary(
        id = "tt-show:$season:$ep",
        title = "E$ep",
        released = released,
        thumbnail = null,
        season = season,
        episode = ep,
        overview = null
    )

    private fun progress(type: String, season: Int?, episode: Int?) = WatchProgress(
        contentId = "tt-show",
        contentType = type,
        name = "Show",
        poster = null,
        backdrop = null,
        logo = null,
        videoId = "tt-show:$season:$episode",
        season = season,
        episode = episode,
        episodeTitle = null,
        position = 1_000L,
        duration = 10_000L,
        lastWatched = 0L
    )

    private fun nextUp(season: Int, episode: Int) = NextUpInfo(
        contentId = "tt-show",
        contentType = "series",
        name = "Show",
        poster = null,
        backdrop = null,
        logo = null,
        videoId = "tt-show:$season:$episode",
        season = season,
        episode = episode,
        episodeTitle = null,
        thumbnail = null,
        lastWatched = 0L,
        sortTimestamp = 0L
    )
}
