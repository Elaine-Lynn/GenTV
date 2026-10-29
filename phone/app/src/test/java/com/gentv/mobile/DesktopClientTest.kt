package com.gentv.mobile

import android.util.Base64
import com.gentv.mobile.data.DesktopClient
import com.gentv.mobile.data.DesktopReport
import com.gentv.core.ops.TvShell
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DesktopClientTest {
    private class Shell : TvShell {
        val commands = mutableListOf<String>()
        var answers = mutableMapOf<String, String>()
        var fallback: String = ""
        override suspend fun run(command: String): String { commands += command; return answers[command] ?: fallback }
        override suspend fun push(bytes: ByteArray, remotePath: String) = error("not used")
        override suspend fun pull(remotePath: String): ByteArray? = error("not used")
    }
    private fun response(protocol: Int = 1) = "Broadcast completed: result=0, data=\"GENTV1:" + Base64.encodeToString(
        JSONObject().put("protocol", protocol).put("ok", true).put("config", JSONObject()).toString().toByteArray(), Base64.NO_WRAP) + "\""

    private fun profile() = JSONObject().put("schema", 2).put("order", org.json.JSONArray())
        .put("hidden", org.json.JSONArray()).put("names", JSONObject()).put("effects", "auto")

    @Test fun importedProfilesRejectInvalidTypesAndUnknownActions() {
        for (bad in listOf(profile().put("order", org.json.JSONArray().put(42)), profile().put("names", org.json.JSONArray()),
                profile().put("command", "anything"), profile().put("effects", "invalid"), profile().put("schema", 1))) {
            assertTrue(runCatching { com.gentv.mobile.data.parseDesktopProfile(bad.toString()) }.isFailure)
        }
        assertEquals(2, com.gentv.mobile.data.parseDesktopProfile(profile().toString()).getInt("schema"))
    }
    @Test fun rejectsMissingReceiverRatherThanClaimingSuccess() {
        assertThrows(IllegalStateException::class.java) { DesktopClient.decode("Broadcast completed: result=0") }
        assertThrows(IllegalStateException::class.java) { DesktopClient.decode("GENTV_ERROR: invalid") }
    }
    @Test fun rejectsNewerProtocol() {
        assertThrows(IllegalStateException::class.java) { DesktopClient.decode(response(2)) }
        assertEquals(1, DesktopClient.decode(response()).getInt("protocol"))
    }
    @Test fun arbitraryNamesNeverBecomeShellCode() = runBlocking {
        val shell = Shell()
        val config = profile().put("names", JSONObject().put("com.example.app/.Main", "quote' ; echo private $(id)"))
        runCatching { DesktopClient(shell).save(config) }
        assertEquals(1, shell.commands.size)
        assertFalse(shell.commands.single().contains("echo private"))
        assertTrue(shell.commands.single().startsWith("am broadcast --user 0 -n com.gentv.tv/"))
    }
    @Test fun saveRequiresMatchingReadbackAndANewerRevision() = runBlocking {
        val config = profile().put("names", JSONObject().put("com.example.one/.Main", "Cinema"))
        val shell = Shell()
        fun reply(saved: JSONObject, revision: Int) = "GENTV1:" + Base64.encodeToString(JSONObject()
            .put("protocol", 1).put("ok", true).put("config", saved).put("revision", revision)
            .toString().toByteArray(), Base64.NO_WRAP)
        shell.fallback = reply(profile(), 1)
        assertTrue(runCatching { DesktopClient(shell).save(config, 0) }.isFailure)
        shell.fallback = reply(config, 0)
        assertTrue(runCatching { DesktopClient(shell).save(config, 0) }.isFailure)
        shell.fallback = reply(config, 1)
        assertEquals(1, DesktopClient(shell).save(config, 0).getInt("revision"))
    }
    @Test fun oversizedDraftNeverReachesTheTv() = runBlocking {
        val shell = Shell()
        assertTrue(runCatching { DesktopClient(shell).save(JSONObject().put("name", "x".repeat(61000))) }.isFailure)
        assertTrue(shell.commands.isEmpty())
    }
    @Test fun reportDoesNotConfuseMissingHomeWithPassOrRunPhysicalTests() = runBlocking {
        val shell = Shell()
        val report = DesktopReport(shell).collect()
        assertTrue(report.contains("| Default Home is GenTV | UNKNOWN |"))
        assertTrue(report.contains("| Physical remote Home | NOT TESTED |"))
        assertTrue(shell.commands.none { "input keyevent" in it || "reboot" in it || "settings put" in it })
        assertTrue(shell.commands.none { "dumpsys" in it || it == "getprop" })
    }
    @Test fun reportRecognizesTheExactHomeComponent() = runBlocking {
        val shell = Shell()
        shell.answers["cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"] = "com.gentv.tv/com.gentv.tv.MainActivity"
        val report = DesktopReport(shell).collect()
        assertTrue(report.contains("| Default Home is GenTV | PASS |"))
        shell.answers["cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"] = "com.gentv.tv.fake/.MainActivity"
        assertTrue(DesktopReport(shell).collect().contains("| Default Home is GenTV | FAIL |"))
    }
    @Test fun componentProfilesUseTheNewReceiverAndRejectPackageKeys() = runBlocking {
        val shell = Shell()
        val profile = profile().put("order", org.json.JSONArray().put("com.example.one/.MainActivity"))
        runCatching { DesktopClient(shell).save(profile, 0) }
        assertEquals(1, shell.commands.size)
        assertTrue(shell.commands.single().startsWith("am broadcast --user 0 -n com.gentv.tv/"))
        shell.commands.clear()
        profile.put("order", org.json.JSONArray().put("com.example.one"))
        assertTrue(runCatching { DesktopClient(shell).save(profile, 0) }.isFailure)
        assertTrue(shell.commands.isEmpty())
    }
    @Test fun newDesktopProfilesRejectOldAppearanceAndInputFields() {
        val profile = profile().put("appearance", "cinema")
        assertThrows(IllegalArgumentException::class.java) { com.gentv.mobile.data.parseDesktopProfile(profile.toString()) }
    }
    @Test fun undoIsSentOnlyToTheSelectedBackend() = runBlocking {
        val shell = Shell()
        runCatching { DesktopClient(shell).undo(7) }
        assertEquals(1, shell.commands.size)
        assertTrue(shell.commands.single().contains("com.gentv.tv/com.gentv.tv.DesktopReceiver"))
        assertTrue(shell.commands.single().contains("--ei revision 7"))
    }

    @Test fun readUsesOnlyTheGenTvReceiver() = runBlocking {
        val shell=Shell()
        shell.fallback="Broadcast completed: result=0"
        assertTrue(runCatching { DesktopClient(shell).read() }.isFailure)
        assertEquals(1,shell.commands.size)
        assertTrue(shell.commands.single().contains("com.gentv.tv/com.gentv.tv.DesktopReceiver"))
    }

}
