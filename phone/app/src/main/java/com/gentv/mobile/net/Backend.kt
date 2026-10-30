package com.gentv.mobile.net

import com.gentv.adb.AdbIdentity
import com.gentv.adb.ConnectResult
import com.gentv.adb.KadbTvShell
import com.gentv.adb.PortProber
import com.gentv.adb.TvConnector
import com.gentv.core.flow.Attempt
import com.gentv.core.flow.FoundTv
import com.gentv.core.ops.TvShell
import com.gentv.core.testing.FakeTv
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/** A connection attempt's outcome plus the open shell when it worked. */
data class Link(val attempt: Attempt, val shell: TvShell? = null, val message: String? = null)

/** Everything the view model needs from the network. Real TVs use [AdbBackend]; demo mode uses [DemoBackend]. */
interface Backend {
    fun scan(): Flow<FoundTv>
    suspend fun connect(tv: FoundTv, onAwaitingAllow: () -> Unit): Link
    suspend fun pair(tv: FoundTv, code: String): Link
    /** True while something answers on the TV's port 5555. */
    suspend fun portOpen(host: String): Boolean
    /** How long to let Android save package state before moving on (15 s). */
    val saveWaitMs: Long
    fun close(shell: TvShell?) { (shell as? AutoCloseable)?.close() }
}

class AdbBackend(private val discovery: Discovery, keyFile: File, private val trace: (String) -> Unit) : Backend {
    init { AdbIdentity.use(keyFile) }

    private val connector = TvConnector(trace = trace)
    override val saveWaitMs = 15_000L

    override fun scan() = discovery.scan()

    override suspend fun connect(tv: FoundTv, onAwaitingAllow: () -> Unit): Link {
        // Only Wireless debugging on offer: a TV paired before connects with no code; otherwise pair.
        if (!tv.legacyOpen && tv.tlsPort != null) {
            val r = connector.connect(tv.host, tv.tlsPort!!, onAwaitingAllow)
            return if (r is ConnectResult.Connected) r.toLink() else Link(Attempt.PORT_CLOSED)
        }
        return connector.connect(tv.host, 5555, onAwaitingAllow).toLink()
    }

    override suspend fun pair(tv: FoundTv, code: String): Link {
        val pairPort = tv.pairPort ?: return Link(Attempt.FAILED, message = "Type the port shown on your TV.")
        val tls = tv.tlsPort ?: return Link(Attempt.FAILED, message = "Open Wireless debugging on your TV again.")
        return connector.pairAndConnect(tv.host, pairPort, code, tls).toLink()
    }

    override suspend fun portOpen(host: String) = PortProber.SOCKETS.probe(host, 5555, 1_500) == PortProber.Result.OPEN

    private fun ConnectResult.toLink(): Link = when (this) {
        is ConnectResult.Connected -> Link(Attempt.CONNECTED, shell)
        ConnectResult.PortClosed -> Link(Attempt.PORT_CLOSED)
        ConnectResult.Unreachable -> Link(Attempt.UNREACHABLE)
        ConnectResult.Denied -> Link(Attempt.DENIED)
        ConnectResult.AllowTimedOut -> Link(Attempt.ALLOW_TIMED_OUT)
        is ConnectResult.Failed -> Link(Attempt.FAILED, message = message)
    }

    override fun close(shell: TvShell?) { (shell as? KadbTvShell)?.close() }
}

/** A simulated Google TV for trying the app (and for screenshots) without a TV. Debug builds only. */
/**
 * A simulated TV for trying the flow. [phoneApk] lends the phone's own copy of an app (if it has
 * one) so the real-icon path, pm path then stat then pull, also runs in the demo.
 */
class DemoBackend(val tv: FakeTv = FakeTv.tclG06(), private val phoneApk: (String) -> java.io.File? = { null }) : Backend {
    var debuggingOn = true
    /** The demo owner presses Install as soon as a Play Store page opens (tests turn this off). */
    init { tv.playStoreInstalls = true }
    override val saveWaitMs = 1_200L

    override fun scan(): Flow<FoundTv> = flow {
        delay(700)
        emit(DEMO)
    }

    override suspend fun connect(tv: FoundTv, onAwaitingAllow: () -> Unit): Link {
        delay(500); onAwaitingAllow(); delay(1_500)
        debuggingOn = true
        return Link(Attempt.CONNECTED, SlowShell(this.tv, phoneApk))
    }

    override suspend fun pair(tv: FoundTv, code: String) = connect(tv) {}
    override suspend fun portOpen(host: String) = false.also { debuggingOn = false }

    /** Adds a little latency so progress is visible. */
    private class SlowShell(private val tv: FakeTv, private val phoneApk: (String) -> java.io.File?) : TvShell {
        private val lent = mutableMapOf<String, java.io.File>()
        private var genTVInstalled = false

        override suspend fun run(command: String): String {
            delay(40)
            if (command.startsWith("pm install -r /data/local/tmp/gentv-desktop-preview-")) {
                genTVInstalled = true
                // Like a real install: GenTV is on the TV and answers Home, so the handover can follow.
                tv.installed += com.gentv.core.lists.HomeApp.GENTV.pkg
                tv.homeClaimants.putIfAbsent(com.gentv.core.lists.HomeApp.GENTV.component, 0)
                return "Success\n"
            }
            if (genTVInstalled && command == "pm path com.gentv.tv") return "package:/data/app/com.gentv.tv/base.apk\n"
            if (genTVInstalled && command == "am start -W -n com.gentv.tv/.MainActivity") return "Status: ok\n"
            if (genTVInstalled && command.startsWith("am broadcast --user 0 -n com.gentv.tv/")) {
                val response = org.json.JSONObject().put("protocol", 1).put("ok", true).put("backend", "gentv")
                    .put("revision", 0).put("config", org.json.JSONObject().put("schema", 2)
                        .put("order", org.json.JSONArray()).put("hidden", org.json.JSONArray()).put("names", org.json.JSONObject()))
                    .put("apps", org.json.JSONArray()).put("sources", org.json.JSONArray())
                return "GENTV1:" + android.util.Base64.encodeToString(response.toString().toByteArray(), android.util.Base64.NO_WRAP)
            }
            val arg = command.trim().substringAfterLast(' ')
            if (command.startsWith("pm path ") && arg in tv.installed) {
                phoneApk(arg)?.takeIf { it.canRead() }?.let { f ->
                    val path = "/data/app/$arg/base.apk"
                    lent[path] = f
                    return "package:$path\n"
                }
            }
            lent[arg]?.let { if (command.startsWith("stat -c %s ")) return "${it.length()}\n" }
            return tv.run(command)
        }
        override suspend fun push(bytes: ByteArray, remotePath: String) { delay(300); tv.push(bytes, remotePath) }
        override suspend fun pull(remotePath: String): ByteArray? = lent[remotePath]?.readBytes() ?: tv.pull(remotePath)
    }

    companion object {
        val DEMO = FoundTv("demo", name = "Demo TV (no real TV)", model = "TCL Smart TV Pro", legacyOpen = true)
    }
}
