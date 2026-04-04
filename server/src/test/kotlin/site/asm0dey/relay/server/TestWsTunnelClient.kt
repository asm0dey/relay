package site.asm0dey.relay.server

import com.google.protobuf.ByteString
import io.grpc.ManagedChannelBuilder
import io.quarkus.websockets.next.BasicWebSocketConnector
import io.quarkus.websockets.next.CloseReason
import io.quarkus.websockets.next.WebSocketClientConnection
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.Cancellable
import org.slf4j.LoggerFactory
import site.asm0dey.relay.domain.*
import site.asm0dey.relay.domain.ServerMessage.PayloadCase.*
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Lightweight test tunnel client for WebSocket proxy testing.
 * Connects to the relay server's gRPC endpoint, registers a domain,
 * and handles WsOpen by connecting to a local WebSocket echo server.
 *
 * Unlike the production TunnelClient, this is test-only and connects
 * the local WS to the same Quarkus test instance (at /local-echo path).
 */
class TestWsTunnelClient(
    private val relayPort: Int,
    private val domain: String,
    private val localWsPort: Int,
    private val localWsPath: String = "/",
    private val connectorProvider: () -> BasicWebSocketConnector,
) {
    private val log = LoggerFactory.getLogger(TestWsTunnelClient::class.java)
    private val outgoingQueue = LinkedBlockingQueue<ClientMessage>()
    private val localConnections = ConcurrentHashMap<String, WebSocketClientConnection>()
    private val registrationLatch = CountDownLatch(1)
    private val registrationSuccess = AtomicBoolean(false)
    private var subscription: Cancellable? = null
    private var channel: io.grpc.ManagedChannel? = null
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return

        val ch = ManagedChannelBuilder
            .forAddress("localhost", relayPort)
            .usePlaintext()
            .build()
        this.channel = ch
        val relay = MutinyTunnelServiceGrpc.newMutinyStub(ch)

        val streams = Multi.createBy().concatenating().streams(
            Multi.createFrom().item(clientMessage {
                correlationId = UUID.randomUUID().toString()
                register = registerRequest {
                    authToken = "secret"
                    desiredSubdomain = domain
                }
            }),
            Multi.createFrom().emitter {
                thread {
                    while (true) {
                        it.emit(outgoingQueue.take())
                    }
                }
            }
        )

        subscription = relay.openTunnel(streams)
            .subscribe().with(
                { serverMessage ->
                    when (serverMessage.payloadCase) {
                        ACK -> handleAck(serverMessage)
                        WS_OPEN -> handleWsOpen(serverMessage)
                        WS_FRAME -> handleWsFrame(serverMessage)
                        WS_CLOSE -> handleWsClose(serverMessage)
                        else -> log.trace("Ignoring message type: {}", serverMessage.payloadCase)
                    }
                },
                { error ->
                    log.error("Tunnel stream error", error)
                    registrationSuccess.set(false)
                    registrationLatch.countDown()
                }
            )

        registrationLatch.await(5, TimeUnit.SECONDS)
        if (!registrationSuccess.get()) {
            throw RuntimeException("Failed to register domain $domain")
        }
    }

    fun stop() {
        localConnections.values.forEach {
            try { it.closeAndAwait() } catch (_: Exception) {}
        }
        localConnections.clear()
        subscription?.cancel()
        channel?.shutdownNow()
        channel = null
        started.set(false)
    }

    private fun handleAck(msg: ServerMessage) {
        if (msg.ack.success) {
            log.debug("Registered domain: {}", msg.ack.assignedSubdomain)
            registrationSuccess.set(true)
        } else {
            log.error("Registration failed: {}", msg.ack.error)
            registrationSuccess.set(false)
        }
        registrationLatch.countDown()
    }

    private fun handleWsOpen(msg: ServerMessage) {
        val wsOpen = msg.wsOpen
        val connectionId = wsOpen.connectionId
        log.debug("WS_OPEN for connectionId={}", connectionId)

        val path = wsOpen.headersMap["path"] ?: localWsPath

        thread {
            try {
                var c = connectorProvider()
                    .baseUri(URI("http://localhost:$localWsPort"))
                    .path(if (path == "/") localWsPath else path)

                val subprotocols = wsOpen.headersMap["sec-websocket-protocol"]
                if (subprotocols != null) {
                    subprotocols.split(",").map { it.trim() }.forEach { c = c.addSubprotocol(it) }
                }

                c = c.onTextMessage { _, text ->
                    outgoingQueue.add(clientMessage {
                        correlationId = connectionId
                        wsFrame = wsFrame {
                            this.connectionId = connectionId
                            data = ByteString.copyFromUtf8(text)
                            isBinary = false
                        }
                    })
                }

                c = c.onBinaryMessage { _, buf ->
                    outgoingQueue.add(clientMessage {
                        correlationId = connectionId
                        wsFrame = wsFrame {
                            this.connectionId = connectionId
                            data = ByteString.copyFrom(buf.bytes)
                            isBinary = true
                        }
                    })
                }

                c = c.onClose { _, reason ->
                    localConnections.remove(connectionId)
                    outgoingQueue.add(clientMessage {
                        correlationId = connectionId
                        wsClose = wsCloseX {
                            this.connectionId = connectionId
                            code = reason.code
                            this.reason = reason.message ?: ""
                        }
                    })
                }

                val conn = c.connectAndAwait()
                localConnections[connectionId] = conn

                outgoingQueue.add(clientMessage {
                    correlationId = connectionId
                    wsUpgrade = wsUpgradeResponseX {
                        this.connectionId = connectionId
                        accepted = true
                        statusCode = 101
                        subprotocol = conn.subprotocol() ?: ""
                    }
                })
            } catch (e: Exception) {
                log.warn("Local WS connect failed: {}", e.message)
                outgoingQueue.add(clientMessage {
                    correlationId = connectionId
                    wsUpgrade = wsUpgradeResponseX {
                        this.connectionId = connectionId
                        accepted = false
                        statusCode = 403
                    }
                })
            }
        }
    }

    private fun handleWsFrame(msg: ServerMessage) {
        val frame = msg.wsFrame
        val conn = localConnections[frame.connectionId] ?: return
        if (frame.isPing) {
            conn.sendPingAndAwait(io.vertx.core.buffer.Buffer.buffer(frame.data.toByteArray()))
        } else if (frame.isBinary) {
            conn.sendBinaryAndAwait(io.vertx.core.buffer.Buffer.buffer(frame.data.toByteArray()))
        } else {
            conn.sendTextAndAwait(frame.data.toStringUtf8())
        }
    }

    private fun handleWsClose(msg: ServerMessage) {
        val close = msg.wsClose
        val conn = localConnections.remove(close.connectionId) ?: return
        try {
            conn.closeAndAwait(CloseReason(close.code, close.reason))
        } catch (_: Exception) {}
    }
}
