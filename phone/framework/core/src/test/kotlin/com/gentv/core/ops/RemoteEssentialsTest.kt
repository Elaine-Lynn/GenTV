package com.gentv.core.ops

import com.gentv.core.lists.Grouping
import com.gentv.core.lists.ListCatalog
import com.gentv.core.lists.Protected
import com.gentv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The voice assistant and the remote's helpers: never ticked for you, and Check my TV tells you when they're off. */
class RemoteEssentialsTest {
    @Test fun noListEverTicksTheAssistantOrRemoteHelpers() {
        val ex = File("../data/devices")
        val catalog = ListCatalog(ex.listFiles()!!.filter { it.isDirectory }.associate { it.name to File(it, "debloat.txt").readText() })
        var seen = 0
        for (list in catalog.lists) {
            val installed = (list.entries + list.optional).map { it.pkg }.toSet()
            for (c in Grouping.candidates(list, installed)) {
                if (c.pkg in Protected.REMOTE_ESSENTIALS) { seen++; assertTrue("${list.id} ticks ${c.pkg}", !c.selected) }
            }
        }
        assertTrue("some list does offer them", seen > 0)
    }

    @Test fun checkMyTvWarnsWhenTheAssistantWeTurnedOffIsStillOff() = runTest {
        val tv = FakeTv.googleTv()
        val log = MemoryActionLog()
        Debloater(tv, log, clock = { 1L }, sleep = {}).run(listOf("com.google.android.katniss"))
        val r = TvCheck(tv, log).run(emptyList(), installedAtBackup = null)
        assertEquals(listOf("com.google.android.katniss"), r.remoteOff)
        Restorer(tv, log) { 2L }.enableOnly(setOf("com.google.android.katniss"))
        assertTrue(TvCheck(tv, log).run(emptyList(), installedAtBackup = null).remoteOff.isEmpty())
    }
}
