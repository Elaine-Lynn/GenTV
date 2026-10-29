package com.gentv.mobile.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.gentv.adb.SubnetScanner
import com.gentv.core.flow.FoundTv
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.net.Inet4Address

/**
 * Finds TVs three ways at once (bounded by the view model discovery window):
 *  - two bounded TCP sweeps of the phone's /24 on port 5555;
 *  - NSD `_googlecast._tcp` for the friendly name (TXT fn) and model (TXT md);
 *  - NSD `_adb-tls-connect._tcp` / `_adb-tls-pairing._tcp` for TVs that only offer Wireless debugging.
 * Holds a MulticastLock while running, since many phones drop mDNS without one.
 */
class Discovery(private val context: Context, private val trace: (String) -> Unit) {
    private val nsd = context.getSystemService(NsdManager::class.java)

    fun phoneIpv4(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(net)
        if (caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return null
        val lp: LinkProperties = cm.getLinkProperties(net) ?: return null
        return lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    fun scan(): Flow<FoundTv> = callbackFlow {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("GenTV")?.apply { setReferenceCounted(false); runCatching { acquire() } }
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()

        fun browse(type: String, toTv: (NsdServiceInfo) -> FoundTv?) {
            val l = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { trace("nsd $type failed $errorCode") }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onServiceLost(info: NsdServiceInfo) = Unit
                override fun onServiceFound(info: NsdServiceInfo) {
                    resolve(info) { resolved -> toTv(resolved)?.let { trySend(it) } }
                }
            }
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l); listeners += l }
                .onFailure { trace("nsd $type: ${it.message}") }
        }

        browse("_googlecast._tcp") { i ->
            val host = i.hostV4() ?: return@browse null
            FoundTv(host, name = i.txt("fn"), model = i.txt("md"))
        }
        browse("_adb-tls-connect._tcp") { i -> i.hostV4()?.let { FoundTv(it, tlsPort = i.port) } }
        browse("_adb-tls-pairing._tcp") { i -> i.hostV4()?.let { FoundTv(it, pairPort = i.port) } }

        val ip = phoneIpv4()
        trace("scan from ${ip ?: "no Wi-Fi address"}")
        val probe = launch {
            if (ip != null) {
                val found = mutableSetOf<String>()
                // Cold ARP resolution and a waking TV can exceed the upstream 300 ms probe.
                // Retry once within the view model's bounded discovery window.
                repeat(2) { pass ->
                    trace("subnet scan pass ${pass + 1} started")
                    SubnetScanner(timeoutMs = 1_000, parallel = 32).scan(ip).collect { host ->
                        if (found.add(host)) {
                            trace("subnet discovery found a device on port 5555")
                            send(FoundTv(host, legacyOpen = true))
                        }
                    }
                    trace("subnet scan pass ${pass + 1} finished; ${found.size} devices")
                    if (pass == 0) delay(500)
                }
            } else trace("subnet discovery skipped: no local IPv4 network")
        }
        probe.join()
        awaitClose {
            listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
            runCatching { lock?.release() }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo, onResolved: (NsdServiceInfo) -> Unit) {
        // resolveService allows one request at a time on older Android; retry once when busy.
        val listener = object : NsdManager.ResolveListener {
            var retried = false
            override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && !retried) {
                    retried = true
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { nsd.resolveService(info, this) } }, 300)
                }
            }
            override fun onServiceResolved(si: NsdServiceInfo) = onResolved(si)
        }
        runCatching { nsd.resolveService(info, listener) }
    }

    @Suppress("DEPRECATION")
    private fun NsdServiceInfo.hostV4(): String? = (host as? Inet4Address)?.hostAddress

    private fun NsdServiceInfo.txt(key: String): String? =
        attributes[key]?.let { String(it, Charsets.UTF_8) }?.trim()?.takeIf { it.isNotEmpty() }
}
