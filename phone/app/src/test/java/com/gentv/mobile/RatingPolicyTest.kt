package com.gentv.mobile

import com.gentv.mobile.data.RatingPolicy
import com.gentv.mobile.data.RatingState
import com.gentv.mobile.data.RatingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RatingPolicyTest {
    private val day = 24L * 60 * 60 * 1000
    private fun ask(s: RatingState, now: Long, good: Boolean = true, busy: Boolean = false) = RatingPolicy.shouldAsk(s, now, good, busy)

    @Test fun asksAfterAGoodRunTheFirstTime() = assertTrue(ask(RatingState(), 1_000))

    @Test fun neverWithoutAGoodMomentOrWhileBusy() {
        assertFalse(ask(RatingState(), 1_000, good = false))
        assertFalse(ask(RatingState(), 1_000, busy = true))
    }

    @Test fun secondAskWaitsThirtyDaysAndThereAreOnlyTwo() {
        val once = RatingState(asks = 1, lastAskAt = 0)
        assertFalse(ask(once, 29 * day))
        assertTrue(ask(once, 30 * day))
        assertFalse(ask(RatingState(asks = 2, lastAskAt = 0), 400 * day))
    }

    @Test fun stopsForGoodOnceRated() = assertFalse(ask(RatingState(done = true), 400 * day))

    @Test fun storeRoundTripsAndSurvivesAJunkFile() {
        val f = File.createTempFile("rating", ".json").apply { deleteOnExit() }
        var t = 5L
        val store = RatingStore(f) { t }
        assertEquals(RatingState(), store.read())
        store.recordAsk(); t = 9
        assertEquals(RatingState(asks = 1, lastAskAt = 5), store.read())
        store.recordDone()
        assertTrue(store.read().done)
        f.writeText("not json")
        assertEquals(RatingState(), store.read())
    }
}
