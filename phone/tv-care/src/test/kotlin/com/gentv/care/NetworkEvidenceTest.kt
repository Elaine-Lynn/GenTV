package com.gentv.care
import org.junit.Assert.*
import org.junit.Test
class NetworkEvidenceTest {
    @Test fun actualDefaultEthernetIsAcceptedEvenWithoutWifiOwners() {
        val state = "Active default network: 101\nNetworkAgentInfo{network{101} nc{[ Transports: ETHERNET Capabilities: INTERNET ]}}"
        assertTrue(ethernetDefault(state, "default via 192.0.2.1 dev eth0 proto dhcp"))
    }
    @Test fun backgroundEthernetOrRouteDisagreementIsNotProof() {
        val state = "Active default network: 100\nNetworkAgentInfo{network{100} Transports: WIFI}\nNetworkAgentInfo{network{101} Transports: ETHERNET}"
        assertFalse(ethernetDefault(state, "default via 192.0.2.1 dev eth0"))
        assertFalse(ethernetDefault("Active default network: 101\nNetworkAgentInfo{network{101} Transports: ETHERNET}", "default dev wlan0"))
        assertFalse(ethernetDefault("unreadable", "default dev eth0"))
    }
}
