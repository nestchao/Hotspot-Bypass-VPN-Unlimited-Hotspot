package com.example.hotspot_bypass_vpn

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class ProxyServerIntegrationTest {
    private val resources = mutableListOf<Closeable>()
    private val servers = mutableListOf<ProxyServer>()

    @After
    fun tearDown() {
        resources.reversed().forEach {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
        servers.reversed().forEach {
            it.stop()
            assertTrue("Proxy workers did not stop", it.awaitStopped())
        }
    }

    @Test
    fun connectionBeyondCoreWorkerStartsImmediately() {
        val echo = TcpEchoServer().also { resources.add(it) }
        val proxy = startProxy(coreThreads = 1, maxThreads = 4, perClient = 4)

        val first = openTcpTunnel(proxy.localPort(), echo.port).also { resources.add(it) }
        assertEcho(first, "first")

        val second = openTcpTunnel(proxy.localPort(), echo.port, "127.0.0.2").also { resources.add(it) }
        assertEcho(second, "second")

        val metrics = proxy.metrics()
        assertEquals(2, metrics.activeConnections)
        assertEquals(2, metrics.activeTcpSessions)
        assertTrue("Executor never grew beyond its core worker", metrics.executorPoolSize >= 2)
        assertEquals(0, metrics.rejectedConnections)
    }

    @Test
    fun perClientAndGlobalLimitsPreserveCapacityAndReleaseItAfterDisconnect() {
        val echo = TcpEchoServer().also { resources.add(it) }
        val proxy = startProxy(coreThreads = 1, maxThreads = 2, perClient = 1)

        val first = openTcpTunnel(proxy.localPort(), echo.port).also { resources.add(it) }
        assertEcho(first, "held-open")

        val rejected = Socket("127.0.0.1", proxy.localPort()).also { resources.add(it) }
        rejected.soTimeout = 2_000
        rejected.getOutputStream().write(byteArrayOf(5, 1, 0))
        assertSocketClosed(rejected)
        assertTrue(awaitCondition { proxy.metrics().rejectedConnections == 1 })

        val secondDevice = openTcpTunnel(proxy.localPort(), echo.port, "127.0.0.2").also { resources.add(it) }
        assertEcho(secondDevice, "second-device")

        val globallyRejected = connectToProxy(proxy.localPort(), "127.0.0.3").also { resources.add(it) }
        globallyRejected.soTimeout = 2_000
        globallyRejected.getOutputStream().write(byteArrayOf(5, 1, 0))
        assertSocketClosed(globallyRejected)
        assertTrue(awaitCondition { proxy.metrics().rejectedConnections == 2 })

        first.close()
        assertTrue(awaitCondition { proxy.metrics().activeByClient["127.0.0.1"] == null })
        assertEcho(secondDevice, "survives-first-device-disconnect")

        val replacement = openTcpTunnel(proxy.localPort(), echo.port).also { resources.add(it) }
        assertEcho(replacement, "replacement")
        assertEquals(1, proxy.metrics().activeByClient["127.0.0.1"] ?: 0)
        assertEquals(1, proxy.metrics().activeByClient["127.0.0.2"] ?: 0)
    }

    @Test
    fun udpAssociationsRemainIndependentAndShutdownCleansEverything() {
        val udpEcho = UdpEchoServer().also { resources.add(it) }
        val proxy = startProxy(coreThreads = 1, maxThreads = 4, perClient = 4)

        val first = openUdpAssociation(proxy.localPort()).also { resources.add(it) }
        val second = openUdpAssociation(proxy.localPort()).also { resources.add(it) }
        val firstUdp = DatagramSocket().apply { soTimeout = 2_000 }.also { resources.add(it) }
        val secondUdp = DatagramSocket().apply { soTimeout = 2_000 }.also { resources.add(it) }

        assertUdpEcho(firstUdp, first.relayPort, udpEcho.port, "alpha")
        assertUdpEcho(secondUdp, second.relayPort, udpEcho.port, "beta")

        firstUdp.send(
            DatagramPacket(byteArrayOf(0, 0, 1, 1), 4, InetAddress.getByName("127.0.0.1"), first.relayPort)
        )
        assertUdpEcho(firstUdp, first.relayPort, udpEcho.port, "after-malformed")
        assertUdpEndpointRejected(secondUdp, first.relayPort, udpEcho.port)
        assertUdpEcho(firstUdp, first.relayPort, udpEcho.port, "still-isolated")
        assertEquals(2, proxy.metrics().activeUdpAssociations)

        first.close()
        assertTrue(awaitCondition { proxy.metrics().activeUdpAssociations == 1 })
        assertUdpEcho(secondUdp, second.relayPort, udpEcho.port, "survives-first-association-disconnect")
        second.close()
        assertTrue(awaitCondition { proxy.metrics().activeUdpAssociations == 0 })

        proxy.stop()
        assertTrue(proxy.awaitStopped())
        assertTrue(awaitCondition { proxy.metrics().activeConnections == 0 })
        assertEquals(0, proxy.metrics().activeTcpSessions)
        assertEquals(0, proxy.metrics().activeUdpAssociations)
    }

    @Test
    fun shutdownClosesActiveTcpAndUdpConnections() {
        val echo = TcpEchoServer().also { resources.add(it) }
        val udpEcho = UdpEchoServer().also { resources.add(it) }
        val proxy = startProxy(coreThreads = 1, maxThreads = 4, perClient = 4)
        val tcp = openTcpTunnel(proxy.localPort(), echo.port).also { resources.add(it) }
        val udp = openUdpAssociation(proxy.localPort()).also { resources.add(it) }
        val datagrams = DatagramSocket().apply { soTimeout = 2_000 }.also { resources.add(it) }
        assertEcho(tcp, "active-tcp")
        assertUdpEcho(datagrams, udp.relayPort, udpEcho.port, "active-udp")
        proxy.stop()
        assertTrue(proxy.awaitStopped())
        assertSocketClosed(tcp)
        assertSocketClosed(udp.socket)
        assertEquals(0, proxy.metrics().activeConnections)
        assertEquals(0, proxy.metrics().activeTcpSessions)
        assertEquals(0, proxy.metrics().activeUdpAssociations)
        assertTrue(proxy.metrics().activeByClient.isEmpty())
    }

    private fun startProxy(coreThreads: Int, maxThreads: Int, perClient: Int): ProxyServer {
        val proxy = ProxyServer(
            ProxyServer.Config(
                port = 0,
                coreThreads = coreThreads,
                maxThreads = maxThreads,
                maxConnectionsPerClient = perClient,
                workerKeepAliveMs = 500,
                connectTimeoutMs = 2_000,
                handshakeTimeoutMs = 2_000,
                socketPollIntervalMs = 100,
                tcpIdleTimeoutMs = 2_000,
                udpTargetTimeoutMs = 500,
                udpIdleTimeoutMs = 2_000,
                udpCleanupIntervalMs = 100
            )
        )
        servers.add(proxy)
        proxy.start()
        assertTrue("Proxy did not bind", proxy.awaitStarted())
        return proxy
    }

    private fun openTcpTunnel(proxyPort: Int, targetPort: Int, localIp: String? = null): Socket {
        val socket = connectToProxy(proxyPort, localIp)
        socket.soTimeout = 2_000
        negotiate(socket)
        val request = byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1) + portBytes(targetPort)
        socket.getOutputStream().write(request)
        socket.getOutputStream().flush()
        val reply = readExactly(socket.getInputStream(), 10)
        assertEquals("SOCKS CONNECT failed", 0, reply[1].toInt())
        return socket
    }

    private fun connectToProxy(proxyPort: Int, localIp: String? = null): Socket = Socket().apply {
        if (localIp != null) bind(InetSocketAddress(localIp, 0))
        connect(InetSocketAddress("127.0.0.1", proxyPort), 2_000)
    }

    private fun openUdpAssociation(proxyPort: Int): UdpControl {
        val socket = Socket("127.0.0.1", proxyPort)
        socket.soTimeout = 2_000
        negotiate(socket)
        socket.getOutputStream().write(byteArrayOf(5, 3, 0, 1, 0, 0, 0, 0, 0, 0))
        socket.getOutputStream().flush()
        val reply = readExactly(socket.getInputStream(), 10)
        assertEquals("SOCKS UDP ASSOCIATE failed", 0, reply[1].toInt())
        return UdpControl(socket, ((reply[8].toInt() and 0xFF) shl 8) or (reply[9].toInt() and 0xFF))
    }

    private fun negotiate(socket: Socket) {
        socket.getOutputStream().write(byteArrayOf(5, 1, 0))
        socket.getOutputStream().flush()
        assertArrayEquals(byteArrayOf(5, 0), readExactly(socket.getInputStream(), 2))
    }

    private fun assertEcho(socket: Socket, value: String) {
        val bytes = value.toByteArray()
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
        assertArrayEquals(bytes, readExactly(socket.getInputStream(), bytes.size))
    }

    private fun assertUdpEcho(socket: DatagramSocket, relayPort: Int, targetPort: Int, value: String) {
        val payload = value.toByteArray()
        val request = Socks5Parser.buildUdpHeader(InetAddress.getByName("127.0.0.1"), targetPort) + payload
        socket.send(DatagramPacket(request, request.size, InetAddress.getByName("127.0.0.1"), relayPort))

        val buffer = ByteArray(1024)
        val response = DatagramPacket(buffer, buffer.size)
        socket.receive(response)
        val target = Socks5Parser.parseUdpHeader(response.data, response.length)
        assertTrue(target != null)
        assertArrayEquals(payload, response.data.copyOfRange(target!!.headerLength, response.length))
    }

    private fun assertUdpEndpointRejected(socket: DatagramSocket, relayPort: Int, targetPort: Int) {
        val previousTimeout = socket.soTimeout
        socket.soTimeout = 250
        val payload = "wrong-endpoint".toByteArray()
        val request = Socks5Parser.buildUdpHeader(InetAddress.getByName("127.0.0.1"), targetPort) + payload
        socket.send(DatagramPacket(request, request.size, InetAddress.getByName("127.0.0.1"), relayPort))
        try {
            socket.receive(DatagramPacket(ByteArray(1024), 1024))
            throw AssertionError("UDP association accepted traffic from a different source endpoint")
        } catch (_: SocketTimeoutException) {
            // Expected: an association stays bound to the first UDP source endpoint it observes.
        } finally {
            socket.soTimeout = previousTimeout
        }
    }

    private fun assertSocketClosed(socket: Socket) {
        try {
            assertEquals(-1, socket.getInputStream().read())
        } catch (_: SocketException) {
            // A reset is also a valid immediate rejection.
        }
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(result, offset, length - offset)
            if (count < 0) throw EOFException("Expected $length bytes, received $offset")
            offset += count
        }
        return result
    }

    private fun portBytes(port: Int) = byteArrayOf((port ushr 8).toByte(), (port and 0xFF).toByte())

    private fun awaitCondition(timeoutMs: Long = 3_000, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    private data class UdpControl(val socket: Socket, val relayPort: Int) : Closeable {
        override fun close() = socket.close()
    }

    private class TcpEchoServer : Closeable {
        private val running = AtomicBoolean(true)
        private val server = ServerSocket(0)
        private val clients = ConcurrentHashMap.newKeySet<Socket>()
        val port: Int = server.localPort

        init {
            thread(name = "Test-TCP-Echo", isDaemon = true) {
                try {
                    while (running.get()) {
                        val client = server.accept()
                        clients.add(client)
                        thread(isDaemon = true) {
                            try {
                                val buffer = ByteArray(1024)
                                while (true) {
                                    val count = client.getInputStream().read(buffer)
                                    if (count < 0) break
                                    client.getOutputStream().write(buffer, 0, count)
                                    client.getOutputStream().flush()
                                }
                            } catch (_: Exception) {
                            } finally {
                                clients.remove(client)
                                client.close()
                            }
                        }
                    }
                } catch (_: SocketException) {
                }
            }
        }

        override fun close() {
            running.set(false)
            server.close()
            clients.toList().forEach { it.close() }
        }
    }

    private class UdpEchoServer : Closeable {
        private val running = AtomicBoolean(true)
        private val socket = DatagramSocket(0)
        val port: Int = socket.localPort

        init {
            thread(name = "Test-UDP-Echo", isDaemon = true) {
                val buffer = ByteArray(1024)
                try {
                    while (running.get()) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        socket.send(DatagramPacket(packet.data, packet.length, packet.address, packet.port))
                    }
                } catch (_: SocketException) {
                }
            }
        }

        override fun close() {
            running.set(false)
            socket.close()
        }
    }
}
