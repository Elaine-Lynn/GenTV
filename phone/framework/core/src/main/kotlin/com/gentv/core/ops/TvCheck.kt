package com.gentv.core.ops

import com.gentv.core.lists.HomeApp
import com.gentv.core.lists.Launchers
import com.gentv.core.lists.Protected

/** What "Check my TV" found. */
data class CheckReport(
    /** Apps GenTV turned off that are on again, usually after a firmware update. */
    val backOn: List<String>,
    /** Apps from this TV's list that weren't installed at the last backup: new bloat from an update. */
    val newOnList: List<String>,
    /** GenTV's home screen was Home, and now something else is. */
    val homeChanged: Boolean,
    /** Apps GenTV turned off that are still off. */
    val stillOff: Int,
    /** The voice assistant or remote helpers are off, so the mic or shortcut keys may not work (the owner's call). */
    val remoteOff: List<String> = emptyList(),
) {
    val allGood get() = backOn.isEmpty() && newOnList.isEmpty() && !homeChanged
    /** What "Tidy these again" turns off. */
    val toTidy get() = backOn + newOnList
}

/**
 * Firmware updates often switch bloat back on. This compares the TV with what
 * GenTV did: the phone's action log plus the undo list on the TV. Read-only; [Debloater] does the
 * re-tidy, after a fresh backup, like any other clean-up.
 */
class TvCheck(private val tv: TvShell, private val log: ActionLog) {
    /**
     * [listPkgs] is this model's list; [installedAtBackup] the packages at the last backup, so apps the
     * owner chose to keep at review time aren't flagged again, only ones that arrived since.
     */
    suspend fun run(
        listPkgs: Collection<String>,
        installedAtBackup: Set<String>?,
        liveProtected: Set<String> = emptySet(),
        keep: Set<String> = emptySet(),
    ): CheckReport {
        val records = log.records()
        val mirror = Parse.mirror(tv.run(Commands.READ_MIRROR)) ?: emptySet()
        val removedMirror = Parse.mirror(tv.run(Commands.READ_REMOVED)) ?: emptySet()
        val ours = records.disabledByUs() + mirror + records.removedByUs() + removedMirror
        val installed = Parse.packages(tv.run(Commands.LIST_PACKAGES))
        val disabled = Parse.packages(tv.run(Commands.LIST_DISABLED))
        val on = installed - disabled
        val safe = { p: String -> !Launchers.isHomeApp(p) && !Protected.isProtected(p, liveProtected, keep) }

        val backOn = ours.filter { it in on && safe(it) }.sorted()
        val newOnList = if (installedAtBackup == null) emptyList() else
            listPkgs.filter { it in on && it !in installedAtBackup && it !in ours && safe(it) }.distinct().sorted()

        val lastSetHome = records.lastOrNull { it.op == Op.SET_HOME && it.phase == Phase.DONE }?.target
        val ourHomeWasSet = HomeApp.of(lastSetHome?.substringBefore('/')) != null
        val homeNow = Parse.component(tv.run(Commands.RESOLVE_HOME))?.substringBefore('/')
        val homeChanged = ourHomeWasSet && HomeApp.of(homeNow) == null

        val remoteOff = Protected.REMOTE_ESSENTIALS.filter { it in installed && it in disabled && it in ours }.sorted()

        // Counted like the hub and Done screens: the old launcher the home-screen switch turned off isn't an app.
        return CheckReport(backOn, newOnList, homeChanged, stillOff = ours.count { (it in disabled || it !in installed) && !Launchers.isHomeApp(it) }, remoteOff = remoteOff)
    }
}
