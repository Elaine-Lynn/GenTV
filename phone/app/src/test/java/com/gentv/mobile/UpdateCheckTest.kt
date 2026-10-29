package com.gentv.mobile

import com.gentv.mobile.data.UpdateCheck
import com.gentv.mobile.data.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateCheckTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun release(tag: String, draft: Boolean = false, pre: Boolean = false) =
        """{"tag_name":"$tag","html_url":"https://github.com/Elaine-Lynn/GenTV/releases/tag/$tag","draft":$draft,"prerelease":$pre,"assets":[]}"""

    private class Net(var body: String?) {
        var calls = 0
        fun get(url: String): String? { calls++; assertEquals(UpdateCheck.LATEST_URL, url); return body }
    }

    private var clock = 1_000_000L
    private fun check(net: Net, current: String = "0.3.0") =
        UpdateCheck(java.io.File(tmp.root, "update.json"), current, net::get) { clock }

    @Test fun versionsCompareNumerically() {
        assertTrue(UpdateCheck.isNewer("0.3.1", "0.3.0"))
        assertTrue(UpdateCheck.isNewer("0.10.0", "0.9.9"))
        assertTrue(UpdateCheck.isNewer("1.0", "0.9.9"))
        assertFalse(UpdateCheck.isNewer("0.3.0", "0.3.0"))
        assertFalse(UpdateCheck.isNewer("0.2.9", "0.3.0"))
        assertFalse("debug suffix is ignored", UpdateCheck.isNewer("0.3.0", "0.3.0-debug"))
        assertFalse("garbage never nags", UpdateCheck.isNewer("latest", "0.3.0"))
    }

    @Test fun parsesTagsAndSkipsDraftsAndPrereleases() {
        assertEquals(UpdateInfo("0.4.0", "https://github.com/Elaine-Lynn/GenTV/releases/tag/v0.4.0"), UpdateCheck.parse(release("v0.4.0")))
        assertEquals("0.4", UpdateCheck.parse(release("0.4"))?.version)
        assertNull(UpdateCheck.parse(release("v0.5.0", draft = true)))
        assertNull(UpdateCheck.parse(release("v0.5.0-beta", pre = true)))
        assertNull(UpdateCheck.parse("not json"))
        assertNull(UpdateCheck.parse("""{"message":"API rate limit exceeded"}"""))
    }

    @Test fun offersOnlyANewerRelease() {
        assertEquals("0.4.0", check(Net(release("v0.4.0"))).check()?.version)
        clock += 2 * DAY
        assertNull(check(Net(release("v0.3.0"))).check())
        clock += 2 * DAY
        assertNull(check(Net(release("v0.2.0"))).check())
    }

    @Test fun asksGitHubAtMostOnceADay() {
        val net = Net(release("v0.4.0"))
        check(net).check()
        check(net).check()
        assertEquals(1, net.calls)
        clock += DAY + 1
        check(net).check()
        assertEquals(2, net.calls)
    }

    @Test fun cachedAnswerStillShowsWithinTheDay() {
        check(Net(release("v0.4.0"))).check()
        assertEquals("0.4.0", check(Net(null)).check()?.version)
    }

    @Test fun offlineIsSilentAndTriesAgainNextTime() {
        val net = Net(null)
        assertNull(check(net).check())
        net.body = release("v0.4.0")
        assertEquals("tried again, no day-long wait after a failure", "0.4.0", check(net).check()?.version)
    }

    @Test fun throwingFetchNeverCrashes() {
        val c = UpdateCheck(java.io.File(tmp.root, "update.json"), "0.3.0", { throw java.io.IOException("offline") }) { clock }
        assertNull(c.check())
    }

    @Test fun notNowHidesThatVersionButNotANewerOne() {
        val net = Net(release("v0.4.0"))
        check(net).dismiss("0.4.0")
        assertNull(check(net).check())
        clock += DAY + 1
        net.body = release("v0.5.0")
        assertEquals("0.5.0", check(net).check()?.version)
    }

    private companion object { const val DAY = 24L * 60 * 60 * 1000 }

    @Test fun onlySideloadedCopiesCheckGitHub() {
        assertFalse("Play updates its own installs", UpdateCheck.needed("com.android.vending"))
        assertTrue("a browser or file manager install", UpdateCheck.needed("com.google.android.packageinstaller"))
        assertTrue("adb or unknown", UpdateCheck.needed(null))
        assertEquals("the banner uses the GenTV download destination", BuildConfig.DOWNLOAD_URL, UpdateCheck.SITE_URL)
    }
}
