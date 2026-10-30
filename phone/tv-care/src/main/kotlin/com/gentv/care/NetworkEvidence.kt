package com.gentv.care

/** Require both an active Ethernet default network and an actual Ethernet default route. */
fun ethernetDefault(connectivity: String, routes: String): Boolean {
    val id = Regex("(?:Active default network:|mActiveDefaultNetwork[=:])\\s*(\\d+)", RegexOption.IGNORE_CASE)
        .find(connectivity)?.groupValues?.get(1) ?: return false
    val active = connectivity.split("NetworkAgentInfo").drop(1).filter { block ->
        Regex("(?:network\\{|network\\(|Network\\()$id[})]").containsMatchIn(block)
    }
    val ethernet = active.size == 1 && Regex("Transports:\\s*ETHERNET\\b").containsMatchIn(active.single())
    val defaults = routes.lineSequence().filter { it.trimStart().startsWith("default ") }.toList()
    val devices = defaults.mapNotNull { Regex("\\bdev\\s+([A-Za-z0-9_.-]+)").find(it)?.groupValues?.get(1) }.distinct()
    return ethernet && devices.size == 1 && Regex("(?:eth[0-9]+|en[A-Za-z0-9]+|usb[0-9]+)").matches(devices.single())
}
