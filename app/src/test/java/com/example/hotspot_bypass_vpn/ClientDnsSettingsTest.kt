package com.example.hotspot_bypass_vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientDnsSettingsTest {
    @Test
    fun parsesCommaAndSpaceSeparatedLists() {
        assertEquals(listOf("9.9.9.9", "1.0.0.1"), ClientDnsSettings.parse("9.9.9.9, 1.0.0.1"))
        assertEquals(listOf("9.9.9.9", "1.0.0.1"), ClientDnsSettings.parse(" 9.9.9.9   1.0.0.1 "))
    }

    @Test
    fun removesDuplicates() {
        assertEquals(listOf("8.8.8.8"), ClientDnsSettings.parse("8.8.8.8,8.8.8.8"))
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(ClientDnsSettings.parse(null))
        assertNull(ClientDnsSettings.parse("   "))
        assertNull(ClientDnsSettings.parse("8.8.8"))
        assertNull(ClientDnsSettings.parse("256.1.1.1"))
        assertNull(ClientDnsSettings.parse("dns.google"))
        assertNull(ClientDnsSettings.parse("2001:4860:4860::8888"))
        assertNull(ClientDnsSettings.parse("8.8.8.8, nope"))
        assertNull(ClientDnsSettings.parse("1.1.1.1,2.2.2.2,3.3.3.3,4.4.4.4,5.5.5.5"))
    }

    @Test
    fun formatRoundTrips() {
        val servers = listOf("8.8.8.8", "1.1.1.1")
        assertEquals(servers, ClientDnsSettings.parse(ClientDnsSettings.format(servers)))
        assertEquals(ClientDnsSettings.DEFAULT_SERVERS, ClientDnsSettings.parse(ClientDnsSettings.format(ClientDnsSettings.DEFAULT_SERVERS)))
    }
}
