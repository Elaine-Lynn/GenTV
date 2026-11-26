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

    @Test fun macPayloadNormalizesFactoryAddressAndRejectsPlaceholder() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val bodies=mutableListOf<String>()
        var mac="00-1A-2B-3C-4D-5E"
        val c=Analytics(scope,"3.7.3",35,{bodies.add(it);true},macAddress={mac})
        try {
            c.setEnabled(true);c.record(Event.APP_OPEN);c.flush()
            assertEquals("00:1a:2b:3c:4d:5e",JSONObject(bodies.last()).getString("mac_address"))
            mac="02:00:00:00:00:00"
            c.record(Event.APP_OPEN);c.flush()
            assertEquals("",JSONObject(bodies.last()).getString("mac_address"))
        } finally { scope.cancel() }
    }

    @Test fun installationIsStableAndSequencePreservesPathOrder() = withClient { c, bodies, _, _ ->
        c.setEnabled(true)
        c.record(Event.SCREEN_VIEW, "welcome"); c.flush()
        c.record(Event.SCREEN_VIEW, "connect"); c.flush()
        val first=JSONObject(bodies[0]); val second=JSONObject(bodies[1])
        assertEquals(3, first.getInt("schema_version"))
        assertEquals("", first.getString("mac_address"))
        assertEquals(first.getString("install_id"),second.getString("install_id"))
        assertEquals("test",first.getString("traffic"))
        assertEquals(0,first.getJSONArray("events").getJSONObject(0).getInt("sequence"))
        assertEquals(1,second.getJSONArray("events").getJSONObject(0).getInt("sequence"))
    }

    @Test fun foregroundDurationsExcludeBackgroundAndIdResetsOnOptOut() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val bodies=mutableListOf<String>();var elapsed=1000L
        val c=Analytics(scope,"3.7.1",35,{ bodies.add(it);true },elapsed={elapsed})
        try {
            c.setEnabled(true);c.onForeground();c.flush()
            val id=JSONObject(bodies.first()).getString("install_id")
            elapsed+=12_000;c.onBackground();c.flush()
            val seconds=bodies.sumOf { raw -> val rows=JSONObject(raw).getJSONArray("events"); (0 until rows.length()).sumOf { rows.getJSONObject(it).getInt("foreground_seconds") } }
            assertEquals(12,seconds)
            c.setEnabled(false);c.setEnabled(true);c.record(Event.APP_OPEN);c.flush()
            assertNotEquals(id,JSONObject(bodies.last()).getString("install_id"))
        } finally { scope.cancel() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun foregroundSessionRotationDoesNotStrandScheduledEvents() = kotlinx.coroutines.test.runTest {
        val batches=mutableListOf<String>()
        val c=Analytics(this,"3.7.1",35,{batches.add(it);true},elapsed={testScheduler.currentTime})
        c.setEnabled(true);c.record(Event.SCREEN_VIEW,"welcome")
        c.onForeground();c.record(Event.SCREEN_VIEW,"connect")
        testScheduler.advanceTimeBy(3000);testScheduler.runCurrent()
        assertEquals(1,batches.size)
        c.onBackground();advanceUntilIdle()
        assertTrue(batches.any { JSONObject(it).getJSONArray("events").toString().contains("app_open") })
    }

    @Test fun shortBackgroundKeepsSessionAndLongBackgroundStartsAnother() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val bodies=mutableListOf<String>();var elapsed=1000L
        val c=Analytics(scope,"3.7.1",35,{bodies.add(it);true},elapsed={elapsed})
        try {
            c.setEnabled(true);c.onForeground();c.flush()
            val session=JSONObject(bodies.first()).getString("session_id")
            elapsed+=1000;c.onBackground();c.flush()
            elapsed+=1000;c.onForeground();c.record(Event.SCREEN_VIEW,"connect");c.flush()
            assertEquals(session,JSONObject(bodies.last()).getString("session_id"))
            c.onBackground();c.flush();elapsed+=1_800_001;c.onForeground();c.flush()
            assertNotEquals(session,JSONObject(bodies.last()).getString("session_id"))
            c.onBackground();assertTrue(c.flush())
        } finally {scope.cancel()}
    }

}
