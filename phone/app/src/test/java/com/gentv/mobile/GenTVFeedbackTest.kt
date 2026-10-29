package com.gentv.mobile

import com.gentv.mobile.data.FeedbackDraft
import com.gentv.mobile.data.FeedbackKind
import com.gentv.mobile.data.ShareStats
import com.gentv.mobile.data.GenTVFeedback
import com.gentv.mobile.data.caption
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GenTVFeedbackTest {
    private class FakeHttp(val status: Int, val location: String? = null, val reply: String = "{\"success\":true}") : GenTVFeedback.Http {
        val posts = mutableListOf<Pair<String, String>>()
        override fun post(url: String, json: String): Pair<Int, String?> { posts += url to json; return status to reply }
    }

    private fun feedback(http: GenTVFeedback.Http) = GenTVFeedback("0.2.0+2", "Google Pixel 9", "Android 16", "install-1", http)

    @Test fun payloadHasEveryRequiredField() {
        val p = feedback(FakeHttp(302)).payload(FeedbackDraft(FeedbackKind.BUG, "  It stopped at step 2  ", page = "tidy"))
        assertEquals("GenTV", p.getString("app"))
        assertEquals("bug", p.getString("type"))
        assertEquals("It stopped at step 2", p.getString("feedback"))
        assertEquals("android", p.getString("platform"))
        assertEquals("0.2.0+2", p.getString("version"))
        assertEquals("Google Pixel 9", p.getString("device"))
        assertEquals("Android 16", p.getString("os"))
        assertEquals("in-app", p.getString("source"))
        assertEquals("tidy", p.getString("page"))
        assertEquals("install-1", p.getString("user_id"))
        assertEquals("", p.getString("email"))
        assertFalse(p.has("screenshot_base64"))
    }

    @Test fun logGoesIntoTheMessageAndScreenshotIsBase64() {
        val p = feedback(FakeHttp(302)).payload(FeedbackDraft(FeedbackKind.BUG, "hi", log = "connect 10.0.0.x", screenshot = byteArrayOf(1, 2, 3)))
        assertTrue(p.getString("feedback").startsWith("hi\n\n--- GenTV log ---\nconnect"))
        assertEquals("AQID", p.getString("screenshot_base64"))
    }

    @Test fun validJsonAcknowledgementMeansDelivered() = runBlocking {
        val http = FakeHttp(200)
        assertTrue(feedback(http).send(FeedbackDraft(FeedbackKind.PRAISE, "great")))
        assertEquals(GenTVFeedback.ENDPOINT, http.posts.single().first)
        assertEquals("praise", JSONObject(http.posts.single().second).getString("type"))
    }

    @Test fun rateLimitedIsNotDelivered() = runBlocking {
        assertFalse(feedback(FakeHttp(302, "https://x/r", "{\"success\":false,\"error\":\"rate_limited\"}")).send(FeedbackDraft(FeedbackKind.BUG, "x")))
    }

    @Test fun serverErrorAndNetworkFailureAreNotDelivered() = runBlocking {
        assertFalse(feedback(FakeHttp(500)).send(FeedbackDraft(FeedbackKind.BUG, "x")))
        val broken = object : GenTVFeedback.Http {
            override fun post(url: String, json: String): Pair<Int, String?> = throw java.io.IOException("offline")
        }
        assertFalse(feedback(broken).send(FeedbackDraft(FeedbackKind.BUG, "x")))
    }

    @Test fun captionNamesTheSiteAndTheNumbers() {
        val c = ShareStats(6, "TCL 55C735", 123, true, emptyList()).caption()
        assertTrue(c, c.contains("6 junk apps turned off") && c.contains("123 MB") && c.contains("https://www.gentv.me"))
        assertTrue(ShareStats(1, null, null, false, emptyList()).caption().contains("1 junk app turned off"))
    }
    @Test fun statusAloneOrRedirectNeverMeansDelivered() = runBlocking {
        for (http in listOf(FakeHttp(200, reply = "<html>error</html>"), FakeHttp(200, reply = "{}"), FakeHttp(200, reply = "{\"success\":false}"), FakeHttp(302))) {
            assertFalse(feedback(http).send(FeedbackDraft(FeedbackKind.BUG, "test")))
        }
    }

}
