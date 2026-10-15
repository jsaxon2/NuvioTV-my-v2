package com.nuvio.tv.core.stream

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fork: limits how many local scrapers run at once while a video is playing.
 *
 * A source search keeps going after a stream is picked, and slow scrapers can grind for minutes
 * while the user watches; on a weak box that starves the player UI. While [playbackActive] is
 * set, a scraper only starts when fewer than [PLAYBACK_LIMIT] are running; the rest wait their
 * turn. Clearing it (sources panel opened, player closed) lets every waiting scraper go at once.
 */
object ScraperPlaybackThrottle {
    private const val PLAYBACK_LIMIT = 2

    private val playing = MutableStateFlow(false)
    private val running = MutableStateFlow(0)
    private val admission = Mutex()

    fun setPlaybackActive(active: Boolean) {
        playing.value = active
    }

    suspend fun <T> run(block: suspend () -> T): T {
        while (true) {
            val admitted = admission.withLock {
                if (!playing.value || running.value < PLAYBACK_LIMIT) {
                    running.update { it + 1 }
                    true
                } else {
                    false
                }
            }
            if (admitted) break
            combine(playing, running) { active, count -> !active || count < PLAYBACK_LIMIT }.first { it }
        }
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }
}
