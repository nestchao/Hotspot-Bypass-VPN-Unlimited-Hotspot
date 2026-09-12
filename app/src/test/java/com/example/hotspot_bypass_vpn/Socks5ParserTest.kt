package com.example.hotspot_bypass_vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream
import java.net.InetAddress

class Socks5ParserTest {
    @Test
    fun fragmentedGreetingAndDomainRequestAreReadExactly() {
        val greeting = Socks5Parser.readGreeting(fragmented(byteArrayOf(5, 2, 2, 0)))
        assertTrue(greeting.supportsNoAuthentication)

        val domain = "example.com".toByteArray()
        val requestBytes = byteArrayOf(5, 1, 0, 3, domain.size.toByte()) +
            domain + byteArrayOf(0x01, 0xBB.toByte())
        val request = Socks5Parser.readRequest(fragmented(requestBytes))

        assertEquals(Socks5Parser.COMMAND_CONNECT, request.command)
        assertEquals("example.com", request.targetHost)
        assertEquals(443, request.targetPort)
    }

    @Test
    fun greetingRejectsClientsWithoutNoAuthenticationMethod() {
        val greeting = Socks5Parser.readGreeting(ByteArrayInputStream(byteArrayOf(5, 2, 1, 2)))
        assertFalse(greeting.supportsNoAuthentication)
        assertArrayEquals(
            byteArrayOf(5, 0xFF.toByte()),
            Socks5Parser.buildMethodSelection(Socks5Parser.METHOD_NOT_ACCEPTABLE)
        )
    }

    @Test(expected = EOFException::class)
    fun truncatedRequestThrowsEof() {
        Socks5Parser.readRequest(ByteArrayInputStream(byteArrayOf(5, 1, 0, 1, 127, 0)))
    }

    @Test
    fun parsesIpv4AndIpv6Requests() {
        val ipv4 = Socks5Parser.readRequest(
            ByteArrayInputStream(byteArrayOf(5, 1, 0, 1, 1, 2, 3, 4, 0, 53))
        )
        assertEquals("1.2.3.4", ipv4.targetHost)
        assertEquals(53, ipv4.targetPort)

        val ipv6Address = InetAddress.getByName("2001:db8::1").address
        val ipv6 = Socks5Parser.readRequest(
            ByteArrayInputStream(byteArrayOf(5, 1, 0, 4) + ipv6Address + byteArrayOf(0x1F, 0x90.toByte()))
        )
        assertEquals(InetAddress.getByAddress(ipv6Address), InetAddress.getByName(ipv6.targetHost))
        assertEquals(8080, ipv6.targetPort)
    }

    @Test
    fun unsupportedCommandIsPreservedForReplyHandling() {
        val request = Socks5Parser.readRequest(
            ByteArrayInputStream(byteArrayOf(5, 2, 0, 1, 127, 0, 0, 1, 0, 80))
        )
        assertEquals(2, request.command)
        assertArrayEquals(
            byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0),
            Socks5Parser.buildReply(Socks5Parser.REPLY_COMMAND_NOT_SUPPORTED)
        )
    }

    @Test
    fun udpHeaderValidatesBoundsReservedBytesAndFragmentation() {
        val header = Socks5Parser.buildUdpHeader(InetAddress.getByName("8.8.8.8"), 53)
        val packet = header + byteArrayOf(1, 2, 3)
        val parsed = Socks5Parser.parseUdpHeader(packet, packet.size)

        assertNotNull(parsed)
        assertEquals("8.8.8.8", parsed!!.targetHost)
        assertEquals(53, parsed.targetPort)
        assertEquals(header.size, parsed.headerLength)

        assertNull(Socks5Parser.parseUdpHeader(packet, packet.size + 1))
        assertNull(Socks5Parser.parseUdpHeader(packet.copyOf().also { it[0] = 1 }, packet.size))
        assertNull(Socks5Parser.parseUdpHeader(packet.copyOf().also { it[2] = 1 }, packet.size))
        assertNull(Socks5Parser.parseUdpHeader(packet.copyOf(7), 7))
    }

    private fun fragmented(bytes: ByteArray): InputStream = object : FilterInputStream(ByteArrayInputStream(bytes)) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length.coerceAtMost(1))
    }
}
