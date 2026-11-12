package com.nuvio.tv.core.stream

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperPlaybackThrottleTest {

    @After fun tearDown() = ScraperPlaybackThrottle.setPlaybackActive(false)

    @Test fun `while playing, a third scraper waits but the next-episode lookahead does not`() = runTest {
        ScraperPlaybackThrottle.setPlaybackActive(true)
        val hold = CompletableDeferred<Unit>()
        repeat(2) { launch { ScraperPlaybackThrottle.run { hold.await() } } }
        runCurrent()

        val queued = async { ScraperPlaybackThrottle.run { "queued" } }
        val lookahead = async {
            withContext(ScraperPlaybackThrottle.LookaheadExempt()) { ScraperPlaybackThrottle.run { "lookahead" } }
        }
        runCurrent()

        assertTrue(lookahead.isCompleted)
        assertFalse(queued.isCompleted)

        hold.complete(Unit)
        runCurrent()
        assertTrue(queued.isCompleted)
    }
}
