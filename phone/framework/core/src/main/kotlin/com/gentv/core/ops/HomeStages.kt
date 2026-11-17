package com.gentv.core.ops

import com.gentv.core.lists.HomeApp
import com.gentv.core.lists.Launchers
import com.gentv.core.lists.Protected

/**
 * The Home handover that ends a full install (docs/tv-care.md), once GenTV is installed and the owner has seen it
 * on the TV and moved around it with the remote. [keepStock] first: Home is selected and checked with every
 * original package left on. Only when the firmware sends Home back does [compatibility] turn off the identified
 * Home blockers (the original launcher, and Google TV's setup app when it is safe), never uninstalling them and
 * never touching Android or TV core services. Both stages record the original Home, so Restore puts it back.
 */
class HomeStages(
    private val tv: TvShell,
    private val log: ActionLog,
    private val sdk: Int,
    private val sourceConfig: String? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    private val settle: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    /** Stage 1: make GenTV Home and check the Home key, with the original launcher kept on. */
    suspend fun keepStock(): HandoverOutcome = guarded {
        handover(identifyHome(), keepStock = true).run(ByteArray(0), emptyList(), sdk)
    }

    /**
     * Stage 2, after [keepStock] was rolled back. [rebootConfirmed]: the owner restarted a CVTE TV with GenTV
     * installed and saw it come back. [cableClaimed]: the owner says the TV is on a network cable; it only counts
     * when the TV's own default network and route are Ethernet.
     */
    suspend fun compatibility(rebootConfirmed: Boolean, cableClaimed: Boolean): HandoverOutcome = guarded {
        val home = Parse.component(tv.run(Commands.RESOLVE_HOME))?.substringBefore('/')
        val claimants = Parse.componentsIn(tv.run(Commands.QUERY_HOME))
        if (HomeApp.of(home) != null) return@guarded handover(null, keepStock = true).run(ByteArray(0), emptyList(), sdk)
        val stock = identifyHome() ?: return@guarded if (home == null || home == "android") HandoverOutcome.NeedsChoice(claimants)
            else HandoverOutcome.UnknownHome(home)
        // CVTE boards: a separate restart with GenTV installed comes first (a JVC set hung once its launcher was off).
        if (!rebootConfirmed && Parse.packages(tv.run(Commands.LIST_PACKAGES)).any { it.startsWith("com.cvte.") }) {
            return@guarded HandoverOutcome.RebootCheckpoint
        }
        var onEthernet = false
        if (Launchers.SETUPWRAITH in claimants) {
            onEthernet = cableClaimed && ethernetDefault(tv.run(Commands.CONNECTIVITY), tv.run(Commands.DEFAULT_ROUTE))
            val wifi = tv.run(Commands.WIFI_OWNERS)
            if (!onEthernet && wifi.contains("setupwraith")) return@guarded HandoverOutcome.SetupAppBlocked(WraithCheck.OwnsWifi)
            if (!onEthernet && !OWNER.containsMatchIn(wifi)) {
                return@guarded HandoverOutcome.SetupAppBlocked(WraithCheck.Unknown("it couldn't read who owns the saved Wi-Fi"))
            }
        }
        handover(stock, keepStock = false).run(ByteArray(0), emptyList(), sdk, onEthernet = onEthernet)
    }

    /**
     * The launcher that owns Home now, when GenTV may hand Home over from it: it answers the HOME intent on this
     * TV, isn't a factory or settings activity and isn't an Android or TV core service. Null when unclear.
     */
    suspend fun identifyHome(): String? {
        val home = Parse.component(tv.run(Commands.RESOLVE_HOME))?.substringBefore('/') ?: return null
        if (home == "android" || HomeApp.of(home) != null) return null
        if (FACTORY.containsMatchIn(home) || Protected.reason(home) != null) return null
        return home.takeIf { it in Parse.componentsIn(tv.run(Commands.QUERY_HOME)) }
    }

    private fun handover(stock: String?, keepStock: Boolean) = LauncherHandover(
        tv, log, clock, sleep, HomeApp.GENTV, sourceConfig, settle,
        identifiedStockHome = stock, keepStockEnabled = keepStock, alreadyInstalled = true,
    )

    private suspend fun guarded(block: suspend () -> HandoverOutcome): HandoverOutcome = try {
        block()
    } catch (e: TvConnectionLost) {
        HandoverOutcome.Disconnected
    }

    private companion object {
        val FACTORY = Regex("factory|facmenu|factorytest", RegexOption.IGNORE_CASE)
        val OWNER = Regex("(?m)^cname=[A-Za-z0-9_.]+")
    }
}
