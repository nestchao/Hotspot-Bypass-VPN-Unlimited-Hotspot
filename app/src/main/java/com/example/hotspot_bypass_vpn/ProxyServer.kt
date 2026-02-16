package com.example.hotspot_bypass_vpn

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.InetSocketAddress
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class ProxyServer {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val PORT = 8080

    // CHANGE 1: Make clientPool nullable so we can recreate it
    private var clientPool: ThreadPoolExecutor? = null

    // Statistics
    private val activeConnections = AtomicInteger(0)
    private val totalConnections = AtomicInteger(0)
    private val bytesTransferred = AtomicInteger(0)

    fun start() {
        if (isRunning) {
            Log.w("ProxyServer", "Server already running")
            return
        }
        isRunning = true

        // CHANGE 2: Create the ThreadPool HERE, every time we start
        clientPool = ThreadPoolExecutor(
            100,  // Core threads
            2000, // Max threads
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

                Log.d("ProxyServer", "✓ ULTRA-FAST SERVER STARTED on Port $PORT")

                while (isRunning && serverSocket?.isClosed == false) {
                    try {
                        val client = serverSocket?.accept()
                        if (client != null) {
                            totalConnections.incrementAndGet()

                            client.tcpNoDelay = true
                            client.keepAlive = true
                            client.soTimeout = 120000

                            // CHANGE 3: Use the local clientPool variable safely
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
    }

    fun stop() {
        Log.d("ProxyServer", "Stopping server...")
        isRunning = false

        // CHANGE 4: Shutdown and nullify the pool
        clientPool?.shutdownNow()
        clientPool = null

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("ProxyServer", "Error stopping: ${e.message}")
        }
    }

    // ... (Keep handleClient and pipeOptimized exactly as they were in your paste) ...

    private fun handleClient(client: Socket) {
        // ... paste your existing handleClient logic here ...
        // (I am omitting it to save space, but DO NOT delete it from your file)
        // Ensure you copy the handleClient and pipeOptimized methods from your previous code

        val clientId = "${client.inetAddress.hostAddress}:${client.port}"
        activeConnections.incrementAndGet()

        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // SOCKS5 Handshake
            val version = input.read()
            if (version == -1) return
            if (version != 5) return

            val nMethods = input.read()
            if (nMethods > 0) input.skip(nMethods.toLong())

            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            if (input.read() != 5) return
            val cmd = input.read()

            // IMPORTANT: Your proxy only supports TCP (Cmd 1).
            // If Tun2Socks sends UDP (Cmd 3), this will fail.
            if (cmd != 1) {
                Log.w("ProxyServer", "[$clientId] UDP Not supported in this simple proxy")
                return
            }

            input.read() // skip reserved
            val atyp = input.read()

            var targetHost = ""
            when (atyp) {
                1 -> {
                    val ipBytes = ByteArray(4)
                    input.read(ipBytes)
                    targetHost = InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                }
                3 -> {
                    val len = input.read()
                    val domainBytes = ByteArray(len)
                    input.read(domainBytes)
                    targetHost = String(domainBytes)
                }
                else -> return
            }

            val portBytes = ByteArray(2)
            input.read(portBytes)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            val targetSocket = Socket()
            targetSocket.soTimeout = 30000 // 30 second timeout
            targetSocket.tcpNoDelay = true
            targetSocket.keepAlive = true

            try {
                // ADD: Better timeout handling
                targetSocket.connect(InetSocketAddress(targetHost, targetPort), 15000)

                Log.d("ProxyServer", "[$clientId] ✓ Connected to $targetHost:$targetPort")

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

                latch.await(180, TimeUnit.SECONDS) // Increase timeout to 3 minutes

            } catch (e: Exception) {
                Log.e("ProxyServer", "[$clientId] Connection failed: ${e.message}")
                // Send connection refused response
                try {
                    output.write(byteArrayOf(0x05, 0x05.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    output.flush()
                } catch (ex: Exception) {}
            } finally {
                try { targetSocket.close() } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("ProxyServer", "[$clientId] Error: ${e.message}", e)
        } finally {
            try { client.close() } catch (e: Exception) {}
            activeConnections.decrementAndGet()
            Log.d("ProxyServer", "[$clientId] Connection closed (Active: ${activeConnections.get()})")
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
            if (direction.isNotEmpty()) {
                Log.d("ProxyServer", "[$clientId] $direction transferred ${totalBytes / 1024}KB")
            }
        } catch (e: Exception) {
            // Normal closure
        }
    }
}