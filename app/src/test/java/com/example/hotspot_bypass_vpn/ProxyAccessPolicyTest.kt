package com.example.hotspot_bypass_vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class ProxyAccessPolicyTest {
    private fun ip(value: String): InetAddress = InetAddress.getByName(value)

    @Test
    fun wifiDirectClientIsAllowed() {
        assertTrue(ProxyAccessPolicy.isAllowedClient(ip("192.168.49.1"), ip("192.168.49.23"), "p2p-wlan0-0"))
    }

    @Test
    fun wifiDirectSubnetIsAllowedEvenWhenInterfaceNameIsUnknown() {
        assertTrue(ProxyAccessPolicy.isAllowedClient(ip("192.168.49.1"), ip("192.168.49.23"), null))
    }

    @Test
    fun p2pInterfaceIsAllowedOnAnySubnet() {
        assertTrue(ProxyAccessPolicy.isAllowedClient(ip("192.168.50.1"), ip("192.168.50.7"), "p2p0"))
    }

    @Test
    fun loopbackClientIsAllowed() {
        assertTrue(ProxyAccessPolicy.isAllowedClient(ip("127.0.0.1"), ip("127.0.0.1"), "lo"))
    }

    @Test
    fun clientOnAnotherWifiNetworkIsRejected() {
        assertFalse(ProxyAccessPolicy.isAllowedClient(ip("192.168.1.42"), ip("192.168.1.10"), "wlan0"))
    }

    @Test
    fun clientOnMobileDataIsRejected() {
        assertFalse(ProxyAccessPolicy.isAllowedClient(ip("100.72.4.9"), ip("203.0.113.5"), "rmnet_data0"))
    }

    @Test
    fun hostLocalTargetsAreBlocked() {
        assertTrue(ProxyAccessPolicy.isBlockedTarget(ip("127.0.0.1")))
        assertTrue(ProxyAccessPolicy.isBlockedTarget(ip("::1")))
        assertTrue(ProxyAccessPolicy.isBlockedTarget(ip("0.0.0.0")))
    }

    @Test
    fun internetTargetsAreNotBlocked() {
        assertFalse(ProxyAccessPolicy.isBlockedTarget(ip("8.8.8.8")))
        assertFalse(ProxyAccessPolicy.isBlockedTarget(ip("192.168.49.1")))
        assertFalse(ProxyAccessPolicy.isBlockedTarget(null))
    }
}
