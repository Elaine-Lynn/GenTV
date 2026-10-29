package com.gentv.mobile

import com.gentv.mobile.data.Analytics
import com.gentv.mobile.data.Analytics.Event
import kotlinx.coroutines.*
import kotlinx.coroutines.test.advanceUntilIdle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalyticsTest {
    private fun withClient(block: suspend (Analytics, MutableList<String>, (Boolean) -> Unit, (Long) -> Unit) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val bodies = mutableListOf<String>()
        var accept = true
        var time = 1_000L
        val client = Analytics(scope, "0.9.0", 35, { bodies.add(it); accept }, { time })
        try { block(client, bodies, { accept = it }, { time = it }) } finally { scope.cancel() }
    }

    @Test fun disabledByDefaultAndOptOutClearsPending() = withClient { c, bodies, _, _ ->
        c.record(Event.APP_OPEN); c.flush(); assertTrue(bodies.isEmpty())
        c.setEnabled(true); c.record(Event.APP_OPEN); c.setEnabled(false); c.flush()
        c.setEnabled(true); c.flush(); assertTrue(bodies.isEmpty())
    }

    @Test fun arbitraryValuesCannotLeavePhone() = withClient { c, bodies, _, _ ->
        c.setEnabled(true)
        c.record(Event.SCREEN_VIEW, "192.168.1.50")
        c.record(Event.CONNECTION_RESULT, "Living room TV")
        c.record(Event.APP_OPEN, "private@example.com")
        c.flush(); assertTrue(bodies.isEmpty())
    }

    @Test fun failedBatchesRetainIdsAndSuccessfulOnesAreRemoved() = withClient { c, bodies, accept, _ ->
        c.setEnabled(true); c.record(Event.CONNECTION_RESULT, "connected")
        accept(false); c.flush(); accept(true); c.flush(); c.flush()
        assertEquals(2, bodies.size)
        assertEquals(bodies[0], bodies[1])
        val json = JSONObject(bodies[0])
        assertEquals("connected", json.getJSONArray("events").getJSONObject(0).getString("value"))
        assertFalse(json.has("device_id")); assertFalse(json.has("user_id"))
    }

    @Test fun queueAndBatchesAreBounded() = withClient { c, bodies, _, _ ->
        c.setEnabled(true); repeat(150) { c.record(Event.APP_OPEN) }
        c.flush(); c.flush(); c.flush()
        assertEquals(2, bodies.size)
        bodies.forEach { assertEquals(50, JSONObject(it).getJSONArray("events").length()) }
    }

    @Test fun staleEventsExpireAndConsentStartsNewSession() = withClient { c, bodies, _, time ->
        c.setEnabled(true); c.record(Event.APP_OPEN); c.flush()
        c.setEnabled(false); c.setEnabled(true); c.record(Event.APP_OPEN); c.flush()
        assertNotEquals(JSONObject(bodies[0]).getString("session_id"), JSONObject(bodies[1]).getString("session_id"))
        c.record(Event.APP_OPEN); time(86_402_000); c.flush(); assertEquals(2, bodies.size)
    }

    @Test fun endpointStaysUnderConfiguredApi() {
        if (BuildConfig.API_BASE_URL.isEmpty()) {
            assertEquals("", BuildConfig.ANALYTICS_API)
            assertFalse(Analytics.configured)
        } else assertEquals(BuildConfig.API_BASE_URL + "events", BuildConfig.ANALYTICS_API)
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun scheduledDrainSendsAllBatchesWithoutAnotherEvent() = kotlinx.coroutines.test.runTest {
        val batches = mutableListOf<String>()
        val c = Analytics(this, "0.9.0", 35, { batches.add(it); true })
        c.setEnabled(true)
        repeat(75) { c.record(Event.APP_OPEN) }
        advanceUntilIdle()
        assertEquals(2, batches.size)
        assertEquals(75, batches.sumOf { JSONObject(it).getJSONArray("events").length() })
        c.flush()
        assertEquals(2, batches.size)
    }

}
