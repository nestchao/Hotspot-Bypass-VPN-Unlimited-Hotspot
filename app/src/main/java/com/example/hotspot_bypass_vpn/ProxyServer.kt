package com.example.hotspot_bypass_vpn

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class ProxyServer {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val PORT = 8080

    private var clientPool: ThreadPoolExecutor? = null
    private val udpRelays = ConcurrentHashMap<String, UdpAssociation>()

    // Statistics
    private val activeConnections = AtomicInteger(0)
    private val totalConnections = AtomicInteger(0)

    fun start() {
        if (isRunning) {
            Log.w("ProxyServer", "Server already running")
            return
        }
        isRunning = true

        clientPool = ThreadPoolExecutor(
            100,
            2000,
            60L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(10000),
            ThreadFactory { r -> Thread(r).apply {
                priority = Thread.NORM_PRIORITY + 1
                name = "Proxy-${Thread.currentThread().id}"
            }},
            ThreadPoolExecutor.CallerRunsPolicy()
        )

        thread(name = "ProxyServer-Main", isDaemon = true) {
            try {
                serverSocket = ServerSocket()
                serverSocket?.reuseAddress = true
                serverSocket?.receiveBufferSize = 131072

                val bindAddress = InetSocketAddress("0.0.0.0", PORT)
                serverSocket?.bind(bindAddress, 200)

                Log.d("ProxyServer", "✓ PROXY SERVER STARTED (TCP + UDP) on Port $PORT")

                while (isRunning && serverSocket?.isClosed == false) {
                    try {
                        val client = serverSocket?.accept()
                        if (client != null) {
                            totalConnections.incrementAndGet()
                            client.tcpNoDelay = true
                            client.keepAlive = true
                            client.soTimeout = 120000

                            clientPool?.execute {
                                handleClient(client)
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning) Log.e("ProxyServer", "Accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ProxyServer", "✗ Server Error", e)
            }
        }

        // Start UDP cleanup thread
        thread(name = "UDP-Cleanup", isDaemon = true) {
            while (isRunning) {
                try {
                    Thread.sleep(30000) // Every 30 seconds
                    val now = System.currentTimeMillis()
                    udpRelays.entries.removeIf { (key, relay) ->
                        if (now - relay.lastActivity > 60000) { // 60 second timeout
                            relay.close()
                            Log.d("ProxyServer", "Cleaned up stale UDP relay: $key")
                            true
                        } else false
                    }
                } catch (e: Exception) {
                    Log.e("ProxyServer", "UDP cleanup error: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        Log.d("ProxyServer", "Stopping server...")
        isRunning = false

        // Close all UDP relays
        udpRelays.values.forEach { it.close() }
        udpRelays.clear()

        clientPool?.shutdownNow()
        clientPool = null

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("ProxyServer", "Error stopping: ${e.message}")
        }
    }

    private fun handleClient(client: Socket) {
        val clientId = "${client.inetAddress.hostAddress}:${client.port}"
        activeConnections.incrementAndGet()

        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // SOCKS5 Handshake
            val version = input.read()
            if (version == -1) return
            if (version != 5) {
                Log.w("ProxyServer", "[$clientId] Invalid SOCKS version: $version")
                return
            }

            val nMethods = input.read()
            if (nMethods > 0) input.skip(nMethods.toLong())

            // Send: No authentication required
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            // Read request
            if (input.read() != 5) return
            val cmd = input.read()
            input.read() // skip reserved
            val atyp = input.read()

            // Parse target address
            var targetHost = ""
            when (atyp) {
                1 -> { // IPv4
                    val ipBytes = ByteArray(4)
                    input.read(ipBytes)
                    targetHost = InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                }
                3 -> { // Domain name
                    val len = input.read()
                    val domainBytes = ByteArray(len)
                    input.read(domainBytes)
                    targetHost = String(domainBytes)
                }
                4 -> { // IPv6
                    val ipBytes = ByteArray(16)
                    input.read(ipBytes)
                    targetHost = InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                }
                else -> {
                    Log.w("ProxyServer", "[$clientId] Unsupported address type: $atyp")
                    return
                }
            }

            val portBytes = ByteArray(2)
            input.read(portBytes)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            when (cmd) {
                1 -> handleTcpConnect(client, input, output, targetHost, targetPort, clientId)
                3 -> handleUdpAssociate(client, input, output, clientId)
                else -> {
                    Log.w("ProxyServer", "[$clientId] Unsupported command: $cmd")
                    // Send command not supported
                    output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    output.flush()
                }
            }

        } catch (e: Exception) {
            Log.e("ProxyServer", "[$clientId] Error: ${e.message}", e)
        } finally {
            try { client.close() } catch (e: Exception) {}
            activeConnections.decrementAndGet()
            Log.d("ProxyServer", "[$clientId] Connection closed (Active: ${activeConnections.get()})")
        }
    }

    private fun handleTcpConnect(
        client: Socket,
        input: InputStream,
        output: OutputStream,
        targetHost: String,
        targetPort: Int,
        clientId: String
    ) {
        val targetSocket = Socket()
        targetSocket.soTimeout = 30000
        targetSocket.tcpNoDelay = true
        targetSocket.keepAlive = true

        try {
            targetSocket.connect(InetSocketAddress(targetHost, targetPort), 15000)
            Log.d("ProxyServer", "[$clientId] ✓ TCP Connected to $targetHost:$targetPort")

            // Send success response
            val response = byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0)
            output.write(response)
            output.flush()

            val latch = CountDownLatch(2)

            // Client -> Target
            thread(isDaemon = true, name = "Proxy-C2T-$clientId") {
                try {
                    pipeOptimized(input, targetSocket.getOutputStream(), clientId, "C->T")
                } catch (e: Exception) {
                    Log.d("ProxyServer", "[$clientId] C->T pipe closed: ${e.message}")
                } finally {
                    latch.countDown()
                    try { targetSocket.shutdownOutput() } catch (e: Exception) {}
                }
            }

            // Target -> Client
            thread(isDaemon = true, name = "Proxy-T2C-$clientId") {
                try {
                    pipeOptimized(targetSocket.getInputStream(), output, clientId, "T->C")
                } catch (e: Exception) {
                    Log.d("ProxyServer", "[$clientId] T->C pipe closed: ${e.message}")
                } finally {
                    latch.countDown()
                    try { client.shutdownOutput() } catch (e: Exception) {}
                }
            }

            latch.await(180, TimeUnit.SECONDS)

        } catch (e: Exception) {
            Log.e("ProxyServer", "[$clientId] TCP Connection failed: ${e.message}")
            try {
                output.write(byteArrayOf(0x05, 0x05.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()
            } catch (ex: Exception) {}
        } finally {
            try { targetSocket.close() } catch (e: Exception) {}
        }
    }

    private fun handleUdpAssociate(
        client: Socket,
        input: InputStream,
        output: OutputStream,
        clientId: String
    ) {
        try {
            // Create UDP socket for relay
            val udpSocket = DatagramSocket()
            val udpPort = udpSocket.localPort

            Log.d("ProxyServer", "[$clientId] ✓ UDP ASSOCIATE on port $udpPort")

            // Send success response with UDP port
            val response = ByteArray(10)
            response[0] = 0x05 // Version
            response[1] = 0x00 // Success
            response[2] = 0x00 // Reserved
            response[3] = 0x01 // IPv4

            // Bind address (0.0.0.0)
            response[4] = 0x00
            response[5] = 0x00
            response[6] = 0x00
            response[7] = 0x00

            // UDP Port
            response[8] = (udpPort shr 8).toByte()
            response[9] = (udpPort and 0xFF).toByte()

            output.write(response)
            output.flush()

            // Create UDP relay
            val relay = UdpAssociation(clientId, udpSocket, client.inetAddress.hostAddress ?: "unknown")
            udpRelays[clientId] = relay
            relay.start()

            // Keep TCP connection alive until client closes it
            try {
                while (input.read() != -1) {
                    // Just keep reading to detect when client closes
                }
            } catch (e: Exception) {
                Log.d("ProxyServer", "[$clientId] UDP control connection closed")
            }

        } catch (e: Exception) {
            Log.e("ProxyServer", "[$clientId] UDP ASSOCIATE failed: ${e.message}")
            try {
                output.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()
            } catch (ex: Exception) {}
        } finally {
            udpRelays.remove(clientId)?.close()
        }
    }

    private fun pipeOptimized(ins: InputStream, out: OutputStream, clientId: String = "", direction: String = "") {
        val buffer = ByteArray(32768)
        var totalBytes = 0
        try {
            var len: Int
            while (ins.read(buffer).also { len = it } != -1) {
                out.write(buffer, 0, len)
                out.flush()
                totalBytes += len
            }
            if (direction.isNotEmpty() && totalBytes > 0) {
                Log.d("ProxyServer", "[$clientId] $direction transferred ${totalBytes / 1024}KB")
            }
        } catch (e: Exception) {
            // Normal closure
        }
    }

    inner class UdpAssociation(
        private val clientId: String,
        private val socket: DatagramSocket,
        private val clientAddress: String
    ) {
        @Volatile var lastActivity = System.currentTimeMillis()
        private var isRunning = true

        fun start() {
            thread(isDaemon = true, name = "UDP-Relay-$clientId") {
                val buffer = ByteArray(65536)
                socket.soTimeout = 5000 // 5 second timeout for receive

                while (isRunning) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        lastActivity = System.currentTimeMillis()

                        // Parse SOCKS5 UDP header
                        val data = packet.data
                        var offset = 0

                        // Skip RSV (2 bytes) and FRAG (1 byte)
                        offset += 3

                        val atyp = data[offset].toInt() and 0xFF
                        offset++

                        var targetHost = ""
                        when (atyp) {
                            1 -> { // IPv4
                                targetHost = "${data[offset].toInt() and 0xFF}.${data[offset+1].toInt() and 0xFF}." +
                                        "${data[offset+2].toInt() and 0xFF}.${data[offset+3].toInt() and 0xFF}"
                                offset += 4
                            }
                            3 -> { // Domain
                                val len = data[offset].toInt() and 0xFF
                                offset++
                                targetHost = String(data, offset, len)
                                offset += len
                            }
                            4 -> { // IPv6
                                val ipBytes = data.copyOfRange(offset, offset + 16)
                                targetHost = InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                                offset += 16
                            }
                        }

                        val targetPort = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset+1].toInt() and 0xFF)
                        offset += 2

                        // Extract actual UDP payload
                        val payloadSize = packet.length - offset
                        val payload = data.copyOfRange(offset, offset + payloadSize)

                        // Forward to target
                        try {
                            val targetAddress = InetAddress.getByName(targetHost)
                            val forwardPacket = DatagramPacket(payload, payload.size, targetAddress, targetPort)
                            socket.send(forwardPacket)

                            Log.d("ProxyServer", "[$clientId] UDP -> $targetHost:$targetPort (${payload.size} bytes)")

                            // Start receiver for responses
                            receiveAndRelay(targetHost, targetPort, packet.address, packet.port)

                        } catch (e: Exception) {
                            Log.e("ProxyServer", "[$clientId] UDP forward error: ${e.message}")
                        }

                    } catch (e: SocketTimeoutException) {
                        // Normal timeout, check if should continue
                        if (System.currentTimeMillis() - lastActivity > 60000) {
                            Log.d("ProxyServer", "[$clientId] UDP relay timeout")
                            break
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e("ProxyServer", "[$clientId] UDP receive error: ${e.message}")
                        }
                        break
                    }
                }

                close()
            }
        }

        private fun receiveAndRelay(
            originalHost: String,
            originalPort: Int,
            clientAddr: InetAddress,
            clientPort: Int
        ) {
            thread(isDaemon = true, name = "UDP-Receiver-$clientId-$originalPort") {
                val recvSocket = DatagramSocket()
                recvSocket.soTimeout = 10000 // 10 second timeout

                try {
                    val buffer = ByteArray(65536)
                    val packet = DatagramPacket(buffer, buffer.size)

                    while (isRunning) {
                        try {
                            recvSocket.receive(packet)
                            lastActivity = System.currentTimeMillis()

                            // Build SOCKS5 UDP header
                            val responseData = buildUdpResponsePacket(
                                originalHost,
                                originalPort,
                                packet.data.copyOf(packet.length)
                            )

                            // Send back to client
                            val responsePacket = DatagramPacket(
                                responseData,
                                responseData.size,
                                clientAddr,
                                clientPort
                            )
                            socket.send(responsePacket)

                            Log.d("ProxyServer", "[$clientId] UDP <- $originalHost:$originalPort (${packet.length} bytes)")

                        } catch (e: SocketTimeoutException) {
                            break // No more responses
                        } catch (e: Exception) {
                            if (isRunning) {
                                Log.e("ProxyServer", "[$clientId] UDP response error: ${e.message}")
                            }
                            break
                        }
                    }
                } finally {
                    recvSocket.close()
                }
            }
        }

        private fun buildUdpResponsePacket(host: String, port: Int, payload: ByteArray): ByteArray {
            val header = mutableListOf<Byte>()

            // RSV (2 bytes)
            header.add(0x00)
            header.add(0x00)

            // FRAG
            header.add(0x00)

            // ATYP and Address
            if (host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                // IPv4
                header.add(0x01)
                host.split(".").forEach { header.add(it.toInt().toByte()) }
            } else {
                // Domain
                header.add(0x03)
                header.add(host.length.toByte())
                host.toByteArray().forEach { header.add(it) }
            }

            // Port
            header.add((port shr 8).toByte())
            header.add((port and 0xFF).toByte())

            // Combine header + payload
            return header.toByteArray() + payload
        }

        fun close() {
            isRunning = false
            try {
                socket.close()
            } catch (e: Exception) {}
        }
    }
}