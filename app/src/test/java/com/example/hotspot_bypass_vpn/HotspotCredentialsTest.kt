package com.example.hotspot_bypass_vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotCredentialsTest {
    private val generated = "generated1"

    @Test
    fun newInstallGetsAGeneratedPassword() {
        assertEquals(generated, HotspotCredentials.resolvePassword(null, usedHostBefore = false) { generated })
    }

    @Test
    fun upgradedInstallThatNeverChangedThePasswordKeepsTheOldDefault() {
        // Devices that saved the network with the old default must still reconnect after the update.
        assertEquals(
            HotspotCredentials.LEGACY_DEFAULT_PASSWORD,
            HotspotCredentials.resolvePassword(null, usedHostBefore = true) { generated }
        )
    }

    @Test
    fun savedPasswordIsKept() {
        assertEquals("my-own-pass", HotspotCredentials.resolvePassword("my-own-pass", usedHostBefore = true) { generated })
        assertEquals("87654321", HotspotCredentials.resolvePassword("87654321", usedHostBefore = false) { generated })
    }

    @Test
    fun invalidSavedPasswordIsReplaced() {
        // Wi-Fi Direct rejects passphrases shorter than 8 characters.
        assertEquals(generated, HotspotCredentials.resolvePassword("short", usedHostBefore = true) { generated })
    }

    @Test
    fun generatedPasswordsAreValidAndDiffer() {
        val first = HotspotCredentials.generatePassword()
        val second = HotspotCredentials.generatePassword()
        assertTrue(HotspotCredentials.isValidPassword(first))
        assertTrue(HotspotCredentials.isValidPassword(second))
        assertNotEquals(first, second)
        assertNotEquals(HotspotCredentials.LEGACY_DEFAULT_PASSWORD, first)
    }

    @Test
    fun passwordValidationFollowsWpa2Rules() {
        assertFalse(HotspotCredentials.isValidPassword("1234567"))
        assertTrue(HotspotCredentials.isValidPassword("12345678"))
        assertTrue(HotspotCredentials.isValidPassword("a".repeat(63)))
        assertFalse(HotspotCredentials.isValidPassword("a".repeat(64)))
        assertFalse(HotspotCredentials.isValidPassword("pässwörd123"))
    }

    @Test
    fun blankSsidFallsBackToDefault() {
        assertEquals(HotspotCredentials.DEFAULT_SSID, HotspotCredentials.resolveSsid(null))
        assertEquals(HotspotCredentials.DEFAULT_SSID, HotspotCredentials.resolveSsid("  "))
        assertEquals("DIRECT-MyPhone", HotspotCredentials.resolveSsid("DIRECT-MyPhone"))
    }
}
