package com.example.hotspot_bypass_vpn

import android.util.Log
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal enum class ProxyLogLevel { DEBUG, WARN, ERROR }

internal fun interface ProxyLogger {
    fun log(level: ProxyLogLevel, message: String, error: Throwable?)
}

private object AndroidProxyLogger : ProxyLogger {
    override fun log(level: ProxyLogLevel, message: String, error: Throwable?) {
        when (level) {
            ProxyLogLevel.DEBUG -> Log.d("ProxyServer", message)
            ProxyLogLevel.WARN -> Log.w("ProxyServer", message, error)
            ProxyLogLevel.ERROR -> Log.e("ProxyServer", message, error)
        }
    }
}

class ProxyServer internal constructor(
    private val config: Config,
    private val logger: ProxyLogger
) {
    constructor() : this(Config(), AndroidProxyLogger)

    internal constructor(config: Config) : this(config, ProxyLogger { _, _, _ -> })

    internal data class Config(
        val port: Int = 8080,
        val coreThreads: Int = 16,
        val maxThreads: Int = 256,
        val maxConnectionsPerClient: Int = 128,
        val workerKeepAliveMs: Long = 60_000L,
        val connectTimeoutMs: Int = 15_000,
        val handshakeTimeoutMs: Int = 15_000,
        val socketPollIntervalMs: Int = 60_000,
        val tcpIdleTimeoutMs: Long = 600_000L,
        val udpTargetTimeoutMs: Int = 30_000,
        val udpIdleTimeoutMs: Long = 300_000L,
        val udpCleanupIntervalMs: Long = 120_000L
    ) {
        init {
            require(port in 0..65535)
            require(coreThreads > 0)
            require(maxThreads >= coreThreads)
            require(maxConnectionsPerClient in 1..maxThreads)
            require(workerKeepAliveMs > 0)
            require(connectTimeoutMs > 0)
            require(handshakeTimeoutMs > 0)
            require(socketPollIntervalMs > 0)
            require(tcpIdleTimeoutMs >= socketPollIntervalMs)
            require(udpTargetTimeoutMs > 0)
            require(udpIdleTimeoutMs > 0)
            require(udpCleanupIntervalMs > 0)
        }
    }

    internal data class Metrics(
        val activeConnections: Int,
        val totalConnections: Int,
        val rejectedConnections: Int,
        val activeTcpSessions: Int,
        val activeUdpAssociations: Int,
        val activeByClient: Map<String, Int>,
        val executorActiveThreads: Int,
        val executorPoolSize: Int
    )

    private val running = AtomicBoolean(false)
    private val startLatch = CountDownLatch(1)
    private val connectionSequence = AtomicLong(0)
    private val udpAssociationSequence = AtomicLong(0)
    private val workerSequence = AtomicInteger(0)
    private val pipeSequence = AtomicInteger(0)

    private val activeConnections = AtomicInteger(0)
    private val totalConnections = AtomicInteger(0)
    private val rejectedConnections = AtomicInteger(0)
    private val activeByClient = ConcurrentHashMap<String, AtomicInteger>()
    private val activeClientSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val activeTargetSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val activeTcpSessions = ConcurrentHashMap.newKeySet<TcpSession>()
    private val udpAssociations = ConcurrentHashMap<Long, UdpAssociation>()

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var clientPool: ThreadPoolExecutor? = null
    @Volatile private var serverThread: Thread? = null
    @Volatile private var cleanupThread: Thread? = null
    @Volatile private var startFailure: Throwable? = null
    @Volatile private var listeningPort = -1

    fun start() {
        if (!running.compareAndSet(false, true)) {
            log(ProxyLogLevel.WARN, "Server already running")
            return
        }

        val executor = ThreadPoolExecutor(
            config.coreThreads,
            config.maxThreads,
            config.workerKeepAliveMs,
            TimeUnit.MILLISECONDS,
            SynchronousQueue(true),
            ThreadFactory { runnable ->
                Thread(runnable, "Proxy-Worker-${workerSequence.incrementAndGet()}").apply {
                    priority = Thread.NORM_PRIORITY + 1
                }
            },
            ThreadPoolExecutor.AbortPolicy()
        ).apply {
            allowCoreThreadTimeOut(true)
        }
        clientPool = executor

        serverThread = thread(name = "ProxyServer-Main", isDaemon = true) {
            runAcceptLoop(executor)
        }
        cleanupThread = thread(name = "Proxy-UDP-Cleanup", isDaemon = true) {
            runUdpCleanup()
        }
    }

    fun stop() {
        val wasRunning = running.getAndSet(false)
        if (wasRunning) log(ProxyLogLevel.DEBUG, "Stopping server...")

        tryClose(serverSocket)
        cleanupThread?.interrupt()

        activeTcpSessions.toList().forEach { it.close() }
        udpAssociations.values.toList().forEach { it.close() }
        activeClientSockets.toList().forEach { tryClose(it) }
        activeTargetSockets.toList().forEach { tryClose(it) }

        clientPool?.shutdownNow()
        logMetrics("Server stop requested")
    }

    internal fun awaitStarted(timeoutMs: Long = 5_000L): Boolean =
        startLatch.await(timeoutMs, TimeUnit.MILLISECONDS) && startFailure == null && listeningPort >= 0

    internal fun awaitStopped(timeoutMs: Long = 5_000L): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        serverThread?.join(remainingMillis(deadline))
        cleanupThread?.join(remainingMillis(deadline))
        val executor = clientPool
        if (executor != null) executor.awaitTermination(remainingMillis(deadline), TimeUnit.MILLISECONDS)
        return serverThread?.isAlive != true && cleanupThread?.isAlive != true && executor?.isTerminated != false
    }

    internal fun localPort(): Int = listeningPort

    internal fun metrics(): Metrics {
        val executor = clientPool
        return Metrics(
            activeConnections = activeConnections.get(),
            totalConnections = totalConnections.get(),
            rejectedConnections = rejectedConnections.get(),
            activeTcpSessions = activeTcpSessions.size,
            activeUdpAssociations = udpAssociations.size,
            activeByClient = activeByClient.mapValues { it.value.get() },
            executorActiveThreads = executor?.activeCount ?: 0,
            executorPoolSize = executor?.poolSize ?: 0
        )
    }

    private fun runAcceptLoop(executor: ThreadPoolExecutor) {
        try {
            val socket = ServerSocket().apply {
                reuseAddress = true
                receiveBufferSize = 256 * 1024
                bind(InetSocketAddress("0.0.0.0", config.port), 256)
            }
            serverSocket = socket
            listeningPort = socket.localPort
            startLatch.countDown()

            if (!running.get()) {
                tryClose(socket)
                return
            }

            log(
                ProxyLogLevel.DEBUG,
                "Proxy server started on port $listeningPort " +
                    "(workers=${config.coreThreads}-${config.maxThreads}, perClient=${config.maxConnectionsPerClient})"
            )

            while (running.get() && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    try {
                        configureAcceptedSocket(client)
                    } catch (error: Exception) {
                        tryClose(client)
                        throw error
                    }
                    totalConnections.incrementAndGet()
                    dispatchClient(executor, client)
                } catch (error: SocketException) {
                    if (running.get()) log(ProxyLogLevel.ERROR, "Accept failed", error)
                } catch (error: IOException) {
                    if (running.get()) log(ProxyLogLevel.ERROR, "Accept failed", error)
                }
            }
        } catch (error: Throwable) {
            startFailure = error
            running.set(false)
            startLatch.countDown()
            executor.shutdownNow()
            cleanupThread?.interrupt()
            log(ProxyLogLevel.ERROR, "Proxy server failed to start", error)
        } finally {
            tryClose(serverSocket)
            if (running.get()) {
                log(ProxyLogLevel.ERROR, "Accept loop exited while the proxy was still marked running")
                stop()
            }
        }
    }

    private fun configureAcceptedSocket(client: Socket) {
        client.tcpNoDelay = true
        client.keepAlive = true
        client.soTimeout = config.handshakeTimeoutMs
    }

    private fun dispatchClient(executor: ThreadPoolExecutor, client: Socket) {
        val clientIp = client.inetAddress.hostAddress ?: "unknown"
        if (!reserveConnection(clientIp)) {
            rejectClient(client, clientIp, "connection limit reached")
            return
        }

        activeClientSockets.add(client)
        try {
            executor.execute { handleClient(client, clientIp) }
            logMetrics("Accepted client $clientIp")
        } catch (error: RejectedExecutionException) {
            activeClientSockets.remove(client)
            releaseConnection(clientIp)
            rejectClient(client, clientIp, "worker pool saturated", error)
        }
    }

    private fun reserveConnection(clientIp: String): Boolean {
        while (true) {
            val current = activeConnections.get()
            if (current >= config.maxThreads) return false
            if (activeConnections.compareAndSet(current, current + 1)) break
        }

        var reserved = false
        activeByClient.compute(clientIp) { _, existing ->
            val counter = existing ?: AtomicInteger(0)
            if (counter.get() < config.maxConnectionsPerClient) {
                counter.incrementAndGet()
                reserved = true
            }
            counter
        }
        if (!reserved) activeConnections.decrementAndGet()
        return reserved
    }

    private fun releaseConnection(clientIp: String) {
        activeConnections.decrementAndGet()
        activeByClient.computeIfPresent(clientIp) { _, counter ->
            if (counter.decrementAndGet() <= 0) null else counter
        }
    }

    private fun rejectClient(client: Socket, clientIp: String, reason: String, error: Throwable? = null) {
        rejectedConnections.incrementAndGet()
        tryClose(client)
        val snapshot = metrics()
        log(
            ProxyLogLevel.WARN,
            "Rejected client $clientIp: $reason " +
                "(active=${snapshot.activeConnections}/${config.maxThreads}, " +
                "client=${snapshot.activeByClient[clientIp] ?: 0}/${config.maxConnectionsPerClient}, " +
                "executor=${snapshot.executorActiveThreads}/${config.maxThreads})",
            error
        )
    }

    private fun handleClient(client: Socket, clientIp: String) {
        val clientId = "$clientIp:${client.port}#${connectionSequence.incrementAndGet()}"
        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()
            val greeting = Socks5Parser.readGreeting(input)
            if (!greeting.supportsNoAuthentication) {
                output.write(Socks5Parser.buildMethodSelection(Socks5Parser.METHOD_NOT_ACCEPTABLE))
                output.flush()
                log(ProxyLogLevel.WARN, "[$clientId] Client did not offer no-authentication mode")
                return
            }

            output.write(Socks5Parser.buildMethodSelection(Socks5Parser.METHOD_NO_AUTH))
            output.flush()

            val request = try {
                Socks5Parser.readRequest(input)
            } catch (error: Socks5Parser.ProtocolException) {
                sendReply(output, error.replyCode)
                throw error
            }

            when (request.command) {
                Socks5Parser.COMMAND_CONNECT -> {
                    if (request.targetPort == 0) {
                        sendReply(output, Socks5Parser.REPLY_CONNECTION_REFUSED)
                        return
                    }
                    handleTcpConnect(client, input, output, request, clientId)
                }
                Socks5Parser.COMMAND_UDP_ASSOCIATE ->
                    handleUdpAssociate(client, input, output, clientIp, clientId)
                else -> {
                    sendReply(output, Socks5Parser.REPLY_COMMAND_NOT_SUPPORTED)
                    log(ProxyLogLevel.WARN, "[$clientId] Unsupported command: ${request.command}")
                }
            }
        } catch (error: EOFException) {
            log(ProxyLogLevel.DEBUG, "[$clientId] Client closed during SOCKS negotiation")
        } catch (error: SocketTimeoutException) {
            log(ProxyLogLevel.WARN, "[$clientId] SOCKS negotiation timed out")
        } catch (error: Socks5Parser.ProtocolException) {
            log(ProxyLogLevel.WARN, "[$clientId] Invalid SOCKS request: ${error.message}")
        } catch (error: Exception) {
            if (running.get()) log(ProxyLogLevel.ERROR, "[$clientId] Client error", error)
        } finally {
            activeClientSockets.remove(client)
            tryClose(client)
            releaseConnection(clientIp)
            logMetrics("[$clientId] Connection closed")
        }
    }

    private fun handleTcpConnect(
        client: Socket,
        input: InputStream,
        output: OutputStream,
        request: Socks5Parser.Request,
        clientId: String
    ) {
        val target = Socket().apply {
            tcpNoDelay = true
            keepAlive = true
            soTimeout = config.socketPollIntervalMs
        }
        client.soTimeout = config.socketPollIntervalMs

        var session: TcpSession? = null
        activeTargetSockets.add(target)
        try {
            if (!running.get()) throw SocketException("Proxy server is stopping")
            target.connect(InetSocketAddress(request.targetHost, request.targetPort), config.connectTimeoutMs)
            session = TcpSession(clientId, client, target)
            activeTcpSessions.add(session)

            sendReply(output, Socks5Parser.REPLY_SUCCEEDED)
            log(ProxyLogLevel.DEBUG, "[$clientId] TCP connected to ${request.targetHost}:${request.targetPort}")
            session.run(input, output)
        } catch (error: Exception) {
            if (session == null) sendReply(output, Socks5Parser.REPLY_CONNECTION_REFUSED)
            if (running.get()) {
                log(
                    ProxyLogLevel.ERROR,
                    "[$clientId] TCP connection to ${request.targetHost}:${request.targetPort} failed",
                    error
                )
            }
        } finally {
            session?.close() ?: tryClose(target)
            activeTargetSockets.remove(target)
        }
    }

    private fun handleUdpAssociate(
        client: Socket,
        input: InputStream,
        output: OutputStream,
        clientIp: String,
        clientId: String
    ) {
        var association: UdpAssociation? = null
        try {
            val relaySocket = DatagramSocket(0)
            val associationId = udpAssociationSequence.incrementAndGet()
            association = UdpAssociation(associationId, clientId, relaySocket, clientIp)
            udpAssociations[associationId] = association

            sendReply(output, Socks5Parser.REPLY_SUCCEEDED, relaySocket.localPort)
            log(
                ProxyLogLevel.DEBUG,
                "[$clientId] UDP association #$associationId listening on port ${relaySocket.localPort}"
            )
            association.start()

            client.soTimeout = config.socketPollIntervalMs
            while (running.get() && association.isOpen()) {
                try {
                    if (input.read() < 0) break
                } catch (_: SocketTimeoutException) {
                    // Poll so shutdown and idle cleanup do not depend on control traffic.
                }
            }
        } catch (error: Exception) {
            if (association == null) sendReply(output, Socks5Parser.REPLY_GENERAL_FAILURE)
            if (running.get()) log(ProxyLogLevel.ERROR, "[$clientId] UDP association failed", error)
        } finally {
            association?.close()
        }
    }

    private inner class TcpSession(
        private val clientId: String,
        private val client: Socket,
        private val target: Socket
    ) {
        private val closed = AtomicBoolean(false)
        private val lastActivity = AtomicLong(System.currentTimeMillis())

        fun run(clientInput: InputStream, clientOutput: OutputStream) {
            val reversePipe = thread(
                name = "Proxy-Pipe-${pipeSequence.incrementAndGet()}-$clientId",
                isDaemon = true
            ) {
                try {
                    pipe(target.getInputStream(), clientOutput, "target->client")
                } catch (error: Exception) {
                    if (!closed.get()) {
                        log(ProxyLogLevel.DEBUG, "[$clientId] target->client setup failed: ${error.message}")
                    }
                    close()
                } finally {
                    try {
                        client.shutdownOutput()
                    } catch (_: Exception) {
                    }
                }
            }

            try {
                pipe(clientInput, target.getOutputStream(), "client->target")
                try {
                    target.shutdownOutput()
                } catch (_: Exception) {
                }

                while (reversePipe.isAlive && !closed.get()) {
                    reversePipe.join(config.socketPollIntervalMs.toLong())
                    closeIfIdle()
                }
            } finally {
                close()
                reversePipe.join(config.socketPollIntervalMs.toLong().coerceAtMost(1_000L))
            }
        }

        private fun pipe(input: InputStream, output: OutputStream, direction: String) {
            val buffer = ByteArray(64 * 1024)
            var totalBytes = 0L
            while (!closed.get()) {
                val count = try {
                    input.read(buffer)
                } catch (_: SocketTimeoutException) {
                    closeIfIdle()
                    continue
                } catch (error: IOException) {
                    if (!closed.get()) log(ProxyLogLevel.DEBUG, "[$clientId] $direction closed: ${error.message}")
                    close()
                    return
                }

                if (count < 0) break
                if (count == 0) continue
                try {
                    output.write(buffer, 0, count)
                    output.flush()
                } catch (error: IOException) {
                    if (!closed.get()) log(ProxyLogLevel.DEBUG, "[$clientId] $direction write failed: ${error.message}")
                    close()
                    return
                }
                totalBytes += count
                lastActivity.set(System.currentTimeMillis())
            }
            if (totalBytes > 0) log(ProxyLogLevel.DEBUG, "[$clientId] $direction transferred ${totalBytes / 1024}KB")
        }

        private fun closeIfIdle() {
            val idleMs = System.currentTimeMillis() - lastActivity.get()
            if (idleMs >= config.tcpIdleTimeoutMs) {
                log(ProxyLogLevel.DEBUG, "[$clientId] TCP session idle for ${idleMs / 1000}s; closing")
                close()
            }
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            activeTcpSessions.remove(this)
            activeTargetSockets.remove(target)
            tryClose(target)
            tryClose(client)
        }
    }

    private inner class UdpAssociation(
        private val associationId: Long,
        private val clientId: String,
        private val relaySocket: DatagramSocket,
        private val clientIp: String
    ) {
        private val open = AtomicBoolean(true)
        private val clientEndpoint = AtomicReference<InetSocketAddress?>(null)
        private val targetSockets = ConcurrentHashMap<String, DatagramSocket>()
        @Volatile var lastActivity = System.currentTimeMillis()
            private set

        init {
            relaySocket.receiveBufferSize = 128 * 1024
            relaySocket.sendBufferSize = 128 * 1024
        }

        fun isOpen(): Boolean = open.get()

        fun start() {
            thread(name = "Proxy-UDP-$associationId", isDaemon = true) {
                val buffer = ByteArray(65_535)
                try {
                    while (running.get() && open.get()) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        relaySocket.receive(packet)
                        if (!acceptClientEndpoint(packet)) continue

                        try {
                            relayPacket(packet)
                        } catch (error: Exception) {
                            if (open.get() && running.get()) {
                                log(ProxyLogLevel.WARN, "[$clientId] Dropped UDP packet: ${error.message}")
                            }
                        }
                    }
                } catch (error: SocketException) {
                    if (open.get() && running.get()) log(ProxyLogLevel.ERROR, "[$clientId] UDP relay socket failed", error)
                } catch (error: Exception) {
                    if (open.get() && running.get()) log(ProxyLogLevel.ERROR, "[$clientId] UDP relay failed", error)
                } finally {
                    close()
                }
            }
        }

        private fun relayPacket(packet: DatagramPacket) {
            val target = Socks5Parser.parseUdpHeader(packet.data, packet.length)
            if (target == null || target.headerLength >= packet.length) {
                throw IOException("malformed SOCKS5 UDP header")
            }

            val payload = packet.data.copyOfRange(target.headerLength, packet.length)
            val targetAddress = InetAddress.getByName(target.targetHost)
            val key = "${targetAddress.hostAddress}:${target.targetPort}"
            sendToTarget(key, payload, targetAddress, target.targetPort)
            lastActivity = System.currentTimeMillis()
        }

        private fun sendToTarget(key: String, payload: ByteArray, address: InetAddress, port: Int) {
            var lastError: SocketException? = null
            repeat(2) {
                val socket = targetSockets[key] ?: createTargetSocket(key)
                try {
                    socket.send(DatagramPacket(payload, payload.size, address, port))
                    return
                } catch (error: SocketException) {
                    lastError = error
                    targetSockets.remove(key, socket)
                    socket.close()
                }
            }
            throw lastError ?: SocketException("Unable to create UDP target socket")
        }

        private fun acceptClientEndpoint(packet: DatagramPacket): Boolean {
            val packetIp = packet.address.hostAddress ?: return false
            if (packetIp != clientIp) {
                log(ProxyLogLevel.WARN, "[$clientId] Dropped UDP packet from unexpected client $packetIp")
                return false
            }

            val incoming = InetSocketAddress(packet.address, packet.port)
            val known = clientEndpoint.get()
            if (known == null && clientEndpoint.compareAndSet(null, incoming)) return true
            if (clientEndpoint.get() == incoming) return true

            log(ProxyLogLevel.WARN, "[$clientId] Dropped UDP packet from unexpected endpoint $incoming")
            return false
        }

        private fun createTargetSocket(key: String): DatagramSocket {
            val created = DatagramSocket().apply {
                soTimeout = config.udpTargetTimeoutMs
                receiveBufferSize = 128 * 1024
                sendBufferSize = 128 * 1024
            }
            val existing = targetSockets.putIfAbsent(key, created)
            if (existing != null) {
                created.close()
                return existing
            }
            startResponseListener(key, created)
            return created
        }

        private fun startResponseListener(key: String, socket: DatagramSocket) {
            thread(name = "Proxy-UDP-Response-$associationId-${pipeSequence.incrementAndGet()}", isDaemon = true) {
                val buffer = ByteArray(65_535)
                try {
                    while (running.get() && open.get()) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val endpoint = clientEndpoint.get() ?: continue
                        val header = Socks5Parser.buildUdpHeader(packet.address, packet.port)
                        val response = ByteArray(header.size + packet.length)
                        System.arraycopy(header, 0, response, 0, header.size)
                        System.arraycopy(packet.data, packet.offset, response, header.size, packet.length)
                        relaySocket.send(DatagramPacket(response, response.size, endpoint.address, endpoint.port))
                        lastActivity = System.currentTimeMillis()
                    }
                } catch (_: SocketTimeoutException) {
                    // Remove idle target sockets; the association remains available for future datagrams.
                } catch (error: Exception) {
                    if (open.get() && running.get()) {
                        log(ProxyLogLevel.DEBUG, "[$clientId] UDP target $key closed: ${error.message}")
                    }
                } finally {
                    targetSockets.remove(key, socket)
                    socket.close()
                }
            }
        }

        fun close() {
            if (!open.compareAndSet(true, false)) return
            udpAssociations.remove(associationId, this)
            targetSockets.values.toList().forEach { it.close() }
            targetSockets.clear()
            relaySocket.close()
        }
    }

    private fun runUdpCleanup() {
        while (running.get()) {
            try {
                Thread.sleep(config.udpCleanupIntervalMs)
            } catch (_: InterruptedException) {
                if (!running.get()) return
            }
            val now = System.currentTimeMillis()
            udpAssociations.values.toList().forEach { association ->
                if (now - association.lastActivity >= config.udpIdleTimeoutMs) {
                    log(ProxyLogLevel.DEBUG, "Closing idle UDP association")
                    association.close()
                }
            }
        }
    }

    private fun sendReply(output: OutputStream, replyCode: Int, bindPort: Int = 0) {
        try {
            output.write(Socks5Parser.buildReply(replyCode, bindPort))
            output.flush()
        } catch (_: Exception) {
        }
    }

    private fun logMetrics(prefix: String) {
        val snapshot = metrics()
        log(
            ProxyLogLevel.DEBUG,
            "$prefix (active=${snapshot.activeConnections}, clients=${snapshot.activeByClient}, " +
                "tcp=${snapshot.activeTcpSessions}, udp=${snapshot.activeUdpAssociations}, " +
                "workers=${snapshot.executorActiveThreads}/${snapshot.executorPoolSize}, " +
                "rejected=${snapshot.rejectedConnections})"
        )
    }

    private fun log(level: ProxyLogLevel, message: String, error: Throwable? = null) {
        logger.log(level, message, error)
    }

    private fun tryClose(socket: Socket?) {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    private fun tryClose(socket: ServerSocket?) {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    private fun remainingMillis(deadlineNanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis((deadlineNanos - System.nanoTime()).coerceAtLeast(0L)).coerceAtLeast(1L)
}
